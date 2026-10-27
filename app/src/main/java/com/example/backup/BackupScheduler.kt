package com.example.backup

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.data.local.AppDatabase
import com.example.data.local.model.UploadState
import com.example.data.preferences.BackupPreferencesRepository
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class BackupScheduler(private val context: Context) {
    private val workManager = WorkManager.getInstance(context)
    private val database = AppDatabase.getInstance(context)
    private val preferencesRepository = BackupPreferencesRepository(context)

    suspend fun scheduleUpload(mediaId: Long, force: Boolean = false) {
        val prefs = preferencesRepository.preferencesFlow.first()
        if (!force && !prefs.autoBackupEnabled) {
            return
        }

        val requiredNetwork = if (force) {
            NetworkType.CONNECTED
        } else if (prefs.wifiOnly) {
            NetworkType.UNMETERED
        } else {
            NetworkType.CONNECTED
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(requiredNetwork)
            .setRequiresCharging(!force && prefs.backupWhileCharging)
            .build()

        val uploadRequest = OneTimeWorkRequestBuilder<DriveUploadWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(DriveUploadWorker.KEY_MEDIA_ID to mediaId))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .addTag("upload_tag_$mediaId")
            .build()

        database.mediaDao().updateProgress(mediaId, 0, UploadState.QUEUED)
        workManager.enqueueUniqueWork(
            "work_upload_$mediaId",
            ExistingWorkPolicy.REPLACE,
            uploadRequest
        )
    }

    suspend fun scheduleAllPending(force: Boolean = false) {
        val prefs = preferencesRepository.preferencesFlow.first()
        if (!force && !prefs.autoBackupEnabled) {
            return
        }

        val requiredNetwork = if (force) {
            NetworkType.CONNECTED
        } else if (prefs.wifiOnly) {
            NetworkType.UNMETERED
        } else {
            NetworkType.CONNECTED
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(requiredNetwork)
            .setRequiresCharging(!force && prefs.backupWhileCharging)
            .build()

        val uploadRequest = OneTimeWorkRequestBuilder<DriveUploadWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniqueWork(
            "work_upload_all_pending",
            ExistingWorkPolicy.KEEP,
            uploadRequest
        )
    }

    suspend fun retryUpload(mediaId: Long, force: Boolean = true) {
        database.mediaDao().retryItem(mediaId)
        scheduleUpload(mediaId, force = force)
    }

    suspend fun retryAllFailed(accountEmail: String) {
        database.mediaDao().retryAllFailedForAccount(accountEmail)
        scheduleAllPending(force = true)
    }

    suspend fun cancelUpload(mediaId: Long) {
        database.mediaDao().cancelItem(mediaId)
        workManager.cancelUniqueWork("work_upload_$mediaId")
    }
}
