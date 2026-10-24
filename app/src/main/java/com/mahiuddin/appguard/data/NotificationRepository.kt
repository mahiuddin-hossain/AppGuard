package com.mahiuddin.appguard.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.mahiuddin.appguard.data.db.AppDatabase
import com.mahiuddin.appguard.data.db.CapturedNotificationEntity
import com.mahiuddin.appguard.sync.NotificationSyncWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.notificationDataStore: DataStore<Preferences> by preferencesDataStore(name = "notification_capture_prefs")

class NotificationRepository private constructor(private val context: Context) {

    private val dao = AppDatabase.getInstance(context).capturedNotificationDao()
    private val workManager = WorkManager.getInstance(context)

    companion object {
        private val KEY_IS_CAPTURE_ENABLED = booleanPreferencesKey("is_notification_capture_enabled")
        private val KEY_IS_CLOUD_SYNC_ENABLED = booleanPreferencesKey("is_cloud_sync_enabled")
        private val KEY_SELECTED_PACKAGES = stringSetPreferencesKey("selected_notification_packages")

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

    fun triggerSyncWork() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncWorkRequest = OneTimeWorkRequestBuilder<NotificationSyncWorker>()
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniqueWork(
            NotificationSyncWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            syncWorkRequest
        )
    }
}
