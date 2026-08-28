package com.yunx.app.data.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.yunx.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Microsoft identity-platform Authorization Code + PKCE flow for OneDrive.
 *
 * Native/public clients do not use a client secret. The app registration client id and exact
 * Android redirect URI are build-time configuration, never user credentials.
 */
class OneDriveAuthorization(
    private val context: Context,
    private val client: OkHttpClient,
    private val tokenStore: OAuthTokenStore = OAuthTokenStore(context),
    private val clientId: String = BuildConfig.ONEDRIVE_CLIENT_ID,
    private val redirectUri: String = BuildConfig.ONEDRIVE_REDIRECT_URI
) {
    fun isConfigured(): Boolean = clientId.isNotBlank() && redirectUri.isNotBlank()

    fun authorizationIntent(): Result<Intent> = runCatching {
        require(isConfigured()) {
            "OneDrive OAuth 尚未配置：需要 YUNX_ONEDRIVE_CLIENT_ID 与 YUNX_ONEDRIVE_REDIRECT_URI"
        }
        val pkce = OAuthPkce.generate()
        val state = OAuthPkce.randomState()
        tokenStore.savePending(OAuthProviderId.ONEDRIVE, pkce.verifier, state)

        val uri = Uri.parse(AUTHORIZATION_ENDPOINT).buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("response_mode", "query")
            .appendQueryParameter("scope", SCOPES.joinToString(" "))
            .appendQueryParameter("code_challenge", pkce.challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("state", state)
            .build()

        Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
    }

    fun isRedirect(uri: Uri): Boolean {
        if (!isConfigured()) return false
        val expected = Uri.parse(redirectUri)
        return uri.scheme.equals(expected.scheme, ignoreCase = true) &&
            uri.host.equals(expected.host, ignoreCase = true) &&
            uri.path == expected.path
    }

    suspend fun completeRedirect(uri: Uri): Result<OAuthTokenSet> = withContext(Dispatchers.IO) {
        runCatching {
            require(isRedirect(uri)) { "不是 OneDrive OAuth 回调" }
            uri.getQueryParameter("error")?.let { error ->
                val description = uri.getQueryParameter("error_description")
                error("Microsoft 授权失败：${description ?: error}")
            }
            val code = uri.getQueryParameter("code")?.takeIf { it.isNotBlank() }
                ?: error("Microsoft 授权回调缺少 code")
            val returnedState = uri.getQueryParameter("state")?.takeIf { it.isNotBlank() }
                ?: error("Microsoft 授权回调缺少 state")
            val (verifier, expectedState) = tokenStore.loadPending(OAuthProviderId.ONEDRIVE)
                ?: error("OneDrive 授权会话已过期，请重新授权")
            require(constantTimeEquals(returnedState, expectedState)) { "OneDrive OAuth state 校验失败" }

            exchangeCode(code, verifier).also {
                tokenStore.save(OAuthProviderId.ONEDRIVE, it)
                tokenStore.clearPending(OAuthProviderId.ONEDRIVE)
            }
        }
    }

    fun loadToken(): OAuthTokenSet? = tokenStore.load(OAuthProviderId.ONEDRIVE)

    suspend fun validAccessToken(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val token = loadToken() ?: error("OneDrive 尚未授权")
            if (!token.isExpired()) return@runCatching token.accessToken
            val refresh = token.refreshToken?.takeIf { it.isNotBlank() }
                ?: error("OneDrive 授权已过期，请重新授权")
            refreshToken(refresh).accessToken
        }
    }

    fun clear() {
        tokenStore.clear(OAuthProviderId.ONEDRIVE)
        tokenStore.clearPending(OAuthProviderId.ONEDRIVE)
    }

    private fun exchangeCode(code: String, verifier: String): OAuthTokenSet {
        val form = FormBody.Builder()
            .add("client_id", clientId)
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", redirectUri)
            .add("code_verifier", verifier)
            .add("scope", SCOPES.joinToString(" "))
            .build()
        return tokenRequest(form)
    }

    private fun refreshToken(refreshToken: String): OAuthTokenSet {
        val form = FormBody.Builder()
            .add("client_id", clientId)
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .add("redirect_uri", redirectUri)
            .add("scope", SCOPES.joinToString(" "))
            .build()
        val refreshed = tokenRequest(form)
        val merged = if (refreshed.refreshToken.isNullOrBlank()) {
            refreshed.copy(refreshToken = refreshToken)
        } else refreshed
        tokenStore.save(OAuthProviderId.ONEDRIVE, merged)
        return merged
    }

    private fun tokenRequest(form: FormBody): OAuthTokenSet {
        val request = Request.Builder()
            .url(TOKEN_ENDPOINT)
            .post(form)
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching {
                    JSONObject(body).optString("error_description").takeIf(String::isNotBlank)
                }.getOrNull()
                error(message ?: "Microsoft token 交换失败 HTTP ${response.code}")
            }
            val json = JSONObject(body)
            val accessToken = json.optString("access_token").takeIf(String::isNotBlank)
                ?: error("Microsoft token 响应缺少 access_token")
            val expiresInSeconds = json.optLong("expires_in", 3600L).coerceAtLeast(60L)
            val scopes = json.optString("scope")
                .split(' ')
                .map(String::trim)
                .filter(String::isNotEmpty)
                .toSet()
            return OAuthTokenSet(
                accessToken = accessToken,
                refreshToken = json.optString("refresh_token").takeIf(String::isNotBlank),
                expiresAtEpochMillis = System.currentTimeMillis() + expiresInSeconds * 1000L,
                scopes = scopes,
                tokenType = json.optString("token_type", "Bearer")
            )
        }
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        val aa = a.toByteArray(Charsets.UTF_8)
        val bb = b.toByteArray(Charsets.UTF_8)
        return java.security.MessageDigest.isEqual(aa, bb)
    }

    companion object {
        private const val AUTHORIZATION_ENDPOINT =
            "https://login.microsoftonline.com/common/oauth2/v2.0/authorize"
        private const val TOKEN_ENDPOINT =
            "https://login.microsoftonline.com/common/oauth2/v2.0/token"
        val SCOPES = setOf("openid", "profile", "offline_access", "Files.Read")
    }
}
