package com.pulselink.app.bluetooth

import com.pulselink.app.data.model.ArduinoResetState
import com.pulselink.app.data.model.BuzzerHardwareState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ArduinoCommander(
    private val sendRawCommand: (String) -> Boolean,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    private val commandMutex = Mutex()

    private val _resetState = MutableStateFlow<ArduinoResetState>(ArduinoResetState.Idle)
    val resetState: StateFlow<ArduinoResetState> = _resetState.asStateFlow()

    private val _buzzerState = MutableStateFlow<BuzzerHardwareState>(BuzzerHardwareState.OFF)
    val buzzerState: StateFlow<BuzzerHardwareState> = _buzzerState.asStateFlow()

    private val _buzzerWarning = MutableStateFlow<String?>(null)
    val buzzerWarning: StateFlow<String?> = _buzzerWarning.asStateFlow()

    private val _lastAck = MutableStateFlow("")
    val lastAck: StateFlow<String> = _lastAck.asStateFlow()

    @Volatile
    private var lastSentCommand: String = ""

    private var resetTimeoutJob: Job? = null
    private var buzzerAckTimeoutJob: Job? = null

    // Pending commands queue for disconnected / retry states
    @Volatile
    private var pendingBuzzerOff: Boolean = false

    /**
     * Sends START command to Arduino.
     */
    fun sendStart(): Boolean {
        if (_resetState.value.isRestartInProgress) return false
        return transmitSerialized("START\n")
    }

    /**
     * Sends STOP command to Arduino and immediately silences buzzer.
     */
    fun sendStop(): Boolean {
        if (_resetState.value.isRestartInProgress) return false
        sendBuzzerOff()
        return transmitSerialized("STOP\n")
    }

    /**
     * Sends TEST command to Arduino.
     */
    fun sendTest(): Boolean {
        if (_resetState.value.isRestartInProgress) return false
        return transmitSerialized("TEST\n")
    }

    /**
     * Sends RESET command to trigger ATmega328P hardware watchdog restart.
     * Prevents duplicate execution while a restart is in progress.
     */
    fun sendReset(): Boolean {
        if (_resetState.value.isRestartInProgress) {
            return false // Debounce: Restart already in progress
        }

        resetTimeoutJob?.cancel()
        _resetState.value = ArduinoResetState.SendingReset

        // Send exactly RESET\n and no other command simultaneously
        val success = transmitSerialized("RESET\n")
        if (!success) {
            _resetState.value = ArduinoResetState.Failed("Could not transmit RESET command over Bluetooth")
            return false
        }

        _resetState.value = ArduinoResetState.WaitingResetAck

        // Start 12-second timeout waiting for ACK:RESETTING and SYSTEM_READY
        resetTimeoutJob = scope.launch {
            delay(12000L)
            if (isActive && _resetState.value.isRestartInProgress) {
                _resetState.value = ArduinoResetState.Failed("Reboot confirmation timed out. Did not receive SYSTEM_READY.")
            }
        }

        return true
    }

    /**
     * Sends BUZZER_ON and tracks acknowledgment.
     */
    fun sendBuzzerOn(): Boolean {
        if (_resetState.value.isRestartInProgress) return false

        pendingBuzzerOff = false
        _buzzerWarning.value = null
        _buzzerState.value = BuzzerHardwareState.PENDING_ON

        val success = transmitSerialized("BUZZER_ON\n")
        if (!success) {
            _buzzerState.value = BuzzerHardwareState.UNCONFIRMED_ON
            return false
        }

        // ACK timeout tracking for buzzer ON (3 seconds)
        buzzerAckTimeoutJob?.cancel()
        buzzerAckTimeoutJob = scope.launch {
            delay(3000L)
            if (isActive && _buzzerState.value == BuzzerHardwareState.PENDING_ON) {
                _buzzerState.value = BuzzerHardwareState.UNCONFIRMED_ON
            }
        }

        return true
    }

    /**
     * Sends BUZZER_OFF immediately and tracks acknowledgment.
     * If connection fails or ACK times out, marks as UNCONFIRMED_OFF and sets warning.
     */
    fun sendBuzzerOff(): Boolean {
        pendingBuzzerOff = true
        _buzzerState.value = BuzzerHardwareState.PENDING_OFF
        _buzzerWarning.value = null

        buzzerAckTimeoutJob?.cancel()

        val success = transmitSerialized("BUZZER_OFF\n")

        // Wait for ACK:BUZZER_OFF with a 3.5-second timeout
        buzzerAckTimeoutJob = scope.launch {
            delay(3500L)
            if (isActive && _buzzerState.value == BuzzerHardwareState.PENDING_OFF) {
                _buzzerState.value = BuzzerHardwareState.UNCONFIRMED_OFF
                _buzzerWarning.value = "Arduino buzzer silence unconfirmed by hardware (ACK:BUZZER_OFF timed out)"
            }
        }

        if (!success) {
            _buzzerState.value = BuzzerHardwareState.UNCONFIRMED_OFF
            _buzzerWarning.value = "Cannot reach Arduino to silence buzzer. Command queued for retry."
            return false
        }

        return true
    }

    fun stopPattern() {
        sendBuzzerOff()
    }

    /**
     * Called whenever Bluetooth connection is established or reconnected.
     * Retries any pending critical commands (such as queued BUZZER_OFF).
     */
    fun onConnectionRestored() {
        if (pendingBuzzerOff) {
            transmitSerialized("BUZZER_OFF\n")
        }
    }

    /**
     * Processes every incoming protocol line from the Arduino to match acknowledgments.
     */
    fun onIncomingLine(rawLine: String) {
        val line = rawLine.trim().uppercase()
        if (line.isEmpty()) return

        _lastAck.value = line

        when {
            line == "ACK:RESETTING" -> {
                resetTimeoutJob?.cancel()
                _resetState.value = ArduinoResetState.RestartingArduino
                // Extend timeout for SYSTEM_READY after reboot
                resetTimeoutJob = scope.launch {
                    delay(12000L)
                    if (isActive && _resetState.value == ArduinoResetState.RestartingArduino) {
                        _resetState.value = ArduinoResetState.Failed("Arduino restart timed out: SYSTEM_READY not received")
                    }
                }
            }

            line == "SYSTEM_READY" -> {
                resetTimeoutJob?.cancel()
                if (_resetState.value.isRestartInProgress) {
                    _resetState.value = ArduinoResetState.RestartedSuccess
                    // Auto-revert success state to idle after 4 seconds
                    scope.launch {
                        delay(4000L)
                        if (_resetState.value is ArduinoResetState.RestartedSuccess) {
                            _resetState.value = ArduinoResetState.Idle
                        }
                    }
                }
                // When Arduino boots, its buzzer is OFF by default
                _buzzerState.value = BuzzerHardwareState.OFF
                _buzzerWarning.value = null
                pendingBuzzerOff = false
            }

            line == "ACK:BUZZER_OFF" -> {
                buzzerAckTimeoutJob?.cancel()
                _buzzerState.value = BuzzerHardwareState.OFF
                _buzzerWarning.value = null
                pendingBuzzerOff = false
            }

            line == "ACK:BUZZER_ON" -> {
                buzzerAckTimeoutJob?.cancel()
                _buzzerState.value = BuzzerHardwareState.ON
                _buzzerWarning.value = null
            }

            line == "ACK:STOP_OK" -> {
                _buzzerState.value = BuzzerHardwareState.OFF
                pendingBuzzerOff = false
            }

            line.startsWith("ERR:") -> {
                // If an error was returned for buzzer command, mark as unconfirmed
                if (_buzzerState.value == BuzzerHardwareState.PENDING_OFF) {
                    _buzzerState.value = BuzzerHardwareState.UNCONFIRMED_OFF
                    _buzzerWarning.value = "Hardware error received: $rawLine"
                }
            }
        }
    }

    private fun transmitSerialized(cmd: String): Boolean {
        return try {
            val success = sendRawCommand(cmd)
            if (success) {
                lastSentCommand = cmd.trim()
            }
            success
        } catch (e: Exception) {
            false
        }
    }

    fun clearResetState() {
        resetTimeoutJob?.cancel()
        _resetState.value = ArduinoResetState.Idle
    }

    fun getLastCommand(): String = lastSentCommand

    fun isBuzzerActive(): Boolean = _buzzerState.value == BuzzerHardwareState.ON ||
            _buzzerState.value == BuzzerHardwareState.PENDING_ON ||
            _buzzerState.value == BuzzerHardwareState.UNCONFIRMED_ON
}
