package io.aicommit.settings

object ModelCatalog {
    fun tags(provider: Provider, modelId: String): List<String> {
        val id = modelId.lowercase()
        val tags = mutableListOf<String>()
        if (modelId == ProviderPresets.byId(provider.presetId)?.defaultModel) tags += "推荐"
        if (provider.presetId in setOf("ollama", "lmstudio")) tags += "本地"
        if (id.contains("flash") || id.contains("mini") || id.contains("turbo") || id.contains("haiku")) {
            tags += "快速"
        }
        if (id.contains("pro") || id.contains("sonnet") || id.contains("gpt-4o") || id.contains("opus")) {
            tags += "高质量"
        }
        if (id.contains("reasoner") ||
            id.contains("reasoning") ||
            id.contains("thinking") ||
            id.matches(Regex(""".*\bo[13](?:[-.].*)?$"""))
        ) {
            tags += "推理/思考"
        }
        return tags.distinct()
    }

    fun chooseBestModel(provider: Provider, models: List<String>): String {
        val current = provider.model.trim()
        val presetDefault = ProviderPresets.byId(provider.presetId)?.defaultModel.orEmpty()
        return when {
            current.isNotBlank() && models.contains(current) -> current
            presetDefault.isNotBlank() && models.contains(presetDefault) -> presetDefault
            else -> models.firstOrNull().orEmpty()
        }
    }

    fun modelsForUi(provider: Provider): List<String> =
        provider.cachedModels.ifEmpty {
            provider.enabledModels.ifEmpty {
                listOfNotNull(provider.model.takeIf { it.isNotBlank() })
            }
        }
}
