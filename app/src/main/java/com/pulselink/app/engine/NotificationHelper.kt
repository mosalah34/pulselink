package com.pulselink.app.engine

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.pulselink.app.MainActivity
import com.pulselink.app.R
import com.pulselink.app.data.model.AlertEvent

class NotificationHelper(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                // Channel 1: Foreground Service Channel (Low/Default importance)
                val serviceChannel = NotificationChannel(
                    SERVICE_CHANNEL_ID,
                    context.getString(R.string.channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = context.getString(R.string.channel_desc)
                    setShowBadge(false)
                }

                // Channel 2: Emergency Alert Channel (High/Max importance for heads-up notifications)
                val alertChannel = NotificationChannel(
                    ALERT_CHANNEL_ID,
                    context.getString(R.string.alert_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.alert_channel_desc)
                    enableVibration(true)
                    setShowBadge(true)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }

                notificationManager.createNotificationChannel(serviceChannel)
                notificationManager.createNotificationChannel(alertChannel)
            } catch (e: Exception) {
                Log.e("NotificationHelper", "Failed to create notification channels: ${e.message}", e)
            }
        }
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun buildForegroundServiceNotification(
        statusText: String,
        details: String
    ): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, SERVICE_CHANNEL_ID)
            .setContentTitle("PulseLink • $statusText")
            .setContentText(details)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    fun showAlertNotification(alert: AlertEvent) {
        if (!hasNotificationPermission()) {
            Log.w("NotificationHelper", "POST_NOTIFICATIONS permission not granted. Skipping notification.")
            return
        }

        try {
            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openPendingIntent = PendingIntent.getActivity(
                context,
                1,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val ackIntent = Intent(ACTION_ACKNOWLEDGE_ALERT).apply {
                setPackage(context.packageName)
                putExtra(EXTRA_ALERT_ID, alert.id)
            }
            val ackPendingIntent = PendingIntent.getBroadcast(
                context,
                2,
                ackIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val title = "CRITICAL ALERT: ${alert.alertType.title}"
            val body = alert.notes.ifEmpty {
                "Measurement: ${alert.measurementValue} (Threshold: ${alert.thresholdValue})"
            }

            val notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(body)
                .setSmallIcon(R.drawable.ic_notification)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(openPendingIntent)
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Acknowledge",
                    ackPendingIntent
                )
                .build()

            notificationManager.notify(ALERT_NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e("NotificationHelper", "Failed to display alert notification: ${e.message}", e)
        }
    }

    fun dismissAlertNotification() {
        try {
            notificationManager.cancel(ALERT_NOTIFICATION_ID)
        } catch (e: Exception) {
            Log.e("NotificationHelper", "Failed to dismiss notification: ${e.message}", e)
        }
    }

    companion object {
        const val SERVICE_CHANNEL_ID = "pulselink_service_channel"
        const val ALERT_CHANNEL_ID = "pulselink_emergency_alerts_channel"
        const val SERVICE_NOTIFICATION_ID = 1001
        const val ALERT_NOTIFICATION_ID = 2002
        const val ACTION_ACKNOWLEDGE_ALERT = "com.pulselink.app.ACTION_ACKNOWLEDGE_ALERT"
        const val EXTRA_ALERT_ID = "extra_alert_id"
    }
}
