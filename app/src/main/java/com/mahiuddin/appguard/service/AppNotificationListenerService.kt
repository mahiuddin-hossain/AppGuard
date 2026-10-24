package com.mahiuddin.appguard.service

import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.google.gson.Gson
import com.mahiuddin.appguard.data.InstalledAppsRepository
import com.mahiuddin.appguard.data.NotificationRepository
import com.mahiuddin.appguard.data.db.CapturedNotificationEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.security.MessageDigest

class AppNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()

    @Volatile
    private var isCaptureEnabled: Boolean = false

    @Volatile
    private var selectedPackages: Set<String> = emptySet()

    private lateinit var repository: NotificationRepository
    private lateinit var installedAppsRepository: InstalledAppsRepository

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate: Initializing AppNotificationListenerService")

        repository = NotificationRepository.getInstance(applicationContext)
        installedAppsRepository = InstalledAppsRepository(applicationContext)

        loadInitialStateSynchronously()

        serviceScope.launch {
            try {
                repository.isCaptureEnabledFlow.collect { enabled ->
                    Log.d(TAG, "isCaptureEnabledFlow emitted enabled=$enabled")
                    isCaptureEnabled = enabled
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error collecting isCaptureEnabledFlow", e)
            }
        }

        serviceScope.launch {
            try {
                repository.selectedPackagesFlow.collect { packages ->
                    Log.d(TAG, "selectedPackagesFlow emitted ${packages.size} packages")
                    selectedPackages = packages
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error collecting selectedPackagesFlow", e)
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "onListenerConnected: NotificationListenerService connected")
        loadInitialStateSynchronously()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "onListenerDisconnected: NotificationListenerService disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        try {
            if (!isCaptureEnabled || sbn == null) {
                return
            }

            val sourcePackage = sbn.packageName ?: return

            // Ignore notifications posted by AppGuard itself
            if (sourcePackage == packageName) {
                return
            }

            // Verify if package is selected for capture
            if (!selectedPackages.contains(sourcePackage)) {
                return
            }

            val notification = sbn.notification ?: return
            val extras = notification.extras ?: return

            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            val message = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            val conversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()

            val textLinesArray = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            val textLinesJson = if (textLinesArray != null && textLinesArray.isNotEmpty()) {
                gson.toJson(textLinesArray.map { it.toString() })
            } else null

            val senderName = extras.getCharSequence("android.messagingContract.sender")?.toString()
                ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()

            val category = notification.category
            val channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                notification.channelId
            } else null
            val notificationKey = sbn.key
            val postedAt = sbn.postTime
            val isGroupSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0

            val appInfo = installedAppsRepository.getCachedOrFetchAppInfo(sourcePackage)
            val appLabel = appInfo.label

            val rawIdentity = "$sourcePackage|$notificationKey|$postedAt|$title|$message"
            val eventId = generateHashId(rawIdentity)

            val entity = CapturedNotificationEntity(
                eventId = eventId,
                packageName = sourcePackage,
                appLabel = appLabel,
                title = title,
                message = message,
                bigText = bigText,
                textLinesJson = textLinesJson,
                conversationTitle = conversationTitle,
                senderName = senderName,
                category = category,
                channelId = channelId,
                notificationKey = notificationKey,
                postedAt = postedAt,
                capturedAt = System.currentTimeMillis(),
                isGroupSummary = isGroupSummary
            )

            serviceScope.launch {
                try {
                    val result = repository.insertNotification(entity)
                    if (result > 0) {
                        Log.d(TAG, "Successfully captured & persisted notification for $sourcePackage (eventId: $eventId)")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to persist captured notification", e)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error handling notification event", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "onDestroy: AppNotificationListenerService destroying")
        serviceScope.cancel()
    }

    private fun loadInitialStateSynchronously() {
        try {
            runBlocking(Dispatchers.IO) {
                isCaptureEnabled = repository.isCaptureEnabledFlow.first()
                selectedPackages = repository.selectedPackagesFlow.first()
            }
            Log.d(TAG, "Loaded initial state: isCaptureEnabled=$isCaptureEnabled, selectedPackagesCount=${selectedPackages.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading initial DataStore state for NotificationListener", e)
        }
    }

    private fun generateHashId(input: String): String {
        return try {
            val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            bytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            input.hashCode().toString()
        }
    }

    companion object {
        private const val TAG = "NotificationListener"

        fun isNotificationListenerPermissionGranted(context: Context): Boolean {
            val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(context)
            return enabledPackages.contains(context.packageName)
        }
    }
}
