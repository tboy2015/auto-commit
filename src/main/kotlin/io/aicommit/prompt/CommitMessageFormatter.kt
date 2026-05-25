package io.aicommit.prompt

object CommitMessageFormatter {
    fun format(raw: String): String {
        val lines = raw
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .lines()
            .map { it.trimEnd() }
            .dropWhile { it.isBlank() }
            .dropLastWhile { it.isBlank() }

        if (lines.size <= 1) return lines.joinToString("\n")

        val subject = lines.first().trim()
        val body = lines.drop(1)
            .dropWhile { it.isBlank() }
            .mapNotNull { line ->
                val trimmed = line.trim()
                when {
                    trimmed.isBlank() -> null
                    isBullet(trimmed) -> trimmed
                    else -> "- $trimmed"
                }
            }

        return if (body.isEmpty()) subject else buildString {
            append(subject)
            append("\n\n")
            append(body.joinToString("\n"))
        }
    }

    private fun isBullet(line: String): Boolean =
        line.startsWith("- ") ||
            line.startsWith("* ") ||
            line.startsWith("+ ")
}
