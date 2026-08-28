package com.yunx.app.data.provider

import java.net.URI

enum class ProviderRegion { DOMESTIC, GLOBAL }

enum class ProviderReadiness {
    INTEGRATED,
    PUBLIC_DOWNLOAD,
    DETECTED
}

enum class ProviderAuthMode { NONE, COOKIE, TOKEN, PASSWORD, OAUTH, WEB_SESSION }

data class ProviderCapabilities(
    val browseShare: Boolean = false,
    val directDownload: Boolean = false,
    val saveToCloud: Boolean = false,
    val refreshDownloadLink: Boolean = false,
    val alternativeEndpoints: Boolean = false
)

data class CloudProviderDescriptor(
    val id: String,
    val displayName: String,
    val region: ProviderRegion,
    val hosts: Set<String>,
    val readiness: ProviderReadiness,
    val authMode: ProviderAuthMode,
    val capabilities: ProviderCapabilities
)

object CloudProviderRegistry {
    val all: List<CloudProviderDescriptor> = listOf(
        CloudProviderDescriptor("quark", "夸克网盘", ProviderRegion.DOMESTIC, setOf("pan.quark.cn"), ProviderReadiness.INTEGRATED, ProviderAuthMode.COOKIE, ProviderCapabilities(true, true, true, true, true)),
        CloudProviderDescriptor("uc", "UC 网盘", ProviderRegion.DOMESTIC, setOf("drive.uc.cn"), ProviderReadiness.INTEGRATED, ProviderAuthMode.COOKIE, ProviderCapabilities(true, true, true, true)),
        CloudProviderDescriptor("xunlei", "迅雷网盘", ProviderRegion.DOMESTIC, setOf("pan.xunlei.com"), ProviderReadiness.INTEGRATED, ProviderAuthMode.TOKEN, ProviderCapabilities(true, true, true, true)),
        CloudProviderDescriptor("baidu", "百度网盘", ProviderRegion.DOMESTIC, setOf("pan.baidu.com"), ProviderReadiness.INTEGRATED, ProviderAuthMode.COOKIE, ProviderCapabilities(true, true, true, true)),
        CloudProviderDescriptor("c139", "139 网盘", ProviderRegion.DOMESTIC, setOf("yun.139.com"), ProviderReadiness.INTEGRATED, ProviderAuthMode.COOKIE, ProviderCapabilities(true, true, true, true)),
        CloudProviderDescriptor("pan123", "123 云盘", ProviderRegion.DOMESTIC, setOf("123pan.com", "www.123pan.com", "123865.com", "www.123865.com", "share.123pan.cn"), ProviderReadiness.INTEGRATED, ProviderAuthMode.TOKEN, ProviderCapabilities(true, true, true, true)),

        CloudProviderDescriptor("aliyun", "阿里云盘", ProviderRegion.DOMESTIC, setOf("alipan.com", "www.alipan.com", "aliyundrive.com", "www.aliyundrive.com"), ProviderReadiness.DETECTED, ProviderAuthMode.WEB_SESSION, ProviderCapabilities(refreshDownloadLink = true)),
        CloudProviderDescriptor("tianyi", "天翼云盘", ProviderRegion.DOMESTIC, setOf("cloud.189.cn"), ProviderReadiness.DETECTED, ProviderAuthMode.WEB_SESSION, ProviderCapabilities(refreshDownloadLink = true)),
        CloudProviderDescriptor("lanzou", "蓝奏云", ProviderRegion.DOMESTIC, setOf("lanzou.com", "lanzoui.com", "lanzoux.com", "lanzoub.com", "lanzouw.com", "lanzouj.com", "lanzoue.com", "lanzouf.com", "lanzoup.com"), ProviderReadiness.DETECTED, ProviderAuthMode.NONE, ProviderCapabilities(directDownload = true, refreshDownloadLink = true)),
        CloudProviderDescriptor("115", "115", ProviderRegion.DOMESTIC, setOf("115.com", "share.115.com"), ProviderReadiness.DETECTED, ProviderAuthMode.WEB_SESSION, ProviderCapabilities(refreshDownloadLink = true)),
        CloudProviderDescriptor("pikpak", "PikPak", ProviderRegion.DOMESTIC, setOf("mypikpak.com", "www.mypikpak.com"), ProviderReadiness.DETECTED, ProviderAuthMode.OAUTH, ProviderCapabilities(refreshDownloadLink = true)),
        CloudProviderDescriptor("ctfile", "城通网盘", ProviderRegion.DOMESTIC, setOf("ctfile.com", "www.ctfile.com", "app.ctfile.com"), ProviderReadiness.DETECTED, ProviderAuthMode.WEB_SESSION, ProviderCapabilities(refreshDownloadLink = true)),

        CloudProviderDescriptor("dropbox", "Dropbox", ProviderRegion.GLOBAL, setOf("dropbox.com", "www.dropbox.com"), ProviderReadiness.PUBLIC_DOWNLOAD, ProviderAuthMode.NONE, ProviderCapabilities(directDownload = true, refreshDownloadLink = true)),
        CloudProviderDescriptor("google_drive", "Google Drive", ProviderRegion.GLOBAL, setOf("drive.google.com", "docs.google.com"), ProviderReadiness.PUBLIC_DOWNLOAD, ProviderAuthMode.NONE, ProviderCapabilities(directDownload = true, refreshDownloadLink = true)),
        CloudProviderDescriptor("onedrive", "OneDrive", ProviderRegion.GLOBAL, setOf("1drv.ms", "onedrive.live.com"), ProviderReadiness.PUBLIC_DOWNLOAD, ProviderAuthMode.NONE, ProviderCapabilities(directDownload = true, refreshDownloadLink = true)),
        CloudProviderDescriptor("icloud_drive", "iCloud Drive", ProviderRegion.GLOBAL, setOf("icloud.com", "www.icloud.com"), ProviderReadiness.PUBLIC_DOWNLOAD, ProviderAuthMode.NONE, ProviderCapabilities(directDownload = true, refreshDownloadLink = true)),
        CloudProviderDescriptor("mega", "MEGA", ProviderRegion.GLOBAL, setOf("mega.nz", "mega.io"), ProviderReadiness.DETECTED, ProviderAuthMode.NONE, ProviderCapabilities(refreshDownloadLink = true)),
        CloudProviderDescriptor("box", "Box", ProviderRegion.GLOBAL, setOf("box.com", "app.box.com"), ProviderReadiness.DETECTED, ProviderAuthMode.OAUTH, ProviderCapabilities(refreshDownloadLink = true)),
        CloudProviderDescriptor("pcloud", "pCloud", ProviderRegion.GLOBAL, setOf("pcloud.com", "www.pcloud.com", "my.pcloud.com", "e.pcloud.link", "u.pcloud.link", "pc.cd"), ProviderReadiness.PUBLIC_DOWNLOAD, ProviderAuthMode.NONE, ProviderCapabilities(directDownload = true, refreshDownloadLink = true, alternativeEndpoints = true)),
        CloudProviderDescriptor("mediafire", "MediaFire", ProviderRegion.GLOBAL, setOf("mediafire.com", "www.mediafire.com"), ProviderReadiness.DETECTED, ProviderAuthMode.NONE, ProviderCapabilities(directDownload = true, refreshDownloadLink = true))
    )

