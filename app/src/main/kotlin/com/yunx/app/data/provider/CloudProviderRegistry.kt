package com.yunx.app.data.provider

import java.net.URI

enum class ProviderRegion { DOMESTIC, GLOBAL }

enum class ProviderReadiness {
    /** 已接入现有 YunX 分享浏览/取链流程。 */
    INTEGRATED,

    /** 无需账号即可把公开分享转换为可下载候选地址。 */
    PUBLIC_DOWNLOAD,

    /** 已可靠识别分享域名，但完整 API/认证适配仍需 Provider 实现。 */
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

/**
 * YunX Provider 能力注册表。
 * DETECTED 只代表准确识别，不冒充完整支持；完整浏览/取链后再提升 readiness。
 */
object CloudProviderRegistry {
    val all: List<CloudProviderDescriptor> = listOf(
        CloudProviderDescriptor(
            id = "quark", displayName = "夸克网盘", region = ProviderRegion.DOMESTIC,
            hosts = setOf("pan.quark.cn"), readiness = ProviderReadiness.INTEGRATED,
            authMode = ProviderAuthMode.COOKIE,
            capabilities = ProviderCapabilities(true, true, true, true, true)
        ),
        CloudProviderDescriptor(
            id = "uc", displayName = "UC 网盘", region = ProviderRegion.DOMESTIC,
            hosts = setOf("drive.uc.cn"), readiness = ProviderReadiness.INTEGRATED,
            authMode = ProviderAuthMode.COOKIE,
            capabilities = ProviderCapabilities(true, true, true, true)
        ),
        CloudProviderDescriptor(
            id = "xunlei", displayName = "迅雷网盘", region = ProviderRegion.DOMESTIC,
            hosts = setOf("pan.xunlei.com"), readiness = ProviderReadiness.INTEGRATED,
            authMode = ProviderAuthMode.TOKEN,
            capabilities = ProviderCapabilities(true, true, true, true)
        ),
        CloudProviderDescriptor(
            id = "baidu", displayName = "百度网盘", region = ProviderRegion.DOMESTIC,
            hosts = setOf("pan.baidu.com"), readiness = ProviderReadiness.INTEGRATED,
            authMode = ProviderAuthMode.COOKIE,
            capabilities = ProviderCapabilities(true, true, true, true)
        ),
        CloudProviderDescriptor(
            id = "c139", displayName = "139 网盘", region = ProviderRegion.DOMESTIC,
            hosts = setOf("yun.139.com"), readiness = ProviderReadiness.INTEGRATED,
            authMode = ProviderAuthMode.COOKIE,
            capabilities = ProviderCapabilities(true, true, true, true)
        ),
        CloudProviderDescriptor(
            id = "pan123", displayName = "123 云盘", region = ProviderRegion.DOMESTIC,
            hosts = setOf("123pan.com", "www.123pan.com", "123865.com", "www.123865.com", "share.123pan.cn"),
            readiness = ProviderReadiness.INTEGRATED, authMode = ProviderAuthMode.TOKEN,
            capabilities = ProviderCapabilities(true, true, true, true)
        ),

        // V3 国内扩展：准确识别 + Provider 能力边界，认证 API 独立实现。
        CloudProviderDescriptor(
            id = "aliyun", displayName = "阿里云盘", region = ProviderRegion.DOMESTIC,
            hosts = setOf("alipan.com", "www.alipan.com", "aliyundrive.com", "www.aliyundrive.com"),
            readiness = ProviderReadiness.DETECTED, authMode = ProviderAuthMode.WEB_SESSION,
            capabilities = ProviderCapabilities(refreshDownloadLink = true)
        ),
        CloudProviderDescriptor(
            id = "tianyi", displayName = "天翼云盘", region = ProviderRegion.DOMESTIC,
            hosts = setOf("cloud.189.cn"), readiness = ProviderReadiness.DETECTED,
            authMode = ProviderAuthMode.WEB_SESSION,
            capabilities = ProviderCapabilities(refreshDownloadLink = true)
        ),
        CloudProviderDescriptor(
            id = "lanzou", displayName = "蓝奏云", region = ProviderRegion.DOMESTIC,
            hosts = setOf(
                "lanzou.com", "lanzoui.com", "lanzoux.com", "lanzoub.com",
                "lanzouw.com", "lanzouj.com", "lanzoue.com", "lanzouf.com", "lanzoup.com"
            ),
            readiness = ProviderReadiness.DETECTED, authMode = ProviderAuthMode.NONE,
            capabilities = ProviderCapabilities(directDownload = true, refreshDownloadLink = true)
        ),
        CloudProviderDescriptor(
            id = "115", displayName = "115", region = ProviderRegion.DOMESTIC,
            hosts = setOf("115.com", "share.115.com"), readiness = ProviderReadiness.DETECTED,
            authMode = ProviderAuthMode.WEB_SESSION,
            capabilities = ProviderCapabilities(refreshDownloadLink = true)
        ),
        CloudProviderDescriptor(
            id = "pikpak", displayName = "PikPak", region = ProviderRegion.DOMESTIC,
            hosts = setOf("mypikpak.com", "www.mypikpak.com"), readiness = ProviderReadiness.DETECTED,
            authMode = ProviderAuthMode.OAUTH,
            capabilities = ProviderCapabilities(refreshDownloadLink = true)
        ),
        CloudProviderDescriptor(
            id = "ctfile", displayName = "城通网盘", region = ProviderRegion.DOMESTIC,
            hosts = setOf("ctfile.com", "www.ctfile.com", "app.ctfile.com"),
            readiness = ProviderReadiness.DETECTED, authMode = ProviderAuthMode.WEB_SESSION,
            capabilities = ProviderCapabilities(refreshDownloadLink = true)
        ),

        // V4 海外扩展。
        CloudProviderDescriptor(
            id = "dropbox", displayName = "Dropbox", region = ProviderRegion.GLOBAL,
            hosts = setOf("dropbox.com", "www.dropbox.com"), readiness = ProviderReadiness.PUBLIC_DOWNLOAD,
            authMode = ProviderAuthMode.NONE,
            capabilities = ProviderCapabilities(directDownload = true, refreshDownloadLink = true)
        ),
        CloudProviderDescriptor(
            id = "google_drive", displayName = "Google Drive", region = ProviderRegion.GLOBAL,
            hosts = setOf("drive.google.com", "docs.google.com"), readiness = ProviderReadiness.DETECTED,
            authMode = ProviderAuthMode.OAUTH,
            capabilities = ProviderCapabilities(refreshDownloadLink = true)
        ),
        CloudProviderDescriptor(
            id = "onedrive", displayName = "OneDrive", region = ProviderRegion.GLOBAL,
            hosts = setOf("1drv.ms", "onedrive.live.com"), readiness = ProviderReadiness.DETECTED,
            authMode = ProviderAuthMode.OAUTH,
            capabilities = ProviderCapabilities(refreshDownloadLink = true)
        ),
        CloudProviderDescriptor(
            id = "mega", displayName = "MEGA", region = ProviderRegion.GLOBAL,
            hosts = setOf("mega.nz", "mega.io"), readiness = ProviderReadiness.DETECTED,
            authMode = ProviderAuthMode.NONE,
            capabilities = ProviderCapabilities(refreshDownloadLink = true)
        ),
        CloudProviderDescriptor(
            id = "box", displayName = "Box", region = ProviderRegion.GLOBAL,
            hosts = setOf("box.com", "app.box.com"), readiness = ProviderReadiness.DETECTED,
            authMode = ProviderAuthMode.OAUTH,
            capabilities = ProviderCapabilities(refreshDownloadLink = true)
        ),
        CloudProviderDescriptor(
            id = "pcloud", displayName = "pCloud", region = ProviderRegion.GLOBAL,
            hosts = setOf(
                "pcloud.com", "www.pcloud.com", "my.pcloud.com",
                "e.pcloud.link", "u.pcloud.link", "pc.cd"
            ),
            readiness = ProviderReadiness.PUBLIC_DOWNLOAD, authMode = ProviderAuthMode.NONE,
            capabilities = ProviderCapabilities(
                directDownload = true,
                refreshDownloadLink = true,
                alternativeEndpoints = true
            )
        ),
        CloudProviderDescriptor(
            id = "mediafire", displayName = "MediaFire", region = ProviderRegion.GLOBAL,
            hosts = setOf("mediafire.com", "www.mediafire.com"), readiness = ProviderReadiness.DETECTED,
            authMode = ProviderAuthMode.NONE,
            capabilities = ProviderCapabilities(directDownload = true, refreshDownloadLink = true)
        )
    )

    private val byId = all.associateBy { it.id }

    fun byId(id: String): CloudProviderDescriptor? = byId[id.lowercase()]

    fun detect(text: String): CloudProviderDescriptor? {
        val url = extractFirstUrl(text) ?: return null
        val host = runCatching { URI(url).host?.lowercase() }.getOrNull() ?: return null
        return all.firstOrNull { provider ->
            provider.hosts.any { allowed -> host == allowed || host.endsWith(".$allowed") }
        }
    }

    fun extractFirstUrl(text: String): String? =
        Regex("""https?://[^\s]+""", RegexOption.IGNORE_CASE)
            .find(text.trim())?.value
            ?.trimEnd('。', '，', ',', '；', ';', ')', ']', '}', '"', '\'')
}
