package com.pulselink.app

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.pulselink.app.bluetooth.ArduinoCommander
import com.pulselink.app.bluetooth.BluetoothManager
import com.pulselink.app.bluetooth.SerialProtocolParser
import com.pulselink.app.data.local.PulseLinkDatabase
import com.pulselink.app.data.model.ConnectionState
import com.pulselink.app.data.model.LogDirection
import com.pulselink.app.data.model.TerminalLog
import com.pulselink.app.data.model.TerminalLogLevel
import com.pulselink.app.data.model.VitalReading
import com.pulselink.app.data.repository.PulseLinkRepository
import com.pulselink.app.engine.AlarmSoundPlayer
import com.pulselink.app.engine.AlarmStateMachine
import com.pulselink.app.engine.NotificationHelper
import com.pulselink.app.engine.PhoneDecisionEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue

class PulseLinkApp : Application() {

    lateinit var database: PulseLinkDatabase private set
    lateinit var repository: PulseLinkRepository private set
    lateinit var bluetoothManager: BluetoothManager private set
    lateinit var serialParser: SerialProtocolParser private set
    lateinit var commander: ArduinoCommander private set
    lateinit var decisionEngine: PhoneDecisionEngine private set
    lateinit var alarmStateMachine: AlarmStateMachine private set
    lateinit var alarmPlayer: AlarmSoundPlayer private set
    lateinit var notificationHelper: NotificationHelper private set

    private val applicationScope = CoroutineScope(Dispatchers.Default)

    // In-memory bounded terminal logs (max 500)
    private val _terminalLogs = MutableStateFlow<List<TerminalLog>>(emptyList())
    val terminalLogs: StateFlow<List<TerminalLog>> = _terminalLogs.asStateFlow()

    // Current latest vital reading
    private val _currentVitalReading = MutableStateFlow<VitalReading?>(null)
    val currentVitalReading: StateFlow<VitalReading?> = _currentVitalReading.asStateFlow()

    // Bounded chronological readings for live chart (max 60 points, oldest first, newest last)
    private val _liveChartReadings = MutableStateFlow<List<VitalReading>>(emptyList())
    val liveChartReadings: StateFlow<List<VitalReading>> = _liveChartReadings.asStateFlow()

    // High-volume batch buffers
    private val readingBatchQueue = ConcurrentLinkedQueue<VitalReading>()
    private val logBatchChannel = Channel<TerminalLog>(capacity = 1000)

    private var staleCheckJob: Job? = null
    private var dbFlushJob: Job? = null
    private var logFlushJob: Job? = null

    // UI emission throttle timestamps
    private var lastUiEmissionTime = 0L

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = PulseLinkDatabase.getDatabase(this)
        repository = PulseLinkRepository(
            context = this,
            vitalReadingDao = database.vitalReadingDao(),
            alertEventDao = database.alertEventDao()
        )

        bluetoothManager = BluetoothManager(this)
        serialParser = SerialProtocolParser()
        commander = ArduinoCommander(
            sendRawCommand = { cmd ->
                val success = bluetoothManager.sendCommand(cmd)
                if (success) {
                    appendLog(
                        direction = LogDirection.TX,
                        level = TerminalLogLevel.ACTION,
                        message = cmd.trim()
                    )
                }
                success
            },
            scope = applicationScope
        )

        alarmPlayer = AlarmSoundPlayer(this)
        notificationHelper = NotificationHelper(this)
        decisionEngine = PhoneDecisionEngine(repository.settings.value)

