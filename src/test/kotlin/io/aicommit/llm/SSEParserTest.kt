package io.aicommit.llm

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SSEParserTest {
    @Test
    fun `extracts content deltas and stops on DONE`() {
        val lines = listOf(
            """data: {"choices":[{"delta":{"content":"Hello"}}]}""",
            """data: {"choices":[{"delta":{"content":" world"}}]}""",
            "data: [DONE]",
        )
        val out = mutableListOf<String>()
        SSEParser.parse(lines.asSequence()) { out += it }
        assertEquals(listOf("Hello", " world"), out)
    }

    @Test
    fun `ignores comment and blank lines`() {
        val lines = listOf("", ":heartbeat", """data: {"choices":[{"delta":{"content":"x"}}]}""", "data: [DONE]")
        val out = mutableListOf<String>()
        SSEParser.parse(lines.asSequence()) { out += it }
        assertEquals(listOf("x"), out)
    }
}
