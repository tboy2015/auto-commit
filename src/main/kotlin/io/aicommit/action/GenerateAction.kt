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
import com.intellij.openapi.project.Project
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
import com.intellij.ui.awt.RelativePoint
import io.aicommit.diff.DiffMode
import io.aicommit.prompt.Templates
import io.aicommit.service.CommitMsgService
import io.aicommit.settings.AppSettings
import io.aicommit.settings.SettingsConfigurable
import io.aicommit.ui.Notifications
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.lang.ref.WeakReference
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.SwingUtilities

class GenerateAction : AnAction(), CustomComponentAction {
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

        e.presentation.isEnabled = project != null && ui != null
        e.presentation.icon = when {
            generating -> AllIcons.Actions.Suspend
            hasProvider -> AllIcons.Actions.Lightning
            else -> IconLoader.getDisabledIcon(AllIcons.Actions.Lightning)
        }
        e.presentation.text = when {
            generating -> "停止生成"
            !hasProvider -> "配置 AI Provider"
            else -> "Auto Commit 生成"
        }
        e.presentation.description = when {
            !hasProvider -> "点击打开设置页配置 Provider；右键选择提示词规范"
            ui == null -> "请打开 Commit 工具窗"
            generating -> "点击停止生成；右键选择提示词规范"
            else -> "左键按最简上下文生成；右键选择提示词规范并生成"
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val hasProvider = AppSettings.get().activeProvider() != null

        if (!hasProvider) {
            ShowSettingsUtil.getInstance()
                .showSettingsDialog(project, SettingsConfigurable::class.java)
            return
        }

        val ui = e.getData(VcsDataKeys.COMMIT_MESSAGE_CONTROL) as? CommitMessage ?: return
        val changes = collectChanges(e)
        if (changes.isEmpty()) {
            Notifications.warn(project, "请先勾选要提交的文件，再点击 AI 生成。")
            return
        }

        project.service<CommitMsgService>().generate(ui, changes, DiffMode.STAGED_ONLY)
    }

    override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
        val button = ActionButton(this, presentation, place, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE)
        button.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    e.consume()
                    showTemplatePopup(button, e.point)
                }
            }
        })
        return button
    }

    private fun showTemplatePopup(source: JComponent, point: Point) {
        val settings = AppSettings.get()
        val step = object : BaseListPopupStep<Templates.Convention>("提示词规范", Templates.conventions) {
            override fun getTextFor(value: Templates.Convention): String = value.displayName

            override fun getIconFor(value: Templates.Convention): Icon? =
                if (value.id == settings.state.convention) AllIcons.Actions.Checked else null

            override fun isSpeedSearchEnabled() = false

            override fun onChosen(selectedValue: Templates.Convention, finalChoice: Boolean): PopupStep<*>? {
                settings.state.convention = selectedValue.id
                triggerGenerate()
                return FINAL_CHOICE
            }
        }

        JBPopupFactory.getInstance()
            .createListPopup(step)
            .show(RelativePoint(source, point))
    }

    private fun triggerGenerate() {
        val project = lastProject.get() ?: return
        val ui = lastUi.get() ?: return
        val settings = AppSettings.get()
        if (settings.activeProvider() == null) return

        val service = project.service<CommitMsgService>()
        if (service.isGenerating) return

        val changes = ChangeListManager.getInstance(project).defaultChangeList.changes.toList()
        if (changes.isEmpty()) {
            Notifications.warn(project, "请先勾选要提交的文件，再选择提示词规范。")
            return
        }

        service.generate(ui, changes, DiffMode.STAGED_ONLY)
    }

    private fun collectChanges(e: AnActionEvent): List<Change> {
        val panel = e.getData(Refreshable.PANEL_KEY) as? CheckinProjectPanel
        panel?.selectedChanges?.toList()?.let { if (it.isNotEmpty()) return it }
        e.getData(VcsDataKeys.CHANGES)?.toList()?.let { if (it.isNotEmpty()) return it }
        return emptyList()
    }
}
