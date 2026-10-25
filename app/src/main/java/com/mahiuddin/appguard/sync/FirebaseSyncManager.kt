package com.mahiuddin.appguard.sync

import android.os.Build
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.mahiuddin.appguard.data.db.CapturedNotificationEntity
import kotlinx.coroutines.tasks.await

class FirebaseSyncManager private constructor() {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    companion object {
        private const val TAG = "FirebaseSyncManager"

        @Volatile
        private var INSTANCE: FirebaseSyncManager? = null

        fun getInstance(): FirebaseSyncManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FirebaseSyncManager().also { INSTANCE = it }
            }
        }

        fun getSanitizedDeviceName(): String {
            val manufacturer = Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            val model = Build.MODEL
            val rawName = if (model.lowercase().startsWith(manufacturer.lowercase())) {
                model
            } else {
                "$manufacturer $model"
            }
            val sanitized = rawName.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").trim('_')
            return if (sanitized.isNotBlank()) sanitized else "Android_Device"
        }

        fun getDisplayDeviceName(): String {
            val manufacturer = Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            val model = Build.MODEL
            return if (model.lowercase().startsWith(manufacturer.lowercase())) {
                model
            } else {
                "$manufacturer $model"
            }
        }
    }

    suspend fun ensureAuthenticatedUserUid(): String? {
        return try {
            val currentUser = auth.currentUser
            if (currentUser != null) {
                currentUser.uid
            } else {
                val authResult = auth.signInAnonymously().await()
                authResult.user?.uid
            }
        } catch (e: Exception) {
            Log.w(TAG, "Anonymous auth disabled or unavailable: ${e.localizedMessage}")
            null
        }
    }

    suspend fun uploadNotificationRecord(
        notification: CapturedNotificationEntity
    ): Boolean {
        return try {
            val deviceKey = getSanitizedDeviceName()
            val displayDeviceName = getDisplayDeviceName()

            val docRef = firestore
                .collection("devices")
                .document(deviceKey)
                .collection("capturedNotifications")
                .document(notification.eventId)

            val payload = hashMapOf<String, Any?>(
                "deviceName" to displayDeviceName,
                "deviceKey" to deviceKey,
                "manufacturer" to Build.MANUFACTURER,
                "model" to Build.MODEL,
                "eventId" to notification.eventId,
                "packageName" to notification.packageName,
                "appLabel" to notification.appLabel,
                "title" to notification.title,
                "message" to notification.message,
                "bigText" to notification.bigText,
                "textLinesJson" to notification.textLinesJson,
                "conversationTitle" to notification.conversationTitle,
                "senderName" to notification.senderName,
                "category" to notification.category,
                "channelId" to notification.channelId,
                "postedAt" to notification.postedAt,
                "capturedAt" to notification.capturedAt,
                "isGroupSummary" to notification.isGroupSummary,
                "syncedAt" to FieldValue.serverTimestamp()
            )

            docRef.set(payload, SetOptions.merge()).await()
            Log.d(TAG, "Successfully uploaded document ${notification.eventId} to devices/$deviceKey/capturedNotifications")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload notification ${notification.eventId} to Firestore: ${e.localizedMessage}", e)
            false
        }
    }

    suspend fun deleteDeviceCloudNotifications(): Result<Int> {
        return try {
            val deviceKey = getSanitizedDeviceName()
            val collectionRef = firestore
                .collection("devices")
                .document(deviceKey)
                .collection("capturedNotifications")

            val snapshot = collectionRef.get().await()
            var deletedCount = 0

            val batch = firestore.batch()
            for (doc in snapshot.documents) {
                batch.delete(doc.reference)
                deletedCount++
            }

            if (deletedCount > 0) {
                batch.commit().await()
            }

            Result.success(deletedCount)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete cloud notifications for device: ${e.localizedMessage}", e)
            Result.failure(e)
        }
    }
}
