package com.yunx.app.data.download

/**
 * 单任务自适应并发控制器。
 *
 * worker 池可在安全窗口逐级上调；遇到 429 / Range ignored / 网络抖动则按策略回落。
 * 本类不依赖 Android/OkHttp，后续 DownloadManager 只负责把运行时信号喂进来。
 */
class AdaptiveConcurrencyController(
    requestedThreads: Int,
    providerLimit: Int = DownloadConcurrencyPolicy.MAX_USER_THREADS,
    initialWorkers: Int = minOf(
        DownloadConcurrencyPolicy.clampUserThreads(requestedThreads),
        DownloadConcurrencyPolicy.clampUserThreads(providerLimit)
    )
) {
    val userLimit: Int = DownloadConcurrencyPolicy.clampUserThreads(requestedThreads)
    val providerLimit: Int = DownloadConcurrencyPolicy.clampUserThreads(providerLimit)

    var currentWorkers: Int = initialWorkers.coerceIn(1, minOf(userLimit, this.providerLimit))
        private set

    var healthyWindows: Int = 0
        private set

    var pressureEvents: Int = 0
        private set

    /** 连续健康窗口才升并发，避免每一次成功请求都扩容造成连接风暴。 */
    fun onHealthyWindow(requiredWindows: Int = 2): Int {
        healthyWindows++
        if (healthyWindows >= requiredWindows.coerceAtLeast(1)) {
            currentWorkers = DownloadConcurrencyPolicy.rampUp(currentWorkers, userLimit, providerLimit)
            healthyWindows = 0
        }
        return currentWorkers
    }

    fun onPressure(pressure: ConcurrencyPressure): Int {
        pressureEvents++
        healthyWindows = 0
        currentWorkers = DownloadConcurrencyPolicy.backoff(currentWorkers, pressure)
            .coerceAtMost(minOf(userLimit, providerLimit))
        return currentWorkers
    }

    fun snapshot(): AdaptiveConcurrencySnapshot = AdaptiveConcurrencySnapshot(
        currentWorkers = currentWorkers,
        userLimit = userLimit,
        providerLimit = providerLimit,
        pressureEvents = pressureEvents
    )
}

data class AdaptiveConcurrencySnapshot(
    val currentWorkers: Int,
    val userLimit: Int,
    val providerLimit: Int,
    val pressureEvents: Int
)
