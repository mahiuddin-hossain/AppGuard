package com.mahiuddin.appguard.ui.notification

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mahiuddin.appguard.ui.screens.AppIconImage
import com.mahiuddin.appguard.ui.theme.DarkGreen
import com.mahiuddin.appguard.ui.theme.LightGreenSurface
import com.mahiuddin.appguard.ui.theme.MainBackground
import com.mahiuddin.appguard.ui.theme.PrimaryGreen
import com.mahiuddin.appguard.ui.theme.PrimaryText
import com.mahiuddin.appguard.ui.theme.SecondaryText
import com.mahiuddin.appguard.ui.theme.StatusRed
import com.mahiuddin.appguard.ui.theme.StatusRedSurface
import com.mahiuddin.appguard.ui.theme.SubtleBorder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NotificationCaptureScreen(
    viewModel: NotificationCaptureViewModel,
    onOpenHistory: () -> Unit
) {
    val context = LocalContext.current

    val isCaptureEnabled by viewModel.isCaptureEnabled.collectAsState()
    val isListenerPermissionGranted by viewModel.isListenerPermissionGranted.collectAsState()
    val isCloudSyncEnabled by viewModel.isCloudSyncEnabled.collectAsState()
    val selectedPackages by viewModel.selectedPackages.collectAsState()
    val pendingSyncCount by viewModel.pendingSyncCount.collectAsState()
    val lastSyncedTime by viewModel.lastSyncedTime.collectAsState()
    val totalCount by viewModel.totalNotificationCount.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()

    val isSelectAppsDialogOpen by viewModel.isSelectAppsDialogOpen.collectAsState()
    val isDeleteLocalDialogOpen by viewModel.isDeleteLocalDialogOpen.collectAsState()
    val isDeleteCloudDialogOpen by viewModel.isDeleteCloudDialogOpen.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(userMessage) {
        userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearUserMessage()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MainBackground)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(48.dp))
                NotificationCaptureHeader()
            }

            // Notification Listener Permission Card
            item {
                ListenerPermissionCard(
                    isGranted = isListenerPermissionGranted,
                    onOpenSettings = {
                        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    }
                )
            }

            // Master Notification Capture Switch Card
            item {
                CaptureControlCard(
                    isCaptureEnabled = isCaptureEnabled,
                    isPermissionGranted = isListenerPermissionGranted,
                    selectedAppCount = selectedPackages.size,
                    onToggleCapture = { viewModel.setCaptureEnabled(it) }
                )
            }

            // Cloud Synchronization Card
            item {
                CloudSyncCard(
                    isCloudSyncEnabled = isCloudSyncEnabled,
                    pendingCount = pendingSyncCount,
                    lastSyncedTime = lastSyncedTime,
                    onToggleSync = { viewModel.setCloudSyncEnabled(it) },
                    onSyncNow = { viewModel.triggerSyncNow() }
                )
            }

            // Selected Target Apps Section
            item {
                TargetAppsSection(
                    selectedPackageCount = selectedPackages.size,
                    onOpenSelectApps = { viewModel.openSelectAppsDialog() }
                )
            }

            // History & Data Management Section
            item {
                DataManagementSection(
                    totalNotificationCount = totalCount,
                    onOpenHistory = onOpenHistory,
                    onDeleteLocal = { viewModel.openDeleteLocalDialog() },
                    onDeleteCloud = { viewModel.openDeleteCloudDialog() }
                )
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }

    // Dialogs
    if (isSelectAppsDialogOpen) {
        SelectNotificationAppsDialog(viewModel = viewModel)
    }

    if (isDeleteLocalDialogOpen) {
        AlertDialog(
            onDismissRequest = { viewModel.closeDeleteLocalDialog() },
            title = { Text("Delete Local Records", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete all locally stored notification records?") },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmDeleteLocalHistory() },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusRed)
                ) {
                    Text("Delete Local", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.closeDeleteLocalDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (isDeleteCloudDialogOpen) {
        AlertDialog(
            onDismissRequest = { viewModel.closeDeleteCloudDialog() },
            title = { Text("Delete Cloud Records", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete all notification records stored in Cloud Firestore for your user account?") },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmDeleteCloudHistory() },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusRed)
                ) {
                    Text("Delete Cloud Data", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.closeDeleteCloudDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun NotificationCaptureHeader() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(LightGreenSurface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.NotificationsActive,
                contentDescription = null,
                tint = PrimaryGreen,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column {
            Text(
                text = "Notification Capture",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = PrimaryText
            )
            Text(
                text = "Capture & sync notifications offline",
                fontSize = 13.sp,
                color = SecondaryText
            )
        }
    }
}

@Composable
fun ListenerPermissionCard(
    isGranted: Boolean,
    onOpenSettings: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SubtleBorder, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) LightGreenSurface else StatusRedSurface
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isGranted) DarkGreen else StatusRed,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = if (isGranted) "Notification Access Active" else "Notification Access Required",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (isGranted) DarkGreen else StatusRed
                    )
                    Text(
                        text = if (isGranted) "AppGuard has listener permission to record notifications" else "Grant notification listener access in Settings to enable capture",
                        fontSize = 12.sp,
                        color = SecondaryText
                    )
                }
            }

            if (!isGranted) {
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onOpenSettings,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusRed)
                ) {
                    Text("Settings", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun CaptureControlCard(
    isCaptureEnabled: Boolean,
    isPermissionGranted: Boolean,
    selectedAppCount: Int,
    onToggleCapture: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SubtleBorder, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = MainBackground),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Capture Service", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
                    Text(
                        text = if (isCaptureEnabled) "Capturing notifications for $selectedAppCount apps" else "Capture is turned off",
                        fontSize = 13.sp,
                        color = SecondaryText
                    )
                }

                Switch(
                    checked = isCaptureEnabled,
                    onCheckedChange = onToggleCapture,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = PrimaryGreen,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = SubtleBorder
                    )
                )
            }

            if (isCaptureEnabled && !isPermissionGranted) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = SubtleBorder)
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = StatusRed, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Capture enabled, but Notification Listener permission is missing in Android Settings",
                        fontSize = 12.sp,
                        color = StatusRed,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun CloudSyncCard(
    isCloudSyncEnabled: Boolean,
    pendingCount: Int,
    lastSyncedTime: Long?,
    onToggleSync: (Boolean) -> Unit,
    onSyncNow: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SubtleBorder, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = MainBackground),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Cloud Sync (Firestore)", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
                    Text(
                        text = if (isCloudSyncEnabled) "Uploading records when connected" else "Cloud sync is paused",
                        fontSize = 13.sp,
                        color = SecondaryText
                    )
                }

                Switch(
                    checked = isCloudSyncEnabled,
                    onCheckedChange = onToggleSync,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = PrimaryGreen,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = SubtleBorder
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = SubtleBorder)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Pending Sync: $pendingCount records",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryText
                    )
                    Text(
                        text = "Last Synced: ${formatTimestamp(lastSyncedTime)}",
                        fontSize = 12.sp,
                        color = SecondaryText
                    )
                }

                OutlinedButton(
                    onClick = onSyncNow,
                    enabled = isCloudSyncEnabled,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryGreen),
                    border = BorderStroke(1.dp, PrimaryGreen)
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Sync Now", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun TargetAppsSection(
    selectedPackageCount: Int,
    onOpenSelectApps: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Target Apps", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
            Text("$selectedPackageCount apps selected for capture", fontSize = 13.sp, color = SecondaryText)
        }

        Button(
            onClick = onOpenSelectApps,
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = Color.White),
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Select Apps", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
    }
}

