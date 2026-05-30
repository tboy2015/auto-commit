package io.aicommit.settings

import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.Icon

enum class ProviderStatusKind(
    val label: String,
    val color: Color,
) {
    NOT_CONFIGURED("未配置", Color(0x8A, 0x8F, 0x98)),
    UNVERIFIED("已配置未验证", Color(0xD9, 0x8C, 0x00)),
    VERIFIED("验证成功", Color(0x22, 0x9A, 0x5F)),
    FAILED("验证失败", Color(0xD6, 0x45, 0x45)),
    NOT_REQUIRED("无需 API Key", Color(0x5E, 0x8F, 0xD6)),
}

data class ProviderStatusSummary(
    val kind: ProviderStatusKind,
    val text: String,
    val tooltip: String = text,
)

class StatusDotIcon(private val color: Color) : Icon {
    override fun getIconWidth(): Int = 10
    override fun getIconHeight(): Int = 10

    override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
        val old = g.color
        g.color = color
        g.fillOval(x + 1, y + 1, 8, 8)
        g.color = old
    }
}

fun providerStatus(provider: Provider, key: String?): ProviderStatusSummary {
    if (ProviderPresets.byId(provider.presetId)?.apiKeyUrl == null && provider.presetId != "custom") {
        return ProviderStatusSummary(ProviderStatusKind.NOT_REQUIRED, ProviderStatusKind.NOT_REQUIRED.label)
    }
    if (key.isNullOrBlank()) {
        return ProviderStatusSummary(ProviderStatusKind.NOT_CONFIGURED, "未填写 API Key")
    }
    if (provider.lastVerifiedAt <= 0L) {
        return ProviderStatusSummary(ProviderStatusKind.UNVERIFIED, "已填写，未验证")
    }

    val matchesLastVerification =
        provider.lastVerifiedBaseUrl == provider.baseUrl &&
            provider.lastVerifiedApiKeyMarker == apiKeyMarker(key)
    if (!matchesLastVerification) {
        return ProviderStatusSummary(ProviderStatusKind.UNVERIFIED, "配置已变更，需重新验证")
    }

    val time = formatVerifiedAt(provider.lastVerifiedAt)
    return if (provider.lastVerifyError.isBlank()) {
        ProviderStatusSummary(ProviderStatusKind.VERIFIED, "验证成功（$time）", "上次验证成功：$time")
    } else {
        ProviderStatusSummary(ProviderStatusKind.FAILED, "验证失败（$time）", provider.lastVerifyError)
    }
}

fun apiKeyMarker(key: String?): String {
    if (key.isNullOrBlank()) return ""
    val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
    return digest.take(8).joinToString("") { "%02x".format(it) }
}

private fun formatVerifiedAt(epochMillis: Long): String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
