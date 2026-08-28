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
    fun detectsGlobalPublicProviders() {
        assertEquals("dropbox", CloudProviderRegistry.detect("https://www.dropbox.com/scl/fi/a/file.zip")?.id)
        assertEquals("google_drive", CloudProviderRegistry.detect("https://drive.google.com/file/d/abc/view")?.id)
        assertEquals("onedrive", CloudProviderRegistry.detect("https://1drv.ms/u/s!abc")?.id)
        assertEquals("onedrive", CloudProviderRegistry.detect("https://tenant.sharepoint.com/:u:/g/example")?.id)
        assertEquals("icloud_drive", CloudProviderRegistry.detect("https://www.icloud.com/iclouddrive/abc#file")?.id)
        assertEquals("pcloud", CloudProviderRegistry.detect("https://e.pcloud.link/publink/show?code=abc")?.id)
        assertEquals("pcloud", CloudProviderRegistry.detect("https://pc.cd/abc")?.id)
    }

    @Test
    fun detectsOtherGlobalProvidersWithoutOverclaiming() {
        assertEquals("mega", CloudProviderRegistry.detect("https://mega.nz/file/abc#key")?.id)
        assertEquals("box", CloudProviderRegistry.detect("https://app.box.com/s/abc")?.id)
        assertEquals("mediafire", CloudProviderRegistry.detect("https://www.mediafire.com/file/abc/file.zip/file")?.id)
    }

    @Test
    fun rejectsLookalikeNonHttpAndNonDriveIcloudHosts() {
        assertNull(CloudProviderRegistry.detect("https://dropbox.com.evil.example/s/abc"))
        assertNull(CloudProviderRegistry.detect("https://pan.baidu.com.evil.example/s/abc"))
        assertNull(CloudProviderRegistry.detect("https://sharepoint.com.evil.example/file"))
        assertNull(CloudProviderRegistry.detect("https://www.icloud.com/photos/abc"))
        assertNull(CloudProviderRegistry.detect("javascript:https://www.dropbox.com/s/abc"))
    }

    @Test
    fun readinessMatchesImplementedPublicResolvers() {
        for (id in listOf("dropbox", "pcloud", "google_drive", "onedrive", "icloud_drive")) {
            val provider = CloudProviderRegistry.byId(id)!!
            assertEquals(ProviderReadiness.PUBLIC_DOWNLOAD, provider.readiness)
            assertTrue(provider.capabilities.directDownload)
            assertTrue(PublicShareResolvers.supports(id))
        }
        assertEquals(ProviderReadiness.DETECTED, CloudProviderRegistry.byId("aliyun")!!.readiness)
    }
}
