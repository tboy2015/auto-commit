package io.aicommit.service

import com.intellij.ide.ActivityTracker
import com.intellij.openapi.application.EDT
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.ui.CommitMessage
import io.aicommit.diff.DiffCollector
import io.aicommit.diff.DiffMode
import io.aicommit.llm.LLMException
import io.aicommit.llm.OpenAICompatibleClient
import io.aicommit.prompt.CommitMessageFormatter
import io.aicommit.prompt.PromptBuilder
import io.aicommit.settings.AppSettings
import io.aicommit.settings.SecretStore
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
                clearPlaceholderAnd { Notifications.error(project, "认证失败：请检查 API Key。") }
            } catch (e: LLMException.RateLimited) {
                log.warn("rate limited", e)
                clearPlaceholderAnd { Notifications.warn(project, "触发限流，请稍后重试。") }
            } catch (e: LLMException.ContextTooLong) {
                log.warn("context too long", e)
                clearPlaceholderAnd { Notifications.warn(project, "diff 仍然过长，建议拆分 commit。") }
            } catch (e: LLMException.Network) {
                log.warn("network error", e)
                clearPlaceholderAnd { Notifications.error(project, "网络错误：${e.message}") }
            } catch (e: LLMException.BadResponse) {
                log.warn("bad response", e)
                clearPlaceholderAnd { Notifications.error(project, "模型返回异常：${e.message?.take(200)}") }
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
