package io.aicommit.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.ActivityTracker
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.util.Consumer
import io.aicommit.settings.AppSettings
import io.aicommit.settings.Provider
import io.aicommit.settings.SettingsConfigurable
import java.awt.event.MouseEvent
import javax.swing.Icon

class ProviderStatusBarFactory : StatusBarWidgetFactory {
    override fun getId(): String = "AICommit.ProviderWidget"
    override fun getDisplayName(): String = "AI Commit Provider"
    override fun isAvailable(project: Project): Boolean = true
    override fun createWidget(project: Project): StatusBarWidget = ProviderStatusBarWidget(project)
    override fun disposeWidget(widget: StatusBarWidget) {}
    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}

class ProviderStatusBarWidget(private val project: Project) :
    StatusBarWidget,
    StatusBarWidget.MultipleTextValuesPresentation,
    StatusBarWidget.Multiframe {

    private var statusBar: StatusBar? = null

    override fun ID(): String = "AICommit.ProviderWidget"

    override fun install(statusBar: StatusBar) { this.statusBar = statusBar }

    override fun dispose() { statusBar = null }

    override fun copy(): StatusBarWidget = ProviderStatusBarWidget(project)

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    // ---- MultipleTextValuesPresentation ----

    override fun getSelectedValue(): String {
        val p = AppSettings.get().activeProvider()
        return if (p == null) "AI: 未配置"
        else "AI: ${p.name} · ${p.model.ifBlank { "(无模型)" }}"
    }

    override fun getTooltipText(): String =
        "点击切换 AI Provider / 模型"

    override fun getIcon(): Icon = AllIcons.Actions.Lightning

    override fun getPopup(): ListPopup? =
        JBPopupFactory.getInstance().createListPopup(buildStep())

    private fun buildStep(): BaseListPopupStep<Item> {
        val settings = AppSettings.get()
        val providers = settings.state.providers.filter { it.baseUrl.isNotBlank() || it.isCustom }
        val active = settings.activeProvider()

        // 第一层：provider 列表
        val items = mutableListOf<Item>()
        for (p in providers) items += Item.SelectProvider(p, isActive = p.id == active?.id)
        items += Item.Sep
        items += Item.OpenSettings

        return object : BaseListPopupStep<Item>("AI Commit", items) {
            override fun getTextFor(value: Item): String = when (value) {
                is Item.SelectProvider -> "${value.provider.name} · ${value.provider.model.ifBlank { "(无模型)" }}"
                Item.OpenSettings -> "打开设置…"
                Item.Sep -> "—"
            }
            override fun getIconFor(value: Item): Icon? = when (value) {
                is Item.SelectProvider -> if (value.isActive) AllIcons.Actions.Checked else null
                Item.OpenSettings -> AllIcons.General.Settings
                Item.Sep -> null
            }
            override fun isSelectable(value: Item): Boolean = value !is Item.Sep
            override fun isSpeedSearchEnabled() = true

            override fun onChosen(selectedValue: Item, finalChoice: Boolean): PopupStep<*>? {
                return when (selectedValue) {
                    is Item.SelectProvider -> {
                        // 第二层：在该 provider 的"启用模型"里选一个作为 active model
                        val models = selectedValue.provider.enabledModels.ifEmpty {
                            listOfNotNull(selectedValue.provider.model.takeIf { it.isNotBlank() })
                        }
                        if (models.isEmpty()) {
                            settings.setActive(selectedValue.provider.id)
                            refresh()
                            return FINAL_CHOICE
                        }
                        return object : BaseListPopupStep<String>(
                            "${selectedValue.provider.name} · 选择模型", models
                        ) {
                            override fun getIconFor(value: String): Icon? =
                                if (value == selectedValue.provider.model) AllIcons.Actions.Checked else null

                            override fun onChosen(model: String, finalChoice: Boolean): PopupStep<*>? {
                                val updated = selectedValue.provider.copy(model = model)
                                settings.update(updated)
                                settings.setActive(updated.id)
                                refresh()
                                return FINAL_CHOICE
                            }
                        }
                    }
                    Item.OpenSettings -> {
                        ShowSettingsUtil.getInstance()
                            .showSettingsDialog(project, SettingsConfigurable::class.java)
                        FINAL_CHOICE
                    }
                    Item.Sep -> FINAL_CHOICE
                }
            }
        }
    }

    private fun refresh() {
        statusBar?.updateWidget(ID())
        ActivityTracker.getInstance().inc()
    }

    override fun getClickConsumer(): Consumer<MouseEvent>? = null

    sealed class Item {
        data class SelectProvider(val provider: Provider, val isActive: Boolean) : Item()
        object OpenSettings : Item()
        object Sep : Item()
    }
}
