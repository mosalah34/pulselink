package com.pulselink.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Default.Dashboard)
    object Terminal : Screen("terminal", "Terminal", Icons.Default.Terminal)
    object History : Screen("history", "History", Icons.Default.History)
    object Alerts : Screen("alerts", "Alerts", Icons.Default.NotificationsActive)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    object DeviceConnection : Screen("connection", "Device", Icons.Default.Bluetooth)

    companion object {
        val items = listOf(
            Dashboard,
            Terminal,
            History,
            Alerts,
            Settings,
            DeviceConnection
        )
    }
}

