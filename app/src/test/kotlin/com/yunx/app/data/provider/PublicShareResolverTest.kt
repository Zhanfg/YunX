package com.yunx.app.data.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicShareResolverTest {
    @Test
    fun dropboxForcesDownloadAndPreservesShareKey() {
        val result = PublicShareResolvers.resolve(
            "https://www.dropbox.com/scl/fi/token/file.zip?rlkey=secret&dl=0"
        ).getOrThrow()

        assertEquals("dropbox", result.providerId)
        assertTrue(result.downloadUrl.contains("rlkey=secret"))
        assertTrue(result.downloadUrl.contains("dl=1"))
        assertFalse(result.downloadUrl.contains("dl=0"))
    }

    @Test
    fun dropboxAddsDownloadParameterWhenMissing() {
        val result = PublicShareResolvers.resolve(
            "https://www.dropbox.com/s/abc/example.txt"
        ).getOrThrow()
        assertTrue(result.downloadUrl.endsWith("?dl=1"))
    }

    @Test
    fun googleDriveExtractsCommonFileIdFormsWithoutClaimingDirectLink() {
        assertEquals(
            "abc_DEF-123",
            PublicShareResolvers.googleDriveFileId("https://drive.google.com/file/d/abc_DEF-123/view?usp=sharing")
        )
        assertEquals(
            "xyz_789",
            PublicShareResolvers.googleDriveFileId("https://drive.google.com/open?id=xyz_789")
        )
        assertFalse(PublicShareResolvers.supports("google_drive"))
    }

    @Test
    fun recognizedButNotIntegratedProviderReturnsExplicitUnsupportedError() {
        val result = PublicShareResolvers.resolve("https://www.alipan.com/s/example")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is UnsupportedOperationException)
    }

    @Test
    fun lookalikeDropboxHostCannotUseResolver() {
        val result = PublicShareResolvers.resolve("https://dropbox.com.evil.example/s/abc")
        assertTrue(result.isFailure)
    }
}
