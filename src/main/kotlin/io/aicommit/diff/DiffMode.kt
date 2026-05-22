package io.aicommit.diff

enum class DiffMode(val label: String) {
    STAGED_ONLY("仅 staged diff（最简）"),
    WITH_FILES("diff + 文件路径/模块名"),
    WITH_HISTORY("diff + 最近 N 条 commit 历史"),
}
