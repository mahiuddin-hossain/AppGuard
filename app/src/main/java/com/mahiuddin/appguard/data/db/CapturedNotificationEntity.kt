package com.mahiuddin.appguard.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "captured_notifications",
    indices = [
        Index(value = ["eventId"], unique = true),
        Index(value = ["syncStatus"]),
        Index(value = ["postedAt"])
    ]
)
data class CapturedNotificationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val eventId: String,
    val packageName: String,
    val appLabel: String,
    val title: String? = null,
    val message: String? = null,
    val bigText: String? = null,
    val textLinesJson: String? = null,
    val conversationTitle: String? = null,
    val senderName: String? = null,
    val category: String? = null,
    val channelId: String? = null,
    val notificationKey: String? = null,
    val postedAt: Long,
    val capturedAt: Long = System.currentTimeMillis(),
    val isGroupSummary: Boolean = false,
    val syncStatus: String = SYNC_STATUS_PENDING,
    val syncAttempts: Int = 0,
    val lastSyncError: String? = null,
    val syncedAt: Long? = null
) {
    companion object {
        const val SYNC_STATUS_PENDING = "PENDING"
        const val SYNC_STATUS_SYNCED = "SYNCED"
        const val SYNC_STATUS_FAILED = "FAILED"
    }
}
