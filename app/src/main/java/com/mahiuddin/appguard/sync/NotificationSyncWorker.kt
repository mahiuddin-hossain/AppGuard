package com.mahiuddin.appguard.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mahiuddin.appguard.data.NotificationRepository
import kotlinx.coroutines.flow.first

class NotificationSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val repository = NotificationRepository.getInstance(appContext)
    private val syncManager = FirebaseSyncManager.getInstance()

    companion object {
        const val WORK_NAME = "notification_cloud_sync_work"
        private const val TAG = "NotificationSyncWorker"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "NotificationSyncWorker started execution")

        return try {
            val isSyncEnabled = repository.isCloudSyncEnabledFlow.first()
            if (!isSyncEnabled) {
                Log.d(TAG, "Cloud sync is disabled by user preference. Skipping.")
                return Result.success()
            }

            // Ensure auth if configured, but proceed with upload attempt regardless
            val uid = syncManager.ensureAuthenticatedUserUid()
            Log.d(TAG, "Auth check completed (UID: $uid)")

            var totalUploaded = 0
            var hasFailures = false
            val deviceName = FirebaseSyncManager.getDisplayDeviceName()

            while (true) {
                val pendingNotifications = repository.getPendingSyncNotifications(50)
                if (pendingNotifications.isEmpty()) {
                    break
                }

                Log.i(TAG, "Processing batch of ${pendingNotifications.size} unsynced notifications for device [$deviceName]")

                for (notification in pendingNotifications) {
                    val success = syncManager.uploadNotificationRecord(notification)
                    if (success) {
                        repository.markAsSynced(notification.eventId, System.currentTimeMillis())
                        totalUploaded++
                    } else {
                        hasFailures = true
                        repository.updateSyncFailure(notification.eventId, "Cloud upload failed")
                    }
                }

                if (hasFailures) {
                    break
                }
            }

            if (hasFailures) {
                Log.w(TAG, "Some notification records failed to upload to Firestore. Retrying later.")
                Result.retry()
            } else {
                Log.i(TAG, "Successfully uploaded total $totalUploaded pending notifications for [$deviceName] to Firestore.")
                Result.success()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Unhandled exception in NotificationSyncWorker", e)
            Result.retry()
        }
    }
}
