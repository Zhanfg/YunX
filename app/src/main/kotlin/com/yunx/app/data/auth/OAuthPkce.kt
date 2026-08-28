package com.yunx.app.data.auth

import java.security.MessageDigest
import java.security.SecureRandom

/** OAuth 2.0 Authorization Code + PKCE helpers for native/public clients. */
object OAuthPkce {
    private const val VERIFIER_LENGTH = 64
    private const val VERIFIER_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
    private val secureRandom = SecureRandom()

    data class Pair(
        val verifier: String,
        val challenge: String
    )

    fun generate(): Pair {
        val verifier = buildString(VERIFIER_LENGTH) {
            repeat(VERIFIER_LENGTH) {
                append(VERIFIER_ALPHABET[secureRandom.nextInt(VERIFIER_ALPHABET.length)])
            }
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(Charsets.US_ASCII))
        return Pair(verifier = verifier, challenge = base64UrlNoPadding(digest))
    }

    fun randomState(bytes: Int = 24): String {
        require(bytes >= 16) { "OAuth state must contain at least 128 bits of entropy" }
        return ByteArray(bytes).also(secureRandom::nextBytes).let(::base64UrlNoPadding)
    }

    internal fun base64UrlNoPadding(input: ByteArray): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        val out = StringBuilder((input.size * 4 + 2) / 3)
        var index = 0
        while (index + 2 < input.size) {
            val value = ((input[index].toInt() and 0xff) shl 16) or
                ((input[index + 1].toInt() and 0xff) shl 8) or
                (input[index + 2].toInt() and 0xff)
            out.append(alphabet[(value ushr 18) and 63])
            out.append(alphabet[(value ushr 12) and 63])
            out.append(alphabet[(value ushr 6) and 63])
            out.append(alphabet[value and 63])
            index += 3
        }
        val remaining = input.size - index
        if (remaining == 1) {
            val value = (input[index].toInt() and 0xff) shl 16
            out.append(alphabet[(value ushr 18) and 63])
            out.append(alphabet[(value ushr 12) and 63])
        } else if (remaining == 2) {
            val value = ((input[index].toInt() and 0xff) shl 16) or
                ((input[index + 1].toInt() and 0xff) shl 8)
            out.append(alphabet[(value ushr 18) and 63])
            out.append(alphabet[(value ushr 12) and 63])
            out.append(alphabet[(value ushr 6) and 63])
        }
        return out.toString()
    }
}
