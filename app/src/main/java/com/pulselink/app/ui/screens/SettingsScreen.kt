package com.pulselink.app.ui.screens

import android.widget.Toast
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulselink.app.data.model.AlarmToneType
import com.pulselink.app.data.model.ThresholdSettings
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
import com.pulselink.app.ui.theme.TempCyan
import com.pulselink.app.ui.theme.TextMuted
import com.pulselink.app.ui.theme.TextPrimary
import com.pulselink.app.ui.theme.TextSecondary

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onNavigateToDevice: () -> Unit
) {
    val context = LocalContext.current
    val connectionState by viewModel.connectionState.collectAsState()
    val currentSettings by viewModel.settings.collectAsState()

    var hrMin by remember(currentSettings) { mutableFloatStateOf(currentSettings.hrMinBpm) }
    var hrMax by remember(currentSettings) { mutableFloatStateOf(currentSettings.hrMaxBpm) }
    var tempThreshold by remember(currentSettings) { mutableFloatStateOf(currentSettings.tempThresholdCelsius) }
    var soundEnabled by remember(currentSettings) { mutableStateOf(currentSettings.alarmSoundEnabled) }
    var vibrationEnabled by remember(currentSettings) { mutableStateOf(currentSettings.vibrationEnabled) }
    var selectedTone by remember(currentSettings) { mutableStateOf(currentSettings.alarmTone) }
    var autoReconnect by remember(currentSettings) { mutableStateOf(currentSettings.autoReconnect) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyBg)
            .verticalScroll(scrollState)
            .padding(bottom = 24.dp)
    ) {
        PulseLinkHeader(
            title = "App Settings",
            connectionState = connectionState,
            onBluetoothClick = onNavigateToDevice
        )

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {

            // Section 1: Medical / Experimental Disclaimer Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = NavyCardElevated),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(StatusOrange.copy(alpha = 0.4f))
                )
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = StatusOrange,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "EXPERIMENTAL MONITORING THRESHOLDS",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = StatusOrange
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "These threshold values are configurable experimental trigger levels for telemetry testing, not clinical diagnostic criteria. The DS18B20 sensor measures external probe/surface temperature, not core body temperature.",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                            color = TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 2: Threshold Sliders Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NavyCard),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(NavyCardBorder))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Tune, contentDescription = null, tint = PrimaryBlue)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "VITAL SIGN THRESHOLDS",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Heart Rate Min Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Low HR Threshold (Bradycardia)", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        Text(text = "${hrMin.toInt()} BPM", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = HeartRateRed)
                    }
                    Slider(
                        value = hrMin,
                        onValueChange = { hrMin = it },
                        valueRange = 30f..80f,
                        steps = 50,
                        colors = SliderDefaults.colors(thumbColor = HeartRateRed, activeTrackColor = HeartRateRed)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Heart Rate Max Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "High HR Threshold (Tachycardia)", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        Text(text = "${hrMax.toInt()} BPM", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = HeartRateRed)
                    }
                    Slider(
                        value = hrMax,
                        onValueChange = { hrMax = it },
                        valueRange = 85f..180f,
                        steps = 95,
                        colors = SliderDefaults.colors(thumbColor = HeartRateRed, activeTrackColor = HeartRateRed)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Temperature Threshold Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Temperature Alert Threshold", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        Text(text = "${String.format("%.1f", tempThreshold)}°C", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = TempCyan)
                    }
                    Slider(
                        value = tempThreshold,
                        onValueChange = { tempThreshold = it },
                        valueRange = 30.0f..42.0f,
                        steps = 120,
                        colors = SliderDefaults.colors(thumbColor = TempCyan, activeTrackColor = TempCyan)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 3: Alarm & Sound Settings Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NavyCard),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(NavyCardBorder))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Notifications, contentDescription = null, tint = SecondaryCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ALARM SOUND & FEEDBACK",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Phone Alarm Sound Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "Phone Audio Alarm", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                            Text(text = "Synthesizes medical alert beeps on speaker", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        }
                        Switch(
                            checked = soundEnabled,
                            onCheckedChange = { soundEnabled = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = PrimaryBlue)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Vibration Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "Vibrate Phone on Alert", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                            Text(text = "Distinct rhythmic emergency pulses", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        }
                        Switch(
                            checked = vibrationEnabled,
                            onCheckedChange = { vibrationEnabled = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = PrimaryBlue)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(text = "SELECT ALARM TONE", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = TextSecondary)

                    AlarmToneType.values().forEach { tone ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedTone = tone }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedTone == tone,
                                onClick = { selectedTone = tone },
                                colors = RadioButtonDefaults.colors(selectedColor = PrimaryBlue)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = tone.displayName, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { viewModel.testAlarmTone(selectedTone) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SecondaryCyan)
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "TEST ALARM SOUND & VIBRATION")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 4: Bluetooth Preferences
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NavyCard),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(NavyCardBorder))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "Auto-Reconnect HC-05", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                            Text(text = "Automatically retries RFCOMM connection on link drop", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        }
                        Switch(
                            checked = autoReconnect,
                            onCheckedChange = { autoReconnect = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = PrimaryBlue)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Save Settings Button
            Button(
                onClick = {
                    val updated = currentSettings.copy(
                        hrMinBpm = hrMin,
                        hrMaxBpm = hrMax,
                        tempThresholdCelsius = tempThreshold,
                        alarmSoundEnabled = soundEnabled,
                        vibrationEnabled = vibrationEnabled,
                        alarmTone = selectedTone,
                        autoReconnect = autoReconnect
                    )
                    viewModel.updateSettings(updated)
                    Toast.makeText(context, "Settings saved successfully", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Icon(imageVector = Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "SAVE CONFIGURATION",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // About Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = NavyCard)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(text = "PulseLink Native Patient Telemetry", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                    Text(text = "Version 1.0.0 • Architecture: Jetpack Compose + Room + Coroutines", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp), color = TextSecondary)
                    Text(text = "Protocol: HC-05 Bluetooth Classic SPP @ 9600 Baud", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                }
            }
        }
    }
}

