package io.aicommit.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.JBTable
import io.aicommit.llm.OpenAICompatibleClient
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.table.AbstractTableModel
import javax.swing.table.TableRowSorter
import javax.swing.RowFilter

/**
 * One tab worth of editor UI for a single Provider.
 * Mutates the Provider in-place via [snapshot]/[apply].
 */
class ProviderTabPanel(
    private var provider: Provider,
    private val onActiveToggle: (Provider) -> Unit,   // called when this tab is set active
) {
    private val activeCheck = JBCheckBox("设为当前服务")
    private val baseUrl = JBTextField(provider.baseUrl)
    private val verifyBtn = JButton("验证")
    private val apiKey = JBPasswordField().apply { text = SecretStore.get(provider.id).orEmpty() }

    private val modelCombo = ComboBox(DefaultComboBoxModel(provider.enabledModels.toTypedArray())).apply {
        isEditable = true
        selectedItem = provider.model.ifBlank { provider.enabledModels.firstOrNull() ?: "" }
    }
    private val searchField = JBTextField().apply { emptyText.text = "搜索模型…" }
    private val refreshBtn = JButton("刷新模型")
    private val modelsTableModel = ModelsTableModel(provider)
    private val modelsTable = JBTable(modelsTableModel).apply {
        rowSorter = TableRowSorter(modelsTableModel)
        columnModel.getColumn(0).maxWidth = 60
        columnModel.getColumn(0).preferredWidth = 60
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
        searchField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = applyFilter()
            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = applyFilter()
            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = applyFilter()
        })
        modelsTableModel.addTableModelListener {
            // Sync enabled-models into combo so dropdown shows enabled set
            val enabled = modelsTableModel.enabledModels()
            val current = modelCombo.selectedItem as? String
            modelCombo.model = DefaultComboBoxModel(enabled.toTypedArray())
            if (current != null && enabled.contains(current)) modelCombo.selectedItem = current
            else if (enabled.isNotEmpty()) modelCombo.selectedIndex = 0
        }
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
            }
        }
        group("模型选择") {
            row("当前模型:") { cell(modelCombo).align(AlignX.FILL).resizableColumn() }
            row {
                cell(searchField).align(AlignX.FILL).resizableColumn()
                cell(refreshBtn)
            }
            row {
                cell(com.intellij.ui.components.JBScrollPane(modelsTable))
                    .align(AlignX.FILL).resizableColumn()
            }.resizableRow()
        }
        group("生成参数") {
            row("Temperature:") { cell(temperature) }
            row("Max tokens:") { cell(maxTokens) }
            row("Timeout (sec):") { cell(timeout) }
        }
    }

    /** Build a Provider from current UI state (does NOT persist the API key). */
    fun snapshot(): Provider = provider.copy(
        baseUrl = baseUrl.text.trim(),
        model = (modelCombo.selectedItem as? String).orEmpty().trim(),
        enabledModels = modelsTableModel.enabledModels(),
        temperature = temperature.text.toDoubleOrNull() ?: 0.8,
        maxTokens = maxTokens.text.toIntOrNull() ?: 512,
        timeoutSec = timeout.text.toIntOrNull() ?: 60,
    )

    /** Persist UI state into [provider] and secret store. Returns the updated Provider. */
    fun apply(): Provider {
        provider = snapshot()
        SecretStore.set(provider.id, String(apiKey.password).takeIf { it.isNotBlank() })
        return provider
    }

    private fun verify() {
        val snap = snapshot()
        if (snap.baseUrl.isBlank()) { Messages.showWarningDialog("Base URL 为空", "AI Commit"); return }
        verifyBtn.isEnabled = false; verifyBtn.text = "验证中…"
        val key = String(apiKey.password).takeIf { it.isNotBlank() }
        runBackground("验证 ${snap.baseUrl}") {
            runCatching { OpenAICompatibleClient(snap, key).listModels() }
                .onSuccess { models ->
                    ApplicationManager.getApplication().invokeLater({
                        verifyBtn.isEnabled = true; verifyBtn.text = "验证"
                        Messages.showInfoMessage("连接成功，发现 ${models.size} 个模型。", "AI Commit")
                    }, ModalityState.any())
                }
                .onFailure { e ->
                    ApplicationManager.getApplication().invokeLater({
                        verifyBtn.isEnabled = true; verifyBtn.text = "验证"
                        Messages.showErrorDialog("验证失败：\n${e.message?.take(500)}", "AI Commit")
                    }, ModalityState.any())
                }
        }
    }

    private fun fetchModels() {
        val snap = snapshot()
        if (snap.baseUrl.isBlank()) { Messages.showWarningDialog("Base URL 为空", "AI Commit"); return }
        refreshBtn.isEnabled = false; refreshBtn.text = "刷新中…"
        val key = String(apiKey.password).takeIf { it.isNotBlank() }
        runBackground("拉取模型列表") {
            runCatching { OpenAICompatibleClient(snap, key).listModels() }
                .onSuccess { models ->
                    ApplicationManager.getApplication().invokeLater({
                        refreshBtn.isEnabled = true; refreshBtn.text = "刷新模型"
                        modelsTableModel.replaceAll(models)
                    }, ModalityState.any())
                }
                .onFailure { e ->
                    ApplicationManager.getApplication().invokeLater({
                        refreshBtn.isEnabled = true; refreshBtn.text = "刷新模型"
                        Messages.showErrorDialog("拉取失败：\n${e.message?.take(500)}", "AI Commit")
                    }, ModalityState.any())
                }
        }
    }

    private fun runBackground(title: String, block: () -> Unit) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(null, title, true) {
            override fun run(indicator: ProgressIndicator) { block() }
        })
    }
}

private class ModelsTableModel(initial: Provider) : AbstractTableModel() {
    data class Row(var enabled: Boolean, val id: String)

    private val rows: MutableList<Row> = run {
        val all = initial.enabledModels.toMutableList()
        // If model is set but not in list, include it
        if (initial.model.isNotBlank() && !all.contains(initial.model)) all.add(0, initial.model)
        all.map { Row(true, it) }.toMutableList()
    }

    override fun getRowCount(): Int = rows.size
    override fun getColumnCount(): Int = 3
    override fun getColumnName(c: Int): String = when (c) {
        0 -> "启用"; 1 -> "模型"; 2 -> "模型 ID"; else -> ""
    }
    override fun getColumnClass(c: Int): Class<*> = if (c == 0) java.lang.Boolean::class.java else String::class.java
    override fun isCellEditable(r: Int, c: Int): Boolean = c == 0
    override fun getValueAt(r: Int, c: Int): Any = when (c) {
        0 -> rows[r].enabled
        1, 2 -> rows[r].id
        else -> ""
    }
    override fun setValueAt(v: Any?, r: Int, c: Int) {
        if (c == 0 && v is Boolean) { rows[r].enabled = v; fireTableRowsUpdated(r, r) }
    }

    fun replaceAll(allModelIds: List<String>) {
        val previouslyEnabled = rows.filter { it.enabled }.map { it.id }.toSet()
        rows.clear()
        for (id in allModelIds) rows.add(Row(previouslyEnabled.contains(id), id))
        fireTableDataChanged()
    }

    fun enabledModels(): List<String> = rows.filter { it.enabled }.map { it.id }
}
