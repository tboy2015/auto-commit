package io.aicommit.settings

import kotlinx.serialization.Serializable

@Serializable
data class Provider(
    val id: String,
    val presetId: String,            // "openai" | "deepseek" | ... | "custom"
    val name: String,                // display name (tab title for custom; preset.displayName for preset)
    val baseUrl: String,
    val model: String,               // currently selected ("active") model
    val enabledModels: List<String> = emptyList(),
    val temperature: Double = 0.8,
    val maxTokens: Int = 512,
    val timeoutSec: Int = 60,
    val extraHeaders: Map<String, String> = emptyMap(),
) {
    val isCustom: Boolean get() = presetId == "custom"
}
