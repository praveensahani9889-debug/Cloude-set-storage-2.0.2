package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import coil.request.ImageRequest
import com.example.util.AlbumSortOrder
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.data.preferences.BackupPreferences
import com.example.data.preferences.BackupPreferencesRepository
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.ui.components.BubbleNavTab
import com.example.ui.components.FloatingBubbleNavBar
import com.example.ui.components.rememberScrollAwareNavBarVisibility
import com.example.ui.components.FullScreenMediaViewer
import com.example.ui.components.UploadToDriveDialog
import com.example.ui.viewmodels.GalleryViewModel
import com.example.util.DeviceMediaItem
import com.example.util.MediaAlbum
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel,
    onNavigateToTab: (BubbleNavTab) -> Unit
) {
    val context = LocalContext.current
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val selectedAlbumIds by viewModel.selectedAlbumIds.collectAsStateWithLifecycle()
    val selectedAlbum by viewModel.selectedAlbum.collectAsStateWithLifecycle()
    val albumItems by viewModel.albumItems.collectAsStateWithLifecycle()
    val selectedItemIds by viewModel.selectedItemIds.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val accounts by viewModel.allAccounts.collectAsStateWithLifecycle()
    val activeAccount by viewModel.activeAccount.collectAsStateWithLifecycle()
    val driveFolders by viewModel.driveFolders.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    // Storage Permission Handling
    val permissionsToRequest = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    fun checkStorageGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    var hasStoragePermission by remember { mutableStateOf(checkStorageGranted()) }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasStoragePermission = checkStorageGranted()
        if (hasStoragePermission) {
            viewModel.loadAlbums()
        }
    }

    // Media Viewer state
    var viewingItemIndex by remember { mutableStateOf<Int?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }
    var albumToDelete by remember { mutableStateOf<MediaAlbum?>(null) }

    // Dialog states for batch operations
    var showDeleteAlbumsDialog by remember { mutableStateOf(false) }
    var showDeleteMediaDialog by remember { mutableStateOf(false) }
    var isMoveDialogOpen by remember { mutableStateOf(false) }

    // Preferences & Layout customization (2, 3, 4 columns)
    val preferencesRepo = remember { BackupPreferencesRepository(context) }
    val prefs by preferencesRepo.preferencesFlow.collectAsStateWithLifecycle(initialValue = BackupPreferences())
    val gridColumns = prefs.galleryGridColumns.coerceIn(2, 4)

    val gridState = rememberLazyGridState()
    val (isNavBarVisible, nestedScrollConnection) = rememberScrollAwareNavBarVisibility()

    // System BackHandler for photo viewer, multi-selection, and album navigation
    BackHandler(
        enabled = viewingItemIndex != null ||
                  selectedAlbum != null ||
                  selectedItemIds.isNotEmpty() ||
                  selectedAlbumIds.isNotEmpty()
    ) {
        when {
            viewingItemIndex != null -> viewingItemIndex = null
            selectedItemIds.isNotEmpty() -> viewModel.clearSelection()
            selectedAlbumIds.isNotEmpty() -> viewModel.clearAlbumSelection()
            selectedAlbum != null -> viewModel.backToAlbums()
        }
    }

    // Upload Dialog state
    var itemToUpload by remember { mutableStateOf<DeviceMediaItem?>(null) }
    var isBatchUploadDialogOpen by remember { mutableStateOf(false) }

    // Android Document Picker for PDFs/Docs
    val documentPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.importDocument(it) }
    }

    // Photo Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        uris.forEach { viewModel.importMediaFromUri(it) }
    }

    // Batch Delete Folders Dialog
    if (showDeleteAlbumsDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAlbumsDialog = false },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete ${selectedAlbumIds.size} Selected Folder(s)?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Delete local files in the ${selectedAlbumIds.size} selected folder(s)? Files already backed up to Google Drive remain safe in the cloud."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSelectedAlbums()
                        showDeleteAlbumsDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Folders")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAlbumsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Batch Delete Media Items Dialog
    if (showDeleteMediaDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteMediaDialog = false },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete ${selectedItemIds.size} Item(s)?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Are you sure you want to delete these ${selectedItemIds.size} file(s) from device storage?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSelectedItems()
                        showDeleteMediaDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteMediaDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Move Media Dialog
    if (isMoveDialogOpen) {
        var selectedDestination by remember { mutableStateOf("Camera Pictures") }
        var isNewFolderMode by remember { mutableStateOf(false) }
        var newFolderNameInput by remember { mutableStateOf("") }

        val folderOptions = remember(albums) {
            val defaults = listOf("Camera Pictures", "Camera Videos", "Documents")
            val custom = albums.filter { it.id.startsWith("app_custom_") }.map { it.name }
            (defaults + custom).distinct()
        }

        AlertDialog(
            onDismissRequest = { isMoveDialogOpen = false },
            icon = { Icon(Icons.Default.DriveFileMove, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Move ${selectedItemIds.size} Item(s)", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Choose destination folder:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                    ) {
                        items(folderOptions) { folderName ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        selectedDestination = folderName
                                        isNewFolderMode = false
                                    }
                                    .padding(vertical = 6.dp, horizontal = 4.dp)
                            ) {
                                RadioButton(
                                    selected = (!isNewFolderMode && selectedDestination == folderName),
                                    onClick = {
                                        selectedDestination = folderName
                                        isNewFolderMode = false
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(folderName, fontSize = 14.sp)
                            }
                        }

                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { isNewFolderMode = true }
                                    .padding(vertical = 6.dp, horizontal = 4.dp)
                            ) {
                                RadioButton(
                                    selected = isNewFolderMode,
                                    onClick = { isNewFolderMode = true }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "+ Create New Folder",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    if (isNewFolderMode) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = newFolderNameInput,
                            onValueChange = { newFolderNameInput = it },
                            label = { Text("New Folder Name") },
                            placeholder = { Text("e.g. Vacation, Favorites") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = if (isNewFolderMode) newFolderNameInput.trim() else selectedDestination
                        if (target.isNotBlank()) {
                            viewModel.moveSelectedItems(target)
                            isMoveDialogOpen = false
                        }
                    },
                    enabled = (isNewFolderMode && newFolderNameInput.isNotBlank()) || (!isNewFolderMode && selectedDestination.isNotBlank())
                ) {
                    Text("Move")
                }
            },
            dismissButton = {
                TextButton(onClick = { isMoveDialogOpen = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Folder Deletion Confirmation Dialog (single)
    albumToDelete?.let { album ->
        AlertDialog(
            onDismissRequest = { albumToDelete = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Folder?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (album.id.startsWith("app_"))
                        "Delete local staging files in folder \"${album.name}\"?\n\nThis will remove ${album.itemCount} local file(s) from device storage. Any files already backed up to Google Drive remain untouched in the cloud."
                    else
                        "\"${album.name}\" is an external system album.\n\nAndroid Scoped Storage protects system device folders from batch deletion by third-party apps. You can delete individual media items inside the album or manage app staging storage."
                )
            },
            confirmButton = {
                if (album.id.startsWith("app_")) {
                    Button(
                        onClick = {
                            viewModel.deleteAlbum(album)
                            albumToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete Folder")
                    }
                } else {
                    Button(onClick = { albumToDelete = null }) {
                        Text("OK")
                    }
                }
            },
            dismissButton = {
                if (album.id.startsWith("app_")) {
                    TextButton(onClick = { albumToDelete = null }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.nestedScroll(nestedScrollConnection),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selectedAlbum == null && selectedAlbumIds.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.clearAlbumSelection() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close Selection")
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${selectedAlbumIds.size} Selected",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.selectAllAlbums() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SelectAll,
                                    contentDescription = "Select All"
                                )
                            }
                            IconButton(
                                onClick = { showDeleteAlbumsDialog = true },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Selected",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        } else if (selectedAlbum != null) {
                            IconButton(
                                onClick = { viewModel.backToAlbums() },
                                modifier = Modifier.size(36.dp).testTag("gallery_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back to Albums"
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = selectedAlbum!!.name,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${albumItems.size} items",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.cloud_set_storage_icon_1792912552581),
                                    contentDescription = "Logo",
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Cloud Set Storage",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Albums & Cloud Gallery",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        // Actions
                        if (selectedItemIds.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.selectAllItems() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SelectAll,
                                    contentDescription = "Select All"
                                )
                            }
                            IconButton(
                                onClick = { isMoveDialogOpen = true },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DriveFileMove,
                                    contentDescription = "Move Selected",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = { isBatchUploadDialogOpen = true },
                                modifier = Modifier.size(36.dp).testTag("gallery_batch_upload")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudUpload,
                                    contentDescription = "Upload Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = { showDeleteMediaDialog = true },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Selected",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        } else if (selectedAlbumIds.isEmpty()) {
                            if (selectedAlbum == null) {
                                Box {
                                    IconButton(
                                        onClick = { showSortMenu = true },
                                        modifier = Modifier.size(36.dp).testTag("gallery_sort_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Sort,
                                            contentDescription = "Sort Folders",
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showSortMenu,
                                        onDismissRequest = { showSortMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Name (A to Z)", fontSize = 13.sp) },
                                            onClick = {
                                                showSortMenu = false
                                                viewModel.setSortOrder(AlbumSortOrder.NAME_ASC)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Name (Z to A)", fontSize = 13.sp) },
                                            onClick = {
                                                showSortMenu = false
                                                viewModel.setSortOrder(AlbumSortOrder.NAME_DESC)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Newest First", fontSize = 13.sp) },
                                            onClick = {
                                                showSortMenu = false
                                                viewModel.setSortOrder(AlbumSortOrder.DATE_MODIFIED_DESC)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Oldest First", fontSize = 13.sp) },
                                            onClick = {
                                                showSortMenu = false
                                                viewModel.setSortOrder(AlbumSortOrder.DATE_MODIFIED_ASC)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Most Items First", fontSize = 13.sp) },
                                            onClick = {
                                                showSortMenu = false
                                                viewModel.setSortOrder(AlbumSortOrder.ITEM_COUNT_DESC)
                                            }
                                        )
                                    }
                                }
                            }
                            IconButton(
                                onClick = { documentPickerLauncher.launch(arrayOf("application/pdf", "text/plain", "application/msword", "*/*")) },
                                modifier = Modifier.size(36.dp).testTag("gallery_pick_doc")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureAsPdf,
                                    contentDescription = "Import Document",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                    )
                                },
                                modifier = Modifier.size(36.dp).testTag("gallery_import_photo")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddPhotoAlternate,
                                    contentDescription = "Import Photos",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = { viewModel.loadAlbums() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(
                                painter = painterResource(id = R.drawable.cloud_set_storage_icon_1792912552581),
                                contentDescription = "Loading",
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 2.5.dp
                            )
                        }
                    }
                } else if (selectedAlbum == null) {
                    // Album Grid View
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Storage permission prompt banner if permission not granted
                        if (!hasStoragePermission) {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Storage Permission Required",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Allow storage access to view and manage device photos, videos, and folders.",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Button(
                                            onClick = { storagePermissionLauncher.launch(permissionsToRequest) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            ),
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                        ) {
                                            Text("Grant Storage Access", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }

                        if (albums.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PhotoLibrary,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        text = "No Media Albums Found",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Capture photos/videos using the Camera or tap + to import media from your device.",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 100.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(albums, key = { it.id }) { album ->
                                    val isSelected = selectedAlbumIds.contains(album.id)
                                    AlbumCard(
                                        album = album,
                                        isSelected = isSelected,
                                        onClick = {
                                            if (selectedAlbumIds.isNotEmpty()) {
                                                viewModel.toggleAlbumSelection(album.id)
                                            } else {
                                                viewModel.openAlbum(album)
                                            }
                                        },
                                        onLongClick = {
                                            viewModel.toggleAlbumSelection(album.id)
                                        }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Specific Album Items Grid
                    if (albumItems.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "This album is empty",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 15.sp
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Fixed(gridColumns),
                            contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 100.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(albumItems.size, key = { albumItems[it].id }) { index ->
                                val item = albumItems[index]
                                val isSelected = selectedItemIds.contains(item.id)
                                MediaTile(
                                    item = item,
                                    isSelected = isSelected,
                                    onClick = {
                                        if (selectedItemIds.isNotEmpty()) {
                                            viewModel.toggleItemSelection(item.id)
                                        } else {
                                            viewingItemIndex = index
                                        }
                                    },
                                    onLongClick = {
                                        viewModel.toggleItemSelection(item.id)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Floating Bubble Bottom Navigation
        FloatingBubbleNavBar(
            currentTab = BubbleNavTab.GALLERY,
            onTabSelected = onNavigateToTab,
            modifier = Modifier.align(Alignment.BottomCenter),
            isVisible = isNavBarVisible && viewingItemIndex == null && selectedItemIds.isEmpty() && selectedAlbumIds.isEmpty()
        )

        // Full Screen Media Viewer
        viewingItemIndex?.let { index ->
            FullScreenMediaViewer(
                items = albumItems,
                initialIndex = index,
                onClose = { viewingItemIndex = null },
                onUploadToDrive = { item ->
                    itemToUpload = item
                },
                onDelete = { item ->
                    viewModel.deleteItem(item)
                    viewingItemIndex = null
                }
            )
        }

        // Single Upload to Drive Dialog
        itemToUpload?.let { item ->
            UploadToDriveDialog(
                fileName = item.displayName,
                mediaType = if (item.isVideo) "VIDEO" else "PHOTO",
                accounts = accounts,
                activeAccount = activeAccount,
                existingFolders = driveFolders,
                onCreateFolder = { viewModel.createDriveFolder(it) },
                onDismiss = { itemToUpload = null },
                onConfirmUpload = { email, folderId ->
                    viewModel.uploadItemToDrive(item, email, folderId)
                    itemToUpload = null
                }
            )
        }

        // Batch Upload to Drive Dialog
        if (isBatchUploadDialogOpen) {
            UploadToDriveDialog(
                fileName = "${selectedItemIds.size} Selected Items",
                mediaType = "PHOTO",
                accounts = accounts,
                activeAccount = activeAccount,
                existingFolders = driveFolders,
                onCreateFolder = { viewModel.createDriveFolder(it) },
                onDismiss = { isBatchUploadDialogOpen = false },
                onConfirmUpload = { email, folderId ->
                    viewModel.uploadSelectedToDrive(email, folderId)
                    isBatchUploadDialogOpen = false
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumCard(
    album: MediaAlbum,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        ),
        border = if (isSelected)
            BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary)
        else null,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.1f)
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                    .background(Color.DarkGray)
            ) {
                if (album.thumbnailUri != Uri.EMPTY) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(album.thumbnailUri)
                            .size(300, 300)
                            .crossfade(true)
                            .build(),
                        contentDescription = album.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }

                // SD Card Badge
                if (album.isSdCard) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color.Black.copy(alpha = 0.75f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.SdCard, contentDescription = "SD Card", tint = Color(0xFFFFB300), modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("SD", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Selection Overlay & Checkmark (When Selected)
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }

                // Bottom gradient shadow for readable text
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))
                            )
                        )
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text(
                    text = album.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${album.itemCount} items",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (album.photoCount > 0) {
                            Text("📷 ${album.photoCount}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (album.videoCount > 0) {
                            Text("🎥 ${album.videoCount}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaTile(
    item: DeviceMediaItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.DarkGray)
            .border(
                width = if (isSelected) 3.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(if (item.path.isNotBlank() && File(item.path).exists()) File(item.path) else item.uri)
                .size(240, 240)
                .crossfade(true)
                .build(),
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Video Badge
        if (item.isVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(5.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = "Video",
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }
        }

        // Selection Checkmark
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}
