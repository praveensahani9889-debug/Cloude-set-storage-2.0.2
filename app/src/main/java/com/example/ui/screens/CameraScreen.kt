package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.view.MotionEvent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.camera.CameraManager
import com.example.camera.CaptureMode
import com.example.data.repository.MediaRepository
import com.example.ui.components.BubbleNavTab
import com.example.ui.components.FloatingBubbleNavBar
import com.example.util.MediaFileManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun CameraScreen(
    repository: MediaRepository,
    onNavigateToTab: (BubbleNavTab) -> Unit,
    onOpenGallery: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val cameraManager = remember { CameraManager(context) }
    val uiState by cameraManager.uiState.collectAsStateWithLifecycle()
    val activeAccount by repository.activeAccount.collectAsStateWithLifecycle(initialValue = null)

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { map ->
        hasCameraPermission = map[Manifest.permission.CAMERA] ?: false
        hasAudioPermission = map[Manifest.permission.RECORD_AUDIO] ?: false
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        }
    }

    // Camera Settings overlay state
    var showSettingsOverlay by remember { mutableStateOf(false) }
    var isImmersiveMode by remember { mutableStateOf(false) }

    // Settings
    var timerSeconds by remember { mutableIntStateOf(0) } // 0, 3, 5, 10
    var isGridEnabled by remember { mutableStateOf(false) }
    // Requirement 6: Google Drive is the default backup destination
    var saveDestination by remember { mutableStateOf("DRIVE_DIRECT") } // "INTERNAL", "SD_CARD", "DRIVE_DIRECT"
    var countdownRemaining by remember { mutableIntStateOf(0) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }

    // Handle messages
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            cameraManager.clearMessage()
        }
    }

    BackHandler(enabled = showSettingsOverlay || isImmersiveMode) {
        if (showSettingsOverlay) {
            showSettingsOverlay = false
        } else if (isImmersiveMode) {
            isImmersiveMode = false
        }
    }

    fun performCapture() {
        if (uiState.captureMode == CaptureMode.PHOTO) {
            if (timerSeconds > 0) {
                scope.launch {
                    countdownRemaining = timerSeconds
                    while (countdownRemaining > 0) {
                        delay(1000)
                        countdownRemaining--
                    }
                    cameraManager.playShutterFeedback()
                    cameraManager.takePhoto(
                        onSuccess = { file, uri ->
                            scope.launch {
                                val isDirectDrive = saveDestination == "DRIVE_DIRECT"
                                repository.saveCapturedMedia(
                                    file = file,
                                    uri = uri,
                                    mediaType = "PHOTO",
                                    forceUpload = isDirectDrive
                                )
                            }
                        },
                        onError = { err ->
                            scope.launch {
                                snackbarHostState.showSnackbar("Capture failed: ${err.message}")
                            }
                        }
                    )
                }
            } else {
                cameraManager.playShutterFeedback()
                cameraManager.takePhoto(
                    onSuccess = { file, uri ->
                        scope.launch {
                            val isDirectDrive = saveDestination == "DRIVE_DIRECT"
                            repository.saveCapturedMedia(
                                file = file,
                                uri = uri,
                                mediaType = "PHOTO",
                                forceUpload = isDirectDrive
                            )
                        }
                    },
                    onError = { err ->
                        scope.launch {
                            snackbarHostState.showSnackbar("Capture failed: ${err.message}")
                        }
                    }
                )
            }
        } else {
            // Video Mode
            if (uiState.isRecording) {
                cameraManager.playVideoStopFeedback()
                cameraManager.stopVideoRecording()
            } else {
                cameraManager.playVideoStartFeedback()
                cameraManager.startVideoRecording(
                    recordAudio = hasAudioPermission,
                    onStarted = {},
                    onFinished = { file, uri ->
                        scope.launch {
                            val isDirectDrive = saveDestination == "DRIVE_DIRECT"
                            repository.saveCapturedMedia(
                                file = file,
                                uri = uri,
                                mediaType = "VIDEO",
                                forceUpload = isDirectDrive
                            )
                        }
                    },
                    onError = { err ->
                        scope.launch {
                            snackbarHostState.showSnackbar("Recording error: ${err.message}")
                        }
                    }
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (!hasCameraPermission) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        "Camera Permission Required",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Please grant camera access to take photos and videos.",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {
                        permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
                    }) {
                        Text("Grant Permission")
                    }
                }
            }
        } else {
            // 1. Full-screen CameraX PreviewView
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        setOnTouchListener { v, event ->
                            if (event.action == MotionEvent.ACTION_UP) {
                                val factory = meteringPointFactory
                                cameraManager.tapToFocus(factory, event.x, event.y)
                                v.performClick()
                            }
                            true
                        }
                        previewViewRef = this
                        cameraManager.bindCamera(lifecycleOwner, this)
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, zoom, _ ->
                            cameraManager.setZoom(uiState.zoomRatio * zoom)
                        }
                    }
            )

            // 2. Optional Grid Lines
            if (isGridEnabled && !isImmersiveMode) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val lineColor = Color.White.copy(alpha = 0.28f)
                    drawLine(lineColor, Offset(w / 3f, 0f), Offset(w / 3f, h), strokeWidth = 1f)
                    drawLine(lineColor, Offset(w * 2f / 3f, 0f), Offset(w * 2f / 3f, h), strokeWidth = 1f)
                    drawLine(lineColor, Offset(0f, h / 3f), Offset(w, h / 3f), strokeWidth = 1f)
                    drawLine(lineColor, Offset(0f, h * 2f / 3f), Offset(w, h * 2f / 3f), strokeWidth = 1f)
                }
            }

            // 3. Countdown timer banner
            if (countdownRemaining > 0) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$countdownRemaining",
                        color = Color.White,
                        fontSize = 90.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            // 4. Clean On-Screen Top Bar (Only essential buttons)
            if (!isImmersiveMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Flash Mode Toggle
                    IconButton(
                        onClick = {
                            val nextMode = when (uiState.flashMode) {
                                ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                                ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_OFF
                                else -> ImageCapture.FLASH_MODE_AUTO
                            }
                            cameraManager.setFlashMode(nextMode)
                        },
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                            .testTag("camera_flash_button")
                    ) {
                        val icon = when (uiState.flashMode) {
                            ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                            ImageCapture.FLASH_MODE_OFF -> Icons.Default.FlashOff
                            else -> Icons.Default.FlashAuto
                        }
                        Icon(icon, contentDescription = "Flash", tint = Color.White)
                    }

                    // Save Destination Indicator (Google Drive default)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Black.copy(alpha = 0.55f),
                        modifier = Modifier.clickable {
                            if (saveDestination == "DRIVE_DIRECT" && activeAccount == null) {
                                onNavigateToTab(BubbleNavTab.DRIVE)
                            } else {
                                showSettingsOverlay = true
                            }
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            val destIcon = when (saveDestination) {
                                "DRIVE_DIRECT" -> if (activeAccount != null) Icons.Default.CloudDone else Icons.Default.CloudUpload
                                "SD_CARD" -> Icons.Default.SdCard
                                else -> Icons.Default.Smartphone
                            }
                            val destTint = when (saveDestination) {
                                "DRIVE_DIRECT" -> if (activeAccount != null) Color(0xFF64B5F6) else Color(0xFFFFB74D)
                                else -> Color(0xFF81C784)
                            }
                            Icon(destIcon, contentDescription = null, tint = destTint, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = when (saveDestination) {
                                    "DRIVE_DIRECT" -> if (activeAccount != null) "Google Drive" else "Connect Drive"
                                    "SD_CARD" -> "SD Card"
                                    else -> "Internal"
                                },
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Immersive Mode Toggle
                        IconButton(
                            onClick = { isImmersiveMode = true },
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.45f), CircleShape)
                        ) {
                            Icon(Icons.Default.Fullscreen, contentDescription = "Immersive Mode", tint = Color.White)
                        }

                        // Settings Icon (Opens in-place translucent overlay)
                        IconButton(
                            onClick = { showSettingsOverlay = true },
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                                .testTag("camera_settings_icon")
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "Camera Settings", tint = Color.White)
                        }
                    }
                }
            } else {
                // Exit Immersive Mode button in corner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(16.dp),
                    contentAlignment = Alignment.TopEnd
                ) {
                    IconButton(
                        onClick = { isImmersiveMode = false },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(Icons.Default.FullscreenExit, contentDescription = "Exit Fullscreen", tint = Color.White)
                    }
                }
            }

            // 5. Clean Bottom Controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = if (isImmersiveMode) 20.dp else 90.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Photo / Video Mode Selector
                if (!uiState.isRecording && !isImmersiveMode) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "PHOTO",
                            color = if (uiState.captureMode == CaptureMode.PHOTO) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.6f),
                            fontWeight = if (uiState.captureMode == CaptureMode.PHOTO) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp,
                            modifier = Modifier.clickable {
                                previewViewRef?.let { cameraManager.setCaptureMode(CaptureMode.PHOTO, lifecycleOwner, it) }
                            }
                        )
                        Text(
                            text = "VIDEO",
                            color = if (uiState.captureMode == CaptureMode.VIDEO) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.6f),
                            fontWeight = if (uiState.captureMode == CaptureMode.VIDEO) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp,
                            modifier = Modifier.clickable {
                                previewViewRef?.let { cameraManager.setCaptureMode(CaptureMode.VIDEO, lifecycleOwner, it) }
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Video recording duration
                if (uiState.isRecording) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(Color.Red.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = MediaFileManager.formatDuration(uiState.recordingDurationSeconds),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Main Capture Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 30.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Gallery Thumbnail / Preview
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(Color.DarkGray)
                            .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                            .clickable { onOpenGallery() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (uiState.lastCapturedThumbnailUri != null) {
                            AsyncImage(
                                model = uiState.lastCapturedThumbnailUri,
                                contentDescription = "Gallery Preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(Icons.Default.CloudDone, contentDescription = "Gallery", tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                    }

                    // Main Capture Button
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .border(4.dp, Color.White, CircleShape)
                            .padding(6.dp)
                            .clip(CircleShape)
                            .background(
                                if (uiState.captureMode == CaptureMode.VIDEO) {
                                    if (uiState.isRecording) Color.Red else Color.Red.copy(alpha = 0.8f)
                                } else {
                                    Color.White
                                }
                            )
                            .clickable { performCapture() }
                            .testTag("camera_capture_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (uiState.isRecording) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.White)
                            )
                        }
                    }

                    // Camera Switch (Front/Back)
                    IconButton(
                        onClick = {
                            previewViewRef?.let { cameraManager.toggleLens(lifecycleOwner, it) }
                        },
                        modifier = Modifier
                            .size(50.dp)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                            .testTag("camera_switch_lens_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cameraswitch,
                            contentDescription = "Switch Camera",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            // 6. In-Place Translucent Settings Overlay
            AnimatedVisibility(
                visible = showSettingsOverlay,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.78f))
                        .clickable { showSettingsOverlay = false }
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = false) {}
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Camera Settings", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                IconButton(onClick = { showSettingsOverlay = false }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close")
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Save Destination
                            Text("Save Destination", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(
                                    selected = saveDestination == "INTERNAL",
                                    onClick = { saveDestination = "INTERNAL" },
                                    label = { Text("Internal", fontSize = 11.sp) }
                                )
                                FilterChip(
                                    selected = saveDestination == "SD_CARD",
                                    onClick = { saveDestination = "SD_CARD" },
                                    label = { Text("SD Card", fontSize = 11.sp) }
                                )
                                FilterChip(
                                    selected = saveDestination == "DRIVE_DIRECT",
                                    onClick = { saveDestination = "DRIVE_DIRECT" },
                                    label = { Text("Direct Drive", fontSize = 11.sp) }
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Timer
                            Text("Self Timer", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(0 to "Off", 3 to "3s", 5 to "5s", 10 to "10s").forEach { (sec, label) ->
                                    FilterChip(
                                        selected = timerSeconds == sec,
                                        onClick = { timerSeconds = sec },
                                        label = { Text(label, fontSize = 11.sp) }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Grid Lines Toggle
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Rule of Thirds Grid", fontSize = 13.sp)
                                Switch(
                                    checked = isGridEnabled,
                                    onCheckedChange = { isGridEnabled = it }
                                )
                            }

                            // Camera Extensions (HDR, Night, Bokeh if device supports them)
                            if (uiState.isHdrSupported || uiState.isNightSupported || uiState.isBokehSupported) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Hardware Extensions", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilterChip(
                                        selected = uiState.activeExtension == null,
                                        onClick = { previewViewRef?.let { cameraManager.setExtension(null, lifecycleOwner, it) } },
                                        label = { Text("Standard", fontSize = 11.sp) }
                                    )
                                    if (uiState.isHdrSupported) {
                                        FilterChip(
                                            selected = uiState.activeExtension == "HDR",
                                            onClick = { previewViewRef?.let { cameraManager.setExtension("HDR", lifecycleOwner, it) } },
                                            label = { Text("HDR", fontSize = 11.sp) }
                                        )
                                    }
                                    if (uiState.isNightSupported) {
                                        FilterChip(
                                            selected = uiState.activeExtension == "NIGHT",
                                            onClick = { previewViewRef?.let { cameraManager.setExtension("NIGHT", lifecycleOwner, it) } },
                                            label = { Text("Night", fontSize = 11.sp) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating Bubble Bottom Navigation (Hidden in immersive mode, styled ultra-transparent and compact in camera)
        if (!isImmersiveMode && !uiState.isRecording) {
            FloatingBubbleNavBar(
                currentTab = BubbleNavTab.CAMERA,
                onTabSelected = onNavigateToTab,
                modifier = Modifier.align(Alignment.BottomCenter),
                isCameraMode = true
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 90.dp)
        )
    }
}
