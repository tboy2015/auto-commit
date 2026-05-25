package io.aicommit.settings

import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.panel
import io.aicommit.diff.DiffMode
import io.aicommit.diff.DiffPayload
import io.aicommit.prompt.PromptBuilder
import io.aicommit.prompt.Templates
import javax.swing.JButton
import javax.swing.JComponent

/**
 * Settings 里的"提示词"区块：编辑 system / user 模板 + 一键恢复默认 + 预览渲染结果。
 */
class PromptEditorPanel(private val settings: AppSettings) {

    private val conventionPicker = ComboBox(Templates.conventions.toTypedArray()).apply {
        renderer = SimpleListCellRenderer.create("") { it?.displayName.orEmpty() }
        selectedItem = Templates.conventionFor(settings.state.convention)
    }
    private val conventionDescription = JBLabel(Templates.conventionFor(settings.state.convention).description)

    private val systemArea = JBTextArea(
        settings.state.customSystemPrompt
            ?: Templates.systemFor(settings.state.convention, null)
    ).apply {
        lineWrap = true; wrapStyleWord = true; rows = 10
    }
    private val userArea = JBTextArea(
        settings.state.customUserTemplate ?: Templates.DEFAULT_USER_TEMPLATE
    ).apply {
        lineWrap = true; wrapStyleWord = true; rows = 8
    }
    private val previewArea = JBTextArea().apply {
        lineWrap = true; wrapStyleWord = true; rows = 12; isEditable = false
    }

    private val resetSystemBtn = JButton("恢复默认").apply {
        addActionListener {
            systemArea.text = Templates.systemFor(currentConvention(), null)
        }
    }
    private val resetUserBtn = JButton("恢复默认").apply {
        addActionListener { userArea.text = Templates.DEFAULT_USER_TEMPLATE }
    }
    private val previewBtn = JButton("预览渲染").apply {
        addActionListener { refreshPreview() }
    }

    init {
        // 切换规范时，若 system 区是默认值就跟着更新
        conventionPicker.addActionListener {
            val newDefault = Templates.systemFor(currentConvention(), null)
            conventionDescription.text = currentConventionMeta().description
            // 只在用户没自定义的情况下更新
            if (settings.state.customSystemPrompt == null) {
                systemArea.text = newDefault
            }
        }
    }

    private fun currentConvention(): String =
        currentConventionMeta().id

    private fun currentConventionMeta(): Templates.Convention =
        (conventionPicker.selectedItem as? Templates.Convention) ?: Templates.conventions[0]

    val component: JComponent = panel {
        group("System Prompt") {
            row("提示词规范:") { cell(conventionPicker) }
            row { cell(conventionDescription) }
            row { cell(JBScrollPane(systemArea)).align(AlignX.FILL).resizableColumn() }
            row { cell(resetSystemBtn) }
        }
        group("User Prompt 模板") {
            row {
                comment(
                    "可用变量：<code>{{language}}</code> <code>{{diff}}</code> " +
                        "<code>{{files}}</code> <code>{{recent_commits}}</code> " +
                        "<code>{{branch}}</code> <code>{{#truncated}}…{{/truncated}}</code>"
                )
            }
            row { cell(JBScrollPane(userArea)).align(AlignX.FILL).resizableColumn() }
            row { cell(resetUserBtn) }
        }
        group("预览") {
            row { cell(previewBtn) }
            row {
                cell(JBScrollPane(previewArea)).align(AlignX.FILL).align(AlignY.FILL).resizableColumn()
            }.resizableRow()
        }
    }

    /** Persist editor state into settings. Pure system/user texts that match the default → store null. */
    fun apply() {
        settings.state.convention = currentConvention()
        val defaultSystem = Templates.systemFor(currentConvention(), null)
        settings.state.customSystemPrompt =
            systemArea.text.takeIf { it.isNotBlank() && it != defaultSystem }
        settings.state.customUserTemplate =
            userArea.text.takeIf { it.isNotBlank() && it != Templates.DEFAULT_USER_TEMPLATE }
    }

    private fun refreshPreview() {
        // 用一份合成的、可读的 payload 渲染
        val sample = DiffPayload(
            diff = """diff --git a/src/Foo.kt b/src/Foo.kt
                |--- a/src/Foo.kt
                |+++ b/src/Foo.kt
                |@@ -1,3 +1,4 @@
                | class Foo {
                |+  fun greet(name: String) = "Hello, ${'$'}name"
                | }
                |""".trimMargin(),
            files = listOf("src/Foo.kt", "test/FooTest.kt"),
            recentCommits = listOf(
                "feat(api): add user endpoint",
                "fix(ui): align header on mobile",
                "refactor(core): extract validator"
            ),
            branch = "main",
        )
        // 临时用当前编辑值（不写入 settings）渲染
        val ephemeralState = settings.state.copy(
            convention = currentConvention(),
            customSystemPrompt = systemArea.text.ifBlank { null },
            customUserTemplate = userArea.text.ifBlank { null },
        )
        val mode = DiffMode.STAGED_ONLY
        try {
            val msgs = PromptBuilder.build(sample, ephemeralState, userTemplate = null, mode = mode)
            previewArea.text = buildString {
                append("=== system ===\n").append(msgs[0].content)
                append("\n\n=== user (mode=$mode) ===\n").append(msgs[1].content)
            }
        } catch (e: Throwable) {
            Messages.showErrorDialog("预览失败：${e.message}", "Auto Commit")
        }
    }
}
