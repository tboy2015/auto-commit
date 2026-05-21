package io.aicommit.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import io.aicommit.prompt.Templates
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import java.awt.BorderLayout
import java.awt.FlowLayout

class SettingsConfigurable : Configurable {

    private val settings = AppSettings.get()
    private val tabs = JBTabbedPane()
    private val tabPanels = mutableListOf<ProviderTabPanel>()

    private val convention = ComboBox(Templates.conventions.map { it.id }.toTypedArray()).apply {
        selectedItem = settings.state.convention
    }
    private val language = ComboBox(arrayOf("中文", "English", "日本語", "한국어")).apply {
        isEditable = true
        selectedItem = settings.state.language
    }
    private val recentCount = JBTextField(settings.state.recentCommitCount.toString())
    private val maxChars = JBTextField(settings.state.maxDiffChars.toString())

    private val rootPanel: JPanel = JPanel(BorderLayout())

    override fun getDisplayName() = "AI Commit"

    override fun createComponent(): JComponent {
        rebuildTabs()

        val addCustomBtn = JButton("+ Custom").apply {
            addActionListener {
                val newProv = settings.addCustom()
                addTab(newProv)
                tabs.selectedIndex = tabs.tabCount - 1
            }
        }
        val topBar = JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0)).apply { add(addCustomBtn) }

        val tabsContainer = JPanel(BorderLayout()).apply {
            add(topBar, BorderLayout.NORTH)
            add(tabs, BorderLayout.CENTER)
        }

        val generationPanel = panel {
            group("生成") {
                row("Convention:") { cell(convention) }
                row("Language:") { cell(language).align(AlignX.FILL) }
                row("Recent commits N:") { cell(recentCount) }
                row("Max diff chars:") { cell(maxChars) }
            }
        }

        rootPanel.removeAll()
        rootPanel.add(tabsContainer, BorderLayout.CENTER)
        rootPanel.add(generationPanel, BorderLayout.SOUTH)
        return rootPanel
    }

    private fun rebuildTabs() {
        tabs.removeAll(); tabPanels.clear()
        // Fixed preset tabs (excluding "custom")
        val presetOrder = listOf("openai", "anthropic", "deepseek", "kimi", "glm", "qwen", "siliconflow", "openrouter", "ollama", "lmstudio")
        for (pid in presetOrder) {
            val prov = settings.getOrCreatePresetProvider(pid)
            addTab(prov)
        }
        // Custom tabs
        for (custom in settings.state.providers.filter { it.isCustom }) addTab(custom)

        // Reflect current active
        val activeId = settings.state.activeProviderId
        for (panel in tabPanels) panel.setActiveInPlace(panel.providerId == activeId)
    }

    private fun addTab(prov: Provider) {
        val panel = ProviderTabPanel(
            provider = prov,
            onActiveToggle = { snap ->
                settings.update(snap)
                settings.setActive(snap.id)
                // Uncheck all other tabs visually
                for (p in tabPanels) if (p.providerId != snap.id) p.setActiveInPlace(false)
            },
            onRemove = if (prov.isCustom) ({
                settings.remove(prov.id)
                rebuildTabs()
            }) else null,
        ).also { it.providerId = prov.id }
        tabPanels.add(panel)
        tabs.addTab(prov.name, panel.component)
    }

    override fun isModified(): Boolean = true

    override fun apply() {
        for (p in tabPanels) {
            val updated = p.apply()
            settings.update(updated)
            if (p.isActive) settings.setActive(updated.id)
        }
        settings.state.convention = (convention.selectedItem as? String) ?: "conventional"
        settings.state.language = (language.selectedItem as? String)?.trim().orEmpty().ifBlank { "中文" }
        settings.state.recentCommitCount = recentCount.text.toIntOrNull() ?: 5
        settings.state.maxDiffChars = maxChars.text.toIntOrNull() ?: 12000
    }

    override fun reset() {
        rebuildTabs()
    }
}

private var ProviderTabPanel.providerId: String
    get() = idMap[this] ?: ""
    set(value) { idMap[this] = value }

private val idMap = java.util.WeakHashMap<ProviderTabPanel, String>()
