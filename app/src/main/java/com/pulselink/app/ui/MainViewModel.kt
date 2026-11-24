package com.pulselink.app.ui

import android.bluetooth.BluetoothDevice
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulselink.app.PulseLinkApp
import com.pulselink.app.data.model.AlarmAckState
import com.pulselink.app.data.model.AlarmCondition
import com.pulselink.app.data.model.AlarmToneType
import com.pulselink.app.data.model.AlertEvent
import com.pulselink.app.data.model.ArduinoResetState
import com.pulselink.app.data.model.BuzzerHardwareState
import com.pulselink.app.data.model.ConnectionState
import com.pulselink.app.data.model.TerminalLog
import com.pulselink.app.data.model.ThresholdSettings
import com.pulselink.app.data.model.VitalReading
import com.pulselink.app.engine.DecisionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel : ViewModel() {

    private val app = PulseLinkApp.instance

    val currentReading: StateFlow<VitalReading?> = app.currentVitalReading
    val liveChartReadings: StateFlow<List<VitalReading>> = app.liveChartReadings
    val connectionState: StateFlow<ConnectionState> = app.bluetoothManager.connectionState
    val decisionResult: StateFlow<DecisionResult> = app.decisionEngine.decisionFlow
    val terminalLogs: StateFlow<List<TerminalLog>> = app.terminalLogs
    val settings: StateFlow<ThresholdSettings> = app.repository.settings
    val lastReceivedPacket: StateFlow<String> = app.bluetoothManager.lastReceivedPacket
    val lastTransmittedCommand: StateFlow<String> = app.bluetoothManager.lastTransmittedCommand
    val lastConnectionError: StateFlow<String?> = app.bluetoothManager.lastConnectionError

    // Reset & Hardware Buzzer States
    val resetState: StateFlow<ArduinoResetState> = app.commander.resetState
    val buzzerHardwareState: StateFlow<BuzzerHardwareState> = app.commander.buzzerState
    val buzzerWarning: StateFlow<String?> = app.commander.buzzerWarning
    val alarmCondition: StateFlow<AlarmCondition> = app.alarmStateMachine.condition
    val alarmAckState: StateFlow<AlarmAckState> = app.alarmStateMachine.ackState

    val recentReadings: StateFlow<List<VitalReading>> = app.repository.getRecentReadings(50)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val activeAlerts: StateFlow<List<AlertEvent>> = app.repository.getActiveAlerts()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val resolvedAlerts: StateFlow<List<AlertEvent>> = app.repository.getResolvedAlerts()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _pairedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val pairedDevices: StateFlow<List<BluetoothDevice>> = _pairedDevices.asStateFlow()

    private val _isCommandSending = MutableStateFlow(false)
    val isCommandSending: StateFlow<Boolean> = _isCommandSending.asStateFlow()

    private val _commandError = MutableStateFlow<String?>(null)
    val commandError: StateFlow<String?> = _commandError.asStateFlow()

    init {
        refreshPairedDevices()
    }

    fun refreshPairedDevices() {
        _pairedDevices.value = app.bluetoothManager.getPairedDevices()
    }

    fun connectDevice(device: BluetoothDevice) {
        _commandError.value = null
        app.bluetoothManager.connect(device, autoReconnect = settings.value.autoReconnect)
    }

    fun disconnectDevice() {
        app.bluetoothManager.disconnect()
        app.commander.stopPattern()
        app.alarmPlayer.stop()
    }

    fun clearCommandError() {
        _commandError.value = null
    }

    fun retryBuzzerOff() {
        viewModelScope.launch(Dispatchers.IO) {
            app.commander.sendBuzzerOff()
        }
    }

    fun sendStart() {
        executeBluetoothCommand("START") {
            app.commander.sendStart()
        }
    }

    fun sendStop() {
        executeBluetoothCommand("STOP") {
            app.alarmStateMachine.stop()
            app.commander.sendStop()
        }
    }

    fun sendTest() {
        executeBluetoothCommand("TEST") {
            app.commander.sendTest()
        }
    }

    fun sendReset() {
        // Prevent repeated RESET while reboot is already in progress
        if (resetState.value.isRestartInProgress) return

        if (!connectionState.value.isConnected) {
            _commandError.value = "Cannot restart: HC-05 is not connected."
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _isCommandSending.value = true
            _commandError.value = null
            try {
                // Send RESET command to trigger watchdog restart
                val success = app.commander.sendReset()
                if (success) {
                    app.decisionEngine.reset()
                    app.alarmStateMachine.reset()
                } else {
                    _commandError.value = "Failed to send RESET command."
                }
            } catch (e: Exception) {
                _commandError.value = "Reset error: ${e.localizedMessage ?: "Unknown error"}"
            } finally {
                _isCommandSending.value = false
            }
        }
    }

    private fun executeBluetoothCommand(cmdName: String, action: () -> Boolean) {
        if (_isCommandSending.value || resetState.value.isRestartInProgress) return

        if (!connectionState.value.isConnected) {
            _commandError.value = "Cannot send $cmdName: HC-05 is not connected."
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _isCommandSending.value = true
            _commandError.value = null
            try {
                val ok = action()
                if (!ok) {
                    _commandError.value = "Failed to transmit $cmdName over Bluetooth."
                }
                delay(200L)
            } catch (e: Exception) {
                _commandError.value = "Command error: ${e.localizedMessage ?: "Unknown error"}"
            } finally {
                _isCommandSending.value = false
            }
        }
    }

    fun sendCustomTerminalCommand(cmd: String) {
        if (cmd.isNotBlank()) {
            val formatted = if (cmd.endsWith("\n")) cmd else "$cmd\n"
            viewModelScope.launch(Dispatchers.IO) {
                app.bluetoothManager.sendCommand(formatted)
            }
        }
    }

    fun clearTerminal() {
        app.clearTerminal()
    }

    fun acknowledgeAlert(alertId: Long) {
        app.acknowledgeAlert(alertId)
    }

    fun updateSettings(newSettings: ThresholdSettings) {
        app.repository.updateSettings(newSettings)
    }

    fun testAlarmTone(tone: AlarmToneType) {
        app.alarmPlayer.testAlarmSound(tone)
    }

    fun clearHistory() {
        viewModelScope.launch {
            app.repository.clearHistory()
        }
    }

    fun exportCsv(onDone: (File?) -> Unit) {
        viewModelScope.launch {
            try {
                val file = app.repository.exportHistoryToCsvFile()
                onDone(file)
            } catch (e: Exception) {
                onDone(null)
            }
        }
    }
}
