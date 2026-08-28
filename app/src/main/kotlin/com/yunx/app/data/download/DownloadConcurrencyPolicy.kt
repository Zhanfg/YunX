package com.yunx.app.data.download

import kotlin.math.max
import kotlin.math.min

/**
 * 下载并发策略的纯 Kotlin 基础层。
 *
 * 这里刻意把「用户允许的最大线程数」和「某一时刻真正启动的 worker 数」分开：
 * - 用户可以允许最高 128 线程；
 * - Provider / CDN 可以施加更低的安全上限；
 * - 下载过程中遇到限流、Range 被忽略或瞬时网络异常时，可按压力类型主动回落。
 *
 * 该策略不持有 Android / 网络对象，便于单元测试，也方便后续接入实时吞吐、RTT、429/5xx 等指标。
 */
object DownloadConcurrencyPolicy {
    const val MIN_USER_THREADS = 1
    const val DEFAULT_USER_THREADS = 16
    const val MAX_USER_THREADS = 128

    /** 已知迅雷 CDN 对同文件高并发 Range 更敏感，现有实现经验上保持 8 更稳定。 */
    const val XUNLEI_SAFE_CAP = 8

    fun clampUserThreads(value: Int): Int =
        value.coerceIn(MIN_USER_THREADS, MAX_USER_THREADS)

    /**
     * 计算任务初始 worker 数。
     * 目前只保留已有代码已经验证过的迅雷安全上限；其它 Provider 暂不拍脑袋加限制。
     */
    fun initialWorkers(
        requestedThreads: Int,
        url: String,
        headers: Map<String, String>
    ): Int {
        val requested = clampUserThreads(requestedThreads)
        return if (isXunlei(url, headers)) min(requested, XUNLEI_SAFE_CAP) else requested
    }

    /**
     * 根据运行时压力回落并发。
     *
     * RATE_LIMITED / RANGE_IGNORED：快速减半，尽快摆脱 429 或 CDN 忽略 Range 的失败风暴；
     * TRANSIENT_NETWORK：温和回落 25%，避免 Wi-Fi/蜂窝瞬断时一次性把并发砍得过低。
     */
    fun backoff(currentWorkers: Int, pressure: ConcurrencyPressure): Int {
        val current = clampUserThreads(currentWorkers)
        return when (pressure) {
            ConcurrencyPressure.RATE_LIMITED,
            ConcurrencyPressure.RANGE_IGNORED -> max(MIN_USER_THREADS, current / 2)

            ConcurrencyPressure.TRANSIENT_NETWORK ->
                max(MIN_USER_THREADS, (current * 3) / 4)
        }
    }

    /**
     * 健康状态下逐级增并发，而不是从 16 直接跳到 128。
     * 每次最多增长 50%，并受用户上限与 Provider 上限共同约束。
     */
    fun rampUp(currentWorkers: Int, userLimit: Int, providerLimit: Int = MAX_USER_THREADS): Int {
        val current = clampUserThreads(currentWorkers)
        val cap = min(clampUserThreads(userLimit), clampUserThreads(providerLimit))
        if (current >= cap) return cap
        val step = max(1, current / 2)
        return min(cap, current + step)
    }

    private fun isXunlei(url: String, headers: Map<String, String>): Boolean =
        url.contains("xunlei", ignoreCase = true) ||
            headers["User-Agent"]?.contains("xunlei", ignoreCase = true) == true
}

enum class ConcurrencyPressure {
    RATE_LIMITED,
    RANGE_IGNORED,
    TRANSIENT_NETWORK
}
