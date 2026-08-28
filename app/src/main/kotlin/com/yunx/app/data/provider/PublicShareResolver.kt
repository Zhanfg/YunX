package com.yunx.app.data.provider

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
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
 * 只处理分享方明确开放给“任何拥有链接的人”的公开资源；不会绕过登录、密码或禁止下载权限。
 */
object PublicShareResolvers {
    private const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126 Mobile Safari/537.36 YunX/1.3"

    private val httpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val resolvers: Map<String, PublicShareResolver> = mapOf(
        "dropbox" to PublicShareResolver(::resolveDropbox),
        "pcloud" to PublicShareResolver(::resolvePCloud),
        "google_drive" to PublicShareResolver(::resolveGoogleDrive),
        "onedrive" to PublicShareResolver(::resolveOneDrive),
        "icloud_drive" to PublicShareResolver(::resolveICloudDrive)
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

        val params = parseQuery(uri.rawQuery).toMutableMap()
        params.remove("dl")
        params["dl"] = "1"
        val normalized = rebuildUri(uri, path = uri.rawPath, query = params)

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
                .header("User-Agent", UA)
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

    /** Google Drive / Docs / Sheets / Slides 的公开分享下载。 */
    private suspend fun resolveGoogleDrive(url: String): Result<PublicDownloadCandidate> = runCatching {
        val uri = URI(url)
        val host = uri.host?.lowercase() ?: error("Google Drive URL 缺少 host")
        require(host == "drive.google.com" || host == "docs.google.com") { "不是 Google Drive 分享链接" }
        val id = googleDriveFileId(url) ?: error("无法提取 Google Drive 文件 ID")

        val typedExport = googleWorkspaceExport(uri, id)
        if (typedExport != null) {
            val probe = probeBinary(typedExport.first, referer = url)
            if (!probe.isDownload) {
                error("Google Workspace 文件不可公开下载，或分享方已禁止下载")
            }
            return@runCatching PublicDownloadCandidate(
                providerId = "google_drive",
                sourceUrl = url,
                downloadUrl = probe.finalUrl,
                fileKey = probe.fileName ?: typedExport.second,
                headers = mapOf("Referer" to url, "User-Agent" to UA)
            )
        }

        val direct = "https://drive.usercontent.google.com/download?id=${encodeQueryValue(id)}&export=download&confirm=t"
        var probe = probeBinary(direct, referer = url, captureHtml = true)
        if (!probe.isDownload && !probe.html.isNullOrBlank()) {
            val confirmed = googleConfirmUrl(probe.html)
            if (confirmed != null) {
                probe = probeBinary(confirmed, referer = url)
            }
        }
        if (!probe.isDownload) {
            error("Google Drive 文件不可公开下载、需要登录，或分享方已禁止下载")
        }

        PublicDownloadCandidate(
            providerId = "google_drive",
            sourceUrl = url,
            downloadUrl = probe.finalUrl,
            fileKey = probe.fileName ?: "google_drive_$id",
            headers = mapOf("Referer" to url, "User-Agent" to UA)
        )
    }

    /** OneDrive / SharePoint 的 Anyone 公共分享。 */
    private suspend fun resolveOneDrive(url: String): Result<PublicDownloadCandidate> = runCatching {
        val sourceUri = URI(url)
        val sourceHost = sourceUri.host?.lowercase() ?: error("OneDrive URL 缺少 host")
        require(
            sourceHost == "1drv.ms" ||
                sourceHost == "onedrive.live.com" ||
                sourceHost.endsWith(".sharepoint.com")
        ) { "不是 OneDrive 分享链接" }

        val expanded = expandPublicUrl(url)
        val expandedUri = URI(expanded)
        val host = expandedUri.host?.lowercase() ?: error("OneDrive 展开链接缺少 host")

        val direct = when {
            host == "onedrive.live.com" -> {
                val params = parseQuery(expandedUri.rawQuery).toMutableMap()
                params["download"] = "1"
                rebuildUri(expandedUri, path = "/download", query = params)
            }
            host.endsWith(".sharepoint.com") -> {
                val params = parseQuery(expandedUri.rawQuery).toMutableMap()
                params["download"] = "1"
                rebuildUri(expandedUri, path = expandedUri.rawPath, query = params)
            }
            else -> error("OneDrive 分享链接未展开到可识别地址")
        }

        val probe = probeBinary(direct, referer = expanded)
        if (!probe.isDownload) {
            error("OneDrive 文件不可公开下载；可能启用了 Block download、密码、登录限制，或链接指向文件夹")
        }

        PublicDownloadCandidate(
            providerId = "onedrive",
            sourceUrl = url,
            downloadUrl = probe.finalUrl,
            fileKey = probe.fileName ?: expandedUri.path.substringAfterLast('/').takeIf { it.contains('.') }
                ?: "onedrive_download",
            headers = mapOf("Referer" to expanded, "User-Agent" to UA)
        )
    }

    /** iCloud Drive 公开文件：通过 public CloudKit resolve 获取 Apple 下发的 downloadURL。 */
    private suspend fun resolveICloudDrive(url: String): Result<PublicDownloadCandidate> = runCatching {
        val uri = URI(url)
        val host = uri.host?.lowercase() ?: error("iCloud URL 缺少 host")
        require(host == "icloud.com" || host == "www.icloud.com") { "不是 iCloud 分享链接" }
        val shortId = iCloudShortId(url) ?: error("无法提取 iCloud Drive 分享 ID")

        val payload = JSONObject()
            .put("shortGUIDs", org.json.JSONArray().put(JSONObject().put("value", shortId)))
            .toString()
        val resolveUrl = "https://ckdatabasews.icloud.com/database/1/com.apple.cloudkit/production/public/records/resolve"
        val json = withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(resolveUrl)
                .header("User-Agent", UA)
                .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("iCloud CloudKit HTTP ${response.code}")
                JSONObject(response.body?.string().orEmpty())
            }
        }

