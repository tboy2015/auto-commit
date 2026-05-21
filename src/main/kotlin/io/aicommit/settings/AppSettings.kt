package io.aicommit.settings

import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil

@State(name = "AICommitSettings", storages = [Storage("aicommit.xml")])
@Service(Service.Level.APP)
class AppSettings : PersistentStateComponent<AppSettings.State> {
    data class State(
        var providers: MutableList<Provider> = mutableListOf(),
        var activeProviderId: String? = null,
        var convention: String = "conventional",
        var language: String = "English",
        var includeRecentCommits: Boolean = true,
        var recentCommitCount: Int = 5,
        var includeFilePaths: Boolean = true,
        var maxDiffChars: Int = 12000,
        var clearMessageBeforeGenerate: Boolean = true,
        var redactSecrets: Boolean = true,
        var customSystemPrompt: String? = null,
        var customUserTemplate: String? = null,
    )

    private var state = State()
    override fun getState(): State = state
    override fun loadState(s: State) { XmlSerializerUtil.copyBean(s, state) }

    fun activeProvider(): Provider? =
        state.activeProviderId?.let { id -> state.providers.firstOrNull { it.id == id } }

    companion object {
        fun get(): AppSettings = service()
    }
}
