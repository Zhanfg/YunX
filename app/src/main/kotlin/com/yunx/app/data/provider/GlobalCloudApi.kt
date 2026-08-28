package com.yunx.app.data.provider

import com.yunx.app.data.download.DownloadSource

/** Common file model for OAuth-backed global cloud providers. */
data class GlobalCloudFile(
    val id: String,
    val name: String,
    val size: Long,
    val isFolder: Boolean,
    val mimeType: String? = null,
    val modifiedTime: String? = null,
    val downloadUrlHint: String? = null
)

interface GlobalCloudApi {
    val providerId: String
    suspend fun listFiles(parentId: String?, accessToken: String): Result<List<GlobalCloudFile>>
    suspend fun getDownloadSource(file: GlobalCloudFile, accessToken: String): Result<DownloadSource>
}
