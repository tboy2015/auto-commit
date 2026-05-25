package io.aicommit.prompt

import io.aicommit.diff.DiffMode
import io.aicommit.diff.DiffPayload
import io.aicommit.llm.ChatMessage
import io.aicommit.settings.AppSettings
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    private fun payload() = DiffPayload(
        diff = "diff --git a/A.kt b/A.kt\n+val x = 1\n",
        files = listOf("A.kt"),
        recentCommits = listOf("feat: a", "fix: b"),
        branch = "main",
    )

    @Test
    fun `WITH_FILES mode includes file paths but not history`() {
        val msgs: List<ChatMessage> = PromptBuilder.build(payload(), settings(), userTemplate = null, mode = DiffMode.WITH_FILES)
        assertEquals(2, msgs.size)
        assertEquals("system", msgs[0].role)
        assertEquals("user", msgs[1].role)
        val u = msgs[1].content
        assertTrue(u.contains("中文"))
        assertTrue(u.contains("A.kt"))
        assertTrue(u.contains("val x = 1"))
        assertFalse(u.contains("feat: a"))
    }

    @Test
    fun `WITH_HISTORY mode includes recent commits but not file paths`() {
        val msgs = PromptBuilder.build(payload(), settings(), userTemplate = null, mode = DiffMode.WITH_HISTORY)
        val u = msgs[1].content
        assertTrue(u.contains("feat: a"))
        assertTrue(u.contains("fix: b"))
        assertFalse(u.contains("- A.kt"))
    }

    @Test
    fun `STAGED_ONLY mode includes neither files nor history`() {
        val msgs = PromptBuilder.build(payload(), settings(), userTemplate = null, mode = DiffMode.STAGED_ONLY)
        val u = msgs[1].content
        assertFalse(u.contains("- A.kt"))
        assertFalse(u.contains("feat: a"))
        assertTrue(u.contains("val x = 1"))
    }

    @Test
    fun `marks diff truncated`() {
        val big = "diff --git a/A.kt b/A.kt\n" + "+x\n".repeat(2000)
        val p = DiffPayload(big, listOf("A.kt"), emptyList(), "main")
        val s = settings().also { it.maxDiffChars = 200 }
        val msgs = PromptBuilder.build(p, s, userTemplate = null)
        assertTrue(msgs[1].content.contains("(truncated)"))
    }

    @Test
    fun `conventions expose localized labels and descriptions`() {
        val conventional = Templates.conventionFor("conventional")

        assertEquals("标准提交（Conventional Commits）", conventional.displayName)
        assertTrue(conventional.description.contains("规范化提交信息"))
    }
}