@Composable
fun DataManagementSection(
    totalNotificationCount: Int,
    onOpenHistory: () -> Unit,
    onDeleteLocal: () -> Unit,
    onDeleteCloud: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SubtleBorder, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = MainBackground),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Data & History", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PrimaryText)

            Button(
                onClick = onOpenHistory,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = Color.White),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.History, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("View Notification History ($totalNotificationCount)", fontWeight = FontWeight.Bold)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDeleteLocal,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusRed),
                    border = BorderStroke(1.dp, StatusRed)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete Local", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onDeleteCloud,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusRed),
                    border = BorderStroke(1.dp, StatusRed)
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete Cloud", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun SelectNotificationAppsDialog(
    viewModel: NotificationCaptureViewModel
) {
    val searchQuery by viewModel.appSearchQuery.collectAsState()
    val filteredApps by viewModel.filteredInstalledApps.collectAsState()
    val selectedPackages by viewModel.selectedPackages.collectAsState()

    Dialog(
        onDismissRequest = { viewModel.closeSelectAppsDialog() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(20.dp),
            color = MainBackground
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Select Apps to Capture", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
                        Text("${selectedPackages.size} selected", fontSize = 13.sp, color = SecondaryText)
                    }

                    IconButton(onClick = { viewModel.closeSelectAppsDialog() }) {
                        Icon(Icons.Default.Clear, contentDescription = "Close", tint = SecondaryText)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateAppSearchQuery(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search installed apps...", fontSize = 14.sp, color = SecondaryText) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = SecondaryText) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryGreen,
                        unfocusedBorderColor = SubtleBorder,
                        focusedContainerColor = MainBackground,
                        unfocusedContainerColor = MainBackground
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        val isSelected = selectedPackages.contains(app.packageName)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { viewModel.togglePackageSelection(app.packageName) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                AppIconImage(drawable = app.icon, contentDescription = app.label, modifier = Modifier.size(40.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(app.label, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = PrimaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(app.packageName, fontSize = 12.sp, color = SecondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }

                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { viewModel.togglePackageSelection(app.packageName) },
                                colors = CheckboxDefaults.colors(checkedColor = PrimaryGreen, checkmarkColor = Color.White, uncheckedColor = SubtleBorder)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(
                        onClick = { viewModel.closeSelectAppsDialog() },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Done (${selectedPackages.size})", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun formatTimestamp(timeMs: Long?): String {
    if (timeMs == null || timeMs == 0L) return "Never"
    val sdf = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
    return sdf.format(Date(timeMs))
}
