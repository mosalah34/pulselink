package com.pulselink.app.data.model

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    data class Connecting(val deviceName: String, val address: String) : ConnectionState()
    data class Connected(val deviceName: String, val address: String) : ConnectionState()
    data class Error(val message: String, val cause: Throwable? = null) : ConnectionState()
    data class Reconnecting(val attempt: Int, val maxAttempts: Int) : ConnectionState()

    val isConnected: Boolean
        get() = this is Connected

    val statusText: String
        get() = when (this) {
            is Disconnected -> "Disconnected"
            is Connecting -> "Connecting to $deviceName..."
            is Connected -> "Connected ($deviceName)"
            is Error -> "Error: $message"
            is Reconnecting -> "Reconnecting ($attempt/$maxAttempts)..."
        }
}

