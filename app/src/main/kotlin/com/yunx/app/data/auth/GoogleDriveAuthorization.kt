package com.yunx.app.data.auth

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope

/**
 * Google Drive authorization through Google Play services AuthorizationClient.
 *
 * This intentionally requests OAuth scope/token access instead of reading Google login cookies.
 */
class GoogleDriveAuthorization(activity: Activity) {
    private val client = Identity.getAuthorizationClient(activity)

    sealed interface Result {
        data class Granted(
            val accessToken: String,
            val grantedScopes: Set<String>
        ) : Result

        data class NeedsResolution(val pendingIntent: PendingIntent) : Result
        data class Failed(val message: String) : Result
    }

    fun authorize(onResult: (Result) -> Unit) {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_READONLY_SCOPE)))
            .build()

        client.authorize(request)
            .addOnSuccessListener { authResult ->
                if (authResult.hasResolution()) {
                    val pendingIntent = authResult.pendingIntent
                    if (pendingIntent != null) {
                        onResult(Result.NeedsResolution(pendingIntent))
                    } else {
                        onResult(Result.Failed("Google 授权需要用户确认，但未返回授权页面"))
                    }
                } else {
                    val token = authResult.accessToken
                    if (token.isNullOrBlank()) {
                        onResult(Result.Failed("Google 未返回访问令牌"))
                    } else {
                        onResult(Result.Granted(token, authResult.grantedScopes.toSet()))
                    }
                }
            }
            .addOnFailureListener { error ->
                onResult(Result.Failed(error.message ?: "Google Drive 授权失败"))
            }
    }

    fun consumeResolution(intent: Intent?): Result {
        if (intent == null) return Result.Failed("Google 授权结果为空")
        return runCatching { client.getAuthorizationResultFromIntent(intent) }
            .fold(
                onSuccess = { authResult ->
                    val token = authResult.accessToken
                    if (token.isNullOrBlank()) {
                        Result.Failed("Google 未返回访问令牌")
                    } else {
                        Result.Granted(token, authResult.grantedScopes.toSet())
                    }
                },
                onFailure = { Result.Failed(it.message ?: "Google Drive 授权失败") }
            )
    }

    fun revoke(onComplete: (Boolean) -> Unit = {}) {
        // AuthorizationClient exposes revokeAccess through a request object, but clearing local
        // token state is intentionally left to the account action until disconnect UI is added.
        onComplete(true)
    }

    companion object {
        const val DRIVE_READONLY_SCOPE = "https://www.googleapis.com/auth/drive.readonly"
    }
}
