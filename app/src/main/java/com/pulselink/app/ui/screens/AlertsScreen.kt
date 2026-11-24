package com.pulselink.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulselink.app.data.model.AlertEvent
import com.pulselink.app.data.model.AlertType
import com.pulselink.app.data.model.BuzzerHardwareState
import com.pulselink.app.ui.MainViewModel
import com.pulselink.app.ui.components.PulseLinkHeader
import com.pulselink.app.ui.theme.NavyBg
import com.pulselink.app.ui.theme.NavyCard
import com.pulselink.app.ui.theme.NavyCardBorder
import com.pulselink.app.ui.theme.NavyCardElevated
import com.pulselink.app.ui.theme.PrimaryBlue
import com.pulselink.app.ui.theme.StatusGreen
import com.pulselink.app.ui.theme.StatusOrange
import com.pulselink.app.ui.theme.StatusRed
import com.pulselink.app.ui.theme.TextMuted
import com.pulselink.app.ui.theme.TextPrimary
import com.pulselink.app.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AlertsScreen(
    viewModel: MainViewModel,
    onNavigateToDevice: () -> Unit
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val activeAlerts by viewModel.activeAlerts.collectAsState()
    val resolvedAlerts by viewModel.resolvedAlerts.collectAsState()
    val buzzerState by viewModel.buzzerHardwareState.collectAsState()
    val buzzerWarning by viewModel.buzzerWarning.collectAsState()

    var showClearConfirmDialog by remember { mutableStateOf(false) }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Clear All Alert History?", color = TextPrimary) },
            text = { Text("This will permanently delete all logged active and resolved emergency events.", color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearHistory()
                        showClearConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusRed)
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = NavyCard,
            shape = RoundedCornerShape(16.dp)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyBg)
    ) {
        PulseLinkHeader(
            title = "Medical Alerts",
            connectionState = connectionState,
            onBluetoothClick = onNavigateToDevice
        )

        // Clear History Action Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "EMERGENCY MONITORING",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = TextMuted
            )

            OutlinedButton(
                onClick = { showClearConfirmDialog = true },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusOrange)
            ) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "CLEAR LOGS", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
            }
        }

        // Communication warning banner if Arduino buzzer ACK is missing or timed out
        if (buzzerWarning != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = StatusRed.copy(alpha = 0.15f)),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(StatusRed.copy(alpha = 0.6f)))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = StatusRed, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = buzzerWarning!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { viewModel.retryBuzzerOff() },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = StatusOrange)
                    ) {
                        Text("RETRY", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Section 1: Active Alerts
            item {
                Text(
                    text = "ACTIVE ALERTS (${activeAlerts.size})",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (activeAlerts.isNotEmpty()) StatusRed else TextSecondary,
                    modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)
                )
            }

            if (activeAlerts.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = NavyCard)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(StatusGreen)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "All patient vitals within normal parameters. No active alarms.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }
            } else {
                items(activeAlerts, key = { it.id }) { alert ->
                    ActiveAlertCard(
                        alert = alert,
                        buzzerState = buzzerState,
                        onAcknowledge = { viewModel.acknowledgeAlert(alert.id) }
                    )
                }
            }

            // Section 2: Previous Alerts
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "PREVIOUS / RESOLVED ALERTS (${resolvedAlerts.size})",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            if (resolvedAlerts.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = NavyCard)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No past alerts recorded.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted
                            )
                        }
                    }
                }
            } else {
                items(resolvedAlerts, key = { it.id }) { alert ->
                    AlertHistoryCard(alert = alert)
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun ActiveAlertCard(
    alert: AlertEvent,
    buzzerState: BuzzerHardwareState,
    onAcknowledge: () -> Unit
) {
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val startTime = sdf.format(Date(alert.timestamp))
    val isTemp = alert.alertType == AlertType.HIGH_TEMPERATURE
    val alertColor = if (isTemp) StatusRed else StatusOrange

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCardElevated),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.verticalGradient(
                listOf(alertColor, NavyCardBorder)
            )
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(alertColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = alertColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = alert.alertType.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = alertColor
                        )
                        if (isTemp) {
                            Text(
                                text = "HIGH PRIORITY TELEMETRY",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = StatusRed
                            )
                        }
                    }
                }

                Text(
                    text = startTime,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = TextMuted
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = alert.notes,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Dual Alarm Source Status Bar (Phone + Arduino Buzzer)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NavyCard, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Phone Alarm Status
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (alert.isAcknowledged) Icons.Default.NotificationsOff else Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = if (alert.isAcknowledged) StatusGreen else StatusRed,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (alert.isAcknowledged) "Phone: Silenced" else "Phone: Sounding",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (alert.isAcknowledged) StatusGreen else StatusRed
                        )
                    )
                }

                // Arduino Hardware Buzzer Status
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (buzzerState == BuzzerHardwareState.PENDING_OFF || buzzerState == BuzzerHardwareState.PENDING_ON) {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = StatusOrange)
                    } else {
                        Icon(
                            imageVector = if (buzzerState == BuzzerHardwareState.OFF) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                            contentDescription = null,
                            tint = when (buzzerState) {
                                BuzzerHardwareState.OFF -> StatusGreen
                                BuzzerHardwareState.UNCONFIRMED_OFF -> StatusOrange
                                else -> StatusRed
                            },
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = when (buzzerState) {
                            BuzzerHardwareState.OFF -> "Buzzer: Silenced (Confirmed)"
                            BuzzerHardwareState.PENDING_OFF -> "Buzzer: Silencing (Waiting ACK)..."
                            BuzzerHardwareState.UNCONFIRMED_OFF -> "Buzzer: Silenced (Unconfirmed)"
                            BuzzerHardwareState.PENDING_ON -> "Buzzer: Activating..."
                            BuzzerHardwareState.ON -> "Buzzer: Active (Pin D8)"
                            BuzzerHardwareState.UNCONFIRMED_ON -> "Buzzer: Active (Unconfirmed)"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = when (buzzerState) {
                                BuzzerHardwareState.OFF -> StatusGreen
                                BuzzerHardwareState.UNCONFIRMED_OFF, BuzzerHardwareState.PENDING_OFF -> StatusOrange
                                else -> StatusRed
                            }
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action / Acknowledgment Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (alert.isAcknowledged) "Acknowledged (Phone & Buzzer Silenced)" else "UNACKNOWLEDGED",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (alert.isAcknowledged) StatusGreen else StatusRed
                    )
                )

                if (!alert.isAcknowledged) {
                    Button(
                        onClick = onAcknowledge,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "ACKNOWLEDGE & SILENCE",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AlertHistoryCard(alert: AlertEvent) {
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val startTime = sdf.format(Date(alert.timestamp))
    val resolveTime = if (alert.resolvedTimestamp != null) sdf.format(Date(alert.resolvedTimestamp)) else "Resolved"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(NavyCardBorder.copy(alpha = 0.5f)))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(StatusGreen)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = alert.alertType.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                    Text(
                        text = alert.notes,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        maxLines = 1
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$startTime - $resolveTime",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = TextMuted
                )
                Text(
                    text = "RESOLVED",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = StatusGreen
                )
            }
        }
    }
}
