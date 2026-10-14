package com.milestone.app.ui

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** A one-off snackbar message, optionally with an action such as "Undo". */
data class UiMessage(
    val text: String,
    val actionLabel: String? = null,
    val isError: Boolean = false,
    val onAction: suspend () -> Unit = {},
)

/**
 * App-wide snackbar channel. Screens emit messages here and the root scaffold shows them, so a
 * message survives navigation (e.g. "Session deleted · Undo" after leaving the detail screen).
 */
class MessageBus {
    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val messages: SharedFlow<UiMessage> = _messages

    fun show(message: UiMessage) {
        _messages.tryEmit(message)
    }
}
