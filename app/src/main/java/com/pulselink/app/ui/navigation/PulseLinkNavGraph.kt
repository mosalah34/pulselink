package com.pulselink.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pulselink.app.ui.MainViewModel
import com.pulselink.app.ui.screens.AlertsScreen
import com.pulselink.app.ui.screens.DashboardScreen
import com.pulselink.app.ui.screens.DeviceConnectionScreen
import com.pulselink.app.ui.screens.HistoryScreen
import com.pulselink.app.ui.screens.SettingsScreen
import com.pulselink.app.ui.screens.TerminalScreen
import com.pulselink.app.ui.theme.NavyBg
import com.pulselink.app.ui.theme.NavySurface
import com.pulselink.app.ui.theme.PrimaryBlue
import com.pulselink.app.ui.theme.SecondaryCyan
import com.pulselink.app.ui.theme.StatusRed
import com.pulselink.app.ui.theme.TextMuted
import com.pulselink.app.ui.theme.TextPrimary

@Composable
fun PulseLinkNavGraph(
    viewModel: MainViewModel
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val activeAlerts by viewModel.activeAlerts.collectAsState()

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = NavySurface,
                tonalElevation = 8.dp
            ) {
                Screen.items.forEach { screen ->
                    val selected = currentRoute == screen.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (currentRoute != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            if (screen == Screen.Alerts && activeAlerts.isNotEmpty()) {
                                BadgedBox(
                                    badge = {
                                        Badge(containerColor = StatusRed) {
                                            Text(
                                                text = "${activeAlerts.size}",
                                                color = TextPrimary,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = screen.icon,
                                        contentDescription = screen.title
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = screen.icon,
                                    contentDescription = screen.title
                                )
                            }
                        },
                        label = {
                            Text(
                                text = screen.title,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = SecondaryCyan,
                            selectedTextColor = SecondaryCyan,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted,
                            indicatorColor = PrimaryBlue.copy(alpha = 0.2f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(NavyBg)
                .padding(innerPadding)
        ) {
            NavHost(
                navController = navController,
                startDestination = Screen.Dashboard.route
            ) {
                composable(Screen.Dashboard.route) {
                    DashboardScreen(
                        viewModel = viewModel,
                        onNavigateToDevice = {
                            navController.navigate(Screen.DeviceConnection.route)
                        }
                    )
                }
                composable(Screen.Terminal.route) {
                    TerminalScreen(
                        viewModel = viewModel,
                        onNavigateToDevice = {
                            navController.navigate(Screen.DeviceConnection.route)
                        }
                    )
                }
                composable(Screen.History.route) {
                    HistoryScreen(
                        viewModel = viewModel,
                        onNavigateToDevice = {
                            navController.navigate(Screen.DeviceConnection.route)
                        }
                    )
                }
                composable(Screen.Alerts.route) {
                    AlertsScreen(
                        viewModel = viewModel,
                        onNavigateToDevice = {
                            navController.navigate(Screen.DeviceConnection.route)
                        }
                    )
                }
                composable(Screen.Settings.route) {
                    SettingsScreen(
                        viewModel = viewModel,
                        onNavigateToDevice = {
                            navController.navigate(Screen.DeviceConnection.route)
                        }
                    )
                }
                composable(Screen.DeviceConnection.route) {
                    DeviceConnectionScreen(
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}