        val root = json.optJSONArray("results")
            ?.optJSONObject(0)
            ?.optJSONObject("rootRecord")
            ?: error("iCloud 未返回公开文件记录")
        if (root.optString("recordType") == "structure") {
            error("iCloud Drive 公开文件夹暂不支持直接下载，请分享单个文件")
        }
        val fields = root.optJSONObject("fields") ?: error("iCloud 文件字段缺失")
        val content = fields.optJSONObject("fileContent")
            ?.optJSONObject("value")
            ?: error("iCloud 文件内容字段缺失")
        val downloadUrl = content.optString("downloadURL").takeIf { it.startsWith("https://") }
            ?: error("iCloud 未返回公开下载地址")

        val extension = fields.optJSONObject("extension")?.optString("value").orEmpty()
        val encodedBaseName = fields.optJSONObject("encryptedBasename")?.optString("value").orEmpty()
        val decodedBaseName = decodeICloudBasename(encodedBaseName)
        val fragmentName = runCatching { URLDecoder.decode(uri.rawFragment.orEmpty(), "UTF-8") }
            .getOrDefault(uri.rawFragment.orEmpty())
            .takeIf { it.isNotBlank() }
        val baseName = decodedBaseName ?: fragmentName ?: "icloud_download"
        val fileName = if (extension.isNotBlank() && !baseName.endsWith(".$extension", ignoreCase = true)) {
            "$baseName.$extension"
        } else {
            baseName
        }

