package com.example.util

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class AlbumSortOrder {
    NAME_ASC,
    NAME_DESC,
    DATE_MODIFIED_DESC,
    DATE_MODIFIED_ASC,
    ITEM_COUNT_DESC
}

data class MediaAlbum(
    val id: String,
    val name: String,
    val thumbnailUri: Uri,
    val itemCount: Int,
    val photoCount: Int,
    val videoCount: Int,
    val isSdCard: Boolean = false,
    val storageType: String = "Internal", // "Internal", "SD Card", "App Storage"
    val latestTimestamp: Long = 0L
)

data class DeviceMediaItem(
    val id: Long,
    val uri: Uri,
    val path: String,
    val displayName: String,
    val mimeType: String,
    val size: Long,
    val dateModified: Long,
    val isVideo: Boolean,
    val bucketId: String,
    val bucketName: String
)

object LocalMediaDiscovery {

    suspend fun loadAlbums(
        context: Context,
        sortOrder: AlbumSortOrder = AlbumSortOrder.NAME_ASC
    ): List<MediaAlbum> = withContext(Dispatchers.IO) {
        val albumMap = mutableMapOf<String, MutableAlbumData>()

        // 1. Scan App's own local staging directories first
        scanAppDirectories(context, albumMap)

        // 2. Query MediaStore if storage access is available
        queryMediaStore(context, albumMap)

        // 3. Convert to MediaAlbum list
        val rawList = albumMap.values.map { data ->
            MediaAlbum(
                id = data.id,
                name = data.name,
                thumbnailUri = data.latestUri ?: Uri.EMPTY,
                itemCount = data.totalCount,
                photoCount = data.photoCount,
                videoCount = data.videoCount,
                isSdCard = data.isSdCard,
                storageType = if (data.isSdCard) "SD Card" else "Internal",
                latestTimestamp = data.latestTimestamp
            )
        }

        when (sortOrder) {
            AlbumSortOrder.NAME_ASC -> rawList.sortedBy { it.name.lowercase() }
            AlbumSortOrder.NAME_DESC -> rawList.sortedByDescending { it.name.lowercase() }
            AlbumSortOrder.DATE_MODIFIED_DESC -> rawList.sortedByDescending { it.latestTimestamp }
            AlbumSortOrder.DATE_MODIFIED_ASC -> rawList.sortedBy { it.latestTimestamp }
            AlbumSortOrder.ITEM_COUNT_DESC -> rawList.sortedByDescending { it.itemCount }
        }
    }

    fun deleteAlbumFiles(context: Context, album: MediaAlbum): Pair<Boolean, String> {
        if (album.id.startsWith("app_")) {
            val targetDir = when {
                album.id == "app_pictures" -> context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: File(context.filesDir, "Pictures")
                album.id == "app_movies" -> context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: File(context.filesDir, "Movies")
                album.id == "app_documents" -> File(context.filesDir, "Documents")
                album.id.startsWith("app_custom_") -> {
                    val customName = album.id.removePrefix("app_custom_")
                    File(context.getExternalFilesDir(null) ?: context.filesDir, "Albums/$customName")
                }
                else -> {
                    val dirName = album.id.removePrefix("app_")
                    val customCandidate = File(context.getExternalFilesDir(null) ?: context.filesDir, "Albums/$dirName")
                    if (customCandidate.exists()) customCandidate else context.getExternalFilesDir(null)
                }
            }
            var count = 0
            targetDir?.listFiles()?.forEach { file ->
                if (file.isFile) {
                    if (file.delete()) count++
                }
            }
            if (targetDir != null && targetDir.parentFile?.name == "Albums") {
                targetDir.delete()
            }
            return Pair(true, "Removed $count local files from ${album.name}")
        } else {
            return Pair(false, "System gallery albums cannot be bulk-deleted under Android Scoped Storage. You can delete individual photos or manage staging copies.")
        }
    }

