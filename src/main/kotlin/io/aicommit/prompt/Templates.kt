package io.aicommit.prompt

object Templates {
    data class Convention(val id: String, val displayName: String, val systemPrompt: String)

    val conventions: List<Convention> = listOf(
        Convention("conventional", "Conventional Commits",
            """You write high-quality git commit messages following Conventional Commits 1.0.0.
            |
            |HEADER format: `<type>(<scope>): <subject>`
            |- type: feat | fix | docs | style | refactor | perf | test | build | ci | chore | revert
            |- scope: the primary affected module, derived from the diff (e.g. auth, web-ui, api). Omit if truly cross-cutting.
            |- subject: imperative mood, no trailing period, <= 72 chars, summarizes the OVERALL change.
            |
            |BODY (required when there are multiple meaningful changes):
            |- A blank line after the header, then a Markdown bullet list.
            |- GROUP bullets by module/file, ordered by importance.
            |- Each bullet: `- <module-or-file>: <what changed and why, concise>`.
            |- Cover every significant change visible in the diff; skip pure formatting/whitespace.
            |- Do NOT enumerate every line — describe the intent, not the line count.
            |
            |Output ONLY the commit message. No markdown fences. No leading/trailing explanations.""".trimMargin()),
        Convention("conventional-emoji", "Conventional + Gitmoji",
            """Same rules as Conventional Commits, but prefix the subject with the matching gitmoji
            |(✨ feat, 🐛 fix, 📝 docs, ♻️ refactor, ⚡ perf, ✅ test, 🔧 chore, 👷 ci, 🎨 style).
            |Body grouped by module/file as bullets. Output ONLY the commit message.""".trimMargin()),
        Convention("gitmoji", "Gitmoji only",
            """Prefix the subject line with the gitmoji that best matches the change.
            |Single-line subject preferred; if there are several distinct changes add a body of bullets grouped by module.
            |Output ONLY the message.""".trimMargin()),
        Convention("simple", "Simple",
            """Write a clear, concise git commit message:
            |- One imperative subject line (<= 72 chars) summarizing the overall change.
            |- If multiple meaningful changes exist, add a blank line then a bullet list grouped by module/file.
            |Output ONLY the message.""".trimMargin()),
    )

    const val DEFAULT_USER_TEMPLATE: String = """Write a git commit message in {{language}} that follows the rules above.

Identify the affected modules from the file paths and group your bullets accordingly. Order modules by significance of change. Skip noise (formatting-only edits, generated files).

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
