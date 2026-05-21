package io.aicommit.prompt

import io.aicommit.diff.DiffPayload
import io.aicommit.llm.ChatMessage
import io.aicommit.settings.AppSettings

object PromptBuilder {
    fun build(
        payload: DiffPayload,
        s: AppSettings.State,
        userTemplate: String?,
    ): List<ChatMessage> {
        val system = Templates.systemFor(s.convention, s.customSystemPrompt)

        val cleaned = if (s.redactSecrets) Redactor.scrub(payload.diff) else payload.diff
        val tr = Truncator.truncate(cleaned, s.maxDiffChars)

        val template = (s.customUserTemplate ?: userTemplate ?: Templates.DEFAULT_USER_TEMPLATE)
        val recent = if (s.includeRecentCommits)
            payload.recentCommits.take(s.recentCommitCount).joinToString("\n") { "- $it" }
        else ""
        val files = if (s.includeFilePaths) payload.files.joinToString("\n") { "- $it" } else ""

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
