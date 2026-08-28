package com.yunx.app.data.download

import android.util.Log
import com.yunx.app.util.LogRedactor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext
import kotlin.math.min

private const val TAG = "YunX-DL"

/** 分片瞬时网络/HTTP 重试次数。 */
private const val CHUNK_RETRIES = 4
/** 保留额外尝试预算，供网络抖动/Range 中间态恢复。 */
private const val RANGE_RETRIES = 4
/** 网络读缓冲：256KB */
private const val BUFFER_SIZE = 256 * 1024

/** 需要重新建立连接并退避后重试的 HTTP 状态。 */
private class RetryableHttpException(
    val statusCode: Int,
    val retryAfterMillis: Long? = null
) : IOException("retryable HTTP $statusCode")

/**
 * 分片下载结果（结构化）：
 * - OK            : 该分片已正确写入「预期字节数」；
 * - RANGE_IGNORED : 服务器忽略 Range（返回 200 整文件）——上层应回退单流整文件；
 * - FAILED        : 结构性失败（认证失效、非可重试 HTTP、HTML 错误页、字节数不足等）。
 */
enum class ChunkResult { OK, RANGE_IGNORED, FAILED }

/**
 * OkHttp 分片下载器：
 * - Range 分片 + 多线程并行 + 断点续传；
 * - 429 / 408 / 425 / 5xx 使用新 Call + fresh connection 退避重试；
 * - IO 失败后的后续尝试显式 Connection: close，避免持续复用异常 TCP/TLS 链路；
 * - 服务器忽略 Range（200）时不把整文件写入单分片，交由上层安全回退；
 * - 写入后严格校验「已写字节 == 预期字节」；
 * - 任务级取消会主动 cancel() 当前所有 Call。
 */
class ChunkDownloader(private val clientProvider: () -> OkHttpClient) {
    private val client get() = clientProvider()

    /** 任务 id → 该任务当前所有分片请求 */
    private val activeCalls = ConcurrentHashMap<Long, MutableSet<Call>>()
    private fun newCallSet(): MutableSet<Call> =
        Collections.newSetFromMap(ConcurrentHashMap<Call, Boolean>())

    fun cancelCalls(taskId: Long) {
        activeCalls.remove(taskId)?.forEach { call -> runCatching { call.cancel() } }
    }

    // ---------- 总大小探测 ----------

    suspend fun getTotalSize(url: String, headers: Map<String, String>): Long? = withContext(Dispatchers.IO) {
        val withRange = probeSize(url, headers, withRange = true)
        if (withRange != null) return@withContext withRange
        probeSize(url, headers, withRange = false)
    }

