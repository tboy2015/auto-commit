package io.aicommit.settings

data class ProviderPreset(
    val id: String,
    val displayName: String,
    val baseUrl: String,
    val defaultModel: String,
    val notes: String = "",
)

object ProviderPresets {
    val all: List<ProviderPreset> = listOf(
        ProviderPreset("openai", "OpenAI",
            "https://api.openai.com/v1", "gpt-4o-mini"),
        ProviderPreset("anthropic", "Anthropic Claude",
            "https://api.anthropic.com/v1", "claude-sonnet-4-5",
            "Anthropic 原生 API；某些路径与 OpenAI 不完全兼容"),
        ProviderPreset("deepseek", "DeepSeek",
            "https://api.deepseek.com/v1", "deepseek-chat"),
        ProviderPreset("kimi", "Kimi (Moonshot)",
            "https://api.moonshot.cn/v1", "moonshot-v1-8k"),
        ProviderPreset("glm", "智谱 GLM",
            "https://open.bigmodel.cn/api/paas/v4", "glm-4-flash"),
        ProviderPreset("qwen", "通义千问 DashScope",
            "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus"),
        ProviderPreset("siliconflow", "硅基流动 SiliconFlow",
            "https://api.siliconflow.cn/v1", "Qwen/Qwen2.5-7B-Instruct"),
        ProviderPreset("openrouter", "OpenRouter",
            "https://openrouter.ai/api/v1", "openai/gpt-4o-mini"),
        ProviderPreset("ollama", "Ollama (本地)",
            "http://localhost:11434/v1", "llama3.2", "本地无需 API Key"),
        ProviderPreset("lmstudio", "LM Studio (本地)",
            "http://localhost:1234/v1", "local-model", "本地无需 API Key"),
        ProviderPreset("custom", "Custom",
            "", ""),
    )

    fun byId(id: String): ProviderPreset? = all.firstOrNull { it.id == id }
}
