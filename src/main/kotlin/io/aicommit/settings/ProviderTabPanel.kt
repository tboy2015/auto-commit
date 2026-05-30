package io.aicommit.settings

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.JBTable
import io.aicommit.llm.LLMErrorMessages
import io.aicommit.llm.OpenAICompatibleClient
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.RowFilter
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.table.AbstractTableModel
import javax.swing.table.TableRowSorter

class ProviderTabPanel(
    private var provider: Provider,
    private val onActiveToggle: (Provider) -> Unit,
    private val onStatusChanged: (Provider, String?) -> Unit = { _, _ -> },
) {
    private val activeCheck = JBCheckBox("设为当前服务")
    private val baseUrl = JBTextField(provider.baseUrl)
    private val verifyBtn = JButton("验证")
    private val apiKey = JBPasswordField().apply { text = SecretStore.get(provider.id).orEmpty() }
    private val apiKeyUrl = ProviderPresets.byId(provider.presetId)?.apiKeyUrl
    private val apiKeyStatus = JBLabel()

    private val modelCombo = ComboBox(DefaultComboBoxModel(ModelCatalog.modelsForUi(provider).toTypedArray())).apply {
        isEditable = true
        selectedItem = provider.model.ifBlank { ModelCatalog.modelsForUi(provider).firstOrNull() ?: "" }
    }
    private val searchField = JBTextField().apply { emptyText.text = "搜索模型..." }
    private val refreshBtn = JButton("刷新模型")
    private val modelsTableModel = ModelsTableModel(provider)
    private val modelsTable = JBTable(modelsTableModel).apply {
        rowSorter = TableRowSorter(modelsTableModel)
        columnModel.getColumn(0).maxWidth = 60
        columnModel.getColumn(0).preferredWidth = 60
        columnModel.getColumn(3).preferredWidth = 180
    }

    private val enableProxy = com.intellij.ui.components.JBCheckBox("使用独立代理（覆盖 IDE 全局）").apply {
        isSelected = provider.proxyUrl.isNotBlank()
    }
    private val proxyPort = JBTextField(extractPortFromUrl(provider.proxyUrl)).apply {
        emptyText.text = "7890"
        isEnabled = enableProxy.isSelected
        columns = 6
    }
    private val temperature = JBTextField(provider.temperature.toString())
    private val maxTokens = JBTextField(provider.maxTokens.toString())
    private val timeout = JBTextField(provider.timeoutSec.toString())

    init {
        activeCheck.addActionListener {
            if (activeCheck.isSelected) onActiveToggle(snapshot())
        }
        verifyBtn.addActionListener { verify() }
        refreshBtn.addActionListener { fetchModels() }
        enableProxy.addActionListener {
            proxyPort.isEnabled = enableProxy.isSelected
            if (enableProxy.isSelected && proxyPort.text.isBlank()) proxyPort.text = "7890"
        }
        searchField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = applyFilter()
            override fun removeUpdate(e: DocumentEvent?) = applyFilter()
            override fun changedUpdate(e: DocumentEvent?) = applyFilter()
        })
        val statusListener = object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = updateApiKeyStatus()
            override fun removeUpdate(e: DocumentEvent?) = updateApiKeyStatus()
            override fun changedUpdate(e: DocumentEvent?) = updateApiKeyStatus()
        }
        apiKey.document.addDocumentListener(statusListener)
        baseUrl.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = updateApiKeyStatus()
            override fun removeUpdate(e: DocumentEvent?) = updateApiKeyStatus()
            override fun changedUpdate(e: DocumentEvent?) = updateApiKeyStatus()
        })
        modelsTableModel.addTableModelListener {
            val enabled = modelsTableModel.enabledModels()
            val current = modelCombo.selectedItem as? String
            modelCombo.model = DefaultComboBoxModel(enabled.toTypedArray())
            if (current != null && enabled.contains(current)) modelCombo.selectedItem = current
            else if (enabled.isNotEmpty()) modelCombo.selectedIndex = 0
        }
        updateApiKeyStatus(notify = false)
    }

    private fun applyFilter() {
        @Suppress("UNCHECKED_CAST")
        val sorter = modelsTable.rowSorter as TableRowSorter<ModelsTableModel>
        val text = searchField.text.trim()
        sorter.rowFilter = if (text.isEmpty()) null
        else RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(text), 1, 2)
    }

    fun setActiveInPlace(active: Boolean) {
        activeCheck.isSelected = active
    }

    val isActive: Boolean get() = activeCheck.isSelected
    val isCustom: Boolean get() = provider.isCustom

    val component: JComponent = panel {
        row { cell(activeCheck) }
        group("身份验证") {
            row("Base URL:") {
                cell(baseUrl).align(AlignX.FILL).resizableColumn()
                cell(verifyBtn)
            }
            row("API Key:") {
                cell(apiKey).align(AlignX.FILL).resizableColumn()
                if (apiKeyUrl != null) {
                    link("获取 API Key") { BrowserUtil.browse(apiKeyUrl) }
                }
            }
            row("状态:") {
                cell(apiKeyStatus)
            }
            row("代理:") {
                cell(enableProxy)
                label("端口:")
                cell(proxyPort)
            }
            row {
                comment(
                    "默认跟随 IDE 全局代理（<b>Settings → Appearance &amp; Behavior → System Settings → HTTP Proxy</b>）。<br/>" +
                        "勾选后此 provider 改走 <code>http://127.0.0.1:&lt;port&gt;</code>（Clash / v2ray 等），不再跟随全局。"
                )
            }
        }
        group("模型选择") {
            row("当前模型:") { cell(modelCombo).align(AlignX.FILL).resizableColumn() }
            row {
                cell(searchField).align(AlignX.FILL).resizableColumn()
                cell(refreshBtn)
            }
            row {
                cell(JBScrollPane(modelsTable)).align(AlignX.FILL).resizableColumn()
            }.resizableRow()
        }
        group("生成参数") {
            row("Temperature:") { cell(temperature) }
            row("Max tokens:") { cell(maxTokens) }
            row("Timeout (sec):") { cell(timeout) }
        }
    }

    fun snapshot(): Provider = provider.copy(
        baseUrl = baseUrl.text.trim(),
        model = (modelCombo.selectedItem as? String).orEmpty().trim(),
        enabledModels = modelsTableModel.enabledModels(),
        temperature = temperature.text.toDoubleOrNull() ?: 0.8,
        maxTokens = maxTokens.text.toIntOrNull() ?: 512,
        timeoutSec = timeout.text.toIntOrNull() ?: 60,
        proxyUrl = buildProxyUrl(),
        cachedModels = provider.cachedModels,
        cachedModelsAt = provider.cachedModelsAt,
    )

    private fun buildProxyUrl(): String {
        if (!enableProxy.isSelected) return ""
        val port = proxyPort.text.trim().toIntOrNull() ?: return ""
        if (port !in 1..65535) return ""
        return "http://127.0.0.1:$port"
    }

    fun apply(): Provider {
        provider = snapshot()
        SecretStore.set(provider.id, String(apiKey.password).takeIf { it.isNotBlank() })
        return provider
    }

    private fun updateApiKeyStatus(notify: Boolean = true) {
        val snap = snapshot()
        val key = currentApiKey()
        val status = providerStatus(snap, key)
        apiKeyStatus.text = status.text
        apiKeyStatus.toolTipText = status.tooltip
        if (notify) onStatusChanged(snap, key)
    }

    private fun currentApiKey(): String? =
        String(apiKey.password).takeIf { it.isNotBlank() } ?: SecretStore.get(provider.id)

    private fun rememberVerification(snap: Provider, key: String?, error: String?) {
        provider = snap.copy(
            lastVerifiedBaseUrl = snap.baseUrl,
            lastVerifiedApiKeyMarker = apiKeyMarker(key),
            lastVerifiedAt = System.currentTimeMillis(),
            lastVerifyError = error.orEmpty(),
        )
        updateApiKeyStatus()
    }

    fun statusProviderSnapshot(): Provider = snapshot()

    fun currentApiKeyForStatus(): String? = currentApiKey()

    private fun verify() {
        val snap = snapshot()
        if (snap.baseUrl.isBlank()) {
            Messages.showWarningDialog("Base URL 为空", "Auto Commit")
            return
        }
        verifyBtn.isEnabled = false
        verifyBtn.text = "验证中..."
        val key = currentApiKey()
        runBackground("验证 ${snap.baseUrl}") {
            runCatching { OpenAICompatibleClient(snap, key).listModels() }
                .onSuccess { models ->
                    ApplicationManager.getApplication().invokeLater({
                        rememberVerification(snap, key, null)
                        verifyBtn.isEnabled = true
                        verifyBtn.text = "验证"
                        Messages.showInfoMessage("连接成功，发现 ${models.size} 个模型。", "Auto Commit")
                    }, ModalityState.any())
                }
                .onFailure { e ->
                    ApplicationManager.getApplication().invokeLater({
                        val message = LLMErrorMessages.userMessage(e)
                        rememberVerification(snap, key, message.take(500))
                        verifyBtn.isEnabled = true
                        verifyBtn.text = "验证"
                        Messages.showErrorDialog("验证失败：\n${message.take(500)}", "Auto Commit")
                    }, ModalityState.any())
                }
        }
    }

    private fun fetchModels() {
        val snap = snapshot()
        if (snap.baseUrl.isBlank()) {
            Messages.showWarningDialog("Base URL 为空", "Auto Commit")
            return
        }
        refreshBtn.isEnabled = false
        refreshBtn.text = "刷新中..."
        val key = currentApiKey()
        runBackground("拉取模型列表") {
            runCatching { OpenAICompatibleClient(snap, key).listModels() }
                .onSuccess { models ->
                    ApplicationManager.getApplication().invokeLater({
                        rememberVerification(snap, key, null)
                        refreshBtn.isEnabled = true
                        refreshBtn.text = "刷新模型"
                        selectModelAfterRefresh(models)
                    }, ModalityState.any())
                }
                .onFailure { e ->
                    ApplicationManager.getApplication().invokeLater({
                        val message = LLMErrorMessages.userMessage(e)
                        rememberVerification(snap, key, message.take(500))
                        refreshBtn.isEnabled = true
                        refreshBtn.text = "刷新模型"
                        Messages.showErrorDialog("拉取失败：\n${message.take(500)}", "Auto Commit")
                    }, ModalityState.any())
                }
        }
    }

    private fun selectModelAfterRefresh(models: List<String>) {
        provider = snapshot().copy(cachedModels = models, cachedModelsAt = System.currentTimeMillis())
        val selected = ModelCatalog.chooseBestModel(provider, models)
        val previouslyEnabled = modelsTableModel.enabledModels().filter { models.contains(it) }.toMutableSet()
        if (selected.isNotBlank()) previouslyEnabled.add(selected)
        modelsTableModel.replaceAll(models, previouslyEnabled)
        if (selected.isNotBlank()) modelCombo.selectedItem = selected
    }

    private fun runBackground(title: String, block: () -> Unit) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(null, title, true) {
            override fun run(indicator: ProgressIndicator) { block() }
        })
    }
}

