package io.aicommit.settings

import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

class ProviderEditor(private var provider: Provider) {
    private val name = JBTextField(provider.name)
    private val baseUrl = JBTextField(provider.baseUrl)
    private val model = JBTextField(provider.model)
    private val apiKey = JBPasswordField().apply { text = SecretStore.get(provider.id).orEmpty() }
    private val temperature = JBTextField(provider.temperature.toString())
    private val maxTokens = JBTextField(provider.maxTokens.toString())
    private val timeout = JBTextField(provider.timeoutSec.toString())

    val component: JComponent = panel {
        row("Name:") { cell(name).resizableColumn() }
        row("Base URL:") { cell(baseUrl).resizableColumn() }
        row("Model:") { cell(model).resizableColumn() }
        row("API Key:") { cell(apiKey).resizableColumn() }
        row("Temperature:") { cell(temperature) }
        row("Max tokens:") { cell(maxTokens) }
        row("Timeout (sec):") { cell(timeout) }
    }

    fun apply(): Provider {
        provider = provider.copy(
            name = name.text.trim(),
            baseUrl = baseUrl.text.trim(),
            model = model.text.trim(),
            temperature = temperature.text.toDoubleOrNull() ?: 0.3,
            maxTokens = maxTokens.text.toIntOrNull() ?: 512,
            timeoutSec = timeout.text.toIntOrNull() ?: 60,
        )
        SecretStore.set(provider.id, String(apiKey.password).takeIf { it.isNotBlank() })
        return provider
    }
}
