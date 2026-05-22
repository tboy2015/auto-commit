package io.aicommit.ui

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

object Notifications {
    private fun group() = NotificationGroupManager.getInstance().getNotificationGroup("Auto Commit")
    fun info(project: Project?, content: String) =
        group().createNotification(content, NotificationType.INFORMATION).notify(project)
    fun warn(project: Project?, content: String) =
        group().createNotification(content, NotificationType.WARNING).notify(project)
    fun error(project: Project?, content: String) =
        group().createNotification(content, NotificationType.ERROR).notify(project)
}
