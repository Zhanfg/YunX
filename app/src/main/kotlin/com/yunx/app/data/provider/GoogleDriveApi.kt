package com.yunx.app.data.provider

import com.yunx.app.data.download.DownloadSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class GoogleDriveApi(
    private val client: OkHttpClient
) : GlobalCloudApi {
    override val providerId: String = "google_drive"

    override suspend fun listFiles(
        parentId: String?,
        accessToken: String
    ): Result<List<GlobalCloudFile>> = withContext(Dispatchers.IO) {
        runCatching {
            val parent = parentId?.takeIf { it.isNotBlank() } ?: "root"
            val url = "$API_BASE/files".toHttpUrl().newBuilder()
                .addQueryParameter("q", "'$parent' in parents and trashed = false")
                .addQueryParameter("spaces", "drive")
                .addQueryParameter("pageSize", "1000")
                .addQueryParameter(
                    "fields",
                    "files(id,name,mimeType,size,modifiedTime)"
                )
                .addQueryParameter("orderBy", "folder,name_natural")
                .build()
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .header("Accept", "application/json")
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) error(apiError("Google Drive", response.code, body))
                val files = JSONObject(body).optJSONArray("files")
                    ?: return@use emptyList<GlobalCloudFile>()
                buildList {
                    for (index in 0 until files.length()) {
                        val item = files.getJSONObject(index)
                        val mime = item.optString("mimeType")
                        add(
                            GlobalCloudFile(
                                id = item.getString("id"),
                                name = item.optString("name", "未命名"),
                                size = item.optLong("size", 0L),
                                isFolder = mime == FOLDER_MIME,
                                mimeType = mime,
                                modifiedTime = item.optString("modifiedTime").takeIf(String::isNotBlank)
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
            val url = "$API_BASE/files/${file.id}".toHttpUrl().newBuilder()
                .addQueryParameter("alt", "media")
                .build()
                .toString()
            DownloadSource(
                providerId = providerId,
                fileKey = file.id,
                primaryUrl = url,
                headers = mapOf("Authorization" to "Bearer $accessToken"),
                refreshContext = mapOf("fileId" to file.id)
            )
        }
    }

    private fun apiError(provider: String, code: Int, body: String): String {
        val message = runCatching {
            JSONObject(body).optJSONObject("error")?.optString("message")
                ?.takeIf(String::isNotBlank)
        }.getOrNull()
        return message ?: "$provider API 请求失败 HTTP $code"
    }

    private companion object {
        const val API_BASE = "https://www.googleapis.com/drive/v3"
        const val FOLDER_MIME = "application/vnd.google-apps.folder"
    }
}