    private val byId = all.associateBy { it.id }

    fun byId(id: String): CloudProviderDescriptor? = byId[id.lowercase()]

    fun detect(text: String): CloudProviderDescriptor? {
        val url = extractFirstUrl(text) ?: return null
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        return all.firstOrNull { provider ->
            val hostMatches = when (provider.id) {
                "onedrive" -> provider.hosts.any { allowed -> host == allowed || host.endsWith(".$allowed") } || host.endsWith(".sharepoint.com")
                else -> provider.hosts.any { allowed -> host == allowed || host.endsWith(".$allowed") }
            }
            if (!hostMatches) return@firstOrNull false
            if (provider.id == "icloud_drive") {
                uri.path.orEmpty().startsWith("/iclouddrive/", ignoreCase = true)
            } else {
                true
            }
        }
    }

    fun extractFirstUrl(text: String): String? {
        val trimmed = text.trim()
        val match = Regex("""https?://[^\s]+""", RegexOption.IGNORE_CASE).find(trimmed) ?: return null
        val prefix = trimmed.substring(0, match.range.first)
        if (Regex("""[A-Za-z][A-Za-z0-9+.-]*:$""").containsMatchIn(prefix)) return null
        return match.value.trimEnd('。', '，', ',', '；', ';', ')', ']', '}', '"', '\'')
    }
}
