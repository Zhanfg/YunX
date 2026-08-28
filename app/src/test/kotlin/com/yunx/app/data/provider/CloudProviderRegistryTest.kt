package com.yunx.app.data.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudProviderRegistryTest {
    @Test
    fun detectsCurrentIntegratedProviders() {
        assertEquals("quark", CloudProviderRegistry.detect("https://pan.quark.cn/s/abc")?.id)
        assertEquals("uc", CloudProviderRegistry.detect("https://drive.uc.cn/s/abc")?.id)
        assertEquals("xunlei", CloudProviderRegistry.detect("https://pan.xunlei.com/s/abc")?.id)
        assertEquals("baidu", CloudProviderRegistry.detect("https://pan.baidu.com/s/1abc")?.id)
        assertEquals("pan123", CloudProviderRegistry.detect("https://www.123pan.com/s/abc-def")?.id)
    }

    @Test
    fun detectsV3DomesticExpansionProviders() {
        assertEquals("aliyun", CloudProviderRegistry.detect("https://www.alipan.com/s/example")?.id)
        assertEquals("tianyi", CloudProviderRegistry.detect("https://cloud.189.cn/t/example")?.id)
        assertEquals("lanzou", CloudProviderRegistry.detect("https://example.lanzoui.com/iabc")?.id)
        assertEquals("115", CloudProviderRegistry.detect("https://share.115.com/example")?.id)
        assertEquals("pikpak", CloudProviderRegistry.detect("https://mypikpak.com/s/example")?.id)
        assertEquals("ctfile", CloudProviderRegistry.detect("https://www.ctfile.com/f/example")?.id)
    }

    @Test
    fun detectsV4GlobalExpansionProviders() {
        assertEquals("dropbox", CloudProviderRegistry.detect("https://www.dropbox.com/scl/fi/a/file.zip")?.id)
        assertEquals("google_drive", CloudProviderRegistry.detect("https://drive.google.com/file/d/abc/view")?.id)
        assertEquals("onedrive", CloudProviderRegistry.detect("https://1drv.ms/u/s!abc")?.id)
        assertEquals("onedrive", CloudProviderRegistry.detect("https://tenant.sharepoint.com/:f:/g/personal/example")?.id)
        assertEquals("icloud", CloudProviderRegistry.detect("https://www.icloud.com/iclouddrive/0123456789")?.id)
        assertEquals("mega", CloudProviderRegistry.detect("https://mega.nz/file/abc#key")?.id)
        assertEquals("box", CloudProviderRegistry.detect("https://app.box.com/s/abc")?.id)
        assertEquals("pcloud", CloudProviderRegistry.detect("https://e.pcloud.link/publink/show?code=abc")?.id)
        assertEquals("pcloud", CloudProviderRegistry.detect("https://pc.cd/abc")?.id)
        assertEquals("mediafire", CloudProviderRegistry.detect("https://www.mediafire.com/file/abc/file.zip/file")?.id)
    }

    @Test
    fun rejectsLookalikeNonHttpAndNonDriveICloudUrls() {
        assertNull(CloudProviderRegistry.detect("https://dropbox.com.evil.example/s/abc"))
        assertNull(CloudProviderRegistry.detect("https://pan.baidu.com.evil.example/s/abc"))
        assertNull(CloudProviderRegistry.detect("https://sharepoint.com.evil.example/x"))
        assertNull(CloudProviderRegistry.detect("https://www.icloud.com/mail"))
        assertNull(CloudProviderRegistry.detect("https://www.icloud.com/photos"))
        assertNull(CloudProviderRegistry.detect("javascript:https://www.dropbox.com/s/abc"))
    }

    @Test
    fun readinessDistinguishesAccountApiPublicDownloadAndDetection() {
        val google = CloudProviderRegistry.byId("google_drive")!!
        val oneDrive = CloudProviderRegistry.byId("onedrive")!!
        val iCloud = CloudProviderRegistry.byId("icloud")!!
        val dropbox = CloudProviderRegistry.byId("dropbox")!!
        val pcloud = CloudProviderRegistry.byId("pcloud")!!
        val aliyun = CloudProviderRegistry.byId("aliyun")!!

        assertEquals(ProviderReadiness.ACCOUNT_API, google.readiness)
        assertEquals(ProviderReadiness.ACCOUNT_API, oneDrive.readiness)
        assertTrue(google.capabilities.directDownload)
        assertTrue(oneDrive.capabilities.directDownload)
        assertEquals(ProviderReadiness.DETECTED, iCloud.readiness)
        assertEquals(ProviderReadiness.PUBLIC_DOWNLOAD, dropbox.readiness)
        assertEquals(ProviderReadiness.PUBLIC_DOWNLOAD, pcloud.readiness)
        assertTrue(pcloud.capabilities.alternativeEndpoints)
        assertEquals(ProviderReadiness.DETECTED, aliyun.readiness)
    }
}
