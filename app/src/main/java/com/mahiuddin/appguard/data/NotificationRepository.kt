package com.mahiuddin.appguard.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.mahiuddin.appguard.data.db.AppDatabase
import com.mahiuddin.appguard.data.db.CapturedNotificationEntity
import com.mahiuddin.appguard.sync.FirebaseSyncManager
import com.mahiuddin.appguard.sync.NotificationSyncWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.concurrent.TimeUnit

private val Context.notificationDataStore: DataStore<Preferences> by preferencesDataStore(name = "notification_capture_prefs")

class NotificationRepository private constructor(private val context: Context) {

    private val dao = AppDatabase.getInstance(context).capturedNotificationDao()
    private val workManager = WorkManager.getInstance(context)

    init {
        registerNetworkCallback()
        schedulePeriodicSyncWork()
    }

    companion object {
        private val KEY_IS_CAPTURE_ENABLED = booleanPreferencesKey("is_notification_capture_enabled")
        private val KEY_IS_CAPTURE_ALL_APPS_ENABLED = booleanPreferencesKey("is_capture_all_apps_enabled")
        private val KEY_IS_CLOUD_SYNC_ENABLED = booleanPreferencesKey("is_cloud_sync_enabled")
        private val KEY_SELECTED_PACKAGES = stringSetPreferencesKey("selected_notification_packages")

        private const val TAG = "NotificationRepo"
        const val PERIODIC_WORK_NAME = "notification_periodic_cloud_sync_work"

        @Volatile
        private var INSTANCE: NotificationRepository? = null

        fun getInstance(context: Context): NotificationRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NotificationRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    val isCaptureEnabledFlow: Flow<Boolean> = context.notificationDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> preferences[KEY_IS_CAPTURE_ENABLED] ?: false }

    val isCaptureAllAppsEnabledFlow: Flow<Boolean> = context.notificationDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> preferences[KEY_IS_CAPTURE_ALL_APPS_ENABLED] ?: false }

    val isCloudSyncEnabledFlow: Flow<Boolean> = context.notificationDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> preferences[KEY_IS_CLOUD_SYNC_ENABLED] ?: true }

    val selectedPackagesFlow: Flow<Set<String>> = context.notificationDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> preferences[KEY_SELECTED_PACKAGES] ?: emptySet() }

    val pendingSyncCountFlow: Flow<Int> = dao.getPendingCountFlow()
    val lastSyncedTimeFlow: Flow<Long?> = dao.getLastSyncedTimeFlow()
    val totalCountFlow: Flow<Int> = dao.getTotalCountFlow()

    suspend fun setCaptureEnabled(enabled: Boolean) {
        context.notificationDataStore.edit { preferences ->
            preferences[KEY_IS_CAPTURE_ENABLED] = enabled
        }
    }

    suspend fun setCaptureAllAppsEnabled(enabled: Boolean) {
        context.notificationDataStore.edit { preferences ->
            preferences[KEY_IS_CAPTURE_ALL_APPS_ENABLED] = enabled
        }
    }

    suspend fun setCloudSyncEnabled(enabled: Boolean) {
        context.notificationDataStore.edit { preferences ->
            preferences[KEY_IS_CLOUD_SYNC_ENABLED] = enabled
        }
        if (enabled) {
            triggerSyncWork()
        }
    }

    suspend fun setSelectedPackages(packages: Set<String>) {
        context.notificationDataStore.edit { preferences ->
            preferences[KEY_SELECTED_PACKAGES] = packages
        }
    }

    suspend fun togglePackageSelection(packageName: String) {
        context.notificationDataStore.edit { preferences ->
            val current = preferences[KEY_SELECTED_PACKAGES] ?: emptySet()
            if (current.contains(packageName)) {
                preferences[KEY_SELECTED_PACKAGES] = current - packageName
            } else {
                preferences[KEY_SELECTED_PACKAGES] = current + packageName
            }
        }
    }

    suspend fun insertNotification(notification: CapturedNotificationEntity): Long {
        val result = dao.insertOrIgnore(notification)
        if (result > 0) {
            triggerSyncWork()
        }
        return result
    }

    suspend fun getPendingSyncNotifications(limit: Int = 50): List<CapturedNotificationEntity> {
        return dao.getPendingSyncNotifications(limit)
    }

    suspend fun markAsSynced(eventId: String, syncedAt: Long) {
        dao.markAsSynced(eventId, syncedAt)
    }

    suspend fun updateSyncFailure(eventId: String, errorMsg: String) {
        dao.updateSyncFailure(eventId, errorMsg)
    }

    fun getNotificationsPaged(limit: Int, offset: Int, query: String = ""): Flow<List<CapturedNotificationEntity>> {
        return if (query.isBlank()) {
            dao.getNotificationsPaged(limit, offset)
        } else {
            dao.searchNotificationsPaged(query.trim(), limit, offset)
        }
    }

    suspend fun deleteLocalHistory() {
        dao.deleteAll()
    }

    suspend fun forceSyncAllPendingNow(): Int {
        val syncManager = FirebaseSyncManager.getInstance()
        var uploadedCount = 0

        while (true) {
            val pendingList = dao.getPendingSyncNotifications(50)
            if (pendingList.isEmpty()) break

            for (notification in pendingList) {
                val success = syncManager.uploadNotificationRecord(notification)
                if (success) {
                    dao.markAsSynced(notification.eventId, System.currentTimeMillis())
                    uploadedCount++
                } else {
                    dao.updateSyncFailure(notification.eventId, "Direct sync failed")
                }
            }
        }
        return uploadedCount
    }

    fun triggerSyncWork() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncWorkRequest = OneTimeWorkRequestBuilder<NotificationSyncWorker>()
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniqueWork(
            NotificationSyncWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            syncWorkRequest
        )
    }

    fun schedulePeriodicSyncWork() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicWorkRequest = PeriodicWorkRequestBuilder<NotificationSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicWorkRequest
        )
    }

    private fun registerNetworkCallback() {
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            connectivityManager?.registerNetworkCallback(
                networkRequest,
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        Log.d(TAG, "Network connected (WiFi/Mobile Data). Triggering auto sync.")
                        triggerSyncWork()
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback", e)
        }
    }
}
