package com.pulselink.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulselink.app.data.model.ConnectionState
import com.pulselink.app.data.model.PatientStatus
import com.pulselink.app.ui.theme.HeartRateRed
import com.pulselink.app.ui.theme.NavyCard
import com.pulselink.app.ui.theme.NavyCardBorder
import com.pulselink.app.ui.theme.NavyCardElevated
import com.pulselink.app.ui.theme.PrimaryBlue
import com.pulselink.app.ui.theme.SecondaryCyan
import com.pulselink.app.ui.theme.StatusGreen
import com.pulselink.app.ui.theme.StatusOrange
import com.pulselink.app.ui.theme.StatusRed
import com.pulselink.app.ui.theme.TempCyan
import com.pulselink.app.ui.theme.TextMuted
import com.pulselink.app.ui.theme.TextPrimary
import com.pulselink.app.ui.theme.TextSecondary

@Composable
fun PulseLinkHeader(
    title: String = "PulseLink",
    connectionState: ConnectionState,
    onBluetoothClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(PrimaryBlue, SecondaryCyan)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "PulseLink Logo",
                    tint = TextPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = TextPrimary
                )
                Text(
                    text = "HC-05 MEDICAL TELEMETRY",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
        }

        // Real Bluetooth Indicator Pill
        val (btColor, btIcon, btText) = when (connectionState) {
            is ConnectionState.Connected -> Triple(
                StatusGreen,
                Icons.Default.BluetoothConnected,
                "HC-05 Connected"
            )
            is ConnectionState.Connecting -> Triple(
                StatusOrange,
                Icons.Default.Bluetooth,
                "Connecting..."
            )
            is ConnectionState.Reconnecting -> Triple(
                StatusOrange,
                Icons.Default.Bluetooth,
                "Reconnecting..."
            )
            is ConnectionState.Error -> Triple(
                StatusRed,
                Icons.Default.BluetoothDisabled,
                "Error"
            )
            is ConnectionState.Disconnected -> Triple(
                TextMuted,
                Icons.Default.BluetoothDisabled,
                "Disconnected"
            )
        }

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(NavyCardElevated)
                .border(1.dp, btColor.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                .clickable { onBluetoothClick() }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(btColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = btIcon,
                contentDescription = null,
                tint = btColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = btText,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = btColor
            )
        }
    }
}

@Composable
fun PatientStatusBadge(
    status: PatientStatus,
    isStale: Boolean
) {
    val (statusColor, label) = when {
        isStale -> Pair(StatusOrange, "DATA STALE")
        status == PatientStatus.NORMAL -> Pair(StatusGreen, "NORMAL VITALS")
        status == PatientStatus.WARNING -> Pair(StatusOrange, "WARNING")
        status == PatientStatus.CRITICAL -> Pair(StatusRed, "CRITICAL EMERGENCY")
        else -> Pair(TextMuted, "STANDBY")
    }

    val animatedColor by animateColorAsState(
        targetValue = statusColor,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "statusColor"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(animatedColor.copy(alpha = 0.12f))
            .border(1.dp, animatedColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(animatedColor)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "PATIENT STATUS",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = TextSecondary
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            ),
            color = animatedColor
        )
    }
}

@Composable
fun VitalMetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    unit: String,
    icon: ImageVector,
    accentColor: Color,
    subText: String,
    isStale: Boolean
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = NavyCard
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.verticalGradient(
                colors = listOf(
                    accentColor.copy(alpha = if (isStale) 0.15f else 0.45f),
                    NavyCardBorder
                )
            )
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = TextSecondary
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 38.sp
                    ),
                    color = if (isStale) TextMuted else TextPrimary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = accentColor,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (isStale) "Stale sensor data" else subText,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                color = if (isStale) StatusOrange else TextMuted
            )
        }
    }
}

