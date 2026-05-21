package io.aicommit.prompt

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RedactorTest {
    @Test
    fun `masks key=value secrets`() {
        val out = Redactor.scrub("api_key=abcd1234\nfoo=bar")
        assertEquals("api_key=***\nfoo=bar", out)
    }

    @Test
    fun `drops content of dotenv-like hunks by path`() {
        val diff = """
            diff --git a/.env b/.env
            +SECRET=hunter2
            diff --git a/src/Foo.kt b/src/Foo.kt
            +val x = 1
        """.trimIndent()
        val out = Redactor.scrub(diff)
        assertTrue(out.contains("[redacted: .env]"))
        assertTrue(out.contains("val x = 1"))
        assertTrue(!out.contains("hunter2"))
    }
}
