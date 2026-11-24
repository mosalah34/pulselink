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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulselink.app.data.model.LogDirection
import com.pulselink.app.data.model.TerminalLog
import com.pulselink.app.data.model.TerminalLogLevel
import com.pulselink.app.ui.MainViewModel
import com.pulselink.app.ui.components.PulseLinkHeader
import com.pulselink.app.ui.theme.NavyBg
import com.pulselink.app.ui.theme.NavyCard
import com.pulselink.app.ui.theme.NavyCardBorder
import com.pulselink.app.ui.theme.NavyCardElevated
import com.pulselink.app.ui.theme.PrimaryBlue
import com.pulselink.app.ui.theme.SecondaryCyan
import com.pulselink.app.ui.theme.StatusGreen
import com.pulselink.app.ui.theme.StatusOrange
import com.pulselink.app.ui.theme.StatusRed
import com.pulselink.app.ui.theme.TempCyan
import com.pulselink.app.ui.theme.TerminalAlert
import com.pulselink.app.ui.theme.TerminalRx
import com.pulselink.app.ui.theme.TerminalSys
import com.pulselink.app.ui.theme.TerminalTx
import com.pulselink.app.ui.theme.TerminalWarning
import com.pulselink.app.ui.theme.TextMuted
import com.pulselink.app.ui.theme.TextPrimary
import com.pulselink.app.ui.theme.TextSecondary

@Composable
fun TerminalScreen(
    viewModel: MainViewModel,
    onNavigateToDevice: () -> Unit
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val terminalLogs by viewModel.terminalLogs.collectAsState()
    val lastReceived by viewModel.lastReceivedPacket.collectAsState()
    val lastTransmitted by viewModel.lastTransmittedCommand.collectAsState()

    var commandInput by remember { mutableStateOf("") }
    var autoScroll by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()

    LaunchedEffect(terminalLogs.size, autoScroll) {
        if (autoScroll && terminalLogs.isNotEmpty()) {
            listState.scrollToItem(terminalLogs.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyBg)
    ) {
        // Top Header
        PulseLinkHeader(
            title = "Serial Terminal",
            connectionState = connectionState,
            onBluetoothClick = onNavigateToDevice
        )

        // Status Ribbon: Last RX and Last TX
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = NavyCard),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(NavyCardBorder))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "LAST RX PACKET", style = MaterialTheme.typography.labelSmall, color = TerminalRx)
                    Text(
                        text = if (lastReceived.isBlank()) "None" else lastReceived,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = TextPrimary,
                        maxLines = 1
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "LAST TX COMMAND", style = MaterialTheme.typography.labelSmall, color = TerminalTx)
                    Text(
                        text = if (lastTransmitted.isBlank()) "None" else lastTransmitted,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = TextPrimary,
                        maxLines = 1
                    )
                }
            }
        }

        // Terminal Controls Bar: Auto-scroll & Clear
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = autoScroll,
                    onCheckedChange = { autoScroll = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = PrimaryBlue,
                        checkedTrackColor = NavyCardElevated
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Auto-scroll",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            IconButton(
                onClick = { viewModel.clearTerminal() }
            ) {
                Icon(
                    imageVector = Icons.Default.ClearAll,
                    contentDescription = "Clear Terminal",
                    tint = StatusOrange
                )
            }
        }

        // Terminal Log Viewport
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF040A14))
                .border(1.dp, NavyCardBorder, RoundedCornerShape(12.dp))
        ) {
            if (terminalLogs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Awaiting HC-05 serial transmission...\nBaud Rate: 9600 bps",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        color = TextMuted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                ) {
                    items(terminalLogs, key = { it.id }) { log ->
                        TerminalLogLine(log = log)
                    }
                }
            }
        }

        // Serial Command Input Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = commandInput,
                onValueChange = { commandInput = it },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        text = "e.g. START, STOP, TEST, BUZZER_ON",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                },
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    color = TextPrimary
                ),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = NavyCard,
                    unfocusedContainerColor = NavyCard,
                    focusedBorderColor = PrimaryBlue,
                    unfocusedBorderColor = NavyCardBorder
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (commandInput.isNotBlank()) {
                        viewModel.sendCustomTerminalCommand(commandInput)
                        commandInput = ""
                    }
                }),
                singleLine = true
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = {
                    if (commandInput.isNotBlank()) {
                        viewModel.sendCustomTerminalCommand(commandInput)
                        commandInput = ""
                    }
                },
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(PrimaryBlue)
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send Command",
                    tint = TextPrimary
                )
            }
        }
    }
}

@Composable
fun TerminalLogLine(log: TerminalLog) {
    val levelColor = when (log.level) {
        TerminalLogLevel.INFO -> SecondaryCyan
        TerminalLogLevel.DATA -> StatusGreen
        TerminalLogLevel.WARNING -> TerminalWarning
        TerminalLogLevel.ERROR -> StatusRed
        TerminalLogLevel.ALERT -> TerminalAlert
        TerminalLogLevel.ACTION -> PrimaryBlue
    }

    val dirBadge = when (log.direction) {
        LogDirection.RX -> "RX"
        LogDirection.TX -> "TX"
        LogDirection.SYS -> "SYS"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = log.formattedTime,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
            color = TextMuted
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "[$dirBadge]",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            ),
            color = when (log.direction) {
                LogDirection.RX -> TerminalRx
                LogDirection.TX -> TerminalTx
                LogDirection.SYS -> TerminalSys
            }
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = log.message,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            ),
            color = levelColor
        )
    }
}

