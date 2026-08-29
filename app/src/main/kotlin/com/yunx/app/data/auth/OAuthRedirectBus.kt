package com.yunx.app.data.auth

import android.net.Uri
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/** In-process delivery for browser OAuth redirects handled by MainActivity. */
object OAuthRedirectBus {
    private val channel = Channel<Uri>(capacity = Channel.BUFFERED)
    val redirects = channel.receiveAsFlow()

    fun offer(uri: Uri?) {
        if (uri != null) channel.trySend(uri)
    }
}
