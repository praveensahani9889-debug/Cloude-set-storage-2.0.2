package com.example.camera

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.media.CamcorderProfile
import android.net.Uri
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.MeteringPointFactory
import androidx.camera.core.Preview
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.PendingRecording
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.util.MediaFileManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

class CameraManager(private val context: Context) {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private val feedbackHelper = CameraFeedbackHelper(context)

    fun playShutterFeedback() = feedbackHelper.onPhotoCaptured()
    fun playVideoStartFeedback() = feedbackHelper.onVideoRecordingStarted()
    fun playVideoStopFeedback() = feedbackHelper.onVideoRecordingStopped()

    private var cameraProvider: ProcessCameraProvider? = null
    private var extensionsManager: ExtensionsManager? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var currentRecording: Recording? = null
    private var recordingFile: File? = null

    fun checkCapabilities(provider: ProcessCameraProvider, extensions: ExtensionsManager) {
        val baseSelector = CameraSelector.Builder()
            .requireLensFacing(_uiState.value.lensFacing)
            .build()

        val hasHdr = extensions.isExtensionAvailable(baseSelector, ExtensionMode.HDR)
        val hasNight = extensions.isExtensionAvailable(baseSelector, ExtensionMode.NIGHT)
        val hasBokeh = extensions.isExtensionAvailable(baseSelector, ExtensionMode.BOKEH)
        val hasVideo = getSupportedVideoQualities(_uiState.value.lensFacing).isNotEmpty()

        _uiState.update {
            it.copy(
                isHdrSupported = hasHdr,
                isNightSupported = hasNight,
                isBokehSupported = hasBokeh,
                isVideoSupported = hasVideo
            )
        }
    }

    private fun getSupportedVideoQualities(lensFacing: Int): List<Quality> {
        return try {
            val cameraManager2 = context.getSystemService(Context.CAMERA_SERVICE) as? android.hardware.camera2.CameraManager
                ?: return emptyList()

            var targetCameraId: Int? = null
            for (id in cameraManager2.cameraIdList) {
                val chars = cameraManager2.getCameraCharacteristics(id)
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                val expectedFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                    CameraCharacteristics.LENS_FACING_BACK
                } else {
                    CameraCharacteristics.LENS_FACING_FRONT
                }
                if (facing == expectedFacing) {
                    targetCameraId = id.toIntOrNull()
                    break
                }
            }

            val camId = targetCameraId ?: 0
            val qualities = mutableListOf<Quality>()

            // Query CamcorderProfile safely - avoids CameraX CapabilitiesByQuality "No supported EncoderProfiles" errors
            if (CamcorderProfile.hasProfile(camId, CamcorderProfile.QUALITY_2160P)) {
                qualities.add(Quality.UHD)
            }
            if (CamcorderProfile.hasProfile(camId, CamcorderProfile.QUALITY_1080P)) {
                qualities.add(Quality.FHD)
            }
            if (CamcorderProfile.hasProfile(camId, CamcorderProfile.QUALITY_720P)) {
                qualities.add(Quality.HD)
            }
            if (CamcorderProfile.hasProfile(camId, CamcorderProfile.QUALITY_480P)) {
                qualities.add(Quality.SD)
            }
            if (qualities.isEmpty()) {
                if (CamcorderProfile.hasProfile(camId, CamcorderProfile.QUALITY_LOW)) {
                    qualities.add(Quality.LOWEST)
                } else if (CamcorderProfile.hasProfile(camId, CamcorderProfile.QUALITY_HIGH)) {
                    qualities.add(Quality.HIGHEST)
                }
            }
            qualities
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onReady: () -> Unit = {}
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider

                val extensionsManagerFuture = ExtensionsManager.getInstanceAsync(context, provider)
                extensionsManagerFuture.addListener({
                    try {
                        val extManager = extensionsManagerFuture.get()
                        extensionsManager = extManager
                        checkCapabilities(provider, extManager)
                        startCameraSession(lifecycleOwner, previewView, provider, extManager)
                        onReady()
                    } catch (_: Exception) {
                        startCameraSession(lifecycleOwner, previewView, provider, null)
                        onReady()
                    }
                }, ContextCompat.getMainExecutor(context))
            } catch (e: Exception) {
                _uiState.update { it.copy(message = "Failed to start camera: ${e.message}") }
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun startCameraSession(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        provider: ProcessCameraProvider,
        extManager: ExtensionsManager?
    ) {
        provider.unbindAll()

        var cameraSelector = CameraSelector.Builder()
            .requireLensFacing(_uiState.value.lensFacing)
            .build()

        val activeExt = _uiState.value.activeExtension
        if (extManager != null && activeExt != null) {
            val extensionMode = when (activeExt) {
                "HDR" -> ExtensionMode.HDR
                "NIGHT" -> ExtensionMode.NIGHT
                "BOKEH" -> ExtensionMode.BOKEH
                else -> ExtensionMode.NONE
            }
            if (extManager.isExtensionAvailable(cameraSelector, extensionMode)) {
                cameraSelector = extManager.getExtensionEnabledCameraSelector(cameraSelector, extensionMode)
            }
        }

        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }

        val imgCapture = ImageCapture.Builder()
            .setFlashMode(_uiState.value.flashMode)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
        imageCapture = imgCapture

        val isVideoMode = _uiState.value.captureMode == CaptureMode.VIDEO

        if (!isVideoMode) {
            // PHOTO MODE: Bind Preview + ImageCapture only.
            // Avoids VideoCapture/Recorder initialization and completely eliminates
            // "CapabilitiesByQuality: No supported EncoderProfiles" errors on startup and photo clicks.
            videoCapture = null
            try {
                camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imgCapture
                )
                observeZoomState(camera, lifecycleOwner)
            } catch (e: Exception) {
                _uiState.update { it.copy(message = "Camera binding failed: ${e.message}") }
            }
            return
        }

