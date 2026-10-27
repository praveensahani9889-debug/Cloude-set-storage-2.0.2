package com.example.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MediaFileManager {

    private fun getTimestamp(): String {
        return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    }

    fun createImageFile(context: Context): File {
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            ?: File(context.filesDir, "Pictures").apply { mkdirs() }
        val fileName = "IMG_${getTimestamp()}.jpg"
        return File(storageDir, fileName)
    }

    fun createVideoFile(context: Context): File {
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: File(context.filesDir, "Movies").apply { mkdirs() }
        val fileName = "VID_${getTimestamp()}.mp4"
        return File(storageDir, fileName)
    }

    fun getFileUri(context: Context, file: File): Uri {
        return try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (_: Exception) {
            Uri.fromFile(file)
        }
    }

    fun copyUriToLocalFile(context: Context, sourceUri: Uri): File? {
        return try {
            val resolver = context.contentResolver
            val mimeType = resolver.getType(sourceUri) ?: "image/jpeg"
            val isVideo = mimeType.startsWith("video/")
            val targetFile = if (isVideo) createVideoFile(context) else createImageFile(context)

            resolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (targetFile.exists() && targetFile.length() > 0) targetFile else null
        } catch (e: Exception) {
            null
        }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        val formatted = String.format(Locale.US, "%.1f", bytes / Math.pow(1024.0, digitGroups.toDouble()))
        return "$formatted ${units[digitGroups]}"
    }

    fun formatDuration(seconds: Long): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return String.format(Locale.US, "%02d:%02d", mins, secs)
    }

    fun getFreeInternalStorageBytes(context: Context): Long {
        return try {
            val stat = android.os.StatFs(context.filesDir.absolutePath)
            stat.availableBytes
        } catch (_: Exception) {
            -1L
        }
    }

    fun getTotalInternalStorageBytes(context: Context): Long {
        return try {
            val stat = android.os.StatFs(context.filesDir.absolutePath)
            stat.totalBytes
        } catch (_: Exception) {
            -1L
        }
    }

    fun getAppStagingStorageBytes(context: Context): Long {
        var totalBytes = 0L
        try {
            val directories = listOfNotNull(
                context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                context.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
                File(context.filesDir, "Pictures"),
                File(context.filesDir, "Movies"),
                context.cacheDir
            )
            for (dir in directories) {
                if (dir.exists()) {
                    dir.walkTopDown().filter { it.isFile }.forEach { file ->
                        totalBytes += file.length()
                    }
                }
            }
        } catch (_: Exception) {}
        return totalBytes
    }

    fun isDeviceStorageCriticallyLow(context: Context, minRequiredMb: Long = 100): Boolean {
        val freeBytes = getFreeInternalStorageBytes(context)
        return freeBytes in 0 until (minRequiredMb * 1024 * 1024)
    }
}
