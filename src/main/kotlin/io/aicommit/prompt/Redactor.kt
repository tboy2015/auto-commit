package io.aicommit.prompt

object Redactor {
    private val sensitivePathRegex = Regex("""diff --git a/(\S+) b/\1""")
    private val sensitiveFile = Regex(""".*(\.env(\..+)?|\.pem|id_rsa|credentials\.json)$""")
    private val kvRegex = Regex("""(?i)(password|passwd|secret|token|api[_-]?key)\s*=\s*\S+""")

    fun scrub(diff: String): String {
        if (diff.isBlank()) return diff
        val sections = mutableListOf<MutableList<String>>()
        var current: MutableList<String>? = null
        for (line in diff.lines()) {
            if (line.startsWith("diff --git ")) {
                current = mutableListOf(line).also { sections += it }
            } else {
                if (current == null) { current = mutableListOf<String>().also { sections += it } }
                current.add(line)
            }
        }
        val out = StringBuilder()
        for ((i, section) in sections.withIndex()) {
            val header = section.firstOrNull().orEmpty()
            val path = sensitivePathRegex.find(header)?.groupValues?.get(1)
            if (path != null && sensitiveFile.matches(path)) {
                out.append(header).append('\n').append("[redacted: $path]")
            } else {
                section.joinTo(out, separator = "\n") { kvRegex.replace(it) { m -> "${m.groupValues[1]}=***" } }
            }
            if (i < sections.lastIndex) out.append('\n')
        }
        return out.toString()
    }
}
