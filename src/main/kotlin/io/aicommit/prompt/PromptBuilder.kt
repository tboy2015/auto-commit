package io.aicommit.prompt

import io.aicommit.diff.DiffMode
import io.aicommit.diff.DiffPayload
import io.aicommit.llm.ChatMessage
import io.aicommit.settings.AppSettings

object PromptBuilder {
    fun build(
        payload: DiffPayload,
        s: AppSettings.State,
        userTemplate: String?,
        mode: DiffMode = DiffMode.WITH_FILES,
    ): List<ChatMessage> {
        val system = Templates.systemFor(s.convention, s.customSystemPrompt)

        val cleaned = if (s.redactSecrets) Redactor.scrub(payload.diff) else payload.diff
        val tr = Truncator.truncate(cleaned, s.maxDiffChars)

        val template = (s.customUserTemplate ?: userTemplate ?: Templates.DEFAULT_USER_TEMPLATE)

        // 按生成模式决定包含哪些上下文（覆盖 Settings 中的开关）
        val recent = when (mode) {
            DiffMode.WITH_HISTORY ->
                payload.recentCommits.take(s.recentCommitCount).joinToString("\n") { "- $it" }
            else -> ""
        }
        val files = when (mode) {
            DiffMode.WITH_FILES ->
                payload.files.joinToString("\n") { "- $it" }
            else -> ""
        }

        val rendered = template
            .replace("{{language}}", s.language)
            .replace("{{recent_commits}}", recent.ifBlank { "(none)" })
            .replace("{{files}}", files.ifBlank { "(omitted)" })
            .replace("{{diff}}", tr.text)
            .replace("{{branch}}", payload.branch)
            .replace("{{#truncated}}", if (tr.truncated) "" else "<!--")
            .replace("{{/truncated}}", if (tr.truncated) "" else "-->")

        return listOf(ChatMessage("system", system), ChatMessage("user", rendered))
    }
}
