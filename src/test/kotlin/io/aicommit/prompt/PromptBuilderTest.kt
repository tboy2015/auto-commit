package io.aicommit.prompt

import io.aicommit.diff.DiffPayload
import io.aicommit.llm.ChatMessage
import io.aicommit.settings.AppSettings
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PromptBuilderTest {
    private fun settings() = AppSettings.State(
        convention = "simple",
        language = "中文",
        includeRecentCommits = true,
        recentCommitCount = 2,
        includeFilePaths = true,
        maxDiffChars = 1000,
        redactSecrets = true,
    )

    @Test
    fun `renders system + user with variables`() {
        val payload = DiffPayload(
            diff = "diff --git a/A.kt b/A.kt\n+val x = 1\n",
            files = listOf("A.kt"),
            recentCommits = listOf("feat: a", "fix: b"),
            branch = "main",
        )
        val msgs: List<ChatMessage> = PromptBuilder.build(payload, settings(), userTemplate = null)
        assertEquals(2, msgs.size)
        assertEquals("system", msgs[0].role)
        assertEquals("user", msgs[1].role)
        val u = msgs[1].content
        assertTrue(u.contains("中文"))
        assertTrue(u.contains("A.kt"))
        assertTrue(u.contains("feat: a"))
        assertTrue(u.contains("val x = 1"))
    }

    @Test
    fun `marks diff truncated`() {
        val big = "diff --git a/A.kt b/A.kt\n" + "+x\n".repeat(2000)
        val payload = DiffPayload(big, listOf("A.kt"), emptyList(), "main")
        val s = settings().also { it.maxDiffChars = 200 }
        val msgs = PromptBuilder.build(payload, s, userTemplate = null)
        assertTrue(msgs[1].content.contains("(truncated)"))
    }
}
