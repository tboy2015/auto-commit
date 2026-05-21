package io.aicommit.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.ui.CommitMessage
import io.aicommit.service.CommitMsgService
import io.aicommit.settings.AppSettings

class GenerateAction : AnAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val ui = e.getData(VcsDataKeys.COMMIT_MESSAGE_CONTROL) as? CommitMessage
        val hasChanges = e.getData(VcsDataKeys.CHANGES)?.isNotEmpty() == true
        val hasProvider = project != null && AppSettings.get().activeProvider() != null
        e.presentation.isEnabled = project != null && ui != null && hasChanges && hasProvider
        e.presentation.text = if (project != null && project.service<CommitMsgService>().isGenerating)
            "Stop generating" else "Generate commit message with AI"
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val ui = e.getData(VcsDataKeys.COMMIT_MESSAGE_CONTROL) as? CommitMessage ?: return
        val changes = e.getData(VcsDataKeys.CHANGES)?.toList().orEmpty()
        project.service<CommitMsgService>().generate(ui, changes)
    }
}