        // Initialize Central Alarm State Machine
        alarmStateMachine = AlarmStateMachine(
            settings = repository.settings.value,
            onStartPhoneAlarm = { alertType ->
                val s = repository.settings.value
                alarmPlayer.startAlert(
                    alertType = alertType,
                    soundEnabled = s.alarmSoundEnabled,
                    vibrationEnabled = s.vibrationEnabled,
                    toneType = s.alarmTone
                )
            },
            onStopPhoneAlarm = {
                alarmPlayer.stop()
            },
            onSendBuzzerOn = {
                commander.sendBuzzerOn()
            },
            onSendBuzzerOff = {
                commander.sendBuzzerOff()
            },
            onAlertCreated = { alertEvent ->
                applicationScope.launch(Dispatchers.IO) {
                    try {
                        repository.saveAlert(alertEvent)
                    } catch (e: Exception) { /* ignore */ }
                }
                notificationHelper.showAlertNotification(alertEvent)
                appendLog(
                    direction = LogDirection.SYS,
                    level = TerminalLogLevel.ALERT,
                    message = "ALERT TRIGGERED: ${alertEvent.alertType.title} (${alertEvent.measurementValue})"
                )
            },
            onAlertAcknowledged = { alertId ->
                applicationScope.launch(Dispatchers.IO) {
                    try {
                        repository.acknowledgeAlert(alertId)
                    } catch (e: Exception) { /* ignore */ }
                }
                notificationHelper.dismissAlertNotification()
                appendLog(
                    direction = LogDirection.SYS,
                    level = TerminalLogLevel.INFO,
                    message = "Alert acknowledged. Silenced phone sound and Arduino buzzer."
                )
            },
            onAlertResolved = { alertType ->
                applicationScope.launch(Dispatchers.IO) {
                    try {
                        repository.resolveActiveAlerts(alertType)
                    } catch (e: Exception) { /* ignore */ }
                }
                notificationHelper.dismissAlertNotification()
                appendLog(
                    direction = LogDirection.SYS,
                    level = TerminalLogLevel.INFO,
                    message = "Normal vitals restored for ${alertType.title}. Clearance sent."
                )
            }
        )

        registerAcknowledgeReceiver()
        observeSettings()
        startDbBatchFlusher()
        startLogBatchFlusher()
        observeIncomingBluetoothData()
        observeConnectionChanges()
        startStaleDataMonitor()

