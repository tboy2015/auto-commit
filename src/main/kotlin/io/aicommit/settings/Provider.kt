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
    /** 上次验证成功/失败所对应的 Base URL。变更后 UI 会提示需要重新验证。 */
    var lastVerifiedBaseUrl: String = "",
    /** 上次验证成功/失败所对应 API Key 的短 hash 标记，不存明文 key。 */
    var lastVerifiedApiKeyMarker: String = "",
    /** 上次验证完成时间，epoch millis；0 表示从未验证。 */
    var lastVerifiedAt: Long = 0,
    /** 为空表示上次验证成功；非空表示上次验证失败的摘要。 */
    var lastVerifyError: String = "",
    /** 最近一次成功刷新得到的模型列表。 */
    var cachedModels: List<String> = emptyList(),
    /** 最近一次成功刷新模型列表的时间，epoch millis；0 表示从未刷新。 */
    var cachedModelsAt: Long = 0,
) {
    val isCustom: Boolean get() = presetId == "custom"
}
