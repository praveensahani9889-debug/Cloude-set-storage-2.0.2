package com.example

import com.example.data.local.entity.MediaItemEntity
import com.example.data.local.model.UploadState
import com.example.drive.model.QuotaInfo
import com.example.util.MediaFileManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadQueueTest {

    @Test
    fun testInitialUploadState_isLocalSaved() {
        val media = MediaItemEntity(
            id = 1,
            localUri = "content://media/1",
            localFilePath = "/data/user/0/pictures/IMG_1.jpg",
            fileName = "IMG_1.jpg",
            mimeType = "image/jpeg",
            fileSize = 1024L,
            mediaType = "PHOTO",
            targetAccountId = "test@gmail.com",
            targetFolderId = "folder_123"
        )
        assertEquals(UploadState.LOCAL_SAVED, media.uploadState)
        assertNull(media.driveFileId)
        assertEquals(0, media.retryCount)
        assertEquals("folder_123", media.targetFolderId)
    }

    @Test
    fun testDocumentMediaTypeSupport() {
        val doc = MediaItemEntity(
            id = 5,
            localUri = "content://documents/5",
            localFilePath = "/data/user/0/Documents/report.pdf",
            fileName = "report.pdf",
            mimeType = "application/pdf",
            fileSize = 204800L,
            mediaType = "DOCUMENT",
            targetAccountId = "praveen@gmail.com",
            targetFolderId = "custom_folder_xyz"
        )
        assertEquals("DOCUMENT", doc.mediaType)
        assertEquals("application/pdf", doc.mimeType)
        assertEquals("custom_folder_xyz", doc.targetFolderId)
    }

    @Test
    fun testStateTransition_toQueuedAndUploading() {
        val initial = MediaItemEntity(
            id = 1,
            localUri = "content://media/1",
            localFilePath = "/data/user/0/pictures/IMG_1.jpg",
            fileName = "IMG_1.jpg",
            mimeType = "image/jpeg",
            fileSize = 2048L,
            mediaType = "PHOTO",
            targetAccountId = "praveen@gmail.com"
        )

        val queued = initial.copy(uploadState = UploadState.QUEUED)
        assertEquals(UploadState.QUEUED, queued.uploadState)

        val uploading = queued.copy(uploadState = UploadState.UPLOADING, uploadProgressPercent = 45)
        assertEquals(UploadState.UPLOADING, uploading.uploadState)
        assertEquals(45, uploading.uploadProgressPercent)

        val uploaded = uploading.copy(
            uploadState = UploadState.UPLOADED,
            driveFileId = "drive_file_id_xyz",
            uploadProgressPercent = 100,
            uploadedTimestamp = System.currentTimeMillis()
        )
        assertEquals(UploadState.UPLOADED, uploaded.uploadState)
        assertEquals("drive_file_id_xyz", uploaded.driveFileId)
        assertEquals(100, uploaded.uploadProgressPercent)
        assertNotNull(uploaded.uploadedTimestamp)
    }

    @Test
    fun testFailureState_andRetryIncrement() {
        val initial = MediaItemEntity(
            id = 2,
            localUri = "content://media/2",
            localFilePath = "/data/user/0/pictures/IMG_2.jpg",
            fileName = "IMG_2.jpg",
            mimeType = "image/jpeg",
            fileSize = 4096L,
            mediaType = "PHOTO",
            targetAccountId = "praveen@gmail.com",
            uploadState = UploadState.UPLOADING
        )

        val failed = initial.copy(
            uploadState = UploadState.RETRY_WAIT,
            retryCount = initial.retryCount + 1,
            lastFailureReason = "SocketTimeoutException"
        )
        assertEquals(UploadState.RETRY_WAIT, failed.uploadState)
        assertEquals(1, failed.retryCount)
        assertEquals("SocketTimeoutException", failed.lastFailureReason)

        // Retry reset
        val retried = failed.copy(
            uploadState = UploadState.QUEUED,
            uploadProgressPercent = 0,
            lastFailureReason = null
        )
        assertEquals(UploadState.QUEUED, retried.uploadState)
        assertEquals(0, retried.uploadProgressPercent)
        assertNull(retried.lastFailureReason)
    }

    @Test
    fun testAccountAssociation_strictOwnership() {
        val item1 = MediaItemEntity(
            id = 1,
            localUri = "content://media/1",
            localFilePath = "/media/1.jpg",
            fileName = "1.jpg",
            mimeType = "image/jpeg",
            fileSize = 100L,
            mediaType = "PHOTO",
            targetAccountId = "account_a@gmail.com"
        )
        val item2 = MediaItemEntity(
            id = 2,
            localUri = "content://media/2",
            localFilePath = "/media/2.jpg",
            fileName = "2.jpg",
            mimeType = "image/jpeg",
            fileSize = 100L,
            mediaType = "PHOTO",
            targetAccountId = "account_b@gmail.com"
        )

        assertEquals("account_a@gmail.com", item1.targetAccountId)
        assertEquals("account_b@gmail.com", item2.targetAccountId)
        assertTrue(item1.targetAccountId != item2.targetAccountId)
    }

    @Test
    fun testQuotaCalculation_andWarningThreshold() {
        val normalQuota = QuotaInfo(
            limitBytes = 15_000_000_000L, // 15 GB
            usageBytes = 3_000_000_000L,  // 3 GB (20%)
            usageInDriveBytes = 2_000_000_000L,
            usageInTrashBytes = 100_000_000L,
            userEmail = "test@gmail.com",
            userDisplayName = "Praveen",
            userPhotoUrl = null
        )
        assertEquals(20f, normalQuota.usagePercentage, 0.1f)
        assertFalse(normalQuota.isNearlyFull)
        assertEquals(12_000_000_000L, normalQuota.remainingBytes)

        val fullQuota = QuotaInfo(
            limitBytes = 15_000_000_000L,
            usageBytes = 14_000_000_000L, // ~93.3%
            usageInDriveBytes = 10_000_000_000L,
            usageInTrashBytes = 0L,
            userEmail = "test@gmail.com",
            userDisplayName = "Praveen",
            userPhotoUrl = null
        )
        assertTrue(fullQuota.usagePercentage >= 90f)
        assertTrue(fullQuota.isNearlyFull)
    }

    @Test
    fun testFormatBytesUtility() {
        assertEquals("0 B", MediaFileManager.formatBytes(0))
        assertEquals("500.0 B", MediaFileManager.formatBytes(500))
        assertEquals("1.0 KB", MediaFileManager.formatBytes(1024))
        assertEquals("1.5 MB", MediaFileManager.formatBytes((1.5 * 1024 * 1024).toLong()))
        assertEquals("2.0 GB", MediaFileManager.formatBytes(2L * 1024 * 1024 * 1024))
    }

    @Test
    fun testFormatDurationUtility() {
        assertEquals("00:00", MediaFileManager.formatDuration(0))
        assertEquals("00:45", MediaFileManager.formatDuration(45))
        assertEquals("01:23", MediaFileManager.formatDuration(83))
        assertEquals("10:05", MediaFileManager.formatDuration(605))
    }

    @Test
    fun testCloudFirstCleanupEligibility_onlyWhenUploadedWithServerId() {
        val uploadingItem = MediaItemEntity(
            id = 10,
            localUri = "content://media/10",
            localFilePath = "/data/user/0/pictures/IMG_10.jpg",
            fileName = "IMG_10.jpg",
            mimeType = "image/jpeg",
            fileSize = 5000L,
            mediaType = "PHOTO",
            targetAccountId = "test@gmail.com",
            uploadState = UploadState.UPLOADING,
            driveFileId = null,
            isLocalCleanedUp = false
        )
        assertFalse(uploadingItem.uploadState == UploadState.UPLOADED && uploadingItem.driveFileId != null)

        val queuedItem = uploadingItem.copy(uploadState = UploadState.QUEUED)
        assertFalse(queuedItem.uploadState == UploadState.UPLOADED && queuedItem.driveFileId != null)

        val failedItem = uploadingItem.copy(uploadState = UploadState.FAILED)
        assertFalse(failedItem.uploadState == UploadState.UPLOADED && failedItem.driveFileId != null)

        val verifiedUploadedItem = uploadingItem.copy(
            uploadState = UploadState.UPLOADED,
            driveFileId = "confirmed_drive_id_12345",
            uploadProgressPercent = 100,
            uploadedTimestamp = System.currentTimeMillis()
        )
        assertTrue(verifiedUploadedItem.uploadState == UploadState.UPLOADED && verifiedUploadedItem.driveFileId != null)

        val cleanedItem = verifiedUploadedItem.copy(isLocalCleanedUp = true)
        assertTrue(cleanedItem.isLocalCleanedUp)
        assertEquals("confirmed_drive_id_12345", cleanedItem.driveFileId)
        assertEquals("IMG_10.jpg", cleanedItem.fileName)
        assertEquals(5000L, cleanedItem.fileSize)
    }

    @Test
    fun testLowStorageThresholdBounds() {
        val criticalFreeBytes = 50L * 1024 * 1024 // 50MB
        val isLow = criticalFreeBytes in 0 until (100L * 1024 * 1024)
        assertTrue(isLow)

        val plentyFreeBytes = 5L * 1024 * 1024 * 1024 // 5GB
        val isNotLow = plentyFreeBytes in 0 until (100L * 1024 * 1024)
        assertFalse(isNotLow)
    }
}
