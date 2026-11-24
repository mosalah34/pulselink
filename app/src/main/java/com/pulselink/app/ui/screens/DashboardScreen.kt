package com.pulselink.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulselink.app.data.model.ArduinoResetState
import com.pulselink.app.data.model.ConnectionState
import com.pulselink.app.ui.MainViewModel
import com.pulselink.app.ui.components.LiveDualAxisChart
import com.pulselink.app.ui.components.PatientStatusBadge
import com.pulselink.app.ui.components.PulseLinkHeader
import com.pulselink.app.ui.components.VitalMetricCard
import com.pulselink.app.ui.theme.HeartRateRed
import com.pulselink.app.ui.theme.NavyBg
import com.pulselink.app.ui.theme.NavyCard
import com.pulselink.app.ui.theme.NavyCardBorder
import com.pulselink.app.ui.theme.PrimaryBlue
import com.pulselink.app.ui.theme.StatusGreen
import com.pulselink.app.ui.theme.StatusOrange
import com.pulselink.app.ui.theme.StatusRed
import com.pulselink.app.ui.theme.TempCyan
import com.pulselink.app.ui.theme.TextMuted
import com.pulselink.app.ui.theme.TextPrimary
import com.pulselink.app.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onNavigateToDevice: () -> Unit
) {
    val currentReading by viewModel.currentReading.collectAsState()
    val liveChartReadings by viewModel.liveChartReadings.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val decisionResult by viewModel.decisionResult.collectAsState()
    val isCommandSending by viewModel.isCommandSending.collectAsState()
    val commandError by viewModel.commandError.collectAsState()
    val resetState by viewModel.resetState.collectAsState()

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyBg)
            .verticalScroll(scrollState)
            .padding(bottom = 24.dp)
    ) {
        // App Header with Real Bluetooth Indicator
        PulseLinkHeader(
            connectionState = connectionState,
            onBluetoothClick = onNavigateToDevice
        )

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            // Optional Command Error Banner
            if (commandError != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .background(StatusRed.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                        .border(1.dp, StatusRed.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Error",
                            tint = StatusRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = commandError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = TextMuted,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { viewModel.clearCommandError() }
                    )
                }
            }

            // Arduino Restart Banner
            when (val rState = resetState) {
                is ArduinoResetState.SendingReset, is ArduinoResetState.WaitingResetAck, is ArduinoResetState.RestartingArduino -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = PrimaryBlue.copy(alpha = 0.15f)),
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(PrimaryBlue))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = PrimaryBlue,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Restarting Arduino... Hardware watchdog reboot in progress.",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = PrimaryBlue
                            )
                        }
                    }
                }
                is ArduinoResetState.RestartedSuccess -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = StatusGreen.copy(alpha = 0.15f)),
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(StatusGreen))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = StatusGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Arduino restarted successfully (SYSTEM_READY confirmed).",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = StatusGreen
                            )
                        }
                    }
                }
                is ArduinoResetState.Failed -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = StatusRed.copy(alpha = 0.15f)),
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(StatusRed))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = StatusRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = rState.reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = StatusRed
                            )
                        }
                    }
                }
                is ArduinoResetState.Idle -> { /* Idle */ }
            }

            // Patient Status Badge
            PatientStatusBadge(
                status = decisionResult.status,
                isStale = decisionResult.isStale
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Vital Signs Cards Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Heart Rate Card (Adjusted BPM)
                val hrText = if (currentReading != null && !currentReading!!.adjustedBpm.isNaN()) {
                    currentReading!!.adjustedBpm.toInt().toString()
                } else "--"

                VitalMetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Heart Rate",
                    value = hrText,
                    unit = "BPM",
                    icon = Icons.Default.Favorite,
                    accentColor = HeartRateRed,
                    subText = "Calibrated BPM",
                    isStale = currentReading?.isStale == true
                )

                // Temperature Card (°C)
                val tempText = if (currentReading != null && !currentReading!!.temperatureCelsius.isNaN()) {
                    String.format(Locale.US, "%.1f", currentReading!!.temperatureCelsius)
                } else "--"

                VitalMetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Temperature",
                    value = tempText,
                    unit = "°C",
                    icon = Icons.Default.Thermostat,
                    accentColor = TempCyan,
                    subText = "DS18B20 Probe Surface",
                    isStale = currentReading?.isStale == true
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Combined Dual Axis Telemetry Chart (Using live in-memory chronological buffer)
            LiveDualAxisChart(
                readings = liveChartReadings
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Telemetry Metadata
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val lastUpdateText = if (currentReading != null) {
                    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    sdf.format(Date(currentReading!!.timestamp))
                } else "None"

                Text(
                    text = "LAST UPDATE: $lastUpdateText",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )

                val connectionLabel = when (connectionState) {
                    is ConnectionState.Connected -> "HC-05 ACTIVE (9600 BAUD)"
                    is ConnectionState.Connecting -> "CONNECTING..."
                    is ConnectionState.Reconnecting -> "RECONNECTING..."
                    else -> "DISCONNECTED"
                }
                Text(
                    text = connectionLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (connectionState.isConnected) StatusGreen else TextMuted
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Arduino Control Center: START, STOP, TEST, RESET
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ARDUINO TELEMETRY CONTROLS",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextMuted
                )
                if (!connectionState.isConnected) {
                    Text(
                        text = "Connect device to enable",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = StatusOrange
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val controlsEnabled = connectionState.isConnected && !isCommandSending && !resetState.isRestartInProgress

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // START
                Button(
                    onClick = { viewModel.sendStart() },
                    enabled = controlsEnabled,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StatusGreen,
                        disabledContainerColor = StatusGreen.copy(alpha = 0.3f)
                    )
                ) {
                    if (isCommandSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = NavyBg,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Start",
                            modifier = Modifier.size(16.dp),
                            tint = if (controlsEnabled) NavyBg else NavyBg.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "START",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (controlsEnabled) NavyBg else NavyBg.copy(alpha = 0.6f)
                            )
                        )
                    }
                }

                // STOP
                Button(
                    onClick = { viewModel.sendStop() },
                    enabled = controlsEnabled,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StatusRed,
                        disabledContainerColor = StatusRed.copy(alpha = 0.3f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop",
                        modifier = Modifier.size(16.dp),
                        tint = if (controlsEnabled) TextPrimary else TextPrimary.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "STOP",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (controlsEnabled) TextPrimary else TextPrimary.copy(alpha = 0.5f)
                        )
                    )
                }

                // TEST
                OutlinedButton(
                    onClick = { viewModel.sendTest() },
                    enabled = controlsEnabled,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = PrimaryBlue,
                        disabledContentColor = PrimaryBlue.copy(alpha = 0.4f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = "Test",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "TEST",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }

                // RESET
                OutlinedButton(
                    onClick = { viewModel.sendReset() },
                    enabled = controlsEnabled,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = StatusOrange,
                        disabledContentColor = StatusOrange.copy(alpha = 0.4f)
                    )
                ) {
                    if (resetState.isRestartInProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = StatusOrange,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "RESET",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}
