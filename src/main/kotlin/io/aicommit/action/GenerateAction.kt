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
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.ui.CommitMessage
import io.aicommit.service.CommitMsgService
import io.aicommit.settings.AppSettings
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.SwingUtilities

class GenerateAction : AnAction(), CustomComponentAction {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val ui = e.getData(VcsDataKeys.COMMIT_MESSAGE_CONTROL) as? CommitMessage
        val hasProvider = AppSettings.get().activeProvider() != null
        val generating = project?.service<CommitMsgService>()?.isGenerating == true

        e.presentation.isEnabled = project != null && ui != null && (hasProvider || generating)
        e.presentation.icon = if (generating) AllIcons.Actions.Suspend else AllIcons.Actions.Lightning
        e.presentation.text = if (generating) "停止生成" else "AI 生成提交信息"
        e.presentation.description = when {
            !hasProvider -> "请先在 Settings → Tools → AI Commit 配置 Provider（右键此按钮可直接打开）"
            ui == null -> "请打开 Commit 工具窗"
            generating -> "再次点击以停止"
            else -> "调用 AI 基于当前变更生成 commit message；右键打开设置"
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val ui = e.getData(VcsDataKeys.COMMIT_MESSAGE_CONTROL) as? CommitMessage ?: return
        val changes = collectChanges(project, e)
        project.service<CommitMsgService>().generate(ui, changes)
    }

    override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
        val button = ActionButton(this, presentation, place, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE)
        button.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    e.consume()
                    val project = ProjectManager.getInstance().openProjects.firstOrNull()
                    ShowSettingsUtil.getInstance()
                        .showSettingsDialog(project, "io.aicommit.settings")
                }
            }
        })
        return button
    }

    private fun collectChanges(project: Project, e: AnActionEvent): List<Change> {
        e.getData(VcsDataKeys.CHANGES)?.toList()?.takeIf { it.isNotEmpty() }?.let { return it }
        val clm = ChangeListManager.getInstance(project)
        val included = clm.defaultChangeList.changes.toList()
        if (included.isNotEmpty()) return included
        return clm.allChanges.toList()
    }
}
