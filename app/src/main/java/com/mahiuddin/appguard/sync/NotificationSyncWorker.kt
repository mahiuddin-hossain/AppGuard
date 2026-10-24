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

            val uid = syncManager.ensureAuthenticatedUserUid()
            if (uid == null) {
                Log.w(TAG, "User authentication failed or unavailable. Scheduling retry.")
                return Result.retry()
            }

            val pendingNotifications = repository.getPendingSyncNotifications(50)
            if (pendingNotifications.isEmpty()) {
                Log.d(TAG, "No pending notifications to sync.")
                return Result.success()
            }

            Log.i(TAG, "Uploading ${pendingNotifications.size} pending notifications for user $uid")

            var hasFailures = false

            for (notification in pendingNotifications) {
                val success = syncManager.uploadNotificationRecord(uid, notification)
                if (success) {
                    repository.markAsSynced(notification.eventId, System.currentTimeMillis())
                } else {
                    hasFailures = true
                    repository.updateSyncFailure(notification.eventId, "Cloud upload failed")
                }
            }

            if (hasFailures) {
                Log.w(TAG, "Some notification records failed to upload. Retrying later.")
                Result.retry()
            } else {
                Log.i(TAG, "Successfully uploaded all pending notifications")
                Result.success()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Unhandled exception in NotificationSyncWorker", e)
            Result.retry()
        }
    }
}
