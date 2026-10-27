package com.example.backup

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

class UploadNotificationManager(private val context: Context) {
    companion object {
        const val CHANNEL_ID = "cloud_set_storage_uploads"
        const val CHANNEL_NAME = "Cloud Set Storage Uploads"
        const val NOTIFICATION_ID_PROGRESS = 1001
        const val NOTIFICATION_ID_ALERT = 1002
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress and completion of media uploads to Google Drive"
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun areNotificationsEnabled(): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return true
    }

    private fun getContentIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun buildProgressNotification(
        fileName: String,
        progress: Int,
        total: Int,
        mediaType: String = "PHOTO"
    ): NotificationCompat.Builder {
        val typeLabel = when (mediaType.uppercase()) {
            "VIDEO" -> "Video"
            "DOCUMENT" -> "Document"
            else -> "Photo"
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Uploading $typeLabel to Google Drive")
            .setContentText(fileName)
            .setProgress(total, progress, total <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(getContentIntent())
            .setPriority(NotificationCompat.PRIORITY_LOW)
    }

    fun showCompletionNotification(
        fileName: String,
        mediaType: String = "PHOTO",
        success: Boolean,
        message: String? = null
    ) {
        if (!areNotificationsEnabled()) return

        val typeLabel = when (mediaType.uppercase()) {
            "VIDEO" -> "Video"
            "DOCUMENT" -> "Document"
            else -> "Photo"
        }

        val title = if (success) "$typeLabel Uploaded Successfully" else "$typeLabel Upload Failed"
        val text = if (success) {
            "$fileName backed up to Google Drive"
        } else {
            "$fileName: ${message ?: "Could not complete upload"}"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(getContentIntent())
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            notificationManager.notify((System.currentTimeMillis() % 100000).toInt(), notification)
        } catch (_: Exception) {}
    }

    fun showAuthRequiredNotification(accountEmail: String) {
        if (!areNotificationsEnabled()) return

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Google Drive Authorization Required")
            .setContentText("Tap to re-authorize account $accountEmail for cloud backup")
            .setAutoCancel(true)
            .setContentIntent(getContentIntent())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        try {
            notificationManager.notify(NOTIFICATION_ID_ALERT, notification)
        } catch (_: Exception) {}
    }

    fun showStorageFullNotification(accountEmail: String) {
        if (!areNotificationsEnabled()) return

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Google Drive Storage Full")
            .setContentText("Cannot backup files: Account $accountEmail is out of storage")
            .setAutoCancel(true)
            .setContentIntent(getContentIntent())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        try {
            notificationManager.notify(NOTIFICATION_ID_ALERT + 1, notification)
        } catch (_: Exception) {}
    }
}
