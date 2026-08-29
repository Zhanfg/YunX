package com.yunx.app.data.download

/**
 * 可恢复下载源。
 *
 * fileKey/providerId 表示稳定身份；URLs/headers 表示可过期的瞬时下载凭证。
 * 下载任务以后不再把某一条临时直链视为文件本身，从而允许 403/过期后刷新直链，
 * 或在同一文件的多个 CDN endpoint 之间切换并继续 Range。
 */
data class DownloadSource(
    val providerId: String,
    val fileKey: String,
    val primaryUrl: String,
    val alternativeUrls: List<String> = emptyList(),
    val headers: Map<String, String> = emptyMap(),
    val expiresAtEpochMillis: Long? = null,
    val refreshContext: Map<String, String> = emptyMap()
) {
    val endpoints: List<String>
        get() = (listOf(primaryUrl) + alternativeUrls)
            .filter { it.isNotBlank() }
            .distinct()

    fun isExpired(nowEpochMillis: Long = System.currentTimeMillis(), skewMillis: Long = 30_000L): Boolean =
        expiresAtEpochMillis?.let { nowEpochMillis + skewMillis >= it } ?: false

    fun endpointAt(index: Int): String {
        val urls = endpoints
        require(urls.isNotEmpty()) { "DownloadSource has no endpoint" }
        return urls[Math.floorMod(index, urls.size)]
    }
}

/** Provider 刷新临时直链的边界接口；具体网盘实现负责重新签名/换 CDN。 */
fun interface DownloadSourceRefresher {
    suspend fun refresh(source: DownloadSource): Result<DownloadSource>
}
