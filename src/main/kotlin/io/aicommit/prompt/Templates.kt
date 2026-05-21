package io.aicommit.prompt

object Templates {
    data class Convention(val id: String, val displayName: String, val systemPrompt: String)

    val conventions: List<Convention> = listOf(
        Convention("conventional", "Conventional Commits",
            """You write git commit messages strictly following Conventional Commits 1.0.0.
            |Format: `<type>(<scope>): <subject>` then a blank line then optional body.
            |Allowed types: feat, fix, docs, style, refactor, perf, test, build, ci, chore, revert.
            |Subject: imperative mood, no trailing period, <= 72 chars.
            |Output ONLY the commit message, no markdown fences, no explanations.""".trimMargin()),
        Convention("conventional-emoji", "Conventional + Gitmoji",
            """Same rules as Conventional Commits, but prefix the subject with the matching gitmoji
            |(e.g. ✨ for feat, 🐛 for fix, 📝 for docs, ♻️ for refactor, ⚡ for perf, ✅ for test).
            |Output ONLY the commit message.""".trimMargin()),
        Convention("gitmoji", "Gitmoji only",
            """You write git commit messages prefixed with a gitmoji that matches the change.
            |Single-line subject preferred, body optional. Output ONLY the message.""".trimMargin()),
        Convention("simple", "Simple",
            """Write a clear, concise git commit message: one imperative subject line (<=72 chars),
            |optional body explaining the why. Output ONLY the message.""".trimMargin()),
    )

    const val DEFAULT_USER_TEMPLATE: String = """Generate a git commit message in {{language}}.

Recent commit style for reference:
{{recent_commits}}

Changed files:
{{files}}

Diff{{#truncated}} (truncated){{/truncated}}:
{{diff}}
"""

    fun systemFor(id: String, custom: String?): String =
        if (id == "custom" && !custom.isNullOrBlank()) custom
        else (conventions.firstOrNull { it.id == id } ?: conventions[0]).systemPrompt
}