    private suspend fun probeSize(url: String, headers: Map<String, String>, withRange: Boolean): Long? =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .apply {
                    if (withRange) header("Range", "bytes=0-0")
                    headers.forEach { (k, v) -> header(k, v) }
                }
                .get().build()
            val call = client.newCall(request)
            val cancelHandle = coroutineContext[Job]?.invokeOnCompletion { call.cancel() }
            try {
                runCatching {
                    call.execute().use { response ->
                        Log.d(TAG, "getTotalSize: range=$withRange code=${response.code} ct=${response.header("Content-Type")} origin=${LogRedactor.url(url)}")
                        if (response.header("Content-Type").orEmpty().contains("text/html", ignoreCase = true)) {
                            return@use null
                        }
                        if (!response.isSuccessful) return@use null
                        if (withRange) {
                            if (response.code != 206) return@use null
                            val range = HttpRangePolicy.parse(response.header("Content-Range"))
                                ?: return@use null
                            if (range.start != 0L || range.end != 0L) return@use null
                            range.total
                        } else {
                            response.header("Content-Length")?.toLongOrNull()?.takeIf { it > 0 }
                        }
                    }
                }.getOrNull()
            } finally {
                cancelHandle?.dispose()
            }
        }

    // ---------- 分片下载 ----------

    suspend fun downloadChunk(
        taskId: Long,
        url: String,
        start: Long,
        end: Long,
        partFile: File,
        headers: Map<String, String>,
        onBytes: suspend (Long) -> Unit
    ): ChunkResult = withContext(Dispatchers.IO) {
        val attempts = CHUNK_RETRIES + RANGE_RETRIES
        repeat(attempts) { attempt ->
            if (!isActive) throw CancellationException("下载被取消")
            val existing = partFile.length()
            val from = start + existing
            val unknownTotal = end == Long.MAX_VALUE
            val expected = if (unknownTotal) -1L else end - start + 1
            if (!unknownTotal && existing >= expected) return@withContext ChunkResult.OK

            var retryDelayOverride: Long? = null
            val res = try {
                doChunkAttempt(
                    taskId = taskId,
                    url = url,
                    from = from,
                    end = end,
                    unknownTotal = unknownTotal,
                    partFile = partFile,
                    headers = headers,
                    existing = existing,
                    forceFreshConnection = attempt > 0,
                    onBytes = onBytes
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: RetryableHttpException) {
                retryDelayOverride = e.retryAfterMillis
                Log.w(TAG, "downloadChunk: task=$taskId 尝试${attempt + 1} HTTP ${e.statusCode}，更换连接后重试")
                null
            } catch (e: IOException) {
                Log.w(TAG, "downloadChunk: task=$taskId 尝试${attempt + 1} IO异常，更换连接后重试: ${e.message}")
                if (!isActive) throw CancellationException("下载被取消", e)
                null
            }

            when (res) {
                ChunkResult.OK -> return@withContext ChunkResult.OK
                ChunkResult.FAILED -> return@withContext ChunkResult.FAILED
                ChunkResult.RANGE_IGNORED -> return@withContext ChunkResult.RANGE_IGNORED
                null -> {
                    if (attempt < attempts - 1) {
                        val policyDelay = DownloadRecoveryPolicy.exponentialBackoff(attempt, baseMillis = 500L)
                        val wait = retryDelayOverride?.coerceIn(250L, DownloadRecoveryPolicy.MAX_BACKOFF_MILLIS)
                            ?: policyDelay.coerceAtMost(5_000L)
                        delay(wait)
                    }
                }
            }
        }
        ChunkResult.FAILED
    }

    /** 单次分片请求；retryable HTTP 通过异常交给外层重新建连。 */
    private suspend fun doChunkAttempt(
        taskId: Long,
        url: String,
        from: Long,
        end: Long,
        unknownTotal: Boolean,
        partFile: File,
        headers: Map<String, String>,
        existing: Long,
        forceFreshConnection: Boolean,
        onBytes: suspend (Long) -> Unit
    ): ChunkResult {
        val request = Request.Builder()
            .url(url)
            .header("Range", if (unknownTotal) "bytes=$from-" else "bytes=$from-$end")
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .apply {
                // 失败后的下一次请求不复用原 keep-alive 连接，使 OkHttp 有机会重新选 TCP/TLS/DNS route。
                if (forceFreshConnection) header("Connection", "close")
            }
            .get().build()

        val call = client.newCall(request)
        activeCalls.getOrPut(taskId) { newCallSet() }.add(call)
        val cancelHandle = coroutineContext[Job]?.invokeOnCompletion { call.cancel() }
        try {
            return call.execute().use { response ->
                val code = response.code

                // 这些状态通常是临时拥塞/网关/CDN 故障。不要立即把分片判死。
                if (code == 408 || code == 425 || code == 429 || code in 500..599) {
                    throw RetryableHttpException(
                        statusCode = code,
                        retryAfterMillis = parseRetryAfterMillis(response.header("Retry-After"))
                    )
                }

                if (response.header("Content-Type").orEmpty().contains("text/html", ignoreCase = true)) {
                    Log.w(TAG, "downloadChunk: task=$taskId 返回 text/html（疑似广告/认证/过期页），终止")
                    return@use ChunkResult.FAILED
                }

                when (code) {
                    206 -> {
                        val requestedEnd = if (unknownTotal) null else end
                        if (!HttpRangePolicy.matches(response.header("Content-Range"), from, requestedEnd)) {
                            Log.w(TAG, "downloadChunk: task=$taskId Content-Range 与请求不一致")
                            return@use ChunkResult.FAILED
                        }
                        val body = response.body ?: return@use ChunkResult.FAILED
                        val expected = if (unknownTotal) -1L else end - from + 1
                        val written = writeSlice(body.byteStream(), partFile, existing, expected, onBytes)
                        if (!unknownTotal && written != expected) {
                            Log.w(TAG, "downloadChunk: task=$taskId 分片写入不足 written=$written 预期=$expected")
                            return@use ChunkResult.FAILED
                        }
                        ChunkResult.OK
                    }
                    200 -> {
                        Log.w(TAG, "downloadChunk: task=$taskId Range 请求返回 200，拒绝按分片写入")
                        ChunkResult.RANGE_IGNORED
                    }
                    else -> {
                        Log.w(TAG, "downloadChunk: task=$taskId 非预期状态码 $code")
                        ChunkResult.FAILED
                    }
                }
            }
        } finally {
            activeCalls[taskId]?.remove(call)
            cancelHandle?.dispose()
        }
    }

    internal fun parseRetryAfterMillis(value: String?): Long? {
        val seconds = value?.trim()?.toLongOrNull() ?: return null
        if (seconds < 0) return null
        return (seconds * 1000L).coerceAtMost(DownloadRecoveryPolicy.MAX_BACKOFF_MILLIS)
    }

    /** 把响应流写入 partFile（seek 到 existing），截断到 expected 字节；返回实际写入字节数 */
    private suspend fun writeSlice(
        input: java.io.InputStream,
        partFile: File,
        existing: Long,
        expected: Long,
        onBytes: suspend (Long) -> Unit
    ): Long {
        var written = 0L
        RandomAccessFile(partFile, "rw").use { raf ->
            raf.seek(existing)
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                val allow = if (expected < 0) read.toLong() else min(read.toLong(), expected - written)
                if (allow <= 0) break
                raf.write(buffer, 0, allow.toInt())
                written += allow
                onBytes(allow)
                if (expected >= 0 && written >= expected) break
            }
        }
        return written
    }

    // ---------- 单流整文件（回退） ----------

    suspend fun downloadFull(
        taskId: Long,
        url: String,
        partFile: File,
        headers: Map<String, String>,
        /** 已知总大小（字节）；-1/0 = 未知，不截断（流式场景） */
        total: Long = -1L,
        onBytes: suspend (Long) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val existing = 0L
        if (partFile.exists()) RandomAccessFile(partFile, "rw").use { it.setLength(0) }
        Log.d(TAG, "downloadFull: task=$taskId 完整下载 origin=${LogRedactor.url(url)} total=$total")

        val maxAttempts = 4
        repeat(maxAttempts) { attempt ->
            if (!isActive) throw CancellationException("下载被取消")
            if (attempt > 0 && partFile.exists()) {
                RandomAccessFile(partFile, "rw").use { it.setLength(0) }
            }
            val request = Request.Builder()
                .url(url)
                .apply { headers.forEach { (k, v) -> header(k, v) } }
                .apply { if (attempt > 0) header("Connection", "close") }
                .get().build()
            val call = client.newCall(request)
            activeCalls.getOrPut(taskId) { newCallSet() }.add(call)
            val cancelHandle = coroutineContext[Job]?.invokeOnCompletion { call.cancel() }
            try {
                val ok = call.execute().use { response ->
                    val code = response.code
                    if (code == 408 || code == 425 || code == 429 || code in 500..599) {
                        val retryAfter = parseRetryAfterMillis(response.header("Retry-After"))
                        throw RetryableHttpException(code, retryAfter)
                    }
                    if (response.header("Content-Type").orEmpty().contains("text/html", ignoreCase = true)) {
                        Log.w(TAG, "downloadFull: task=$taskId 返回 text/html（疑似过期/防盗链/错误页），终止")
                        throw IllegalStateException("下载失败：链接已失效或需要 Referer（返回 HTML 页）")
                    }
                    if (!response.isSuccessful) throw IllegalStateException("下载失败 HTTP $code")
                    val body = response.body ?: return@use false
                    val expected = if (total > 0) total else -1L
                    var written = 0L
                    RandomAccessFile(partFile, "rw").use { raf ->
                        raf.seek(0L)
                        body.byteStream().use { input ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            while (true) {
                                val read = input.read(buffer)
                                if (read <= 0) break
                                val allow = if (expected < 0) read.toLong()
                                else min(read.toLong(), expected - written)
                                if (allow <= 0) break
                                raf.write(buffer, 0, allow.toInt())
                                written += allow
                                onBytes(allow)
                                if (expected >= 0 && written >= expected) break
                            }
                        }
                    }
                    if (total > 0 && written < total) {
                        Log.w(TAG, "downloadFull: task=$taskId 写入不足 written=$written 预期=$total")
                        return@use false
                    }
                    true
                }
                if (ok) return@withContext true
                if (attempt < maxAttempts - 1) {
                    delay(DownloadRecoveryPolicy.exponentialBackoff(attempt, 500L).coerceAtMost(5_000L))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: RetryableHttpException) {
                if (attempt >= maxAttempts - 1) return@withContext false
                val wait = e.retryAfterMillis
                    ?: DownloadRecoveryPolicy.exponentialBackoff(attempt, 500L).coerceAtMost(5_000L)
                Log.w(TAG, "downloadFull: task=$taskId HTTP ${e.statusCode}，更换连接后重试")
                delay(wait)
            } catch (e: IllegalStateException) {
                throw e
            } catch (e: IOException) {
                Log.w(TAG, "downloadFull: task=$taskId IO异常，更换连接后重试: ${e.message}")
                if (!isActive) throw CancellationException("下载被取消", e)
                if (attempt >= maxAttempts - 1) return@withContext false
                delay(DownloadRecoveryPolicy.exponentialBackoff(attempt, 500L).coerceAtMost(5_000L))
            } finally {
                activeCalls[taskId]?.remove(call)
                cancelHandle?.dispose()
            }
        }
        false
    }

    /** 按顺序合并分片为完整文件（零拷贝） */
    suspend fun mergeChunks(chunkFiles: List<File>, target: File): Boolean = withContext(Dispatchers.IO) {
        val ok = runCatching {
            target.parentFile?.mkdirs()
            java.io.FileOutputStream(target).use { fos ->
                fos.channel.use { out ->
                    chunkFiles.forEach { part ->
                        java.io.FileInputStream(part).use { fis ->
                            fis.channel.use { inCh ->
                                var pos = 0L
                                val size = inCh.size()
                                while (pos < size) pos += inCh.transferTo(pos, size - pos, out)
                            }
                        }
                    }
                }
            }
            true
        }.getOrDefault(false)
        Log.d(TAG, "mergeChunks: parts=${chunkFiles.size} target=$target ok=$ok")
        ok
    }
}
