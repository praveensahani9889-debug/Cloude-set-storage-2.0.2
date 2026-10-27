package com.example.camera

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture

enum class CaptureMode {
    PHOTO,
    VIDEO
}

data class CameraUiState(
    val captureMode: CaptureMode = CaptureMode.PHOTO,
    val lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    val flashMode: Int = ImageCapture.FLASH_MODE_AUTO,
    val torchEnabled: Boolean = false,
    val zoomRatio: Float = 1.0f,
    val minZoomRatio: Float = 1.0f,
    val maxZoomRatio: Float = 5.0f,
    val isRecording: Boolean = false,
    val isRecordingPaused: Boolean = false,
    val recordingDurationSeconds: Long = 0L,
    val isHdrSupported: Boolean = false,
    val isNightSupported: Boolean = false,
    val isBokehSupported: Boolean = false,
    val isVideoSupported: Boolean = true,
    val activeExtension: String? = null,
    val lastCapturedThumbnailUri: String? = null,
    val message: String? = null
)
