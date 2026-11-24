package com.pulselink.app.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager as AndroidBtManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Build
import com.pulselink.app.data.model.ConnectionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class BluetoothManager(private val context: Context) {

    companion object {
        // Standard SPP UUID for HC-05 Bluetooth Classic module
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val READ_BUFFER_SIZE = 1024
        private const val MAX_RECONNECT_ATTEMPTS = 5
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val sysBt = context.getSystemService(Context.BLUETOOTH_SERVICE) as? AndroidBtManager
        sysBt?.adapter ?: BluetoothAdapter.getDefaultAdapter()
    }

    private var activeSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    private var connectJob: Job? = null
    private var readerJob: Job? = null
    private var reconnectJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _incomingDataFlow = MutableSharedFlow<String>(
        extraBufferCapacity = 256,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val incomingDataFlow: SharedFlow<String> = _incomingDataFlow.asSharedFlow()

    private val _lastReceivedPacket = MutableStateFlow("")
    val lastReceivedPacket: StateFlow<String> = _lastReceivedPacket.asStateFlow()

    private val _lastTransmittedCommand = MutableStateFlow("")
    val lastTransmittedCommand: StateFlow<String> = _lastTransmittedCommand.asStateFlow()

    private val _lastConnectionError = MutableStateFlow<String?>(null)
    val lastConnectionError: StateFlow<String?> = _lastConnectionError.asStateFlow()

    private var lastTargetDevice: BluetoothDevice? = null
    private var shouldAutoReconnect = true

    fun isBluetoothSupported(): Boolean = bluetoothAdapter != null

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDevice> {
        val adapter = bluetoothAdapter ?: return emptyList()
        return try {
            adapter.bondedDevices?.toList() ?: emptyList()
        } catch (e: SecurityException) {
            _lastConnectionError.value = "Bluetooth permission not granted"
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice, autoReconnect: Boolean = true) {
        lastTargetDevice = device
        shouldAutoReconnect = autoReconnect

        reconnectJob?.cancel()
        connectJob?.cancel()

        connectJob = scope.launch {
            val deviceName = try { device.name ?: "Unknown HC-05" } catch (e: Exception) { "HC-05" }
            val address = device.address ?: "00:00:00:00:00:00"

            _connectionState.value = ConnectionState.Connecting(deviceName, address)
            _lastConnectionError.value = null

            // Cancel device discovery because it slows down the connection
            try {
                bluetoothAdapter?.cancelDiscovery()
            } catch (e: SecurityException) {
                // Ignore if discovery permission isn't available
            }

            try {
                // Create RFCOMM socket using SPP UUID
                val socket = try {
                    device.createRfcommSocketToServiceRecord(SPP_UUID)
                } catch (e: IOException) {
                    // Fallback to hidden reflection method if regular createRfcommSocket fails
                    val m = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                    m.invoke(device, 1) as BluetoothSocket
                }

                socket.connect()

                // Successfully connected!
                activeSocket = socket
                inputStream = socket.inputStream
                outputStream = socket.outputStream

                _connectionState.value = ConnectionState.Connected(deviceName, address)

                // Start continuous reader
                startReaderLoop()

            } catch (e: Exception) {
                if (e is CancellationException) throw e
                val errorMsg = e.localizedMessage ?: "Failed to establish RFCOMM socket"
                _lastConnectionError.value = errorMsg
                _connectionState.value = ConnectionState.Error(errorMsg, e)
                closeStreams()

                if (shouldAutoReconnect && lastTargetDevice != null) {
                    triggerReconnect()
                }
            }
        }
    }

    private fun startReaderLoop() {
        readerJob?.cancel()
        readerJob = scope.launch {
            val stream = inputStream ?: return@launch
            val buffer = ByteArray(READ_BUFFER_SIZE)

            while (isActive) {
                try {
                    val bytesRead = stream.read(buffer)
                    if (bytesRead > 0) {
                        val text = String(buffer, 0, bytesRead, Charsets.UTF_8)
                        _lastReceivedPacket.value = text.trim()
                        _incomingDataFlow.emit(text)
                    } else if (bytesRead == -1) {
                        // End of stream reached, remote disconnected
                        handleDisconnection("HC-05 closed the connection")
                        break
                    }
                } catch (e: IOException) {
                    if (isActive) {
                        handleDisconnection("Connection lost: ${e.message}")
                    }
                    break
                }
            }
        }
    }

    @Synchronized
    fun sendCommand(command: String): Boolean {
        val stream = outputStream
        val socket = activeSocket
        if (socket == null || !socket.isConnected || stream == null) {
            _lastConnectionError.value = "Cannot send command: Device not connected"
            return false
        }

        return try {
            val bytes = command.toByteArray(Charsets.UTF_8)
            stream.write(bytes)
            stream.flush()
            _lastTransmittedCommand.value = command.trim()
            true
        } catch (e: Exception) {
            _lastConnectionError.value = "Write failure: ${e.message}"
            handleDisconnection("Write error: ${e.message}")
            false
        }
    }

    private fun handleDisconnection(reason: String) {
        _lastConnectionError.value = reason
        _connectionState.value = ConnectionState.Disconnected
        closeStreams()

        if (shouldAutoReconnect && lastTargetDevice != null) {
            triggerReconnect()
        }
    }

    private fun triggerReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            val device = lastTargetDevice ?: return@launch
            for (attempt in 1..MAX_RECONNECT_ATTEMPTS) {
                _connectionState.value = ConnectionState.Reconnecting(attempt, MAX_RECONNECT_ATTEMPTS)
                delay(3000L) // Wait 3 seconds before next retry
                if (!isActive) break

                try {
                    connect(device, shouldAutoReconnect)
                    break
                } catch (e: Exception) {
                    // Retry loop will continue
                }
            }
        }
    }

    fun disconnect() {
        shouldAutoReconnect = false
        reconnectJob?.cancel()
        connectJob?.cancel()
        readerJob?.cancel()
        closeStreams()
        _connectionState.value = ConnectionState.Disconnected
    }

    private fun closeStreams() {
        try {
            inputStream?.close()
        } catch (e: Exception) { /* ignore */ }
        try {
            outputStream?.close()
        } catch (e: Exception) { /* ignore */ }
        try {
            activeSocket?.close()
        } catch (e: Exception) { /* ignore */ }
        inputStream = null
        outputStream = null
        activeSocket = null
    }
}

