package io.aicommit.service

import com.intellij.openapi.application.EDT
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.ui.CommitMessage
import io.aicommit.diff.DiffCollector
import io.aicommit.llm.LLMException
import io.aicommit.llm.OpenAICompatibleClient
import io.aicommit.prompt.PromptBuilder
import io.aicommit.settings.AppSettings
import io.aicommit.settings.SecretStore
import io.aicommit.ui.Notifications
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

@Service(Service.Level.PROJECT)
class CommitMsgService(private val project: Project) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var current: Job? = null

    val isGenerating: Boolean get() = current?.isActive == true

    fun cancel() { current?.cancel() }

    fun generate(messageUi: CommitMessage, changes: List<Change>) {
        if (isGenerating) { cancel(); return }
        val settings = AppSettings.get()
        val provider = settings.activeProvider()
        if (provider == null) {
            Notifications.warn(project, "No AI provider configured. Open Settings → Tools → AI Commit.")
            return
        }
        if (changes.isEmpty()) {
            Notifications.warn(project, "No staged changes."); return
        }

        current = scope.launch {
            try {
                val payload = project.service<DiffCollector>().collect(changes)
                val msgs = PromptBuilder.build(payload, settings.state, userTemplate = null)
                val key = SecretStore.get(provider.id)
                val client = OpenAICompatibleClient(provider, key)

                val truncated = msgs[1].content.contains("(truncated)")
                if (truncated) withContext(Dispatchers.EDT) {
                    Notifications.info(project, "Diff was truncated to fit model context.")
                }

                withContext(Dispatchers.EDT) {
                    if (settings.state.clearMessageBeforeGenerate) replaceMessage(messageUi, "")
                }

                client.stream(msgs).collect { chunk ->
                    withContext(Dispatchers.EDT) { appendMessage(messageUi, chunk) }
                }
            } catch (_: CancellationException) {
                // user-initiated, silent
            } catch (e: LLMException.Auth) {
                Notifications.error(project, "Auth failed: check API key.")
            } catch (e: LLMException.RateLimited) {
                Notifications.warn(project, "Rate limited, please retry shortly.")
            } catch (e: LLMException.ContextTooLong) {
                Notifications.warn(project, "Diff is still too long for the model. Consider splitting your commit.")
            } catch (e: LLMException.Network) {
                Notifications.error(project, "Network error: ${e.message}")
            } catch (e: LLMException.BadResponse) {
                Notifications.error(project, "Model error: ${e.message?.take(200)}")
            } catch (e: Throwable) {
                Notifications.error(project, "Unexpected: ${e.message}")
            }
        }
    }

    private fun appendMessage(ui: CommitMessage, chunk: String) {
        CommandProcessor.getInstance().executeCommand(project, {
            ui.setCommitMessage(ui.comment + chunk)
        }, "AI Commit Append", "ai-commit")
    }

    private fun replaceMessage(ui: CommitMessage, text: String) {
        CommandProcessor.getInstance().executeCommand(project, {
            ui.setCommitMessage(text)
        }, "AI Commit Reset", "ai-commit")
    }
}
