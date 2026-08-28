package com.yunx.app.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OAuthPkceTest {
    @Test
    fun generatedPairUsesPkceSafeCharacters() {
        val pair = OAuthPkce.generate()
        assertTrue(pair.verifier.length in 43..128)
        assertTrue(pair.verifier.matches(Regex("[A-Za-z0-9._~-]+")))
        assertTrue(pair.challenge.matches(Regex("[A-Za-z0-9_-]+")))
        assertFalse(pair.challenge.contains('='))
        assertEquals(43, pair.challenge.length)
    }

    @Test
    fun stateHasSufficientEntropyAndUrlSafeEncoding() {
        val first = OAuthPkce.randomState()
        val second = OAuthPkce.randomState()
        assertTrue(first.length >= 22)
        assertTrue(first.matches(Regex("[A-Za-z0-9_-]+")))
        assertFalse(first.contains('='))
        assertTrue(first != second)
    }

    @Test
    fun base64UrlEncodingDoesNotUsePadding() {
        assertEquals("Zm9v", OAuthPkce.base64UrlNoPadding("foo".toByteArray()))
        assertEquals("Zg", OAuthPkce.base64UrlNoPadding("f".toByteArray()))
    }
}