        // VIDEO MODE: Only configure VideoCapture if device has valid encoder profiles
        val supportedQualities = getSupportedVideoQualities(_uiState.value.lensFacing)
        val videoCap = if (supportedQualities.isNotEmpty()) {
            try {
                val qualitySelector = QualitySelector.fromOrderedList(
                    supportedQualities,
                    FallbackStrategy.lowerQualityOrHigherThan(supportedQualities.last())
                )
                val recorder = Recorder.Builder()
                    .setQualitySelector(qualitySelector)
                    .build()
                VideoCapture.withOutput(recorder)
            } catch (e: Exception) {
                android.util.Log.w("CameraManager", "VideoCapture init skipped: ${e.message}")
                null
            }
        } else {
            android.util.Log.i("CameraManager", "No video encoder profiles found for this camera/emulator.")
            null
        }

        videoCapture = videoCap
        _uiState.update { it.copy(isVideoSupported = videoCap != null) }

        try {
            camera = if (videoCap != null) {
                try {
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        videoCap
                    )
                } catch (e: Exception) {
                    android.util.Log.w("CameraManager", "Binding with VideoCapture failed (${e.message}), falling back to preview")
                    videoCapture = null
                    _uiState.update { it.copy(isVideoSupported = false) }
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imgCapture
                    )
                }
            } else {
                provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imgCapture
                )
            }
            observeZoomState(camera, lifecycleOwner)
        } catch (e: Exception) {
            _uiState.update { it.copy(message = "Camera binding failed: ${e.message}") }
        }
    }

    private fun observeZoomState(cam: Camera?, lifecycleOwner: LifecycleOwner) {
        cam?.cameraInfo?.zoomState?.observe(lifecycleOwner) { zoomState ->
            if (zoomState != null) {
                _uiState.update {
                    it.copy(
                        zoomRatio = zoomState.zoomRatio,
                        minZoomRatio = zoomState.minZoomRatio,
                        maxZoomRatio = zoomState.maxZoomRatio.coerceAtMost(10f)
                    )
                }
            }
        }
    }

    fun toggleLens(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        val newLens = if (_uiState.value.lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        _uiState.update { it.copy(lensFacing = newLens, activeExtension = null) }
        cameraProvider?.let { provider ->
            startCameraSession(lifecycleOwner, previewView, provider, extensionsManager)
        }
    }

    fun setFlashMode(mode: Int) {
        _uiState.update { it.copy(flashMode = mode) }
        imageCapture?.flashMode = mode
    }

    fun toggleTorch() {
        val newTorch = !_uiState.value.torchEnabled
        camera?.cameraControl?.enableTorch(newTorch)
        _uiState.update { it.copy(torchEnabled = newTorch) }
    }

    fun setZoom(ratio: Float) {
        camera?.cameraControl?.setZoomRatio(ratio.coerceIn(_uiState.value.minZoomRatio, _uiState.value.maxZoomRatio))
    }

    fun setExtension(name: String?, lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        _uiState.update { it.copy(activeExtension = name) }
        cameraProvider?.let { provider ->
            startCameraSession(lifecycleOwner, previewView, provider, extensionsManager)
        }
    }

    fun setCaptureMode(mode: CaptureMode, lifecycleOwner: LifecycleOwner? = null, previewView: PreviewView? = null) {
        if (!_uiState.value.isRecording) {
            _uiState.update { it.copy(captureMode = mode) }
            if (lifecycleOwner != null && previewView != null) {
                cameraProvider?.let { provider ->
                    startCameraSession(lifecycleOwner, previewView, provider, extensionsManager)
                }
            }
        }
    }

    fun tapToFocus(factory: MeteringPointFactory, x: Float, y: Float) {
        val point = factory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point).build()
        camera?.cameraControl?.startFocusAndMetering(action)
    }

    fun takePhoto(
        onSuccess: (File, Uri) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val capture = imageCapture ?: run {
            onError(IllegalStateException("Camera capture is not ready"))
            return
        }

        val photoFile = MediaFileManager.createImageFile(context)
        photoFile.parentFile?.mkdirs()
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    safeUpdateExif(photoFile)
                    feedbackHelper.onPhotoCaptured()
                    val uri = MediaFileManager.getFileUri(context, photoFile)
                    _uiState.update { it.copy(lastCapturedThumbnailUri = uri.toString(), message = null) }
                    onSuccess(photoFile, uri)
                }

                override fun onError(exception: ImageCaptureException) {
                    // Critical Fix: CameraX may report "Failed to update Exif data" even though
                    // the image bytes were successfully captured and flushed to disk.
                    // If the photo file exists and contains valid data, do NOT fail the capture.
                    if (photoFile.exists() && photoFile.length() > 1024L) {
                        android.util.Log.w("CameraManager", "CameraX reported EXIF error (${exception.message}), but valid image was written (${photoFile.length()} bytes). Retaining photo.")
                        safeUpdateExif(photoFile)
                        feedbackHelper.onPhotoCaptured()
                        val uri = MediaFileManager.getFileUri(context, photoFile)
                        _uiState.update { it.copy(lastCapturedThumbnailUri = uri.toString(), message = null) }
                        onSuccess(photoFile, uri)
                    } else {
                        _uiState.update { it.copy(message = "Capture failed: ${exception.message}") }
                        onError(exception)
                    }
                }
            }
        )
    }

    private fun safeUpdateExif(file: File) {
        try {
            if (file.exists() && file.length() > 0) {
                val exif = android.media.ExifInterface(file.absolutePath)
                val timeStamp = java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())
                if (exif.getAttribute(android.media.ExifInterface.TAG_DATETIME) == null) {
                    exif.setAttribute(android.media.ExifInterface.TAG_DATETIME, timeStamp)
                }
                exif.setAttribute(android.media.ExifInterface.TAG_SOFTWARE, "Cloud Set Storage")
                exif.saveAttributes()
            }
        } catch (e: Exception) {
            android.util.Log.w("CameraManager", "Non-fatal EXIF metadata write skipped: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun startVideoRecording(
        recordAudio: Boolean,
        onStarted: () -> Unit,
        onFinished: (File, Uri) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val capture = videoCapture ?: run {
            _uiState.update { it.copy(message = "Video recording is not supported on this device.") }
            onError(IllegalStateException("Video recording is not supported on this device."))
            return
        }

        val videoFile = MediaFileManager.createVideoFile(context)
        videoFile.parentFile?.mkdirs()
        recordingFile = videoFile

        val outputOptions = FileOutputOptions.Builder(videoFile).build()
        val pendingRecording: PendingRecording = capture.output.prepareRecording(context, outputOptions)

        if (recordAudio) {
            try {
                pendingRecording.withAudioEnabled()
            } catch (e: SecurityException) {
                _uiState.update { it.copy(message = "Audio permission denied; recording muted video") }
            }
        }

        currentRecording = pendingRecording.start(ContextCompat.getMainExecutor(context)) { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    _uiState.update { it.copy(isRecording = true, isRecordingPaused = false, recordingDurationSeconds = 0L) }
                    feedbackHelper.onVideoRecordingStarted()
                    onStarted()
                }
                is VideoRecordEvent.Status -> {
                    val seconds = event.recordingStats.recordedDurationNanos / 1_000_000_000L
                    _uiState.update { it.copy(recordingDurationSeconds = seconds) }
                }
                is VideoRecordEvent.Pause -> {
                    _uiState.update { it.copy(isRecordingPaused = true) }
                }
                is VideoRecordEvent.Resume -> {
                    _uiState.update { it.copy(isRecordingPaused = false) }
                }
                is VideoRecordEvent.Finalize -> {
                    _uiState.update { it.copy(isRecording = false, isRecordingPaused = false, recordingDurationSeconds = 0L) }
                    currentRecording = null
                    feedbackHelper.onVideoRecordingStopped()
                    if (!event.hasError()) {
                        val uri = MediaFileManager.getFileUri(context, videoFile)
                        _uiState.update { it.copy(lastCapturedThumbnailUri = uri.toString(), message = "Video recorded") }
                        onFinished(videoFile, uri)
                    } else {
                        _uiState.update { it.copy(message = "Video recording error: ${event.error}") }
                        onError(Exception("Recording error code: ${event.error}"))
                    }
                }
            }
        }
    }

    fun stopVideoRecording() {
        currentRecording?.stop()
        currentRecording = null
    }

    fun pauseVideoRecording() {
        currentRecording?.pause()
    }

    fun resumeVideoRecording() {
        currentRecording?.resume()
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
