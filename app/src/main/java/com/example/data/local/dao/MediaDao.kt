package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.MediaItemEntity
import com.example.data.local.model.UploadState
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    @Query("SELECT * FROM media_items ORDER BY captureTimestamp DESC")
    fun getAllMedia(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE targetAccountId = :accountEmail ORDER BY captureTimestamp DESC")
    fun getMediaByAccount(accountEmail: String): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): MediaItemEntity?

    @Query("SELECT * FROM media_items WHERE localUri = :localUri LIMIT 1")
    suspend fun getByLocalUri(localUri: String): MediaItemEntity?

    @Query("SELECT * FROM media_items WHERE uploadState IN ('QUEUED', 'RETRY_WAIT', 'LOCAL_SAVED') AND targetAccountId = :accountEmail ORDER BY captureTimestamp ASC")
    suspend fun getPendingUploads(accountEmail: String): List<MediaItemEntity>

    @Query("SELECT * FROM media_items WHERE uploadState IN ('QUEUED', 'RETRY_WAIT') ORDER BY captureTimestamp ASC")
    suspend fun getAllQueuedUploads(): List<MediaItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: MediaItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<MediaItemEntity>): List<Long>

    @Update
    suspend fun update(item: MediaItemEntity)

    @Query("UPDATE media_items SET uploadProgressPercent = :progressPercent, uploadState = :state WHERE id = :id")
    suspend fun updateProgress(id: Long, progressPercent: Int, state: UploadState = UploadState.UPLOADING)

    @Query("UPDATE media_items SET uploadState = 'UPLOADED', driveFileId = :driveFileId, uploadProgressPercent = 100, uploadedTimestamp = :uploadedTime WHERE id = :id")
    suspend fun markUploaded(id: Long, driveFileId: String, uploadedTime: Long)

    @Query("UPDATE media_items SET uploadState = :state, lastFailureReason = :reason, lastAttemptTimestamp = :attemptTime, retryCount = retryCount + 1 WHERE id = :id")
    suspend fun markFailed(id: Long, reason: String, state: UploadState, attemptTime: Long)

    @Query("UPDATE media_items SET uploadState = 'QUEUED', uploadProgressPercent = 0, lastFailureReason = null WHERE id = :id")
    suspend fun retryItem(id: Long)

    @Query("UPDATE media_items SET uploadState = 'QUEUED', uploadProgressPercent = 0, lastFailureReason = null WHERE uploadState IN ('FAILED', 'RETRY_WAIT', 'LOCAL_SAVED') AND targetAccountId = :accountEmail")
    suspend fun retryAllFailedForAccount(accountEmail: String)

    @Query("UPDATE media_items SET targetFolderId = :folderId, targetAccountId = :accountEmail WHERE id = :id")
    suspend fun updateTargetDestination(id: Long, folderId: String?, accountEmail: String)

    @Query("UPDATE media_items SET uploadState = 'CANCELED' WHERE id = :id")
    suspend fun cancelItem(id: Long)

    @Query("DELETE FROM media_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE media_items SET localFilePath = :newPath, localUri = :newUri WHERE localFilePath = :oldPath OR localUri = :oldUri")
    suspend fun updateLocation(oldPath: String, oldUri: String, newPath: String, newUri: String)

    @Query("DELETE FROM media_items WHERE localFilePath = :filePath OR localUri = :uriString")
    suspend fun deleteByPathOrUri(filePath: String, uriString: String)

    @Query("DELETE FROM media_items WHERE targetAccountId = :accountEmail")
    suspend fun deleteByAccount(accountEmail: String)

    @Query("SELECT COUNT(*) FROM media_items WHERE uploadState IN ('QUEUED', 'LOCAL_SAVED', 'RETRY_WAIT') AND targetAccountId = :accountEmail")
    fun getWaitingCount(accountEmail: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM media_items WHERE uploadState IN ('QUEUED', 'LOCAL_SAVED', 'RETRY_WAIT')")
    fun getTotalWaitingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM media_items WHERE uploadState = 'UPLOADING' AND targetAccountId = :accountEmail")
    fun getUploadingCount(accountEmail: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM media_items WHERE uploadState = 'UPLOADED' AND targetAccountId = :accountEmail")
    fun getUploadedCount(accountEmail: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM media_items WHERE uploadState = 'FAILED' AND targetAccountId = :accountEmail")
    fun getFailedCount(accountEmail: String): Flow<Int>

    @Query("SELECT MAX(uploadedTimestamp) FROM media_items WHERE uploadState = 'UPLOADED' AND targetAccountId = :accountEmail")
    fun getLastSuccessfulUploadTime(accountEmail: String): Flow<Long?>

    @Query("UPDATE media_items SET isLocalCleanedUp = 1 WHERE id = :id")
    suspend fun markLocalCleanedUp(id: Long)

    @Query("SELECT * FROM media_items WHERE uploadState = 'UPLOADED' AND isLocalCleanedUp = 0 ORDER BY captureTimestamp DESC")
    suspend fun getVerifiedCleanableItems(): List<MediaItemEntity>

    @Query("SELECT * FROM media_items WHERE uploadState != 'UPLOADED' ORDER BY captureTimestamp DESC")
    suspend fun getNonCleanableItems(): List<MediaItemEntity>

    @Query("SELECT COUNT(*) FROM media_items WHERE uploadState = 'UPLOADED' AND isLocalCleanedUp = 0")
    fun getCleanableCount(): Flow<Int>

    @Query("SELECT SUM(fileSize) FROM media_items WHERE uploadState = 'UPLOADED' AND isLocalCleanedUp = 0")
    fun getCleanableBytes(): Flow<Long?>

    @Query("SELECT SUM(fileSize) FROM media_items WHERE uploadState IN ('QUEUED', 'LOCAL_SAVED', 'RETRY_WAIT', 'UPLOADING')")
    fun getPendingBytes(): Flow<Long?>
}
