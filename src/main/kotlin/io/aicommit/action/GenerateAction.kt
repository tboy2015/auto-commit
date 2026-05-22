package io.aicommit.action

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.components.service
import com.intellij.openapi.options.ShowSettingsUtil
import io.aicommit.settings.SettingsConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.openapi.vcs.ui.Refreshable
import io.aicommit.ui.Notifications
import com.intellij.ui.awt.RelativePoint
import io.aicommit.diff.DiffMode
import io.aicommit.service.CommitMsgService
import io.aicommit.settings.AppSettings
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.lang.ref.WeakReference
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.SwingUtilities

class GenerateAction : AnAction(), CustomComponentAction {

    // 在每次 update() 中刷新，供右键菜单的回调使用
    @Volatile private var lastProject: WeakReference<Project> = WeakReference(null)
    @Volatile private var lastUi: WeakReference<CommitMessage> = WeakReference(null)

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val ui = e.getData(VcsDataKeys.COMMIT_MESSAGE_CONTROL) as? CommitMessage
        val hasProvider = AppSettings.get().activeProvider() != null
        val generating = project?.service<CommitMsgService>()?.isGenerating == true

        if (project != null) lastProject = WeakReference(project)
        if (ui != null) lastUi = WeakReference(ui)

        // 始终启用（只要有项目和 UI），这样鼠标事件能到达我们的监听器。
        // 无 provider 时用灰色图标提示用户需要配置，左键点击会打开设置页。
        e.presentation.isEnabled = project != null && ui != null

        e.presentation.icon = when {
            generating -> AllIcons.Actions.Suspend
            hasProvider -> AllIcons.Actions.Lightning
            else -> IconLoader.getDisabledIcon(AllIcons.Actions.Lightning) // 灰色 = 未配置
        }
        e.presentation.text = when {
            generating -> "停止生成"
            !hasProvider -> "配置 AI Provider"
            else -> "AI 生成提交信息"
        }
        e.presentation.description = when {
            !hasProvider -> "点击打开设置页配置 Provider；右键选择生成模式"
            ui == null -> "请打开 Commit 工具窗"
            generating -> "点击停止生成；右键选择生成模式"
            else -> "左键生成 commit message；右键选择生成模式"
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val hasProvider = AppSettings.get().activeProvider() != null

        // 灰色状态（无 provider）下左键点击 → 直接打开 AI Commit 设置页
        if (!hasProvider) {
            ShowSettingsUtil.getInstance()
                .showSettingsDialog(project, SettingsConfigurable::class.java)
            return
        }

        val ui = e.getData(VcsDataKeys.COMMIT_MESSAGE_CONTROL) as? CommitMessage ?: return
        val changes = collectChanges(project, e)
        if (changes.isEmpty()) {
            Notifications.warn(project, "请先勾选要提交的文件，再点击 AI 生成。")
            return
        }
        val mode = AppSettings.get().state.diffMode
        project.service<CommitMsgService>().generate(ui, changes, mode)
    }

    override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
        val button = ActionButton(this, presentation, place, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE)
        button.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    e.consume()
                    showModePopup(button, e.point)
                }
                // 左键由 ActionButton 正常分发到 actionPerformed
            }
        })
        return button
    }

    // ----- 右键弹出模式菜单 -----

    private fun showModePopup(source: JComponent, point: Point) {
        val settings = AppSettings.get()

        val step = object : BaseListPopupStep<DiffMode>("生成模式", DiffMode.entries.toList()) {

            override fun getTextFor(value: DiffMode): String = when (value) {
                DiffMode.WITH_HISTORY ->
                    "diff + 最近 ${settings.state.recentCommitCount} 条 commit 历史"
                else -> value.label
            }

            // 当前选中项打勾
            override fun getIconFor(value: DiffMode): Icon? =
                if (value == settings.state.diffMode) AllIcons.Actions.Checked else null

            override fun isSpeedSearchEnabled() = false

            override fun onChosen(selectedValue: DiffMode, finalChoice: Boolean): PopupStep<*>? {
                settings.state.diffMode = selectedValue
                triggerGenerate(selectedValue)
                return FINAL_CHOICE
            }
        }

        JBPopupFactory.getInstance()
            .createListPopup(step)
            .show(RelativePoint(source, point))
    }

    /** 选完模式后立即生成（若已有 provider 且不在生成中）。 */
    private fun triggerGenerate(mode: DiffMode) {
        val project = lastProject.get() ?: return
        val ui = lastUi.get() ?: return
        val settings = AppSettings.get()
        if (settings.activeProvider() == null) return
        val service = project.service<CommitMsgService>()
        if (service.isGenerating) return
        val clm = ChangeListManager.getInstance(project)
        val changes = clm.defaultChangeList.changes.toList().ifEmpty { clm.allChanges.toList() }
        service.generate(ui, changes, mode)
    }

    // ----- 工具方法 -----

    /**
     * 严格只取用户在 Commit 面板里"勾选"的文件。
     * 优先级：CheckinProjectPanel.selectedChanges（已勾选）→ 显式数据上下文 CHANGES → 空。
     * 不再回退到「默认 changelist 全部 / 所有变更」，避免把未勾选的文件也发出去。
     */
    private fun collectChanges(project: Project, e: AnActionEvent): List<Change> {
        // 1) Commit 面板的勾选状态（最准确）
        val panel = e.getData(Refreshable.PANEL_KEY) as? CheckinProjectPanel
        panel?.selectedChanges?.toList()?.let { if (it.isNotEmpty()) return it }
        // 2) 显式数据上下文里携带的 changes（例如右键 Changes 面板里的项）
        e.getData(VcsDataKeys.CHANGES)?.toList()?.let { if (it.isNotEmpty()) return it }
        return emptyList()
    }
}
