package com.mahiuddin.appguard.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.mahiuddin.appguard.ui.notification.NotificationCaptureScreen
import com.mahiuddin.appguard.ui.notification.NotificationCaptureViewModel
import com.mahiuddin.appguard.ui.notification.NotificationHistoryScreen
import com.mahiuddin.appguard.ui.screens.HomeScreen
import com.mahiuddin.appguard.ui.theme.DarkGreen
import com.mahiuddin.appguard.ui.theme.LightGreenSurface
import com.mahiuddin.appguard.ui.theme.MainBackground
import com.mahiuddin.appguard.ui.theme.PrimaryGreen
import com.mahiuddin.appguard.ui.theme.SecondaryText

@Composable
fun MainContainer(
    appGuardViewModel: AppGuardViewModel,
    notificationViewModel: NotificationCaptureViewModel
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var isViewingHistory by remember { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            if (!isViewingHistory) {
                NavigationBar(
                    containerColor = MainBackground,
                    contentColor = PrimaryGreen
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = {
                            selectedTab = 0
                            isViewingHistory = false
                        },
                        icon = { Icon(Icons.Default.Shield, contentDescription = "App Blocker") },
                        label = { Text("App Blocker", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PrimaryGreen,
                            selectedTextColor = PrimaryGreen,
                            indicatorColor = LightGreenSurface,
                            unselectedIconColor = SecondaryText,
                            unselectedTextColor = SecondaryText
                        )
                    )

                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = {
                            selectedTab = 1
                            isViewingHistory = false
                        },
                        icon = { Icon(Icons.Default.NotificationsActive, contentDescription = "Notification Capture") },
                        label = { Text("Notifications", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PrimaryGreen,
                            selectedTextColor = PrimaryGreen,
                            indicatorColor = LightGreenSurface,
                            unselectedIconColor = SecondaryText,
                            unselectedTextColor = SecondaryText
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                0 -> HomeScreen(viewModel = appGuardViewModel)
                1 -> {
                    if (isViewingHistory) {
                        NotificationHistoryScreen(
                            viewModel = notificationViewModel,
                            onBack = { isViewingHistory = false }
                        )
                    } else {
                        NotificationCaptureScreen(
                            viewModel = notificationViewModel,
                            onOpenHistory = { isViewingHistory = true }
                        )
                    }
                }
            }
        }
    }
}
