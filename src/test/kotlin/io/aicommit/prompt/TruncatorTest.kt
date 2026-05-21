package io.aicommit.prompt

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TruncatorTest {
    @Test
    fun `under limit untouched`() {
        val r = Truncator.truncate("short diff", maxChars = 100)
        assertEquals("short diff", r.text)
        assertEquals(false, r.truncated)
    }

    @Test
    fun `over limit keeps head and tail per file`() {
        val big = buildString {
            append("diff --git a/A.kt b/A.kt\n")
            repeat(200) { append("+line$it\n") }
            append("diff --git a/B.kt b/B.kt\n")
            repeat(200) { append("+lineB$it\n") }
        }
        val r = Truncator.truncate(big, maxChars = 400)
        assertTrue(r.truncated)
        assertTrue(r.text.contains("a/A.kt"))
        assertTrue(r.text.contains("a/B.kt"))
        assertTrue(r.text.contains("... truncated ..."))
    }
}
