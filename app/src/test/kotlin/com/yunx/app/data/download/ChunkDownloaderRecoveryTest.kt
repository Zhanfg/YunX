package com.yunx.app.data.download

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChunkDownloaderRecoveryTest {
    private val downloader = ChunkDownloader { OkHttpClient() }

    @Test
    fun parsesRetryAfterSeconds() {
        assertEquals(1_000L, downloader.parseRetryAfterMillis("1"))
        assertEquals(30_000L, downloader.parseRetryAfterMillis("120"))
    }

    @Test
    fun rejectsInvalidRetryAfterValues() {
        assertNull(downloader.parseRetryAfterMillis(null))
        assertNull(downloader.parseRetryAfterMillis("Wed, 21 Oct 2015 07:28:00 GMT"))
        assertNull(downloader.parseRetryAfterMillis("-1"))
    }
}
