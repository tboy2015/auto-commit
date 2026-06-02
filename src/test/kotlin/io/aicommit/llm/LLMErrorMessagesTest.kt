package io.aicommit.llm

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LLMErrorMessagesTest {
    @Test
    fun `reasoning only response gives actionable hint without recommending flash`() {
        val message = LLMErrorMessages.userMessage(
            LLMException.BadResponse("本次流式响应只有 reasoning_content，没有 content 正文。")
        )

        assertTrue(message.contains("调大最大输出 token"))
        assertTrue(message.contains("减少本次 diff 范围"))
        assertFalse(message.contains("deepseek-v4-flash"))
    }
}
