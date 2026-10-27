package com.example.camera

import android.content.Context
import android.media.AudioManager
import android.media.MediaActionSound
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.os.SystemClock

class CameraFeedbackHelper(private val context: Context) {

    private val vibrator: Vibrator? by lazy {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Exception) {
            null
        }
    }

    private val audioManager: AudioManager? by lazy {
        try {
            context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        } catch (_: Exception) {
            null
        }
    }

    // Retained MediaActionSound instance pre-loaded for instant playback
    private val actionSound: MediaActionSound? by lazy {
        try {
            MediaActionSound().apply {
                load(MediaActionSound.SHUTTER_CLICK)
                load(MediaActionSound.START_VIDEO_RECORDING)
                load(MediaActionSound.STOP_VIDEO_RECORDING)
            }
        } catch (_: Exception) {
            null
        }
    }

    private var lastPhotoFeedbackTime = 0L
    private var lastVideoFeedbackTime = 0L

    /**
     * Triggered on photo click: crisp shutter click sound + subtle click vibration
     */
    fun onPhotoCaptured() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastPhotoFeedbackTime < 250) return
        lastPhotoFeedbackTime = now

        // 1. Light haptic vibration
        triggerLightVibration(durationMs = 30)

        // 2. Crisp shutter click sound (only if not silent/vibrate mode)
        try {
            val ringerMode = audioManager?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL
            if (ringerMode == AudioManager.RINGER_MODE_NORMAL) {
                actionSound?.play(MediaActionSound.SHUTTER_CLICK)
            }
        } catch (_: Exception) {}
    }

    /**
     * Triggered when video recording starts
     */
    fun onVideoRecordingStarted() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastVideoFeedbackTime < 350) return
        lastVideoFeedbackTime = now

        triggerLightVibration(durationMs = 45)

        try {
            val ringerMode = audioManager?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL
            if (ringerMode == AudioManager.RINGER_MODE_NORMAL) {
                actionSound?.play(MediaActionSound.START_VIDEO_RECORDING)
            }
        } catch (_: Exception) {}
    }

    /**
     * Triggered when video recording stops
     */
    fun onVideoRecordingStopped() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastVideoFeedbackTime < 350) return
        lastVideoFeedbackTime = now

        triggerLightVibration(durationMs = 55)

        try {
            val ringerMode = audioManager?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL
            if (ringerMode == AudioManager.RINGER_MODE_NORMAL) {
                actionSound?.play(MediaActionSound.STOP_VIDEO_RECORDING)
            }
        } catch (_: Exception) {}
    }

    private fun triggerLightVibration(durationMs: Long) {
        try {
            vibrator?.let { vib ->
                if (vib.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        try {
                            vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                        } catch (_: Exception) {
                            vib.vibrate(VibrationEffect.createOneShot(durationMs, 180))
                        }
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vib.vibrate(VibrationEffect.createOneShot(durationMs, 180))
                    } else {
                        @Suppress("DEPRECATION")
                        vib.vibrate(durationMs)
                    }
                }
            }
        } catch (_: Exception) {}
    }
}
