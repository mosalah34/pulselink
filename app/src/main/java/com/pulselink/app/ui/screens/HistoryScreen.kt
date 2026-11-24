package com.pulselink.app.ui.screens

import android.content.Intent
import android.widget.Toast
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.pulselink.app.data.model.AlertEvent
import com.pulselink.app.data.model.VitalReading
import com.pulselink.app.ui.MainViewModel
import com.pulselink.app.ui.components.PulseLinkHeader
import com.pulselink.app.ui.theme.HeartRateRed
import com.pulselink.app.ui.theme.NavyBg
import com.pulselink.app.ui.theme.NavyCard
import com.pulselink.app.ui.theme.NavyCardBorder
import com.pulselink.app.ui.theme.NavyCardElevated
import com.pulselink.app.ui.theme.PrimaryBlue
import com.pulselink.app.ui.theme.SecondaryCyan
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
fun HistoryScreen(
    viewModel: MainViewModel,
    onNavigateToDevice: () -> Unit
) {
    val context = LocalContext.current
    val connectionState by viewModel.connectionState.collectAsState()
    val readings by viewModel.recentReadings.collectAsState()
    val resolvedAlerts by viewModel.resolvedAlerts.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredReadings = remember(readings, searchQuery) {
        if (searchQuery.isBlank()) {
            readings
        } else {
            readings.filter {
                it.adjustedBpm.toString().contains(searchQuery) ||
                it.temperatureCelsius.toString().contains(searchQuery)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyBg)
    ) {
        PulseLinkHeader(
            title = "History & Records",
            connectionState = connectionState,
            onBluetoothClick = onNavigateToDevice
        )

        // Actions Header: Export CSV & Clear
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    viewModel.exportCsv { file ->
                        if (file != null) {
                            Toast.makeText(context, "Exported: ${file.name}", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Icon(imageVector = Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "EXPORT CSV", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
            }

            OutlinedButton(
                onClick = { viewModel.clearHistory() },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusOrange)
            ) {
                Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "CLEAR ALL", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
            }
        }

        // Tabs: Vital Readings vs Past Alert Logs
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = NavyCard,
            contentColor = PrimaryBlue,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = PrimaryBlue
                )
            }
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = {
                    Text(
                        text = "Vitals (${filteredReadings.size})",
                        color = if (selectedTab == 0) TextPrimary else TextSecondary
                    )
                }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Text(
                        text = "Past Alerts (${resolvedAlerts.size})",
                        color = if (selectedTab == 1) TextPrimary else TextSecondary
                    )
                }
            )
        }

        if (selectedTab == 0) {
            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Filter by BPM or Temperature...", color = TextMuted) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted) },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = NavyCard,
                    unfocusedContainerColor = NavyCard,
                    focusedBorderColor = PrimaryBlue,
                    unfocusedBorderColor = NavyCardBorder
                )
            )

            // Readings List
            if (filteredReadings.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No historical readings recorded in local Room Database yet.\nConnect HC-05 to record live telemetry.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    items(filteredReadings, key = { it.id }) { reading ->
                        HistoryReadingItem(reading = reading)
                    }
                }
            }
        } else {
            // Past Alerts List
            if (resolvedAlerts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No historical alerts recorded.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    items(resolvedAlerts, key = { it.id }) { alert ->
                        ResolvedAlertItem(alert = alert)
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryReadingItem(reading: VitalReading) {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    val formattedTime = sdf.format(Date(reading.timestamp))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(NavyCardBorder))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = TextMuted
                )
                Text(
                    text = "Adjusted Telemetry",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                    color = TextSecondary
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // Adjusted HR
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = HeartRateRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${if (reading.adjustedBpm.isNaN()) "--" else reading.adjustedBpm.toInt()} BPM",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = TextPrimary
                    )
                }

                // Temp
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Thermostat,
                        contentDescription = null,
                        tint = TempCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${if (reading.temperatureCelsius.isNaN()) "--" else String.format(Locale.US, "%.1f", reading.temperatureCelsius)}°C",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = TextPrimary
                    )
                }
            }
        }
    }
}

@Composable
fun ResolvedAlertItem(alert: AlertEvent) {
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val startTime = sdf.format(Date(alert.timestamp))
    val resolvedTime = alert.resolvedTimestamp?.let { sdf.format(Date(it)) } ?: "--"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(NavyCardBorder))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = alert.alertType.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = StatusOrange
                )
                Text(
                    text = "RESOLVED",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = SecondaryCyan
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = alert.notes,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Started: $startTime  •  Resolved: $resolvedTime",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }
    }
}