    suspend fun moveMediaItems(
        context: Context,
        items: List<DeviceMediaItem>,
        destinationAlbumName: String,
        mediaDao: com.example.data.local.dao.MediaDao? = null
    ): Pair<Int, String> = withContext(Dispatchers.IO) {
        val cleanName = destinationAlbumName.trim()
        if (cleanName.isBlank()) {
            return@withContext Pair(0, "Invalid folder name")
        }

        val targetDir: File = when (cleanName.lowercase()) {
            "camera pictures", "pictures" -> context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: File(context.filesDir, "Pictures")
            "camera videos", "movies" -> context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: File(context.filesDir, "Movies")
            "documents" -> File(context.filesDir, "Documents")
            else -> File(context.getExternalFilesDir(null) ?: context.filesDir, "Albums/$cleanName")
        }
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        var successCount = 0
        for (item in items) {
            try {
                val sourceFile = if (item.path.isNotBlank()) File(item.path) else null
                var destFile = File(targetDir, item.displayName)
                if (destFile.exists()) {
                    val nameWithoutExt = destFile.nameWithoutExtension
                    val ext = destFile.extension
                    val suffix = System.currentTimeMillis() % 10000
                    destFile = File(targetDir, "${nameWithoutExt}_$suffix.$ext")
                }

                var movedSuccessfully = false
                if (sourceFile != null && sourceFile.exists()) {
                    if (sourceFile.renameTo(destFile)) {
                        movedSuccessfully = true
                    } else {
                        sourceFile.inputStream().use { input ->
                            destFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        if (destFile.exists() && destFile.length() > 0L) {
                            sourceFile.delete()
                            movedSuccessfully = true
                        }
                    }
                } else {
                    context.contentResolver.openInputStream(item.uri)?.use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (destFile.exists() && destFile.length() > 0L) {
                        movedSuccessfully = true
                    }
                }

                if (movedSuccessfully) {
                    successCount++
                    val newUri = MediaFileManager.getFileUri(context, destFile).toString()
                    mediaDao?.updateLocation(
                        oldPath = item.path,
                        oldUri = item.uri.toString(),
                        newPath = destFile.absolutePath,
                        newUri = newUri
                    )
                }
            } catch (_: Exception) {}
        }
        Pair(successCount, "Moved $successCount item(s) to $cleanName")
    }

    private fun scanAppDirectories(context: Context, albumMap: MutableMap<String, MutableAlbumData>) {
        val appDirs = mutableListOf<Triple<String, String, File>>()
        context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.let {
            appDirs.add(Triple("app_pictures", "Camera Pictures", it))
        }
        context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)?.let {
            appDirs.add(Triple("app_movies", "Camera Videos", it))
        }
        File(context.filesDir, "Pictures").takeIf { it.exists() }?.let {
            appDirs.add(Triple("app_pictures_int", "App Pictures", it))
        }
        File(context.filesDir, "Movies").takeIf { it.exists() }?.let {
            appDirs.add(Triple("app_movies_int", "App Movies", it))
        }
        File(context.filesDir, "Documents").takeIf { it.exists() }?.let {
            appDirs.add(Triple("app_documents", "Documents", it))
        }

        // Custom albums under Albums/
        val albumsRoot = File(context.getExternalFilesDir(null) ?: context.filesDir, "Albums")
        if (albumsRoot.exists() && albumsRoot.isDirectory) {
            albumsRoot.listFiles()?.filter { it.isDirectory }?.forEach { subDir ->
                appDirs.add(Triple("app_custom_${subDir.name}", subDir.name, subDir))
            }
        }

        for ((bucketId, bucketName, dir) in appDirs) {
            if (!dir.exists()) continue
            val files = dir.listFiles() ?: continue
            for (file in files) {
                if (!file.isFile || file.length() == 0L) continue
                val name = file.name.lowercase()
                val isImage = name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp")
                val isVideo = name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".mov")
                val isDoc = name.endsWith(".pdf") || name.endsWith(".txt") || name.endsWith(".docx") || name.endsWith(".xlsx")

                if (isImage || isVideo || isDoc) {
                    val uri = MediaFileManager.getFileUri(context, file)
                    val album = albumMap.getOrPut(bucketId) {
                        MutableAlbumData(id = bucketId, name = bucketName, isSdCard = false)
                    }
                    album.totalCount++
                    if (isVideo) album.videoCount++ else album.photoCount++
                    if (album.latestUri == null || file.lastModified() > album.latestTimestamp) {
                        album.latestUri = uri
                        album.latestTimestamp = file.lastModified()
                    }
                }
            }
        }
    }

    private fun queryMediaStore(context: Context, albumMap: MutableMap<String, MutableAlbumData>) {
        try {
            // Images
            val imageProjection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.BUCKET_ID,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Images.Media.DATE_MODIFIED,
                MediaStore.Images.Media.DATA
            )

            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                imageProjection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val bucketIdCol = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_ID)
                val bucketNameCol = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val dateCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED)
                val dataCol = cursor.getColumnIndex(MediaStore.Images.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val bucketId = if (bucketIdCol != -1) cursor.getString(bucketIdCol) ?: "unknown" else "unknown"
                    val bucketName = if (bucketNameCol != -1) cursor.getString(bucketNameCol) ?: "Pictures" else "Pictures"
                    val dateModified = if (dateCol != -1) cursor.getLong(dateCol) * 1000L else System.currentTimeMillis()
                    val path = if (dataCol != -1) cursor.getString(dataCol) ?: "" else ""
                    val isSdCard = path.contains("/storage/") && !path.contains("/storage/emulated/0")

                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

                    val album = albumMap.getOrPut(bucketId) {
                        MutableAlbumData(id = bucketId, name = bucketName, isSdCard = isSdCard)
                    }
                    album.totalCount++
                    album.photoCount++
                    if (album.latestUri == null || dateModified > album.latestTimestamp) {
                        album.latestUri = uri
                        album.latestTimestamp = dateModified
                    }
                }
            }

            // Videos
            val videoProjection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.BUCKET_ID,
                MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Video.Media.DATE_MODIFIED,
                MediaStore.Video.Media.DATA
            )

            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                videoProjection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val bucketIdCol = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_ID)
                val bucketNameCol = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                val dateCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED)
                val dataCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val bucketId = if (bucketIdCol != -1) cursor.getString(bucketIdCol) ?: "videos" else "videos"
                    val bucketName = if (bucketNameCol != -1) cursor.getString(bucketNameCol) ?: "Movies" else "Movies"
                    val dateModified = if (dateCol != -1) cursor.getLong(dateCol) * 1000L else System.currentTimeMillis()
                    val path = if (dataCol != -1) cursor.getString(dataCol) ?: "" else ""
                    val isSdCard = path.contains("/storage/") && !path.contains("/storage/emulated/0")

                    val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

                    val album = albumMap.getOrPut(bucketId) {
                        MutableAlbumData(id = bucketId, name = bucketName, isSdCard = isSdCard)
                    }
                    album.totalCount++
                    album.videoCount++
                    if (album.latestUri == null || dateModified > album.latestTimestamp) {
                        album.latestUri = uri
                        album.latestTimestamp = dateModified
                    }
                }
            }
        } catch (_: SecurityException) {
            // Handled gracefully: app directories fallback will still show photos/videos
        }
    }

    suspend fun loadAlbumItems(context: Context, bucketId: String): List<DeviceMediaItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<DeviceMediaItem>()

        // Check if it's an app-internal directory
        if (bucketId.startsWith("app_")) {
            val targetDir = when {
                bucketId == "app_pictures" -> context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: File(context.filesDir, "Pictures")
                bucketId == "app_movies" -> context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: File(context.filesDir, "Movies")
                bucketId == "app_pictures_int" -> File(context.filesDir, "Pictures")
                bucketId == "app_movies_int" -> File(context.filesDir, "Movies")
                bucketId == "app_documents" -> File(context.filesDir, "Documents")
                bucketId.startsWith("app_custom_") -> {
                    val customName = bucketId.removePrefix("app_custom_")
                    File(context.getExternalFilesDir(null) ?: context.filesDir, "Albums/$customName")
                }
                else -> {
                    val dirName = bucketId.removePrefix("app_")
                    val customCandidate = File(context.getExternalFilesDir(null) ?: context.filesDir, "Albums/$dirName")
                    if (customCandidate.exists()) customCandidate else context.getExternalFilesDir(null)
                }
            }

            targetDir?.listFiles()?.forEach { file ->
                if (file.isFile && file.length() > 0) {
                    val name = file.name.lowercase()
                    val isVideo = name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".mov")
                    val uri = MediaFileManager.getFileUri(context, file)
                    items.add(
                        DeviceMediaItem(
                            id = file.hashCode().toLong(),
                            uri = uri,
                            path = file.absolutePath,
                            displayName = file.name,
                            mimeType = if (isVideo) "video/mp4" else "image/jpeg",
                            size = file.length(),
                            dateModified = file.lastModified(),
                            isVideo = isVideo,
                            bucketId = bucketId,
                            bucketName = targetDir.name
                        )
                    )
                }
            }
            return@withContext items.sortedByDescending { it.dateModified }
        }

        // Query MediaStore for this bucket
        try {
            // Images
            val imageProjection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.MIME_TYPE,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.DATE_MODIFIED,
                MediaStore.Images.Media.DATA
            )

            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                imageProjection,
                "${MediaStore.Images.Media.BUCKET_ID} = ?",
                arrayOf(bucketId),
                "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndex(MediaStore.Images.Media.MIME_TYPE)
                val sizeCol = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)
                val dateCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED)
                val dataCol = cursor.getColumnIndex(MediaStore.Images.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Image_$id"
                    val mime = if (mimeCol != -1) cursor.getString(mimeCol) ?: "image/jpeg" else "image/jpeg"
                    val size = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L
                    val date = if (dateCol != -1) cursor.getLong(dateCol) * 1000L else System.currentTimeMillis()
                    val path = if (dataCol != -1) cursor.getString(dataCol) ?: "" else ""
                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

                    items.add(
                        DeviceMediaItem(
                            id = id,
                            uri = uri,
                            path = path,
                            displayName = name,
                            mimeType = mime,
                            size = size,
                            dateModified = date,
                            isVideo = false,
                            bucketId = bucketId,
                            bucketName = "Album"
                        )
                    )
                }
            }

            // Videos
            val videoProjection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.MIME_TYPE,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DATE_MODIFIED,
                MediaStore.Video.Media.DATA
            )

            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                videoProjection,
                "${MediaStore.Video.Media.BUCKET_ID} = ?",
                arrayOf(bucketId),
                "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndex(MediaStore.Video.Media.MIME_TYPE)
                val sizeCol = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
                val dateCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED)
                val dataCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Video_$id"
                    val mime = if (mimeCol != -1) cursor.getString(mimeCol) ?: "video/mp4" else "video/mp4"
                    val size = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L
                    val date = if (dateCol != -1) cursor.getLong(dateCol) * 1000L else System.currentTimeMillis()
                    val path = if (dataCol != -1) cursor.getString(dataCol) ?: "" else ""
                    val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

                    items.add(
                        DeviceMediaItem(
                            id = id,
                            uri = uri,
                            path = path,
                            displayName = name,
                            mimeType = mime,
                            size = size,
                            dateModified = date,
                            isVideo = true,
                            bucketId = bucketId,
                            bucketName = "Album"
                        )
                    )
                }
            }
        } catch (_: SecurityException) {}

        items.sortedByDescending { it.dateModified }
    }

    private class MutableAlbumData(
        val id: String,
        val name: String,
        val isSdCard: Boolean,
        var totalCount: Int = 0,
        var photoCount: Int = 0,
        var videoCount: Int = 0,
        var latestUri: Uri? = null,
        var latestTimestamp: Long = 0L
    )
}
