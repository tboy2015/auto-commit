package io.aicommit.prompt

data class TruncationResult(val text: String, val truncated: Boolean)

object Truncator {
    fun truncate(diff: String, maxChars: Int): TruncationResult {
        if (diff.length <= maxChars) return TruncationResult(diff, false)

        val files = splitByFile(diff)
        val budgetPerFile = (maxChars / files.size).coerceAtLeast(120)
        val head = budgetPerFile / 2
        val tail = budgetPerFile - head

        val sb = StringBuilder()
        for (f in files) {
            if (f.length <= budgetPerFile) {
                sb.append(f)
            } else {
                sb.append(f.take(head))
                sb.append("\n... truncated ...\n")
                sb.append(f.takeLast(tail))
            }
            sb.append('\n')
        }
        return TruncationResult(sb.toString().trimEnd(), true)
    }

    private fun splitByFile(diff: String): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        for (line in diff.lines()) {
            if (line.startsWith("diff --git ") && current.isNotEmpty()) {
                parts += current.toString(); current.clear()
            }
            current.appendLine(line)
        }
        if (current.isNotEmpty()) parts += current.toString()
        return parts
    }
}
