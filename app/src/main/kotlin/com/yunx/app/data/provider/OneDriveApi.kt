package com.yunx.app.data.provider

import com.yunx.app.data.download.DownloadSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class OneDriveApi(
    private val client: OkHttpClient
) : GlobalCloudApi {
    override val providerId: String = "onedrive"

    override suspend fun listFiles(
        parentId: String?,
        accessToken: String
    ): Result<List<GlobalCloudFile>> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = if (parentId.isNullOrBlank()) {
                "$GRAPH_BASE/me/drive/root/children"
            } else {
                "$GRAPH_BASE/me/drive/items/$parentId/children"
            }
            var nextUrl: HttpUrl? = endpoint.toHttpUrl().newBuilder()
                .addQueryParameter(
                    "\$select",
                    "id,name,size,folder,file,lastModifiedDateTime,@microsoft.graph.downloadUrl"
                )
                .addQueryParameter("\$top", "999")
                .build()
            val result = mutableListOf<GlobalCloudFile>()
            var pageCount = 0

            while (nextUrl != null) {
                requireTrustedGraphUrl(nextUrl)
                val request = Request.Builder()
                    .url(nextUrl)
                    .header("Authorization", "Bearer $accessToken")
                    .header("Accept", "application/json")
                    .build()
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) error(graphError(response.code, body))
                    val json = JSONObject(body)
                    val values = json.optJSONArray("value")
                    if (values != null) {
                        for (index in 0 until values.length()) {
                            val item = values.getJSONObject(index)
                            result += GlobalCloudFile(
                                id = item.getString("id"),
                                name = item.optString("name", "未命名"),
                                size = item.optLong("size", 0L),
                                isFolder = item.has("folder"),
                                mimeType = item.optJSONObject("file")?.optString("mimeType")
                                    ?.takeIf(String::isNotBlank),
                                modifiedTime = item.optString("lastModifiedDateTime")
                                    .takeIf(String::isNotBlank),
                                downloadUrlHint = item.optString("@microsoft.graph.downloadUrl")
                                    .takeIf(String::isNotBlank)
                            )
                        }
                    }
                    nextUrl = json.optString("@odata.nextLink")
                        .takeIf(String::isNotBlank)
                        ?.toHttpUrlOrNull()
                        ?.also(::requireTrustedGraphUrl)
                }
                pageCount++
                check(pageCount <= MAX_PAGES_PER_FOLDER) { "OneDrive 目录分页异常：超过安全页数上限" }
            }
            result
        }
    }

    override suspend fun getDownloadSource(
        file: GlobalCloudFile,
        accessToken: String
    ): Result<DownloadSource> = withContext(Dispatchers.IO) {
        runCatching {
            require(!file.isFolder) { "文件夹不能直接下载" }
            val directUrl = file.downloadUrlHint ?: fetchDownloadUrl(file.id, accessToken)
            DownloadSource(
                providerId = providerId,
                fileKey = file.id,
                primaryUrl = directUrl,
                // Graph 返回的是预认证短时 URL，不附加 Bearer，避免令牌泄露给下载 CDN。
                expiresAtEpochMillis = System.currentTimeMillis() + 45 * 60 * 1000L,
                refreshContext = mapOf("itemId" to file.id)
            )
        }
    }

    private fun fetchDownloadUrl(itemId: String, accessToken: String): String {
        val url = "$GRAPH_BASE/me/drive/items/$itemId".toHttpUrl().newBuilder()
            .addQueryParameter("\$select", "id,name,size,@microsoft.graph.downloadUrl")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) error(graphError(response.code, body))
            return JSONObject(body).optString("@microsoft.graph.downloadUrl")
                .takeIf(String::isNotBlank)
                ?: error("OneDrive 未返回下载地址")
        }
    }

    /** Never attach the user's Graph bearer token to a host supplied by response data. */
    private fun requireTrustedGraphUrl(url: HttpUrl) {
        require(url.isHttps && url.host.equals(GRAPH_HOST, ignoreCase = true)) {
            "拒绝向非 Microsoft Graph 主机发送授权令牌"
        }
    }

    private fun graphError(code: Int, body: String): String {
        val message = runCatching {
            JSONObject(body).optJSONObject("error")?.optString("message")
                ?.takeIf(String::isNotBlank)
        }.getOrNull()
        return message ?: "Microsoft Graph 请求失败 HTTP $code"
    }

    private companion object {
        const val GRAPH_HOST = "graph.microsoft.com"
        const val GRAPH_BASE = "https://$GRAPH_HOST/v1.0"
        const val MAX_PAGES_PER_FOLDER = 1000
    }
}
