package io.aicommit.settings

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ProviderTest {
    @Test
    fun `round-trips through json`() {
        val p = Provider(id = "x", name = "DeepSeek", baseUrl = "https://api.deepseek.com/v1", model = "deepseek-chat")
        val json = Json.encodeToString(Provider.serializer(), p)
        val back = Json.decodeFromString(Provider.serializer(), json)
        assertEquals(p, back)
    }
}
