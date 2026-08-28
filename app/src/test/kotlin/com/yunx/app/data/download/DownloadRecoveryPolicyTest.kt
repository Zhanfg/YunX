package com.yunx.app.data.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadRecoveryPolicyTest {
    @Test
    fun classifiesRecoverableHttpFailures() {
        assertEquals(RecoveryCause.AUTH_OR_LINK_EXPIRED, DownloadRecoveryPolicy.classifyHttp(403))
        assertEquals(RecoveryCause.RATE_LIMITED, DownloadRecoveryPolicy.classifyHttp(429))
        assertEquals(RecoveryCause.SERVER_ERROR, DownloadRecoveryPolicy.classifyHttp(503))
        assertEquals(RecoveryCause.NOT_FOUND, DownloadRecoveryPolicy.classifyHttp(410))
    }

    @Test
    fun refreshesExpiredSignedLinksWhenSupported() {
        val action = DownloadRecoveryPolicy.action(
            RecoveryCause.AUTH_OR_LINK_EXPIRED,
            attempt = 1,
            hasAlternativeEndpoint = false,
            canRefreshSource = true
        )
        assertTrue(action is RecoveryAction.RefreshSource)
    }

    @Test
    fun rotatesEndpointForServerAndNetworkFailures() {
        val server = DownloadRecoveryPolicy.action(
            RecoveryCause.SERVER_ERROR, 0, hasAlternativeEndpoint = true, canRefreshSource = false
        )
        val network = DownloadRecoveryPolicy.action(
            RecoveryCause.TRANSIENT_NETWORK, 0, hasAlternativeEndpoint = true, canRefreshSource = false
        )
        assertTrue(server is RecoveryAction.RotateEndpoint)
        assertTrue(network is RecoveryAction.RotateEndpoint)
    }

    @Test
    fun rateLimitRequestsConcurrencyBackoff() {
        val action = DownloadRecoveryPolicy.action(
            RecoveryCause.RATE_LIMITED, 3, hasAlternativeEndpoint = true, canRefreshSource = true
        )
        assertTrue(action is RecoveryAction.ReduceConcurrency)
        assertEquals(6_000L, action.delayMillis)
    }

    @Test
    fun exponentialBackoffIsCapped() {
        assertEquals(750L, DownloadRecoveryPolicy.exponentialBackoff(0))
        assertEquals(1_500L, DownloadRecoveryPolicy.exponentialBackoff(1))
        assertEquals(30_000L, DownloadRecoveryPolicy.exponentialBackoff(10))
    }
}
