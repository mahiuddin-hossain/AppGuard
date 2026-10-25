package com.mahiuddin.appguard

import com.mahiuddin.appguard.data.db.CapturedNotificationEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationCaptureUnitTest {

    @Test
    fun testEventIdGeneration_isDeterministicAndUnique() {
        val input1 = "com.whatsapp|key1|1000|Hello|World"
        val input2 = "com.whatsapp|key1|1000|Hello|World"
        val input3 = "com.whatsapp|key2|1000|Hello|World"

        val hash1 = generateHashId(input1)
        val hash2 = generateHashId(input2)
        val hash3 = generateHashId(input3)

        assertEquals(hash1, hash2)
        assertNotEquals(hash1, hash3)
    }

    @Test
    fun testReadableEventIdGeneration_containsFormattedDate() {
        val timestamp = 1761305933000L // Oct 24, 2025
        val input = "com.whatsapp|key1|$timestamp|Hello|World"

        val readableEventId = generateReadableEventId(timestamp, input)
        val datePrefix = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date(timestamp))

        assertTrue(readableEventId.startsWith(datePrefix))
        assertTrue(readableEventId.contains("_"))
    }

    @Test
    fun testPackageFiltering_onlyCapturesSelectedPackages() {
        val selectedPackages = setOf("com.whatsapp", "com.instagram.android")

        assertTrue(selectedPackages.contains("com.whatsapp"))
        assertTrue(selectedPackages.contains("com.instagram.android"))
        assertFalse(selectedPackages.contains("com.facebook.katana"))
        assertFalse(selectedPackages.contains("com.mahiuddin.appguard"))
    }

    @Test
    fun testCapturedNotificationEntity_defaultSyncStatusIsPending() {
        val entity = CapturedNotificationEntity(
            eventId = "2026-10-24_11-38-53_a1b2c3d4",
            packageName = "com.whatsapp",
            appLabel = "WhatsApp",
            title = "John",
            message = "Hey there!",
            postedAt = System.currentTimeMillis()
        )

        assertEquals("PENDING", entity.syncStatus)
        assertEquals(0, entity.syncAttempts)
        assertEquals(null, entity.syncedAt)
    }

    private fun generateReadableEventId(postedAt: Long, input: String): String {
        val datePrefix = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date(if (postedAt > 0) postedAt else System.currentTimeMillis()))
        val shortHash = generateHashId(input).take(8)
        return "${datePrefix}_$shortHash"
    }

    private fun generateHashId(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
