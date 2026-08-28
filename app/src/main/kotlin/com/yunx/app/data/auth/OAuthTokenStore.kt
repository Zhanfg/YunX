package com.yunx.app.data.auth

import android.content.Context
import com.yunx.app.data.security.AndroidKeystoreCredentialCipher
import com.yunx.app.data.security.CredentialCipher

/**
 * Small encrypted credential store for OAuth providers.
 *
 * Bearer and refresh tokens are encrypted with the existing Android-Keystore-backed
 * [CredentialCipher]. Non-secret expiry/scope metadata stays in private SharedPreferences.
 */
class OAuthTokenStore(
    context: Context,
    private val cipher: CredentialCipher = AndroidKeystoreCredentialCipher()
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(provider: OAuthProviderId, token: OAuthTokenSet) {
        val prefix = provider.id
        val editor = prefs.edit()
            .putString(key(prefix, "access"), cipher.encrypt(token.accessToken, purpose(prefix, "access")))
            .putString(key(prefix, "type"), token.tokenType)
            .putString(key(prefix, "scopes"), token.scopes.sorted().joinToString("\n"))

        token.refreshToken?.takeIf { it.isNotBlank() }?.let {
            editor.putString(key(prefix, "refresh"), cipher.encrypt(it, purpose(prefix, "refresh")))
        } ?: editor.remove(key(prefix, "refresh"))

        token.expiresAtEpochMillis?.let {
            editor.putLong(key(prefix, "expires"), it)
        } ?: editor.remove(key(prefix, "expires"))

        editor.apply()
    }

    fun load(provider: OAuthProviderId): OAuthTokenSet? {
        val prefix = provider.id
        val accessStored = prefs.getString(key(prefix, "access"), null) ?: return null
        val access = runCatching {
            cipher.decrypt(accessStored, purpose(prefix, "access"))
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null

        val refresh = prefs.getString(key(prefix, "refresh"), null)?.let { stored ->
            runCatching { cipher.decrypt(stored, purpose(prefix, "refresh")) }.getOrNull()
        }?.takeIf { it.isNotBlank() }
        val expires = prefs.getLong(key(prefix, "expires"), Long.MIN_VALUE)
            .takeUnless { it == Long.MIN_VALUE }
        val scopes = prefs.getString(key(prefix, "scopes"), "")
            .orEmpty()
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toSet()

        return OAuthTokenSet(
            accessToken = access,
            refreshToken = refresh,
            expiresAtEpochMillis = expires,
            scopes = scopes,
            tokenType = prefs.getString(key(prefix, "type"), "Bearer") ?: "Bearer"
        )
    }

    fun clear(provider: OAuthProviderId) {
        val prefix = provider.id
        prefs.edit()
            .remove(key(prefix, "access"))
            .remove(key(prefix, "refresh"))
            .remove(key(prefix, "expires"))
            .remove(key(prefix, "scopes"))
            .remove(key(prefix, "type"))
            .apply()
    }

    /** Temporary PKCE/state values are also encrypted because a verifier completes an auth flow. */
    fun savePending(provider: OAuthProviderId, verifier: String, state: String) {
        val prefix = provider.id
        prefs.edit()
            .putString(key(prefix, "pkce"), cipher.encrypt(verifier, purpose(prefix, "pkce")))
            .putString(key(prefix, "state"), cipher.encrypt(state, purpose(prefix, "state")))
            .apply()
    }

    fun loadPending(provider: OAuthProviderId): Pair<String, String>? {
        val prefix = provider.id
        val verifierStored = prefs.getString(key(prefix, "pkce"), null) ?: return null
        val stateStored = prefs.getString(key(prefix, "state"), null) ?: return null
        val verifier = runCatching { cipher.decrypt(verifierStored, purpose(prefix, "pkce")) }.getOrNull()
            ?: return null
        val state = runCatching { cipher.decrypt(stateStored, purpose(prefix, "state")) }.getOrNull()
            ?: return null
        return verifier to state
    }

    fun clearPending(provider: OAuthProviderId) {
        val prefix = provider.id
        prefs.edit()
            .remove(key(prefix, "pkce"))
            .remove(key(prefix, "state"))
            .apply()
    }

    private fun key(provider: String, field: String) = "$provider.$field"
    private fun purpose(provider: String, field: String) = "oauth.$provider.$field"

    private companion object {
        const val PREFS = "yunx_oauth_credentials"
    }
}
