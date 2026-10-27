package com.example.backup

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.example.auth.GoogleAuthManager
import com.example.auth.UserRecoverableException
import com.example.data.local.AppDatabase
import com.example.data.local.model.UploadState
import com.example.drive.DriveApiException
import com.example.drive.DriveApiService
import kotlinx.coroutines.flow.first
import java.io.File

class DriveUploadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_MEDIA_ID = "key_media_id"
    }

    private val database = AppDatabase.getInstance(appContext)
    private val mediaDao = database.mediaDao()
    private val accountDao = database.accountDao()
    private val authManager = GoogleAuthManager(appContext, accountDao)
    private val driveApiService = DriveApiService()
    private val notificationManager = UploadNotificationManager(appContext)
    private val preferencesRepository = com.example.data.preferences.BackupPreferencesRepository(appContext)

    override suspend fun doWork(): Result {
        val mediaId = inputData.getLong(KEY_MEDIA_ID, -1L)
        val itemsToProcess = if (mediaId != -1L) {
            val single = mediaDao.getById(mediaId)
            if (single != null) listOf(single) else emptyList()
        } else {
            mediaDao.getAllQueuedUploads()
        }

        if (itemsToProcess.isEmpty()) {
            return Result.success()
        }

        var anyFailed = false

        for (item in itemsToProcess) {
            // Check cancellation
            if (isStopped || item.uploadState == UploadState.CANCELED) {
                continue
            }

            // Step 1: Verify local file existence & readability
            val file = File(item.localFilePath)
            if (!file.exists() || file.length() == 0L) {
                mediaDao.markFailed(
                    id = item.id,
                    reason = "Local file is missing or corrupted on disk",
                    state = UploadState.FAILED,
                    attemptTime = System.currentTimeMillis()
                )
                anyFailed = true
                continue
            }

            // Resolve target Google Account
            var targetEmail = item.targetAccountId
            if (targetEmail.isBlank() || targetEmail == "unassigned") {
                val active = accountDao.getActiveAccountSync()
                if (active != null) {
                    targetEmail = active.accountEmail
                } else {
                    mediaDao.markFailed(
                        id = item.id,
                        reason = "No connected Google Drive account",
                        state = UploadState.NEEDS_AUTH,
                        attemptTime = System.currentTimeMillis()
                    )
                    notificationManager.showAuthRequiredNotification("Google Drive")
                    anyFailed = true
                    continue
                }
            }

            // Step 2: Acquire target account OAuth token
            val tokenResult = authManager.getAccessToken(targetEmail)
            val accessToken = tokenResult.getOrElse { err ->
                val reason = when (err) {
                    is UserRecoverableException -> "User authorization required for Google Drive"
                    else -> "Authentication failed: ${err.message ?: "Invalid credentials"}"
                }
                mediaDao.markFailed(
                    id = item.id,
                    reason = reason,
                    state = UploadState.NEEDS_AUTH,
                    attemptTime = System.currentTimeMillis()
                )
                notificationManager.showAuthRequiredNotification(targetEmail)
                anyFailed = true
                return Result.failure()
            }

            // Step 3: Transition to UPLOADING and configure foreground service notification
            mediaDao.updateProgress(item.id, 0, UploadState.UPLOADING)

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val notif = notificationManager.buildProgressNotification(
                        fileName = item.fileName,
                        progress = 0,
                        total = 100,
                        mediaType = item.mediaType
                    ).build()

                    val foregroundInfo = ForegroundInfo(
                        UploadNotificationManager.NOTIFICATION_ID_PROGRESS,
                        notif,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                    setForeground(foregroundInfo)
                }
            } catch (_: Exception) {
                // Foreground service could be restricted or not needed in background
            }

            // Step 4: Perform upload with progress streaming & folder ID support
            var lastReportedPercent = 0
            val uploadResult = driveApiService.uploadFileResumable(
                accessToken = accessToken,
                file = file,
                mimeType = item.mimeType,
                fileName = item.fileName,
                folderId = item.targetFolderId
            ) { bytesWritten, totalBytes ->
                val percent = if (totalBytes > 0) ((bytesWritten * 100) / totalBytes).toInt().coerceIn(0, 99) else 0
                if (percent - lastReportedPercent >= 5) {
                    lastReportedPercent = percent
                    kotlinx.coroutines.runBlocking {
                        mediaDao.updateProgress(item.id, percent, UploadState.UPLOADING)
                    }
                }
            }

            // Step 5: Process upload result
            if (uploadResult.isSuccess) {
                val driveFile = uploadResult.getOrThrow()
                mediaDao.markUploaded(
                    id = item.id,
                    driveFileId = driveFile.fileId,
                    uploadedTime = System.currentTimeMillis()
                )

                // In Cloud-First Mode: safely remove temporary staging copy only after verified server confirmation
                try {
                    val prefs = preferencesRepository.preferencesFlow.first()
                    if (prefs.cloudFirstMode && prefs.autoCleanupVerified) {
                        if (file.exists()) {
                            file.delete()
                        }
                        mediaDao.markLocalCleanedUp(item.id)
                    }
                    if (prefs.showNotifications) {
                        notificationManager.showCompletionNotification(
                            fileName = item.fileName,
                            mediaType = item.mediaType,
                            success = true
                        )
                    }
                } catch (_: Exception) {}

                // Refresh quota metadata after successful upload
                val quotaResult = driveApiService.getStorageQuota(accessToken)
                quotaResult.getOrNull()?.let { quota ->
                    accountDao.updateQuota(
                        email = targetEmail,
                        totalBytes = quota.limitBytes,
                        usedBytes = quota.usageBytes,
                        refreshTime = System.currentTimeMillis()
                    )
                }
            } else {
                val exception = uploadResult.exceptionOrNull()
                val isApiDisabled = (exception as? DriveApiException)?.isApiDisabled == true
                val isQuota = (exception as? DriveApiException)?.isQuotaExceeded == true
                val isAuth = (exception as? DriveApiException)?.isAuthError == true

                val (failureState, failureReason) = when {
                    isApiDisabled -> {
                        val msg = (exception as? DriveApiException)?.getActionableUserMessage()
                            ?: "Google Drive API is disabled in Google Cloud Console. Enable it at console.cloud.google.com"
                        Pair(UploadState.FAILED, msg)
                    }
                    isQuota -> {
                        notificationManager.showStorageFullNotification(targetEmail)
                        Pair(UploadState.FAILED, "Google Drive storage quota exceeded")
                    }
                    isAuth -> {
                        authManager.invalidateToken(accessToken)
                        notificationManager.showAuthRequiredNotification(targetEmail)
                        Pair(UploadState.NEEDS_AUTH, "Google Drive access token expired or revoked")
                    }
                    runAttemptCount < 3 -> Pair(UploadState.RETRY_WAIT, "Network error: ${exception?.localizedMessage ?: "Unknown"}")
                    else -> Pair(UploadState.FAILED, "Upload failed after retries: ${exception?.localizedMessage ?: "Unknown"}")
                }

                mediaDao.markFailed(
                    id = item.id,
                    reason = failureReason,
                    state = failureState,
                    attemptTime = System.currentTimeMillis()
                )

                try {
                    val prefs = preferencesRepository.preferencesFlow.first()
                    if (prefs.showNotifications && failureState == UploadState.FAILED) {
                        notificationManager.showCompletionNotification(
                            fileName = item.fileName,
                            mediaType = item.mediaType,
                            success = false,
                            message = failureReason
                        )
                    }
                } catch (_: Exception) {}

                anyFailed = true
                if (failureState == UploadState.RETRY_WAIT) {
                    return Result.retry()
                }
            }
        }

        return if (anyFailed) Result.failure() else Result.success()
    }
}
