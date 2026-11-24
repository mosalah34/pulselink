package com.pulselink.app.data.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class TerminalLogLevel {
    INFO,
    DATA,
    WARNING,
    ERROR,
    ALERT,
    ACTION
}

enum class LogDirection {
    RX,
    TX,
    SYS
}

data class TerminalLog(
    val id: Long = System.nanoTime(),
    val timestamp: Long = System.currentTimeMillis(),
    val direction: LogDirection,
    val level: TerminalLogLevel,
    val message: String
) {
    val formattedTime: String
        get() {
            val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
}

