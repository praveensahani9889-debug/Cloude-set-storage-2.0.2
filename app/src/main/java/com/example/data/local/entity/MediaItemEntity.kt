package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.local.model.UploadState

@Entity(
    tableName = "media_items",
    indices = [
        Index(value = ["localUri"], unique = true),
        Index(value = ["uploadState"]),
        Index(value = ["targetAccountId"])
    ]
)
data class MediaItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val localUri: String,
    val localFilePath: String,
    val fileName: String,
    val mimeType: String,
    val fileSize: Long,
    val captureTimestamp: Long = System.currentTimeMillis(),
    val mediaType: String, // "PHOTO", "VIDEO", "DOCUMENT"
    val targetAccountId: String, // Google Account Email
    val uploadState: UploadState = UploadState.LOCAL_SAVED,
    val driveFileId: String? = null,
    val uploadProgressPercent: Int = 0,
    val retryCount: Int = 0,
    val lastFailureReason: String? = null,
    val lastAttemptTimestamp: Long? = null,
    val uploadedTimestamp: Long? = null,
    val isLocalCleanedUp: Boolean = false,
    val targetFolderId: String? = null,
    val sourceApp: String? = null
)
