package com.yunx.app.data.download

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadConcurrencyPolicyTest {
    @Test
    fun userThreadLimitAllowsUpTo128() {
        assertEquals(1, DownloadConcurrencyPolicy.clampUserThreads(0))
        assertEquals(64, DownloadConcurrencyPolicy.clampUserThreads(64))
        assertEquals(128, DownloadConcurrencyPolicy.clampUserThreads(128))
        assertEquals(128, DownloadConcurrencyPolicy.clampUserThreads(256))
    }

    @Test
    fun xunleiKeepsSafeInitialCap() {
        assertEquals(
            8,
            DownloadConcurrencyPolicy.initialWorkers(
                requestedThreads = 128,
                url = "https://example.xunlei.com/file.bin",
                headers = emptyMap()
            )
        )
        assertEquals(
            8,
            DownloadConcurrencyPolicy.initialWorkers(
                requestedThreads = 64,
                url = "https://cdn.example.com/file.bin",
                headers = mapOf("User-Agent" to "XunLei/1.0")
            )
        )
    }

    @Test
    fun genericProvidersCanUseConfiguredMaximum() {
        assertEquals(
            128,
            DownloadConcurrencyPolicy.initialWorkers(
                requestedThreads = 128,
                url = "https://cdn.example.com/file.bin",
                headers = emptyMap()
            )
        )
    }

    @Test
    fun severePressureHalvesWorkers() {
        assertEquals(32, DownloadConcurrencyPolicy.backoff(64, ConcurrencyPressure.RATE_LIMITED))
        assertEquals(16, DownloadConcurrencyPolicy.backoff(32, ConcurrencyPressure.RANGE_IGNORED))
        assertEquals(1, DownloadConcurrencyPolicy.backoff(1, ConcurrencyPressure.RATE_LIMITED))
    }

    @Test
    fun transientNetworkFailureUsesGentlerBackoff() {
        assertEquals(48, DownloadConcurrencyPolicy.backoff(64, ConcurrencyPressure.TRANSIENT_NETWORK))
        assertEquals(12, DownloadConcurrencyPolicy.backoff(16, ConcurrencyPressure.TRANSIENT_NETWORK))
    }

    @Test
    fun healthyRampUpIsGradualAndCapped() {
        assertEquals(24, DownloadConcurrencyPolicy.rampUp(16, userLimit = 128))
        assertEquals(36, DownloadConcurrencyPolicy.rampUp(24, userLimit = 128))
        assertEquals(64, DownloadConcurrencyPolicy.rampUp(64, userLimit = 128, providerLimit = 64))
    }
}
