package com.yunx.app.data.provider

import com.yunx.app.data.download.DownloadSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
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
            val url = endpoint.toHttpUrl().newBuilder()
                .addQueryParameter(
                    "\$select",
                    "id,name,size,folder,file,lastModifiedDateTime,@microsoft.graph.downloadUrl"
                )
                .addQueryParameter("\$top", "999")
                .build()
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .header("Accept", "application/json")
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) error(graphError(response.code, body))
                val values = JSONObject(body).optJSONArray("value")
                    ?: return@use emptyList<GlobalCloudFile>()
                buildList {
                    for (index in 0 until values.length()) {
                        val item = values.getJSONObject(index)
                        add(
                            GlobalCloudFile(
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
                        )
                    }
                }
            }
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
                // Microsoft Graph preauthenticated URL is deliberately used without Bearer header.
                // It is short-lived and should be refreshed from Graph after expiry.
                expiresAtEpochMillis = System.currentTimeMillis() + 50 * 60 * 1000L,
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

    private fun graphError(code: Int, body: String): String {
        val message = runCatching {
            JSONObject(body).optJSONObject("error")?.optString("message")
                ?.takeIf(String::isNotBlank)
        }.getOrNull()
        return message ?: "Microsoft Graph 请求失败 HTTP $code"
    }

    private companion object {
        const val GRAPH_BASE = "https://graph.microsoft.com/v1.0"
    }
}
