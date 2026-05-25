package io.aicommit.prompt

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class CommitMessageFormatterTest {
    @Test
    fun `adds paragraph break and bullets to generated body lines`() {
        val raw = """
            feat(settings): move API key storage to local settings
            settings: AppSettings adds apiKeys storage
            settings: SecretStore reads local settings
            test: add API key persistence coverage
        """.trimIndent()

        val formatted = CommitMessageFormatter.format(raw)

        assertEquals(
            """
            feat(settings): move API key storage to local settings

            - settings: AppSettings adds apiKeys storage
            - settings: SecretStore reads local settings
            - test: add API key persistence coverage
            """.trimIndent(),
            formatted,
        )
    }

    @Test
    fun `keeps already formatted messages stable`() {
        val raw = """
            fix(prompt): format generated commit bodies

            - prompt: add formatter for streamed output
            - service: normalize message after generation
            """.trimIndent()

        assertEquals(raw, CommitMessageFormatter.format(raw))
    }
}
