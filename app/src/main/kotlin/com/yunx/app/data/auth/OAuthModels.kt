package com.yunx.app.data.auth

/**
 * OAuth token set used by providers that expose supported delegated APIs.
 *
 * Access/refresh tokens are credentials and must never be logged or shown in account UI.
 */
data class OAuthTokenSet(
    val accessToken: String,
    val refreshToken: String? = null,
    val expiresAtEpochMillis: Long? = null,
    val scopes: Set<String> = emptySet(),
    val tokenType: String = "Bearer"
) {
    fun isExpired(
        nowEpochMillis: Long = System.currentTimeMillis(),
        skewMillis: Long = 60_000L
    ): Boolean = expiresAtEpochMillis?.let { nowEpochMillis + skewMillis >= it } ?: false
}

enum class OAuthProviderId(val id: String) {
    GOOGLE_DRIVE("google_drive"),
    ONEDRIVE("onedrive")
}
