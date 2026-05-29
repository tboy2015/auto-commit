package io.aicommit.settings

data class ProviderPreset(
    val id: String,
    val displayName: String,
    val baseUrl: String,
    val defaultModel: String,
    val notes: String = "",
    val apiKeyUrl: String? = null,
)

object ProviderPresets {
    val all: List<ProviderPreset> = listOf(
        ProviderPreset("openai", "OpenAI",
            "https://api.openai.com/v1", "gpt-4o-mini",
            apiKeyUrl = "https://platform.openai.com/api-keys"),
        ProviderPreset("anthropic", "Anthropic Claude",
            "https://api.anthropic.com/v1", "claude-sonnet-4-5",
            "Anthropic 原生 API；某些路径与 OpenAI 不完全兼容",
            apiKeyUrl = "https://console.anthropic.com/settings/keys"),
        ProviderPreset("deepseek", "DeepSeek",
            "https://api.deepseek.com/v1", "deepseek-chat",
            "推荐使用 deepseek-chat（非推理）；deepseek-reasoner / v4-pro 是推理模型，不适合 commit 场景",
            apiKeyUrl = "https://platform.deepseek.com/api_keys"),
        ProviderPreset("kimi", "Kimi (Moonshot)",
            "https://api.moonshot.cn/v1", "moonshot-v1-8k",
            apiKeyUrl = "https://platform.moonshot.cn/console/api-keys"),
        ProviderPreset("glm", "智谱 GLM",
            "https://open.bigmodel.cn/api/paas/v4", "glm-4-flash",
            apiKeyUrl = "https://open.bigmodel.cn/usercenter/apikeys"),
        ProviderPreset("qwen", "通义千问 DashScope",
            "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus",
            apiKeyUrl = "https://bailian.console.aliyun.com/?tab=model#/api-key"),
        ProviderPreset("siliconflow", "硅基流动 SiliconFlow",
            "https://api.siliconflow.cn/v1", "Qwen/Qwen2.5-7B-Instruct",
            apiKeyUrl = "https://cloud.siliconflow.cn/account/ak"),
        ProviderPreset("openrouter", "OpenRouter",
            "https://openrouter.ai/api/v1", "openai/gpt-4o-mini",
            apiKeyUrl = "https://openrouter.ai/settings/keys"),
        ProviderPreset("ollama", "Ollama (本地)",
            "http://localhost:11434/v1", "llama3.2", "本地无需 API Key"),
        ProviderPreset("lmstudio", "LM Studio (本地)",
            "http://localhost:1234/v1", "local-model", "本地无需 API Key"),
        ProviderPreset("custom", "Custom",
            "", ""),
    )

    fun byId(id: String): ProviderPreset? = all.firstOrNull { it.id == id }
}
