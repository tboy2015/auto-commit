package io.aicommit.service

import com.intellij.ide.BrowserUtil
import com.intellij.ide.ActivityTracker
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.openapi.application.EDT
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.ui.CommitMessage
import io.aicommit.diff.DiffCollector
import io.aicommit.diff.DiffMode
import io.aicommit.llm.LLMException
import io.aicommit.llm.LLMErrorMessages
import io.aicommit.llm.OpenAICompatibleClient
import io.aicommit.prompt.CommitMessageFormatter
import io.aicommit.prompt.PromptBuilder
import io.aicommit.settings.AppSettings
import io.aicommit.settings.ModelCatalog
import io.aicommit.settings.Provider
import io.aicommit.settings.ProviderPresets
import io.aicommit.settings.SecretStore
import io.aicommit.settings.SettingsConfigurable
import io.aicommit.ui.Notifications
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Service(Service.Level.PROJECT)
class CommitMsgService(private val project: Project) {
    private val log = Logger.getInstance(CommitMsgService::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var current: Job? = null

    val isGenerating: Boolean get() = current?.isActive == true

    fun cancel() {
        current?.cancel()
    }

    fun generate(messageUi: CommitMessage, changes: List<Change>, mode: DiffMode = DiffMode.WITH_FILES) {
        log.info("generate() called: isGenerating=$isGenerating, changes=${changes.size}, mode=$mode")
        if (isGenerating) {
            cancel()
            Notifications.info(project, "已取消本次生成。再次点击可开始新的生成。")
            return
        }

        val settings = AppSettings.get()
        val provider = settings.activeProvider()
        if (provider == null) {
            Notifications.warn(project, "未配置 AI Provider。请在 Settings → Tools → Auto Commit 中配置。")
            return
        }
        if (changes.isEmpty()) {
            Notifications.warn(project, "没有可生成提交信息的变更。请先 stage 或勾选文件。")
            return
        }

        replaceMessage(messageUi, "正在生成 commit message...")

        current = scope.launch {
            try {
                ActivityTracker.getInstance().inc()
                log.info("starting LLM call to ${provider.baseUrl} / model=${provider.model}")

                val payload = project.service<DiffCollector>().collect(changes)
                val msgs = PromptBuilder.build(payload, settings.state, userTemplate = null, mode = mode)
                val key = SecretStore.get(provider.id)
                val client = OpenAICompatibleClient(provider, key)

                if (msgs[1].content.contains("(truncated)")) {
                    withContext(Dispatchers.EDT) {
                        Notifications.info(project, "Diff 已截断以适配模型上下文。")
                    }
                }

                var firstChunk = true
                var totalLen = 0
                val generated = StringBuilder()
                client.stream(msgs).collect { chunk ->
                    totalLen += chunk.length
                    generated.append(chunk)
                    withContext(Dispatchers.EDT) {
                        if (firstChunk) {
                            replaceMessage(messageUi, chunk)
                            firstChunk = false
                        } else {
                            appendMessage(messageUi, chunk)
                        }
                    }
                }

                val formatted = CommitMessageFormatter.format(generated.toString())
                if (formatted.isNotBlank()) {
                    withContext(Dispatchers.EDT) {
                        replaceMessage(messageUi, formatted)
                    }
                }

                log.info("stream finished: chunks?=${!firstChunk}, totalLen=$totalLen")
                if (firstChunk) {
                    withContext(Dispatchers.EDT) {
                        replaceMessage(messageUi, "")
                        Notifications.warn(project, "模型没有返回内容。请检查模型名称、额度或服务端 SSE 返回。")
                    }
                }
            } catch (_: CancellationException) {
                log.info("generation cancelled by user")
                withContext(Dispatchers.EDT + NonCancellable) {
                    if (messageUi.comment.startsWith("正在生成")) replaceMessage(messageUi, "")
                }
            } catch (e: LLMException.Auth) {
                log.warn("auth failed", e)
                clearPlaceholderAnd { notifyFailure(e, isError = true) }
            } catch (e: LLMException.RateLimited) {
                log.warn("rate limited", e)
                clearPlaceholderAnd { notifyFailure(e, isError = false) }
            } catch (e: LLMException.ContextTooLong) {
                log.warn("context too long", e)
                clearPlaceholderAnd { notifyFailure(e, isError = false) }
            } catch (e: LLMException.Network) {
                log.warn("network error", e)
                clearPlaceholderAnd { notifyFailure(e, isError = true) }
            } catch (e: LLMException.BadResponse) {
                log.warn("bad response", e)
                clearPlaceholderAnd { notifyFailure(e, isError = true) }
            } catch (e: Throwable) {
                log.error("unexpected error during generation", e)
                clearPlaceholderAnd { Notifications.error(project, "未预期错误：${e.javaClass.simpleName}: ${e.message}") }
            } finally {
                ActivityTracker.getInstance().inc()
            }
        }
    }

    private suspend fun clearPlaceholderAnd(then: () -> Unit) {
        withContext(Dispatchers.EDT + NonCancellable) {
            then()
        }
    }

    private fun notifyFailure(error: LLMException, isError: Boolean) {
        val actions = failureActions(error)
        val message = LLMErrorMessages.userMessage(error)
        if (isError) Notifications.error(project, message, actions)
        else Notifications.warn(project, message, actions)
    }

    private fun failureActions(error: LLMException): List<NotificationAction> {
        val settings = AppSettings.get()
        val provider = settings.activeProvider()
        val actions = mutableListOf<NotificationAction>()
        actions += notificationAction("打开设置") {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, SettingsConfigurable::class.java)
        }
        if (error is LLMException.BadResponse) {
            actions += notificationAction("刷新模型") { refreshActiveProviderModels() }
            provider?.let { p ->
                ProviderPresets.byId(p.presetId)?.defaultModel
                    ?.takeIf { it.isNotBlank() && it != p.model }
                    ?.let { recommended ->
                        actions += notificationAction("切换到推荐模型") {
                            val enabled = (p.enabledModels + recommended).distinct()
                            settings.update(p.copy(model = recommended, enabledModels = enabled))
                            settings.setActive(p.id)
                        }
                    }
            }
        }
        provider?.let { p ->
            ProviderPresets.byId(p.presetId)?.apiKeyUrl?.let { url ->
                actions += notificationAction("获取 API Key") { BrowserUtil.browse(url) }
            }
        }
        return actions
    }

