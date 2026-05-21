package io.aicommit.llm

import io.aicommit.settings.Provider
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class OpenAICompatibleClientTest {
    private lateinit var server: MockWebServer

    @BeforeEach fun setUp() { server = MockWebServer(); server.start() }
    @AfterEach fun tearDown() { server.shutdown() }

    private fun provider() = Provider(
        id = "t", name = "t",
        baseUrl = server.url("/v1").toString(),
        model = "m",
    )

    @Test
    fun `streams chunks from sse`() = runTest {
        val body = """
            data: {"choices":[{"delta":{"content":"Hel"}}]}

            data: {"choices":[{"delta":{"content":"lo"}}]}

            data: [DONE]

        """.trimIndent()
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(body))

        val client = OpenAICompatibleClient(provider(), apiKey = "k")
        val chunks = client.stream(listOf(ChatMessage("user", "hi"))).toList()
        assertEquals(listOf("Hel", "lo"), chunks)
    }

    @Test
    fun `401 maps to Auth`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("nope"))
        val client = OpenAICompatibleClient(provider(), apiKey = "bad")
        assertThrows<LLMException.Auth> { client.stream(listOf(ChatMessage("user","x"))).toList() }
    }

    @Test
    fun `429 maps to RateLimited`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429))
        val client = OpenAICompatibleClient(provider(), apiKey = "k")
        assertThrows<LLMException.RateLimited> { client.stream(listOf(ChatMessage("user","x"))).toList() }
    }

    @Test
    fun `400 context length maps to ContextTooLong`() = runTest {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":{"message":"context length exceeded"}}"""))
        val client = OpenAICompatibleClient(provider(), apiKey = "k")
        assertThrows<LLMException.ContextTooLong> { client.stream(listOf(ChatMessage("user","x"))).toList() }
    }
}
