package io.aicommit.prompt

object Templates {
    data class Convention(val id: String, val displayName: String, val systemPrompt: String)

    val conventions: List<Convention> = listOf(
        Convention("conventional", "Conventional Commits",
            """You write high-quality git commit messages following Conventional Commits 1.0.0.
            |
            |OUTPUT FORMAT — this is critical:
            |Line 1: `<type>(<scope>): <subject>`
            |Line 2: (empty line)
            |Line 3+: bullet list, ONE bullet per line, each line ending with an actual newline character.
            |
            |Rules:
            |- type: feat | fix | docs | style | refactor | perf | test | build | ci | chore | revert
            |- scope: primary affected module from the diff (e.g. auth, web-ui, api). Omit if cross-cutting.
            |- subject: imperative mood, no trailing period, <= 72 chars; summarize the OVERALL change.
            |- Each body bullet starts with `- ` and is on its OWN LINE. Do NOT join bullets with extra spaces.
            |- Group bullets by module/file, ordered by importance. Skip pure formatting/whitespace.
            |- Describe intent, not line counts.
            |
            |EXAMPLE (note the real line breaks):
            |feat(southbound): 新增三方系统执行日志能力
            |
            |- southbound-server: 引入执行日志表与 DO，重构 `AbstractThirdSystemClient`
            |- southbound-server: 新增 `SouthboundApiRequest/Result/Exception` 用于结构化调用
            |- sql: 新增 `biz_southbound_execution_log` 表与达梦升级脚本
            |- web-ui: 增加调用日志查询页面
            |
            |Output ONLY the commit message. No markdown fences. No explanations before or after.""".trimMargin()),
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
