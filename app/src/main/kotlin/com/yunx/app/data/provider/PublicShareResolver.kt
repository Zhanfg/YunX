package com.yunx.app.data.provider

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** 无账号公开分享可转换出的下载候选。 */
data class PublicDownloadCandidate(
    val providerId: String,
    val sourceUrl: String,
    val downloadUrl: String,
    val alternativeUrls: List<String> = emptyList(),
    val fileKey: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val requiresConfirmation: Boolean = false
)

fun interface PublicShareResolver {
    suspend fun resolve(url: String): Result<PublicDownloadCandidate>
}

/**
 * 无需账号即可工作的公开分享解析层。
 *
 * - Dropbox：使用官方支持的 dl=1 强制下载参数；
 * - pCloud：调用官方无需认证的 getpublinkdownload，保留多个 CDN host；
 * - Google Drive：仅提取稳定 file id，不把可能需要确认页/OAuth 的地址冒充成直链。
 */
object PublicShareResolvers {
    private val httpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val resolvers: Map<String, PublicShareResolver> = mapOf(
        "dropbox" to PublicShareResolver(::resolveDropbox),
        "pcloud" to PublicShareResolver(::resolvePCloud)
    )

    fun supports(providerId: String): Boolean = resolvers.containsKey(providerId)

    suspend fun resolve(text: String): Result<PublicDownloadCandidate> {
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

    private suspend fun resolveDropbox(url: String): Result<PublicDownloadCandidate> = runCatching {
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
        val normalized = buildString {
            append(uri.scheme)
            append("://")
            append(uri.rawAuthority)
            append(uri.rawPath)
            append('?')
            append(query)
            uri.rawFragment?.let {
                append('#')
                append(it)
            }
        }

        PublicDownloadCandidate(
            providerId = "dropbox",
            sourceUrl = url,
            downloadUrl = normalized,
            fileKey = uri.path.substringAfterLast('/').takeIf { it.isNotBlank() }
        )
    }

    private suspend fun resolvePCloud(url: String): Result<PublicDownloadCandidate> = runCatching {
        val code = pCloudCode(url) ?: error("无法提取 pCloud public-link code")
        val apiUrl = "https://api.pcloud.com/getpublinkdownload?code=${encodeQueryValue(code)}&forcedownload=1"
        val json = withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(apiUrl)
                .header("Referer", "https://pcloud.com/")
                .header("User-Agent", "YunX-Android")
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("pCloud API HTTP ${response.code}")
                JSONObject(response.body?.string().orEmpty())
            }
        }

        val resultCode = json.optInt("result", -1)
        if (resultCode != 0) {
            val message = json.optString("error", "pCloud public link error $resultCode")
            error(message)
        }
        val path = json.optString("path").takeIf { it.isNotBlank() }
            ?: error("pCloud 未返回下载 path")
        val hostsJson = json.optJSONArray("hosts") ?: error("pCloud 未返回下载 host")
        val endpoints = buildList {
            for (i in 0 until hostsJson.length()) {
                val host = hostsJson.optString(i).trim()
                if (host.isNotBlank()) add("https://$host$path")
            }
        }.distinct()
        require(endpoints.isNotEmpty()) { "pCloud 未返回可用下载节点" }

        PublicDownloadCandidate(
            providerId = "pcloud",
            sourceUrl = url,
            downloadUrl = endpoints.first(),
            alternativeUrls = endpoints.drop(1),
            fileKey = path.substringAfterLast('/').takeIf { it.isNotBlank() },
            headers = mapOf("Referer" to "https://pcloud.com/")
        )
    }

    /** 支持 pCloud 长链接 code=xxx 与 pc.cd 短码。 */
    fun pCloudCode(text: String): String? {
        val url = CloudProviderRegistry.extractFirstUrl(text) ?: return null
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (host == "pc.cd" || host.endsWith(".pc.cd")) {
            return uri.path.trim('/').substringBefore('/').takeIf { it.isNotBlank() }
        }
        if (!host.contains("pcloud")) return null

        fun extract(raw: String?): String? = raw.orEmpty()
            .split('&')
            .firstNotNullOfOrNull { pair ->
                val key = pair.substringBefore('=').substringAfterLast('#')
                val value = pair.substringAfter('=', "")
                if (key == "code" && value.isNotBlank()) value else null
            }

        return extract(uri.rawQuery) ?: extract(uri.rawFragment)
    }

    /** Google Drive 公开分享 ID 提取器，供后续 OAuth/确认页 Provider 共用。 */
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

    fun encodeQueryValue(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}
