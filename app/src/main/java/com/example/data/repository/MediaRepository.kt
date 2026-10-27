package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.auth.GoogleAuthManager
import com.example.backup.BackupScheduler
import com.example.data.local.AppDatabase
import com.example.data.local.entity.GoogleAccountEntity
import com.example.data.local.entity.MediaItemEntity
import com.example.data.local.model.UploadState
import com.example.data.preferences.BackupPreferences
import com.example.data.preferences.BackupPreferencesRepository
import com.example.drive.DriveApiService
import com.example.drive.model.DriveFileResult
import com.example.drive.model.QuotaInfo
import com.example.util.MediaFileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MediaRepository(
    private val context: Context,
    private val database: AppDatabase = AppDatabase.getInstance(context),
    val preferencesRepository: BackupPreferencesRepository = BackupPreferencesRepository(context),
    val backupScheduler: BackupScheduler = BackupScheduler(context),
    val authManager: GoogleAuthManager = GoogleAuthManager(context, database.accountDao()),
    private val driveApiService: DriveApiService = DriveApiService()
) {
    val mediaDao = database.mediaDao()
    val accountDao = database.accountDao()

    val activeAccount: Flow<GoogleAccountEntity?> = accountDao.getActiveAccount()
    val allAccounts: Flow<List<GoogleAccountEntity>> = accountDao.getAllAccounts()
    val preferences: Flow<BackupPreferences> = preferencesRepository.preferencesFlow

    val allMedia: Flow<List<MediaItemEntity>> = mediaDao.getAllMedia()

    val activeAccountMedia: Flow<List<MediaItemEntity>> = activeAccount.flatMapLatest { account ->
        if (account != null) {
            mediaDao.getMediaByAccount(account.accountEmail)
        } else {
            mediaDao.getAllMedia()
        }
    }

    val waitingCount: Flow<Int> = activeAccount.flatMapLatest { account ->
        if (account != null) mediaDao.getWaitingCount(account.accountEmail) else mediaDao.getTotalWaitingCount()
    }

    val uploadingCount: Flow<Int> = activeAccount.flatMapLatest { account ->
        if (account != null) mediaDao.getUploadingCount(account.accountEmail) else flowOf(0)
    }

    val uploadedCount: Flow<Int> = activeAccount.flatMapLatest { account ->
        if (account != null) mediaDao.getUploadedCount(account.accountEmail) else flowOf(0)
    }

    val failedCount: Flow<Int> = activeAccount.flatMapLatest { account ->
        if (account != null) mediaDao.getFailedCount(account.accountEmail) else flowOf(0)
    }

    val lastSuccessfulUploadTime: Flow<Long?> = activeAccount.flatMapLatest { account ->
        if (account != null) mediaDao.getLastSuccessfulUploadTime(account.accountEmail) else flowOf(null)
    }

    val cleanableCount: Flow<Int> = mediaDao.getCleanableCount()
    val cleanableBytes: Flow<Long?> = mediaDao.getCleanableBytes()
    val pendingBytes: Flow<Long?> = mediaDao.getPendingBytes()

    suspend fun saveCapturedMedia(
        file: File,
        uri: Uri,
        mediaType: String,
        targetFolderId: String? = null,
        forceUpload: Boolean = false,
        targetAccountId: String? = null,
        sourceApp: String? = null
    ): Result<Long> = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() == 0L) {
            return@withContext Result.failure(IllegalArgumentException("Captured file is missing or empty"))
        }

        val active = accountDao.getActiveAccountSync()
        val accountEmail = targetAccountId ?: active?.accountEmail ?: "unassigned"

        val mimeType = if (mediaType == "VIDEO") "video/mp4" else "image/jpeg"
        val entity = MediaItemEntity(
            localUri = uri.toString(),
            localFilePath = file.absolutePath,
            fileName = file.name,
            mimeType = mimeType,
            fileSize = file.length(),
            captureTimestamp = System.currentTimeMillis(),
            mediaType = mediaType,
            targetAccountId = accountEmail,
            uploadState = UploadState.LOCAL_SAVED,
            targetFolderId = targetFolderId,
            sourceApp = sourceApp
        )

        val id = mediaDao.insert(entity)

        val prefs = preferences.first()
        val isTypeAllowed = if (mediaType == "VIDEO") prefs.backupVideos else prefs.backupPhotos

        if (forceUpload) {
            backupScheduler.scheduleUpload(id, force = true)
        } else if (prefs.autoBackupEnabled && (active != null || accountEmail != "unassigned") && isTypeAllowed) {
            backupScheduler.scheduleUpload(id, force = false)
        }

        Result.success(id)
    }

    suspend fun importUserSelectedMedia(
        sourceUri: Uri,
        targetFolderId: String? = null,
        forceUpload: Boolean = false,
        targetAccountId: String? = null,
        sourceApp: String? = null
    ): Result<Long> = withContext(Dispatchers.IO) {
        val targetFile = MediaFileManager.copyUriToLocalFile(context, sourceUri)
            ?: return@withContext Result.failure(Exception("Failed to copy chosen media to local storage"))

        val localUri = MediaFileManager.getFileUri(context, targetFile)
        val mimeType = context.contentResolver.getType(sourceUri) ?: "image/jpeg"
        val mediaType = if (mimeType.startsWith("video/")) "VIDEO" else "PHOTO"

        saveCapturedMedia(
            file = targetFile,
            uri = localUri,
            mediaType = mediaType,
            targetFolderId = targetFolderId,
            forceUpload = forceUpload,
            targetAccountId = targetAccountId,
            sourceApp = sourceApp
        )
    }

    suspend fun importDocument(
        sourceUri: Uri,
        targetFolderId: String? = null,
        targetAccountId: String? = null
    ): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            var fileName = "Document_${System.currentTimeMillis()}"
            var fileSize = 0L

            resolver.query(sourceUri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) fileName = cursor.getString(nameIndex) ?: fileName
                    if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                }
            }

            val storageDir = File(context.filesDir, "Documents").apply { mkdirs() }
            val targetFile = File(storageDir, fileName)

            resolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (!targetFile.exists() || targetFile.length() == 0L) {
                return@withContext Result.failure(Exception("Could not read document"))
            }

            val mimeType = resolver.getType(sourceUri) ?: "application/octet-stream"
            val active = accountDao.getActiveAccountSync()
            val finalEmail = targetAccountId ?: active?.accountEmail ?: "unassigned"

            val entity = MediaItemEntity(
                localUri = MediaFileManager.getFileUri(context, targetFile).toString(),
                localFilePath = targetFile.absolutePath,
                fileName = targetFile.name,
                mimeType = mimeType,
                fileSize = targetFile.length(),
                captureTimestamp = System.currentTimeMillis(),
                mediaType = "DOCUMENT",
                targetAccountId = finalEmail,
                uploadState = UploadState.LOCAL_SAVED,
                targetFolderId = targetFolderId
            )

            val id = mediaDao.insert(entity)
            backupScheduler.scheduleUpload(id, force = true)
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadMediaDirectly(
        mediaId: Long,
        targetFolderId: String? = null,
        targetAccountId: String? = null
    ) = withContext(Dispatchers.IO) {
        val active = accountDao.getActiveAccountSync()
        val finalAccount = targetAccountId ?: active?.accountEmail ?: "unassigned"
        mediaDao.updateTargetDestination(mediaId, targetFolderId, finalAccount)
        backupScheduler.retryUpload(mediaId, force = true)
    }

    suspend fun refreshQuotaForActiveAccount(): Result<QuotaInfo> = withContext(Dispatchers.IO) {
        val active = accountDao.getActiveAccountSync()
            ?: return@withContext Result.failure(Exception("No active Google account connected"))

        val tokenResult = authManager.getAccessToken(active.accountEmail)
        val token = tokenResult.getOrElse { return@withContext Result.failure(it) }

        val quotaResult = driveApiService.getStorageQuota(token)
        val quota = quotaResult.getOrElse { return@withContext Result.failure(it) }

        accountDao.updateQuota(
            email = active.accountEmail,
            totalBytes = quota.limitBytes,
            usedBytes = quota.usageBytes,
            refreshTime = System.currentTimeMillis()
        )

        Result.success(quota)
    }

    suspend fun listDriveFiles(
        category: String? = null,
        folderId: String? = null
    ): Result<List<DriveFileResult>> = withContext(Dispatchers.IO) {
        val active = accountDao.getActiveAccountSync()
            ?: return@withContext Result.failure(Exception("No active Google account connected"))

        val tokenResult = authManager.getAccessToken(active.accountEmail)
        val token = tokenResult.getOrElse { return@withContext Result.failure(it) }

        driveApiService.listFiles(accessToken = token, folderId = folderId, category = category)
    }

    suspend fun createDriveFolder(
        folderName: String,
        parentFolderId: String? = null
    ): Result<DriveFileResult> = withContext(Dispatchers.IO) {
        val active = accountDao.getActiveAccountSync()
            ?: return@withContext Result.failure(Exception("No active Google account connected"))

        val tokenResult = authManager.getAccessToken(active.accountEmail)
        val token = tokenResult.getOrElse { return@withContext Result.failure(it) }

        driveApiService.createFolder(
            accessToken = token,
            folderName = folderName,
            parentFolderId = parentFolderId
        )
    }

    suspend fun switchActiveAccount(email: String) = withContext(Dispatchers.IO) {
        accountDao.setActiveAccount(email)
        refreshQuotaForActiveAccount()
    }

    suspend fun disconnectAccount(email: String) = withContext(Dispatchers.IO) {
        authManager.signOut(email)
    }

    suspend fun retryUpload(id: Long) {
        backupScheduler.retryUpload(id, force = true)
    }

    suspend fun retryAllFailed() = withContext(Dispatchers.IO) {
        val active = accountDao.getActiveAccountSync() ?: return@withContext
        backupScheduler.retryAllFailed(active.accountEmail)
    }

    suspend fun cancelUpload(id: Long) {
        backupScheduler.cancelUpload(id)
    }

    suspend fun deleteMedia(id: Long) = withContext(Dispatchers.IO) {
        val item = mediaDao.getById(id)
        if (item != null) {
            backupScheduler.cancelUpload(id)
            val file = File(item.localFilePath)
            if (file.exists()) {
                file.delete()
            }
            mediaDao.deleteById(id)
        }
    }

    suspend fun cleanUpAllVerifiedItems(): Int = withContext(Dispatchers.IO) {
        val cleanable = mediaDao.getVerifiedCleanableItems()
        var cleanedCount = 0
        for (item in cleanable) {
            try {
                val file = File(item.localFilePath)
                if (file.exists()) {
                    file.delete()
                }
                mediaDao.markLocalCleanedUp(item.id)
                cleanedCount++
            } catch (_: Exception) {}
        }
        cleanedCount
    }

    suspend fun getNonCleanableItems(): List<MediaItemEntity> = withContext(Dispatchers.IO) {
        mediaDao.getNonCleanableItems()
    }

    suspend fun trashDriveFolder(folderId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val active = accountDao.getActiveAccountSync()
            ?: return@withContext Result.failure(Exception("No active Google account connected"))
        val tokenResult = authManager.getAccessToken(active.accountEmail)
        val token = tokenResult.getOrElse { return@withContext Result.failure(it) }
        driveApiService.trashFolder(token, folderId)
    }

    suspend fun deleteDriveFile(fileId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val active = accountDao.getActiveAccountSync()
            ?: return@withContext Result.failure(Exception("No active Google account connected"))
        val tokenResult = authManager.getAccessToken(active.accountEmail)
        val token = tokenResult.getOrElse { return@withContext Result.failure(it) }
        driveApiService.deleteFile(token, fileId)
    }

    suspend fun clearCache() = withContext(Dispatchers.IO) {
        val cacheDir = context.cacheDir
        cacheDir.deleteRecursively()
    }
}
