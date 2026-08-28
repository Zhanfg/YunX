package com.yunx.app.data.auth

import android.net.Uri
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** In-process delivery for browser OAuth redirects handled by MainActivity. */
object OAuthRedirectBus {
    private val _redirects = MutableSharedFlow<Uri>(extraBufferCapacity = 2)
    val redirects = _redirects.asSharedFlow()

    fun offer(uri: Uri?) {
        if (uri != null) _redirects.tryEmit(uri)
    }
}
