package com.pulselink.app.bluetooth

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import com.pulselink.app.PulseLinkApp
import com.pulselink.app.engine.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PulseLinkBluetoothService : Service() {

    private val binder = LocalBinder()
    private val scope = CoroutineScope(Dispatchers.Main)
    private var monitorJob: Job? = null
    private lateinit var notificationHelper: NotificationHelper

    inner class LocalBinder : Binder() {
        fun getService(): PulseLinkBluetoothService = this@PulseLinkBluetoothService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        try {
            notificationHelper = NotificationHelper(this)
            startInForeground("Monitoring Active", "Connecting to HC-05 vital sensors...")
            observeConnectionAndVitals()
        } catch (e: Exception) {
            Log.e("PulseLinkService", "Error during service onCreate: ${e.message}", e)
        }
    }

    private fun startInForeground(title: String, desc: String) {
        try {
            val notification = notificationHelper.buildForegroundServiceNotification(title, desc)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val hasBtConnect = ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED

                if (hasBtConnect) {
                    startForeground(
                        NotificationHelper.SERVICE_NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                    )
                } else {
                    startForeground(NotificationHelper.SERVICE_NOTIFICATION_ID, notification)
                }
            } else {
                startForeground(NotificationHelper.SERVICE_NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e("PulseLinkService", "startInForeground error: ${e.message}", e)
        }
    }

    private fun observeConnectionAndVitals() {
        val app = application as? PulseLinkApp ?: return
        monitorJob = scope.launch {
            launch {
                app.bluetoothManager.connectionState.collectLatest { state ->
                    try {
                        val notification = notificationHelper.buildForegroundServiceNotification(
                            state.statusText,
                            "Real-time HC-05 Bluetooth patient link"
                        )
                        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                        nm.notify(NotificationHelper.SERVICE_NOTIFICATION_ID, notification)
                    } catch (e: Exception) {
                        Log.e("PulseLinkService", "Failed to update notification: ${e.message}", e)
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        super.onDestroy()
    }
}
