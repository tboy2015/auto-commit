package io.aicommit.prompt

object Templates {
    data class Convention(
        val id: String,
        val displayName: String,
        val description: String,
        val systemPrompt: String,
    )

    val conventions: List<Convention> = listOf(
        Convention(
            id = "conventional",
            displayName = "标准提交（Conventional Commits）",
            description = "生成 feat/fix/test/build 等规范化提交信息，适合团队协作和自动化 changelog。",
            systemPrompt = """You write high-quality git commit messages following Conventional Commits 1.0.0.
            |
            |OUTPUT FORMAT - this is critical:
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
            |feat(settings): persist API keys locally
            |
            |- settings: add local API key storage to AppSettings
            |- settings: read saved keys when opening provider configuration
            |- test: cover API key persistence across settings reloads
            |
            |Output ONLY the commit message. No markdown fences. No explanations before or after.""".trimMargin(),
        ),
        Convention(
            id = "conventional-emoji",
            displayName = "标准提交 + Gitmoji",
            description = "在 Conventional Commits 前加入合适的 Gitmoji，适合偏轻松、视觉识别强的提交风格。",
            systemPrompt = """Same rules as Conventional Commits, but prefix the subject with the matching gitmoji
            |(✨ feat, 🐛 fix, 📝 docs, ♻️ refactor, ⚡ perf, ✅ test, 🔧 chore, 👷 ci, 🎨 style).
            |Body grouped by module/file as bullets. Output ONLY the commit message.""".trimMargin(),
        ),
        Convention(
            id = "gitmoji",
            displayName = "Gitmoji 简洁风格",
            description = "只强调 emoji 和简短主题，适合个人项目或偏轻量的提交信息。",
            systemPrompt = """Prefix the subject line with the gitmoji that best matches the change.
            |Single-line subject preferred; if there are several distinct changes add a body of bullets grouped by module.
            |Output ONLY the message.""".trimMargin(),
        ),
        Convention(
            id = "simple",
            displayName = "简洁说明",
            description = "生成自然语言摘要，不强制 feat/fix 格式，适合临时提交或非规范化仓库。",
            systemPrompt = """Write a clear, concise git commit message:
            |- One imperative subject line (<= 72 chars) summarizing the overall change.
            |- If multiple meaningful changes exist, add a blank line then a bullet list grouped by module/file.
            |Output ONLY the message.""".trimMargin(),
        ),
    )

    const val DEFAULT_USER_TEMPLATE: String = """Write a git commit message in {{language}} that follows the rules above.

Use the staged diff as the primary source of truth. If file paths or recent commit history are provided, use them only as supporting context.

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

    fun conventionFor(id: String): Convention =
        conventions.firstOrNull { it.id == id } ?: conventions[0]
}
