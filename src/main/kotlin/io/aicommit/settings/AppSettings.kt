package io.aicommit.settings

import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil
import io.aicommit.diff.DiffMode
import java.util.UUID

@State(name = "AICommitSettings", storages = [Storage("aicommit.xml")])
@Service(Service.Level.APP)
class AppSettings : PersistentStateComponent<AppSettings.State> {
    data class State(
        var providers: MutableList<Provider> = mutableListOf(),
        var activeProviderId: String? = null,
        var convention: String = "conventional",
        var language: String = "中文",
        var includeRecentCommits: Boolean = true,
        var recentCommitCount: Int = 5,
        var includeFilePaths: Boolean = true,
        var maxDiffChars: Int = 12000,
        var clearMessageBeforeGenerate: Boolean = true,
        var redactSecrets: Boolean = true,
        var customSystemPrompt: String? = null,
        var customUserTemplate: String? = null,
        var diffMode: DiffMode = DiffMode.WITH_FILES,
    )

    private var state = State()
    override fun getState(): State = state
    override fun loadState(s: State) { XmlSerializerUtil.copyBean(s, state) }

    fun activeProvider(): Provider? =
        state.activeProviderId?.let { id -> state.providers.firstOrNull { it.id == id } }

    /** Get-or-create the provider for a preset id (one per preset, idempotent). */
    fun getOrCreatePresetProvider(presetId: String): Provider {
        state.providers.firstOrNull { it.presetId == presetId && !it.isCustom }?.let { return it }
        val preset = ProviderPresets.byId(presetId) ?: ProviderPresets.byId("custom")!!
        val fresh = Provider(
            id = UUID.randomUUID().toString(),
            presetId = preset.id,
            name = preset.displayName,
            baseUrl = preset.baseUrl,
            model = preset.defaultModel,
        )
        state.providers.add(fresh)
        return fresh
    }

    fun addCustom(name: String = "Custom"): Provider {
        val existingCustom = state.providers.count { it.isCustom }
        val suffix = if (existingCustom == 0) "" else " ${existingCustom + 1}"
        val fresh = Provider(
            id = UUID.randomUUID().toString(),
            presetId = "custom",
            name = "$name$suffix",
            baseUrl = "",
            model = "",
        )
        state.providers.add(fresh)
        return fresh
    }

    fun update(provider: Provider) {
        val idx = state.providers.indexOfFirst { it.id == provider.id }
        if (idx >= 0) state.providers[idx] = provider else state.providers.add(provider)
    }

    fun remove(providerId: String) {
        state.providers.removeAll { it.id == providerId }
        if (state.activeProviderId == providerId) state.activeProviderId = null
        SecretStore.clear(providerId)
    }

    fun setActive(providerId: String) {
        if (state.providers.any { it.id == providerId }) state.activeProviderId = providerId
    }

    companion object {
        fun get(): AppSettings = service()
    }
}
