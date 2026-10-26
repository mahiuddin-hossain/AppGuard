package com.mahiuddin.appguard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.mahiuddin.appguard.ui.AppGuardViewModel
import com.mahiuddin.appguard.ui.MainContainer
import com.mahiuddin.appguard.ui.dns.DnsBlockViewModel
import com.mahiuddin.appguard.ui.notification.NotificationCaptureViewModel
import com.mahiuddin.appguard.ui.theme.AppGuardTheme

class MainActivity : ComponentActivity() {

    private val appGuardViewModel: AppGuardViewModel by viewModels()
    private val dnsBlockViewModel: DnsBlockViewModel by viewModels()
    private val notificationViewModel: NotificationCaptureViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppGuardTheme {
                MainContainer(
                    appGuardViewModel = appGuardViewModel,
                    dnsBlockViewModel = dnsBlockViewModel,
                    notificationViewModel = notificationViewModel
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        appGuardViewModel.refreshAllData()
        dnsBlockViewModel.refreshAccessibilityStatus()
        notificationViewModel.refreshAllData()
    }
}
