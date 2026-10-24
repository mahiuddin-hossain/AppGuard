package com.mahiuddin.appguard.sync

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
            Log.e(TAG, "Failed to authenticate with Firebase Auth", e)
            null
        }
    }

    suspend fun uploadNotificationRecord(
        uid: String,
        notification: CapturedNotificationEntity
    ): Boolean {
        return try {
            val docRef = firestore
                .collection("users")
                .document(uid)
                .collection("capturedNotifications")
                .document(notification.eventId)

            val payload = hashMapOf<String, Any?>(
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
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload notification ${notification.eventId} to Firestore", e)
            false
        }
    }

    suspend fun deleteUserCloudNotifications(uid: String): Result<Int> {
        return try {
            val collectionRef = firestore
                .collection("users")
                .document(uid)
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
            Log.e(TAG, "Failed to delete cloud notifications for user $uid", e)
            Result.failure(e)
        }
    }
}
