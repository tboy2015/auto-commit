package io.aicommit.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.ChangeListener

class SettingsConfigurable : Configurable {
    private val settings = AppSettings.get()
    private val tabs = JBTabbedPane().apply {
        tabPlacement = javax.swing.JTabbedPane.LEFT
    }
    private val tabPanels = mutableListOf<ProviderTabPanel>()

    private val language = ComboBox(arrayOf("中文", "English", "日本語", "한국어")).apply {
        isEditable = true
        selectedItem = settings.state.language
    }
    private val recentCount = JBTextField(settings.state.recentCommitCount.toString())
    private val maxChars = JBTextField(settings.state.maxDiffChars.toString())
    private val promptEditor = PromptEditorPanel(settings)
    private val rootPanel: JPanel = JPanel(BorderLayout())

    override fun getDisplayName() = "Auto Commit"

    override fun createComponent(): JComponent {
        rebuildTabs()

        val addCustomBtn = JButton("+ Custom").apply {
            toolTipText = "新增自定义 Provider"
            addActionListener {
                val newProv = settings.addCustom()
                addTab(newProv)
                tabs.selectedIndex = tabs.tabCount - 1
            }
        }
        val removeBtn = JButton("- Remove").apply {
            toolTipText = "删除当前自定义 Provider，内置 Provider 不可删除"
            isEnabled = false
            addActionListener {
                val idx = tabs.selectedIndex
                if (idx < 0 || idx >= tabPanels.size) return@addActionListener
                val panel = tabPanels[idx]
                if (!panel.isCustom) return@addActionListener
                val confirm = Messages.showYesNoDialog(
                    "确定删除「${tabs.getTitleAt(idx)}」吗？此操作不可撤销。",
                    "删除 Provider",
                    Messages.getQuestionIcon(),
                )
                if (confirm == Messages.YES) {
                    settings.remove(panel.providerId)
                    rebuildTabs()
                }
            }
        }
        tabs.addChangeListener(ChangeListener {
            val idx = tabs.selectedIndex
            removeBtn.isEnabled = idx in tabPanels.indices && tabPanels[idx].isCustom
        })

        val topBar = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply {
            add(removeBtn)
            add(addCustomBtn)
        }

        val tabsContainer = JPanel(BorderLayout()).apply {
            add(topBar, BorderLayout.NORTH)
            add(tabs, BorderLayout.CENTER)
        }

        val generationPanel = panel {
            collapsibleGroup("生成参数") {
                row("输出语言:") { cell(language).align(AlignX.FILL) }
                row("最近提交数量:") { cell(recentCount) }
                row("最大 diff 字符数:") { cell(maxChars) }
            }
            collapsibleGroup("提示词模板") {
                row { cell(promptEditor.component).align(AlignX.FILL) }
            }
        }

        rootPanel.removeAll()
        rootPanel.add(tabsContainer, BorderLayout.CENTER)
        rootPanel.add(generationPanel, BorderLayout.SOUTH)
        return rootPanel
    }

    private fun rebuildTabs() {
        tabs.removeAll()
        tabPanels.clear()

        val presetOrder = listOf(
            "openai",
            "anthropic",
            "deepseek",
            "kimi",
            "glm",
            "qwen",
            "siliconflow",
            "openrouter",
            "ollama",
            "lmstudio",
        )
        for (pid in presetOrder) addTab(settings.getOrCreatePresetProvider(pid))
        for (custom in settings.state.providers.filter { it.isCustom }) addTab(custom)

        val activeId = settings.state.activeProviderId
        for (panel in tabPanels) panel.setActiveInPlace(panel.providerId == activeId)
        selectPreferredProviderTab(activeId)
    }

    private fun selectPreferredProviderTab(activeId: String?) {
        val preferredIndex = tabPanels.indexOfFirst { it.providerId == activeId }
            .takeIf { it >= 0 }
            ?: tabPanels.indexOfFirst { !SecretStore.get(it.providerId).isNullOrBlank() }

        if (preferredIndex >= 0 && preferredIndex < tabs.tabCount) {
            tabs.selectedIndex = preferredIndex
        }
    }

    private fun addTab(prov: Provider) {
        val panel = ProviderTabPanel(
            provider = prov,
            onActiveToggle = { snap ->
                settings.update(snap)
                settings.setActive(snap.id)
                for (p in tabPanels) if (p.providerId != snap.id) p.setActiveInPlace(false)
            },
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
        promptEditor.apply()
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
