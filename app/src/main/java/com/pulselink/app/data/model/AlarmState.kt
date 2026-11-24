package com.pulselink.app.data.model

enum class AlarmCondition {
    NORMAL,
    HR_LOW,
    HR_HIGH,
    TEMP_HIGH;

    val isAlert: Boolean
        get() = this != NORMAL

    val alertType: AlertType?
        get() = when (this) {
            HR_LOW -> AlertType.LOW_HEART_RATE
            HR_HIGH -> AlertType.HIGH_HEART_RATE
            TEMP_HIGH -> AlertType.HIGH_TEMPERATURE
            NORMAL -> null
        }
}

enum class AlarmAckState {
    NOT_ACKNOWLEDGED,
    ACKNOWLEDGED
}

enum class PhoneSoundState {
    STOPPED,
    PLAYING
}

enum class BuzzerHardwareState(val label: String) {
    OFF("Silenced (Confirmed)"),
    ON("Active (Confirmed)"),
    PENDING_ON("Activating..."),
    PENDING_OFF("Silencing..."),
    UNCONFIRMED_OFF("Silenced (Unconfirmed by Arduino)"),
    UNCONFIRMED_ON("Active (Unconfirmed by Arduino)")
}

sealed class ArduinoResetState {
    object Idle : ArduinoResetState()
    object SendingReset : ArduinoResetState()
    object WaitingResetAck : ArduinoResetState()
    object RestartingArduino : ArduinoResetState()
    object RestartedSuccess : ArduinoResetState()
    data class Failed(val reason: String) : ArduinoResetState()

    val isRestartInProgress: Boolean
        get() = this is SendingReset || this is WaitingResetAck || this is RestartingArduino

    val statusText: String
        get() = when (this) {
            is Idle -> ""
            is SendingReset -> "Sending RESET command..."
            is WaitingResetAck -> "Waiting for reboot acknowledgment..."
            is RestartingArduino -> "Restarting Arduino..."
            is RestartedSuccess -> "Arduino restarted successfully"
            is Failed -> "Restart failed: $reason"
        }
}
