package io.aicommit.settings

import kotlinx.serialization.Serializable

@Serializable
data class Provider(
    var id: String = "",
    var presetId: String = "",            // "openai" | "deepseek" | ... | "custom"
    var name: String = "",                // display name (tab title for custom; preset.displayName for preset)
    var baseUrl: String = "",
    var model: String = "",               // currently selected ("active") model
    var enabledModels: List<String> = emptyList(),
    var temperature: Double = 0.8,
    var maxTokens: Int = 512,
    var timeoutSec: Int = 60,
    var extraHeaders: Map<String, String> = emptyMap(),
    /** 显式代理，留空 = 跟随 IDEA 全局 HTTP Proxy。格式：`http://host:port` 或 `https://host:port`。 */
    var proxyUrl: String = "",
) {
    val isCustom: Boolean get() = presetId == "custom"
}
