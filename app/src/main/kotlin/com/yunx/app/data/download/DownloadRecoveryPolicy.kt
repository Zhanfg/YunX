package com.yunx.app.data.download

import kotlin.math.min

/** 下载失败的语义分类；上层不再把所有异常都当成“原 URL 再试一次”。 */
enum class RecoveryCause {
    RATE_LIMITED,
    AUTH_OR_LINK_EXPIRED,
    SERVER_ERROR,
    TRANSIENT_NETWORK,
    RANGE_IGNORED,
    NOT_FOUND,
    FATAL
}

sealed interface RecoveryAction {
    val delayMillis: Long

    data class RetrySameEndpoint(override val delayMillis: Long) : RecoveryAction
    data class RotateEndpoint(override val delayMillis: Long) : RecoveryAction
    data class RefreshSource(override val delayMillis: Long) : RecoveryAction
    data class ReduceConcurrency(override val delayMillis: Long) : RecoveryAction
    data class Fail(val reason: String) : RecoveryAction {
        override val delayMillis: Long = 0L
    }
}

/**
 * 纯 Kotlin 恢复策略，便于单测。
 *
 * - 401/403：网盘临时签名、Cookie/JWT 或 URL 可能过期，优先重新取链；
 * - 429：指数退避并降低并发；
 * - 5xx：存在备用 endpoint 时先换 CDN，否则原端点退避；
 * - 网络 IO：优先换 endpoint；
 * - Range 被忽略：降低并发，让现有单流回退机制接管；
 * - 404/410：临时链接可能失效，有刷新能力时重新取链，否则失败。
 */
object DownloadRecoveryPolicy {
    const val MAX_BACKOFF_MILLIS = 30_000L

    fun classifyHttp(code: Int): RecoveryCause = when (code) {
        401, 403 -> RecoveryCause.AUTH_OR_LINK_EXPIRED
        404, 410 -> RecoveryCause.NOT_FOUND
        429 -> RecoveryCause.RATE_LIMITED
        in 500..599 -> RecoveryCause.SERVER_ERROR
        else -> RecoveryCause.FATAL
    }

    fun action(
        cause: RecoveryCause,
        attempt: Int,
        hasAlternativeEndpoint: Boolean,
        canRefreshSource: Boolean
    ): RecoveryAction {
        val delay = exponentialBackoff(attempt)
        return when (cause) {
            RecoveryCause.RATE_LIMITED -> RecoveryAction.ReduceConcurrency(delay)

            RecoveryCause.AUTH_OR_LINK_EXPIRED -> if (canRefreshSource) {
                RecoveryAction.RefreshSource(min(delay, 2_000L))
            } else {
                RecoveryAction.Fail("认证或下载链接已失效")
            }

            RecoveryCause.NOT_FOUND -> if (canRefreshSource) {
                RecoveryAction.RefreshSource(min(delay, 2_000L))
            } else {
                RecoveryAction.Fail("下载资源不存在或临时链接已失效")
            }

            RecoveryCause.SERVER_ERROR -> if (hasAlternativeEndpoint) {
                RecoveryAction.RotateEndpoint(delay)
            } else {
                RecoveryAction.RetrySameEndpoint(delay)
            }

            RecoveryCause.TRANSIENT_NETWORK -> if (hasAlternativeEndpoint) {
                RecoveryAction.RotateEndpoint(min(delay, 3_000L))
            } else {
                RecoveryAction.RetrySameEndpoint(min(delay, 3_000L))
            }

            RecoveryCause.RANGE_IGNORED -> RecoveryAction.ReduceConcurrency(min(delay, 2_000L))
            RecoveryCause.FATAL -> RecoveryAction.Fail("不可恢复的下载错误")
        }
    }

    fun exponentialBackoff(attempt: Int, baseMillis: Long = 750L): Long {
        val safeAttempt = attempt.coerceIn(0, 10)
        val multiplier = 1L shl safeAttempt
        return min(MAX_BACKOFF_MILLIS, baseMillis * multiplier)
    }
}