private fun extractPortFromUrl(url: String): String {
    if (url.isBlank()) return ""
    return runCatching {
        val u = java.net.URI(url.trim())
        if (u.port > 0) u.port.toString() else ""
    }.getOrDefault("")
}

private class ModelsTableModel(private val initial: Provider) : AbstractTableModel() {
    data class Row(var enabled: Boolean, val id: String)

    private val rows: MutableList<Row> = run {
        val all = ModelCatalog.modelsForUi(initial).toMutableList()
        if (initial.model.isNotBlank() && !all.contains(initial.model)) all.add(0, initial.model)
        val enabled = initial.enabledModels.toSet()
        all.map { Row(enabled.isEmpty() || enabled.contains(it), it) }.toMutableList()
    }

    override fun getRowCount(): Int = rows.size
    override fun getColumnCount(): Int = 4
    override fun getColumnName(c: Int): String = when (c) {
        0 -> "启用"
        1 -> "模型"
        2 -> "模型 ID"
        3 -> "标签"
        else -> ""
    }
    override fun getColumnClass(c: Int): Class<*> = if (c == 0) java.lang.Boolean::class.java else String::class.java
    override fun isCellEditable(r: Int, c: Int): Boolean = c == 0
    override fun getValueAt(r: Int, c: Int): Any = when (c) {
        0 -> rows[r].enabled
        1, 2 -> rows[r].id
        3 -> ModelCatalog.tags(initial, rows[r].id).joinToString(" / ")
        else -> ""
    }
    override fun setValueAt(v: Any?, r: Int, c: Int) {
        if (c == 0 && v is Boolean) {
            rows[r].enabled = v
            fireTableRowsUpdated(r, r)
        }
    }

    fun replaceAll(allModelIds: List<String>, enabledIds: Set<String>) {
        rows.clear()
        for (id in allModelIds) rows.add(Row(enabledIds.contains(id), id))
        fireTableDataChanged()
    }

    fun enabledModels(): List<String> = rows.filter { it.enabled }.map { it.id }
}
