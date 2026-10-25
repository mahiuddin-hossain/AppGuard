package com.mahiuddin.appguard.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CapturedNotificationDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOrIgnore(notification: CapturedNotificationEntity): Long

    @Query("SELECT * FROM captured_notifications WHERE syncStatus IS NULL OR syncStatus != 'SYNCED' ORDER BY postedAt ASC LIMIT :limit")
    suspend fun getPendingSyncNotifications(limit: Int = 50): List<CapturedNotificationEntity>

    @Query("UPDATE captured_notifications SET syncStatus = 'SYNCED', syncedAt = :syncedAt, lastSyncError = NULL WHERE eventId = :eventId")
    suspend fun markAsSynced(eventId: String, syncedAt: Long)

    @Query("UPDATE captured_notifications SET syncAttempts = syncAttempts + 1, lastSyncError = :errorMsg WHERE eventId = :eventId")
    suspend fun updateSyncFailure(eventId: String, errorMsg: String)

    @Query("SELECT * FROM captured_notifications ORDER BY postedAt DESC LIMIT :limit OFFSET :offset")
    fun getNotificationsPaged(limit: Int, offset: Int): Flow<List<CapturedNotificationEntity>>

    @Query("SELECT * FROM captured_notifications WHERE packageName LIKE '%' || :query || '%' OR appLabel LIKE '%' || :query || '%' OR title LIKE '%' || :query || '%' OR message LIKE '%' || :query || '%' ORDER BY postedAt DESC LIMIT :limit OFFSET :offset")
    fun searchNotificationsPaged(query: String, limit: Int, offset: Int): Flow<List<CapturedNotificationEntity>>

    @Query("SELECT COUNT(*) FROM captured_notifications WHERE syncStatus IS NULL OR syncStatus != 'SYNCED'")
    fun getPendingCountFlow(): Flow<Int>

    @Query("SELECT MAX(syncedAt) FROM captured_notifications WHERE syncStatus = 'SYNCED'")
    fun getLastSyncedTimeFlow(): Flow<Long?>

    @Query("SELECT COUNT(*) FROM captured_notifications")
    fun getTotalCountFlow(): Flow<Int>

    @Query("DELETE FROM captured_notifications")
    suspend fun deleteAll()

    @Query("DELETE FROM captured_notifications WHERE eventId IN (:eventIds)")
    suspend fun deleteByEventIds(eventIds: List<String>)
}
