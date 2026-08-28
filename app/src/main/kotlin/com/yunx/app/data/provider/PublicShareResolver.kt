package com.yunx.app.data.provider

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** 无账号公开分享可转换出的下载候选。 */
data class PublicDownloadCandidate(
    val providerId: String,
    val sourceUrl: String,
    val downloadUrl: String,
    val fileKey: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val requiresConfirmation: Boolean = false
)

fun interface PublicShareResolver {
    fun resolve(url: String): Result<PublicDownloadCandidate>
}

/**
 * V4 首批无需登录即可安全转换的公开分享解析。
 *
 * Dropbox 的 dl=1 是官方支持的强制下载参数；Google Drive 这里只提取稳定 file id，
 * 不把未验证的大文件确认页当成“已经拿到直链”，避免出现假成功。
 */
object PublicShareResolvers {
    private val resolvers: Map<String, PublicShareResolver> = mapOf(
        "dropbox" to PublicShareResolver(::resolveDropbox)
    )

    fun supports(providerId: String): Boolean = resolvers.containsKey(providerId)

    fun resolve(text: String): Result<PublicDownloadCandidate> {
        val source = CloudProviderRegistry.extractFirstUrl(text)
            ?: return Result.failure(IllegalArgumentException("未找到有效 URL"))
        val provider = CloudProviderRegistry.detect(source)
            ?: return Result.failure(IllegalArgumentException("暂不识别该网盘分享链接"))
        val resolver = resolvers[provider.id]
            ?: return Result.failure(
                UnsupportedOperationException("${provider.displayName} 已识别，但公开取链 Provider 尚未完成")
            )
        return resolver.resolve(source)
    }

    private fun resolveDropbox(url: String): Result<PublicDownloadCandidate> = runCatching {
        val uri = URI(url)
        val host = uri.host?.lowercase() ?: error("Dropbox URL 缺少 host")
        require(host == "dropbox.com" || host.endsWith(".dropbox.com")) { "不是 Dropbox 分享链接" }

        val params = linkedMapOf<String, String>()
        uri.rawQuery.orEmpty()
            .split('&')
            .filter { it.isNotBlank() }
            .forEach { pair ->
                val key = pair.substringBefore('=')
                val value = pair.substringAfter('=', "")
                if (!key.equals("dl", ignoreCase = true)) params[key] = value
            }
        params["dl"] = "1"

        val query = params.entries.joinToString("&") { (key, value) ->
            if (value.isEmpty()) key else "$key=$value"
        }
        val normalized = URI(
            uri.scheme,
            uri.rawAuthority,
            uri.rawPath,
            query,
            uri.rawFragment
        ).toASCIIString()

        PublicDownloadCandidate(
            providerId = "dropbox",
            sourceUrl = url,
            downloadUrl = normalized,
            fileKey = uri.path.substringAfterLast('/').takeIf { it.isNotBlank() }
        )
    }

    /**
     * Google Drive 公开分享 ID 提取器，供后续 OAuth/确认页 Provider 共用。
     * 不直接伪造 downloadUrl：大文件、权限与病毒扫描确认页均可能需要额外协商。
     */
    fun googleDriveFileId(text: String): String? {
        val url = CloudProviderRegistry.extractFirstUrl(text) ?: return null
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (host != "drive.google.com" && host != "docs.google.com") return null

        Regex("""/file/d/([A-Za-z0-9_-]+)""")
            .find(uri.path.orEmpty())?.groupValues?.getOrNull(1)?.let { return it }

        return uri.rawQuery.orEmpty()
            .split('&')
            .mapNotNull { pair ->
                val key = pair.substringBefore('=')
                val value = pair.substringAfter('=', "")
                if (key == "id" && value.isNotBlank()) value else null
            }
            .firstOrNull()
    }

    /** URL query value helper reserved for Provider implementations. */
    fun encodeQueryValue(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}
