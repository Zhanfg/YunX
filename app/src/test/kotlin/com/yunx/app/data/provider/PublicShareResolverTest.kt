package com.yunx.app.data.provider

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicShareResolverTest {
    @Test
    fun dropboxForcesDownloadAndPreservesShareKey() = runBlocking {
        val result = PublicShareResolvers.resolve(
            "https://www.dropbox.com/scl/fi/token/file.zip?rlkey=secret&dl=0"
        ).getOrThrow()

        assertEquals("dropbox", result.providerId)
        assertTrue(result.downloadUrl.contains("rlkey=secret"))
        assertTrue(result.downloadUrl.contains("dl=1"))
        assertFalse(result.downloadUrl.contains("dl=0"))
    }

    @Test
    fun dropboxAddsDownloadParameterWhenMissing() = runBlocking {
        val result = PublicShareResolvers.resolve("https://www.dropbox.com/s/abc/example.txt").getOrThrow()
        assertTrue(result.downloadUrl.endsWith("?dl=1"))
    }

    @Test
    fun pCloudExtractsLongAndShortPublicCodesWithoutNetwork() {
        assertEquals("ABC_def-123", PublicShareResolvers.pCloudCode("https://e.pcloud.link/publink/show?code=ABC_def-123"))
        assertEquals("shortCode", PublicShareResolvers.pCloudCode("https://pc.cd/shortCode"))
        assertTrue(PublicShareResolvers.supports("pcloud"))
    }

    @Test
    fun googleDriveExtractsFileAndWorkspaceIds() {
        assertEquals("abc_DEF-123", PublicShareResolvers.googleDriveFileId("https://drive.google.com/file/d/abc_DEF-123/view?usp=sharing"))
        assertEquals("xyz_789", PublicShareResolvers.googleDriveFileId("https://drive.google.com/open?id=xyz_789"))
        assertEquals("doc_123", PublicShareResolvers.googleDriveFileId("https://docs.google.com/document/d/doc_123/edit"))
        assertTrue(PublicShareResolvers.supports("google_drive"))
    }

    @Test
    fun icloudExtractsOnlyDrivePublicId() {
        assertEquals("0afGK6zDBog_0drwp6YZoDLIg", PublicShareResolvers.iCloudShortId("https://www.icloud.com/iclouddrive/0afGK6zDBog_0drwp6YZoDLIg#Patches"))
        assertEquals(null, PublicShareResolvers.iCloudShortId("https://www.icloud.com/photos/abc"))
        assertTrue(PublicShareResolvers.supports("icloud_drive"))
    }

    @Test
    fun newPublicProvidersAreRegistered() {
        assertTrue(PublicShareResolvers.supports("onedrive"))
        assertTrue(PublicShareResolvers.supports("google_drive"))
        assertTrue(PublicShareResolvers.supports("icloud_drive"))
    }

    @Test
    fun recognizedButNotIntegratedProviderReturnsExplicitUnsupportedError() = runBlocking {
        val result = PublicShareResolvers.resolve("https://www.alipan.com/s/example")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is UnsupportedOperationException)
    }

    @Test
    fun lookalikeDropboxHostCannotUseResolver() = runBlocking {
        val result = PublicShareResolvers.resolve("https://dropbox.com.evil.example/s/abc")
        assertTrue(result.isFailure)
    }
}
