package io.aicommit.ui

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

object Notifications {
    private fun group() = NotificationGroupManager.getInstance().getNotificationGroup("Auto Commit")
    fun info(project: Project?, content: String, actions: List<NotificationAction> = emptyList()) =
        notify(project, content, NotificationType.INFORMATION, actions)
    fun warn(project: Project?, content: String, actions: List<NotificationAction> = emptyList()) =
        notify(project, content, NotificationType.WARNING, actions)
    fun error(project: Project?, content: String, actions: List<NotificationAction> = emptyList()) =
        notify(project, content, NotificationType.ERROR, actions)

    private fun notify(
        project: Project?,
        content: String,
        type: NotificationType,
        actions: List<NotificationAction>,
    ) {
        val notification = group().createNotification(content, type)
        actions.forEach { notification.addAction(it) }
        notification.notify(project)
    }
}
