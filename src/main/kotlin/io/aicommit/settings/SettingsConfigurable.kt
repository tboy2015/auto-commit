package io.aicommit.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import io.aicommit.prompt.Templates
import java.util.UUID
import javax.swing.JComponent
import javax.swing.JPanel

class SettingsConfigurable : Configurable {

    private val settings = AppSettings.get()
    private val model = CollectionListModel(settings.state.providers.toMutableList())
    private val list = JBList(model)
    private var editor: ProviderEditor? = null
    private val editorHolder = JPanel().apply { border = JBUI.Borders.empty(8) }

    private val convention = ComboBox(Templates.conventions.map { it.id }.toTypedArray()).apply {
        selectedItem = settings.state.convention
    }
    private val language = JBTextField(settings.state.language)
    private val recentCount = JBTextField(settings.state.recentCommitCount.toString())
    private val maxChars = JBTextField(settings.state.maxDiffChars.toString())

    override fun getDisplayName() = "AI Commit"

    override fun createComponent(): JComponent {
        list.addListSelectionListener {
            val sel = list.selectedValue ?: return@addListSelectionListener
            editor = ProviderEditor(sel)
            editorHolder.removeAll()
            editorHolder.add(editor!!.component)
            editorHolder.revalidate(); editorHolder.repaint()
        }

        val providersUi = ToolbarDecorator.createDecorator(list)
            .setAddAction {
                val p = Provider(
                    id = UUID.randomUUID().toString(),
                    name = "New",
                    baseUrl = "https://api.openai.com/v1",
                    model = "gpt-4o-mini",
                )
                model.add(p); list.selectedIndex = model.size - 1
            }
            .setRemoveAction {
                val idx = list.selectedIndex
                if (idx >= 0) { SecretStore.clear(model.getElementAt(idx).id); model.remove(idx) }
            }
            .createPanel()

        return panel {
            group("Providers") {
                row { cell(providersUi).resizableColumn() }
                row { cell(editorHolder).resizableColumn() }
            }
            group("Generation") {
                row("Convention:") { cell(convention) }
                row("Language:") { cell(language) }
                row("Recent commits N:") { cell(recentCount) }
                row("Max diff chars:") { cell(maxChars) }
            }
        }
    }

    override fun isModified(): Boolean = true

    override fun apply() {
        editor?.let { ed ->
            val updated = ed.apply()
            val idx = list.selectedIndex
            if (idx >= 0) model.setElementAt(updated, idx)
        }
        settings.state.providers = model.items.toMutableList()
        if (settings.state.activeProviderId == null && model.items.isNotEmpty()) {
            settings.state.activeProviderId = model.items.first().id
        }
        settings.state.convention = (convention.selectedItem as? String) ?: "conventional"
        settings.state.language = language.text.trim().ifBlank { "English" }
        settings.state.recentCommitCount = recentCount.text.toIntOrNull() ?: 5
        settings.state.maxDiffChars = maxChars.text.toIntOrNull() ?: 12000
    }

    override fun reset() {
        model.replaceAll(settings.state.providers)
    }
}
