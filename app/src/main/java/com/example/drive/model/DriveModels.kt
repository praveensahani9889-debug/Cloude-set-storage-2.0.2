package com.example.drive.model

data class QuotaInfo(
    val limitBytes: Long, // -1 if unlimited or unknown
    val usageBytes: Long,
    val usageInDriveBytes: Long,
    val usageInTrashBytes: Long,
    val userEmail: String,
    val userDisplayName: String?,
    val userPhotoUrl: String?
) {
    val isUnlimited: Boolean
        get() = limitBytes <= 0

    val remainingBytes: Long
        get() = if (limitBytes > 0) (limitBytes - usageBytes).coerceAtLeast(0L) else -1L

    val usagePercentage: Float
        get() = if (limitBytes > 0) {
            ((usageBytes.toDouble() / limitBytes.toDouble()) * 100).toFloat().coerceIn(0f, 100f)
        } else {
            0f
        }

    val isNearlyFull: Boolean
        get() = usagePercentage >= 90f
}

data class DriveFileResult(
    val fileId: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long = 0L,
    val isFolder: Boolean = mimeType == "application/vnd.google-apps.folder",
    val modifiedTime: Long? = null,
    val thumbnailLink: String? = null,
    val webViewLink: String? = null,
    val parentFolderId: String? = null
)