        appendLog(
            direction = LogDirection.SYS,
            level = TerminalLogLevel.INFO,
            message = "PulseLink initialized. Ready for HC-05 connection."
        )
    }

    private fun observeSettings() {
        applicationScope.launch {
            repository.settings.collectLatest { s ->
                decisionEngine.settings = s
                alarmStateMachine.settings = s
            }
        }
    }

    /**
     * Periodic flusher for database writes.
     * Collects queued readings and writes them to SQLite in a single transaction
     * every 1000ms.
     */
    private fun startDbBatchFlusher() {
        dbFlushJob?.cancel()
        dbFlushJob = applicationScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(1000L)
                flushReadingBatch()
            }
        }
    }

    private suspend fun flushReadingBatch() {
        if (readingBatchQueue.isEmpty()) return
        val batch = mutableListOf<VitalReading>()
        while (batch.size < 50) {
            val item = readingBatchQueue.poll() ?: break
            batch.add(item)
        }
        if (batch.isNotEmpty()) {
            try {
                repository.saveReadingsBatch(batch)
            } catch (e: Exception) {
                // Ignore transient DB write exceptions to preserve stability
            }
        }
    }

    /**
     * Batched terminal log updater to prevent UI freezes and memory spikes.
     */
    private fun startLogBatchFlusher() {
        logFlushJob?.cancel()
        logFlushJob = applicationScope.launch(Dispatchers.Default) {
            val pendingLogs = mutableListOf<TerminalLog>()
            while (isActive) {
                var log = logBatchChannel.tryReceive().getOrNull()
                while (log != null) {
                    pendingLogs.add(log)
                    if (pendingLogs.size >= 50) break
                    log = logBatchChannel.tryReceive().getOrNull()
                }

                if (pendingLogs.isNotEmpty()) {
                    val currentList = _terminalLogs.value.toMutableList()
                    currentList.addAll(pendingLogs)
                    if (currentList.size > 500) {
                        val overflow = currentList.size - 500
                        currentList.subList(0, overflow).clear()
                    }
                    _terminalLogs.value = currentList
                    pendingLogs.clear()
                }
                delay(150L)
            }
        }
    }

    /**
     * Background collector for incoming HC-05 serial chunks.
     * Parses protocol packets, updates commander acknowledgments,
     * routes vitals through alarm state machine, and updates live telemetry.
     */
    private fun observeIncomingBluetoothData() {
        applicationScope.launch(Dispatchers.Default) {
            val chartBuffer = mutableListOf<VitalReading>()

            bluetoothManager.incomingDataFlow.collect { chunk ->
                val lines = serialParser.processIncomingChunk(chunk)
                val now = System.currentTimeMillis()

                for (line in lines) {
                    // Queue log without blocking
                    logBatchChannel.trySend(
                        TerminalLog(
                            direction = LogDirection.RX,
                            level = TerminalLogLevel.DATA,
                            message = line
                        )
                    )

                    // Dispatch to ArduinoCommander to match ACKs (ACK:RESETTING, SYSTEM_READY, ACK:BUZZER_OFF, etc.)
                    commander.onIncomingLine(line)

                    val upper = line.trim().uppercase()
                    if (upper == "SYSTEM_READY") {
                        decisionEngine.reset()
                        alarmStateMachine.reset()
                        appendLog(
                            direction = LogDirection.SYS,
                            level = TerminalLogLevel.INFO,
                            message = "Arduino reboot confirmed via SYSTEM_READY."
                        )
                    }

                    val reading = serialParser.parseLine(line)
                    if (reading != null) {
                        // Queue reading for batch DB insertion
                        readingBatchQueue.offer(reading)

                        // Update in-memory chart buffer (max 60 points)
                        chartBuffer.add(reading)
                        if (chartBuffer.size > 60) {
                            chartBuffer.removeAt(0)
                        }

                        // Central alarm state machine evaluates condition and manages buzzer & sound
                        alarmStateMachine.processReading(reading, now)
                        decisionEngine.evaluateReading(reading, now)

                        // Throttle UI StateFlow updates to ~4-5 Hz (every 200ms)
                        if (now - lastUiEmissionTime >= 200L) {
                            lastUiEmissionTime = now
                            _currentVitalReading.value = reading
                            _liveChartReadings.value = ArrayList(chartBuffer)
                        }
                    }
                }
            }
        }
    }

    private fun observeConnectionChanges() {
        applicationScope.launch {
            bluetoothManager.connectionState.collectLatest { state ->
                when (state) {
                    is ConnectionState.Connected -> {
                        commander.onConnectionRestored()
                        appendLog(
                            direction = LogDirection.SYS,
                            level = TerminalLogLevel.INFO,
                            message = "Connected to ${state.deviceName} (${state.address})"
                        )
                    }
                    is ConnectionState.Disconnected -> {
                        if (commander.resetState.value.isRestartInProgress) {
                            appendLog(
                                direction = LogDirection.SYS,
                                level = TerminalLogLevel.INFO,
                                message = "Bluetooth link reset during Arduino watchdog reboot."
                            )
                        } else {
                            appendLog(
                                direction = LogDirection.SYS,
                                level = TerminalLogLevel.WARNING,
                                message = "Bluetooth Disconnected."
                            )
                        }
                        alarmPlayer.stop()
                        commander.sendBuzzerOff()
                    }
                    is ConnectionState.Error -> {
                        appendLog(
                            direction = LogDirection.SYS,
                            level = TerminalLogLevel.ERROR,
                            message = "Connection Error: ${state.message}"
                        )
                    }
                    is ConnectionState.Reconnecting -> {
                        appendLog(
                            direction = LogDirection.SYS,
                            level = TerminalLogLevel.WARNING,
                            message = "Reconnecting attempt ${state.attempt}/${state.maxAttempts}..."
                        )
                    }
                    is ConnectionState.Connecting -> {
                        appendLog(
                            direction = LogDirection.SYS,
                            level = TerminalLogLevel.INFO,
                            message = "Initiating RFCOMM connection to ${state.deviceName}..."
                        )
                    }
                }
            }
        }
    }

    private fun startStaleDataMonitor() {
        staleCheckJob?.cancel()
        staleCheckJob = applicationScope.launch {
            while (isActive) {
                delay(1000L)
                val current = _currentVitalReading.value
                if (current != null && !current.isStale) {
                    val decision = decisionEngine.checkStaleness()
                    if (decision.isStale) {
                        _currentVitalReading.value = current.copy(isStale = true)
                        appendLog(
                            direction = LogDirection.SYS,
                            level = TerminalLogLevel.WARNING,
                            message = "Sensor data is stale (> ${repository.settings.value.staleTimeoutMillis} ms)"
                        )
                    }
                }
            }
        }
    }

    fun appendLog(direction: LogDirection, level: TerminalLogLevel, message: String) {
        val entry = TerminalLog(
            direction = direction,
            level = level,
            message = message
        )
        logBatchChannel.trySend(entry)
    }

    fun clearTerminal() {
        _terminalLogs.value = emptyList()
    }

    fun acknowledgeAlert(alertId: Long) {
        alarmStateMachine.acknowledgeAlert(alertId)
        decisionEngine.acknowledgeAlert(alertId)
    }

    private fun registerAcknowledgeReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == NotificationHelper.ACTION_ACKNOWLEDGE_ALERT) {
                    val alertId = intent.getLongExtra(NotificationHelper.EXTRA_ALERT_ID, -1L)
                    if (alertId != -1L) {
                        acknowledgeAlert(alertId)
                    }
                }
            }
        }
        val filter = IntentFilter(NotificationHelper.ACTION_ACKNOWLEDGE_ALERT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }

    companion object {
        lateinit var instance: PulseLinkApp private set
    }
}
