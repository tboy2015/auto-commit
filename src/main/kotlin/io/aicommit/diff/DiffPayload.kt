package io.aicommit.diff

data class DiffPayload(
    val diff: String,
    val files: List<String>,
    val recentCommits: List<String>,
    val branch: String,
)
