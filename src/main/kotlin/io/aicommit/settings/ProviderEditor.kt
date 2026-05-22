package io.aicommit.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.panel
import io.aicommit.llm.OpenAICompatibleClient
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class ProviderEditor(private var provider: Provider) {
    private val name = JBTextField(provider.name)
    private val preset = ComboBox(DefaultComboBoxModel(ProviderPresets.all.toTypedArray())).apply {
        renderer = javax.swing.DefaultListCellRenderer().let { base ->
            javax.swing.ListCellRenderer<ProviderPreset> { list, value, index, selected, focus ->
                base.getListCellRendererComponent(list, value?.displayName ?: "", index, selected, focus)
            }
        }
        selectedItem = guessPreset(provider)
    }
    private val baseUrl = JBTextField(provider.baseUrl)

    private val modelComboModel = DefaultComboBoxModel<String>().apply {
        if (provider.model.isNotBlank()) addElement(provider.model)
    }
    private val modelCombo = ComboBox(modelComboModel).apply {
        isEditable = true
        selectedItem = provider.model
    }
    private val fetchModelsBtn = JButton("Fetch")

    private val apiKey = JBPasswordField().apply { text = SecretStore.get(provider.id).orEmpty() }
    private val temperature = JBTextField(provider.temperature.toString())
    private val maxTokens = JBTextField(provider.maxTokens.toString())
    private val timeout = JBTextField(provider.timeoutSec.toString())

    private var userTouchedBaseUrl = false
    private var userTouchedModel = false

    init {
        baseUrl.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) { userTouchedBaseUrl = true }
            override fun removeUpdate(e: DocumentEvent?) { userTouchedBaseUrl = true }
            override fun changedUpdate(e: DocumentEvent?) { userTouchedBaseUrl = true }
        })
        preset.addActionListener {
            val p = preset.selectedItem as? ProviderPreset ?: return@addActionListener
            if (p.id == "custom") return@addActionListener
            if (baseUrl.text.isBlank() || !userTouchedBaseUrl) {
                baseUrl.text = p.baseUrl; userTouchedBaseUrl = false
            }
            val currentModel = (modelCombo.selectedItem as? String).orEmpty()
            if (currentModel.isBlank() || !userTouchedModel) {
                setModel(p.defaultModel); userTouchedModel = false
            }
            if (name.text.isBlank() || name.text == "New") name.text = p.displayName
        }
        modelCombo.editor.editorComponent.let { ec ->
            if (ec is javax.swing.text.JTextComponent) {
                ec.document.addDocumentListener(object : DocumentListener {
                    override fun insertUpdate(e: DocumentEvent?) { userTouchedModel = true }
                    override fun removeUpdate(e: DocumentEvent?) { userTouchedModel = true }
                    override fun changedUpdate(e: DocumentEvent?) { userTouchedModel = true }
                })
            }
        }
        fetchModelsBtn.addActionListener { fetchModels() }
    }

    private fun setModel(m: String) {
        modelComboModel.removeAllElements()
        if (m.isNotBlank()) modelComboModel.addElement(m)
        modelCombo.selectedItem = m
    }

    private fun guessPreset(p: Provider): ProviderPreset =
        ProviderPresets.all.firstOrNull { it.baseUrl.isNotBlank() && p.baseUrl.startsWith(it.baseUrl) }
            ?: ProviderPresets.byId("custom")!!

    private fun snapshotProvider(): Provider = provider.copy(
        baseUrl = baseUrl.text.trim(),
        model = (modelCombo.selectedItem as? String).orEmpty().trim(),
        timeoutSec = timeout.text.toIntOrNull() ?: 60,
    )

    private fun fetchModels() {
        val snap = snapshotProvider()
        if (snap.baseUrl.isBlank()) {
            Messages.showWarningDialog("Base URL is empty.", "Auto Commit")
            return
        }
        val key = String(apiKey.password).takeIf { it.isNotBlank() } ?: SecretStore.get(provider.id)
        fetchModelsBtn.isEnabled = false
        fetchModelsBtn.text = "Fetching…"

        ProgressManagerCompat.runBackground("Fetching models from ${snap.baseUrl}") {
            runCatching { OpenAICompatibleClient(snap, key).listModels() }
                .onSuccess { models ->
                    ApplicationManager.getApplication().invokeLater({
                        val previouslySelected = modelCombo.selectedItem as? String
                        modelComboModel.removeAllElements()
                        models.forEach { modelComboModel.addElement(it) }
                        if (!previouslySelected.isNullOrBlank() && models.contains(previouslySelected)) {
                            modelCombo.selectedItem = previouslySelected
                        } else if (models.isNotEmpty()) {
                            modelCombo.selectedIndex = 0
                        }
                        fetchModelsBtn.isEnabled = true
                        fetchModelsBtn.text = "Fetch"
                        if (models.isEmpty()) Messages.showInfoMessage(
                            "Endpoint returned no models.", "Auto Commit"
                        )
                    }, ModalityState.any())
                }
                .onFailure { e ->
                    ApplicationManager.getApplication().invokeLater({
                        fetchModelsBtn.isEnabled = true
                        fetchModelsBtn.text = "Fetch"
                        Messages.showErrorDialog(
                            "Failed to fetch models:\n${e.message?.take(500)}", "Auto Commit"
                        )
                    }, ModalityState.any())
                }
        }
    }

    val component: JComponent = panel {
        row("Preset:") { cell(preset) }
        row("Name:") { cell(name).resizableColumn() }
        row("Base URL:") { cell(baseUrl).resizableColumn() }
        row("API Key:") { cell(apiKey).resizableColumn() }
        row("Model:") {
            cell(modelCombo).resizableColumn()
            cell(fetchModelsBtn)
        }
        row("Temperature:") { cell(temperature) }
        row("Max tokens:") { cell(maxTokens) }
        row("Timeout (sec):") { cell(timeout) }
    }

    fun apply(): Provider {
        provider = provider.copy(
            name = name.text.trim().ifBlank { "Untitled" },
            baseUrl = baseUrl.text.trim(),
            model = (modelCombo.selectedItem as? String).orEmpty().trim(),
            temperature = temperature.text.toDoubleOrNull() ?: 0.3,
            maxTokens = maxTokens.text.toIntOrNull() ?: 512,
            timeoutSec = timeout.text.toIntOrNull() ?: 60,
        )
        SecretStore.set(provider.id, String(apiKey.password).takeIf { it.isNotBlank() })
        return provider
    }
}

private object ProgressManagerCompat {
    fun runBackground(title: String, block: () -> Unit) {
        com.intellij.openapi.progress.ProgressManager.getInstance().run(object :
            Task.Backgroundable(null, title, true) {
            override fun run(indicator: ProgressIndicator) { block() }
        })
    }
}