        PublicDownloadCandidate(
            providerId = "icloud_drive",
            sourceUrl = url,
            downloadUrl = downloadUrl,
            fileKey = fileName,
            headers = mapOf("Referer" to "https://www.icloud.com/", "User-Agent" to UA)
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

    /** Google Drive / Docs / Sheets / Slides 分享 ID 提取。 */
    fun googleDriveFileId(text: String): String? {
        val url = CloudProviderRegistry.extractFirstUrl(text) ?: return null
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (host != "drive.google.com" && host != "docs.google.com") return null

        Regex("""/(?:file/d|document/d|spreadsheets/d|presentation/d|drawings/d)/([A-Za-z0-9_-]+)""")
            .find(uri.path.orEmpty())?.groupValues?.getOrNull(1)?.let { return it }

        return parseQuery(uri.rawQuery)["id"]?.takeIf { it.isNotBlank() }
    }

    fun iCloudShortId(text: String): String? {
        val url = CloudProviderRegistry.extractFirstUrl(text) ?: return null
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (host != "icloud.com" && host != "www.icloud.com") return null
        val path = uri.path.orEmpty()
        if (!path.startsWith("/iclouddrive/", ignoreCase = true)) return null
        return path.substringAfter("/iclouddrive/").substringBefore('/').takeIf { it.isNotBlank() }
    }

    private data class DownloadProbe(
        val isDownload: Boolean,
        val finalUrl: String,
        val fileName: String?,
        val html: String? = null
    )

    private suspend fun probeBinary(
        url: String,
        referer: String? = null,
        captureHtml: Boolean = false
    ): DownloadProbe = withContext(Dispatchers.IO) {
        val requestBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Range", "bytes=0-0")
            .get()
        if (!referer.isNullOrBlank()) requestBuilder.header("Referer", referer)

        httpClient.newCall(requestBuilder.build()).execute().use { response ->
            val contentType = response.header("Content-Type").orEmpty().lowercase()
            val disposition = response.header("Content-Disposition")
            val successful = response.isSuccessful || response.code == 206
            val htmlLike = contentType.contains("text/html") || contentType.contains("application/xhtml")
            val isDownload = successful && (!htmlLike || !disposition.isNullOrBlank())
            DownloadProbe(
                isDownload = isDownload,
                finalUrl = response.request.url.toString(),
                fileName = fileNameFromDisposition(disposition),
                html = if (captureHtml && htmlLike) response.body?.string() else null
            )
        }
    }

    private suspend fun expandPublicUrl(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Range", "bytes=0-0")
            .get()
            .build()
        httpClient.newCall(request).execute().use { response ->
            response.request.url.toString()
        }
    }

    private fun googleWorkspaceExport(uri: URI, id: String): Pair<String, String>? {
        val path = uri.path.orEmpty()
        return when {
            path.contains("/document/d/") ->
                "https://docs.google.com/document/d/$id/export?format=docx" to "google_document_$id.docx"
            path.contains("/spreadsheets/d/") ->
                "https://docs.google.com/spreadsheets/d/$id/export?format=xlsx" to "google_sheet_$id.xlsx"
            path.contains("/presentation/d/") ->
                "https://docs.google.com/presentation/d/$id/export/pptx" to "google_slides_$id.pptx"
            path.contains("/drawings/d/") ->
                "https://docs.google.com/drawings/d/$id/export/pdf" to "google_drawing_$id.pdf"
            else -> null
        }
    }

    private fun googleConfirmUrl(html: String): String? {
        val action = Regex("""<form[^>]+action=[\"']([^\"']+)[\"'][^>]*>""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.getOrNull(1)
            ?.replace("&amp;", "&")
            ?: return null
        if (!action.startsWith("https://drive.usercontent.google.com/")) return null

        val params = linkedMapOf<String, String>()
        Regex("""<input[^>]+>""", RegexOption.IGNORE_CASE).findAll(html).forEach { match ->
            val tag = match.value
            val name = Regex("""name=[\"']([^\"']+)[\"']""", RegexOption.IGNORE_CASE)
                .find(tag)?.groupValues?.getOrNull(1)
            val value = Regex("""value=[\"']([^\"']*)[\"']""", RegexOption.IGNORE_CASE)
                .find(tag)?.groupValues?.getOrNull(1)
            if (!name.isNullOrBlank() && value != null && name in setOf("id", "export", "confirm", "uuid")) {
                params[name] = value.replace("&amp;", "&")
            }
        }
        if (params["id"].isNullOrBlank()) return null
        return buildString {
            append(action.substringBefore('?'))
            append('?')
            append(params.entries.joinToString("&") { (key, value) ->
                "${encodeQueryValue(key)}=${encodeQueryValue(value)}"
            })
        }
    }

    private fun fileNameFromDisposition(disposition: String?): String? {
        if (disposition.isNullOrBlank()) return null
        val encoded = Regex("""filename\*=UTF-8''([^;]+)""", RegexOption.IGNORE_CASE)
            .find(disposition)?.groupValues?.getOrNull(1)
        if (!encoded.isNullOrBlank()) {
            return runCatching { URLDecoder.decode(encoded, "UTF-8") }.getOrNull()
        }
        return Regex("""filename=[\"']?([^\"';]+)""", RegexOption.IGNORE_CASE)
            .find(disposition)?.groupValues?.getOrNull(1)?.trim()
    }

    private fun decodeICloudBasename(value: String): String? {
        if (value.isBlank()) return null
        return runCatching {
            String(Base64.decode(value, Base64.DEFAULT), Charsets.UTF_8)
                .trim('\u0000', ' ', '\n', '\r')
                .takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        return rawQuery.split('&')
            .filter { it.isNotBlank() }
            .associate { pair ->
                val key = pair.substringBefore('=')
                val value = pair.substringAfter('=', "")
                key to value
            }
    }

    private fun rebuildUri(uri: URI, path: String, query: Map<String, String>): String = buildString {
        append(uri.scheme)
        append("://")
        append(uri.rawAuthority)
        append(path)
        if (query.isNotEmpty()) {
            append('?')
            append(query.entries.joinToString("&") { (key, value) ->
                if (value.isEmpty()) key else "$key=$value"
            })
        }
    }

    fun encodeQueryValue(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}
