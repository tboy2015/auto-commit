package io.aicommit.settings

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProviderTest {
    @Test
    fun `round-trips through json`() {
        val p = Provider(id = "x", presetId = "deepseek", name = "DeepSeek", baseUrl = "https://api.deepseek.com/v1", model = "deepseek-chat")
        val json = Json.encodeToString(Provider.serializer(), p)
        val back = Json.decodeFromString(Provider.serializer(), json)
        assertEquals(p, back)
    }

    @Test
    fun `api keys round-trip through local settings state`() {
        val settings = AppSettings()

        settings.setApiKey("deepseek-provider", "sk-local")
        val persisted = settings.state.copy()

        val reloaded = AppSettings()
        reloaded.loadState(persisted)

        assertEquals("sk-local", reloaded.getApiKey("deepseek-provider"))

        reloaded.setApiKey("deepseek-provider", null)
        assertNull(reloaded.getApiKey("deepseek-provider"))
    }

    @Test
    fun `drops empty providers from old broken local settings`() {
        val settings = AppSettings()

        settings.loadState(
            AppSettings.State(
                providers = mutableListOf(Provider()),
                activeProviderId = "missing-provider",
            )
        )

        assertEquals(emptyList(), settings.state.providers)
        assertNull(settings.state.activeProviderId)
    }
}