    private fun notificationAction(text: String, block: () -> Unit): NotificationAction =
        object : NotificationAction(text) {
            override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent, notification: Notification) {
                notification.expire()
                block()
            }
        }

    private fun refreshActiveProviderModels() {
        val settings = AppSettings.get()
        val provider = settings.activeProvider() ?: return
        runBackground("刷新 ${provider.name} 模型列表") {
            val key = SecretStore.get(provider.id)
            runCatching { OpenAICompatibleClient(provider, key).listModels() }
                .onSuccess { models ->
                    val updated = provider.withRefreshedModels(models)
                    settings.update(updated)
                    settings.setActive(updated.id)
                    ActivityTracker.getInstance().inc()
                    Notifications.info(project, "模型列表已刷新，当前模型：${updated.model.ifBlank { "(未选择)" }}")
                }
                .onFailure { e ->
                    Notifications.error(project, "刷新模型失败：${LLMErrorMessages.userMessage(e)}")
                }
        }
    }

    private fun Provider.withRefreshedModels(models: List<String>): Provider {
        val selected = ModelCatalog.chooseBestModel(this, models)
        val enabled = enabledModels.filter { models.contains(it) }.toMutableSet()
        if (selected.isNotBlank()) enabled.add(selected)
        return copy(
            model = selected.ifBlank { model },
            enabledModels = enabled.toList(),
            cachedModels = models,
            cachedModelsAt = System.currentTimeMillis(),
        )
    }

    private fun runBackground(title: String, block: () -> Unit) {
        com.intellij.openapi.progress.ProgressManager.getInstance().run(object :
            com.intellij.openapi.progress.Task.Backgroundable(project, title, true) {
            override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) { block() }
        })
    }

    private fun appendMessage(ui: CommitMessage, chunk: String) {
        CommandProcessor.getInstance().executeCommand(project, {
            ui.setCommitMessage(ui.comment + chunk)
        }, "Auto Commit Append", "ai-commit")
    }

    private fun replaceMessage(ui: CommitMessage, text: String) {
        CommandProcessor.getInstance().executeCommand(project, {
            ui.setCommitMessage(text)
        }, "Auto Commit Reset", "ai-commit")
    }
}
