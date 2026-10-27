package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.example.ui.components.rememberScrollAwareNavBarVisibility
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.drive.model.DriveFileResult
import com.example.ui.components.BubbleNavTab
import com.example.ui.components.FloatingBubbleNavBar
import com.example.ui.viewmodels.DriveCategoryTab
import com.example.ui.viewmodels.DriveViewModel
import com.example.util.MediaFileManager

@Composable
fun DriveScreen(
    viewModel: DriveViewModel,
    onNavigateToTab: (BubbleNavTab) -> Unit
) {
    val context = LocalContext.current
    val activeAccount by viewModel.activeAccount.collectAsStateWithLifecycle()
    val allAccounts by viewModel.allAccounts.collectAsStateWithLifecycle()
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val currentFolder by viewModel.currentFolder.collectAsStateWithLifecycle()
    val driveFiles by viewModel.driveFiles.collectAsStateWithLifecycle()
    val quotaInfo by viewModel.quotaInfo.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val apiDisabledDialogInfo by viewModel.apiDisabledDialogInfo.collectAsStateWithLifecycle()
    val isCreatingFolder by viewModel.isCreatingFolder.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    // Google Sign-In launcher
    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.handleSignInResult(result.data)
        }
    }

    var showAccountMenu by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var folderToDelete by remember { mutableStateOf<DriveFileResult?>(null) }

    val listState = rememberLazyListState()
    val (isNavBarVisible, nestedScrollConnection) = rememberScrollAwareNavBarVisibility()

    // System BackHandler: if inside a folder, back navigates up
    BackHandler(enabled = currentFolder != null || showCreateFolderDialog || apiDisabledDialogInfo != null) {
        when {
            apiDisabledDialogInfo != null -> viewModel.dismissApiDisabledDialog()
            showCreateFolderDialog -> showCreateFolderDialog = false
            currentFolder != null -> viewModel.navigateUp()
        }
    }

    // Actionable Google Drive API 403 / SERVICE_DISABLED dialog
    apiDisabledDialogInfo?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissApiDisabledDialog() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Google Drive API Disabled", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
            },
            text = {
                Column {
                    Text(
                        text = "The Google Drive API v3 has not been enabled for your Google Cloud Project.",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "To enable it in Google Cloud Console:\n\n1. Open Google Cloud Console\n2. Navigate to APIs & Services > Library\n3. Search for 'Google Drive API' and click ENABLE\n4. Verify your OAuth consent screen & credentials, then return and retry.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        try {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://console.cloud.google.com/apis/library/drive.googleapis.com")
                            )
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    }
                ) {
                    Text("Open Cloud Console")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissApiDisabledDialog() }) {
                    Text("Dismiss")
                }
            }
        )
    }

    if (showCreateFolderDialog) {
        val isValidName = newFolderName.trim().isNotBlank() && !newFolderName.contains("/") && !newFolderName.contains("\\")
        AlertDialog(
            onDismissRequest = { if (!isCreatingFolder) showCreateFolderDialog = false },
            title = { Text("New Google Drive Folder", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Enter a folder name for your Google Drive:", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        label = { Text("Folder Name") },
                        singleLine = true,
                        isError = newFolderName.isNotBlank() && !isValidName,
                        supportingText = if (newFolderName.isNotBlank() && !isValidName) {
                            { Text("Folder name cannot be empty or contain slashes", color = MaterialTheme.colorScheme.error) }
                        } else null,
                        enabled = !isCreatingFolder,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isValidName && !isCreatingFolder) {
                            val folderNameToCreate = newFolderName.trim()
                            viewModel.createFolder(folderNameToCreate)
                            showCreateFolderDialog = false
                            newFolderName = ""
                        }
                    },
                    enabled = isValidName && !isCreatingFolder
                ) {
                    if (isCreatingFolder) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Creating...")
                    } else {
                        Text("Create")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCreateFolderDialog = false },
                    enabled = !isCreatingFolder
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Confirmation Dialog for Moving Folder to Drive Trash
    folderToDelete?.let { folder ->
        AlertDialog(
            onDismissRequest = { folderToDelete = null },
            icon = { Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Folder from Drive?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Are you sure you want to move folder \"${folder.fileName}\" to Google Drive Trash?\n\nFiles inside this folder will also be moved to Trash, but can be restored from the Google Drive Trash if needed."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.trashFolder(folder)
                        folderToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Move to Trash")
                }
            },
            dismissButton = {
                TextButton(onClick = { folderToDelete = null }) {
                    Text("Cancel")
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cloud,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Google Drive",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = activeAccount?.accountEmail ?: "Not Connected",
                                    fontSize = 12.sp,
                                    color = if (activeAccount != null) MaterialTheme.colorScheme.primary else Color.Gray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Account Switcher Button
                            Box {
                                IconButton(onClick = { showAccountMenu = true }) {
                                    Icon(
                                        imageVector = Icons.Default.AccountCircle,
                                        contentDescription = "Accounts",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                DropdownMenu(
                                    expanded = showAccountMenu,
                                    onDismissRequest = { showAccountMenu = false }
                                ) {
                                    if (allAccounts.isEmpty()) {
                                        DropdownMenuItem(
                                            text = { Text("Connect Google Drive") },
                                            onClick = {
                                                showAccountMenu = false
                                                signInLauncher.launch(viewModel.getSignInIntent())
                                            },
                                            leadingIcon = { Icon(Icons.Default.PersonAdd, contentDescription = null) }
                                        )
                                    } else {
                                        allAccounts.forEach { acc ->
                                            val isActive = acc.accountEmail == activeAccount?.accountEmail
                                            DropdownMenuItem(
                                                text = {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(acc.accountEmail, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal)
                                                        if (isActive) {
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                                        }
                                                    }
                                                },
                                                onClick = {
                                                    showAccountMenu = false
                                                    viewModel.switchAccount(acc.accountEmail)
                                                }
                                            )
                                        }
                                        DropdownMenuItem(
                                            text = { Text("+ Add Another Account") },
                                            onClick = {
                                                showAccountMenu = false
                                                signInLauncher.launch(viewModel.getSignInIntent())
                                            },
                                            leadingIcon = { Icon(Icons.Default.PersonAdd, contentDescription = null) }
                                        )
                                    }
                                }
                            }

                            IconButton(onClick = { viewModel.refreshAll() }) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh"
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Quota & Account Banner
                item {
                    if (activeAccount == null) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    "Google Drive Disconnected",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Connect your Google account to enable cloud auto-backup and remote file management.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Button(
                                    onClick = { signInLauncher.launch(viewModel.getSignInIntent()) },
                                    modifier = Modifier.testTag("drive_connect_button")
                                ) {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Sign In with Google")
                                }
                            }
                        }
                    } else {
                        // Connected Account Card with Honest Quota
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDone,
                                        contentDescription = null,
                                        tint = Color(0xFF4CAF50),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Connected & Authorized",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF4CAF50)
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                val quota = quotaInfo
                                if (quota != null) {
                                    if (quota.limitBytes > 0) {
                                        val usedStr = MediaFileManager.formatBytes(quota.usageBytes)
                                        val limitStr = MediaFileManager.formatBytes(quota.limitBytes)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Storage Usage", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("$usedStr of $limitStr", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        LinearProgressIndicator(
                                            progress = { (quota.usagePercentage / 100f).coerceIn(0f, 1f) },
                                            color = if (quota.isNearlyFull) Color(0xFFFF5252) else MaterialTheme.colorScheme.primary,
                                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(8.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                        )

                                        if (quota.isNearlyFull) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    "Drive storage is nearly full (${quota.usagePercentage.toInt()}%)",
                                                    fontSize = 11.sp,
                                                    color = Color(0xFFFF5252),
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    } else {
                                        // Honest display for accounts where Google API returns -1 / unlimited
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Storage Quota", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(
                                                "Unlimited / Managed",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                } else {
                                    Text(
                                        "Storage quota data loading from Google...",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Category Navigation Tabs + "+ Add Folder"
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            DriveCategoryTab.values().forEach { tab ->
                                FilterChip(
                                    selected = currentTab == tab,
                                    onClick = { viewModel.setCategory(tab) },
                                    label = {
                                        Text(
                                            text = tab.name.lowercase().replaceFirstChar { it.uppercase() },
                                            fontSize = 11.sp
                                        )
                                    }
                                )
                            }
                        }

                        if (activeAccount != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            FilledTonalButton(
                                onClick = { showCreateFolderDialog = true },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("drive_add_folder_button")
                            ) {
                                Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("New Folder", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                // 3. Current Folder Breadcrumb
                currentFolder?.let { folder ->
                    item {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.navigateUp() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Folder: ${folder.fileName} (Tap to go back)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                // 4. File / Folder List
                if (isLoading || isRefreshing) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                if (!isLoading && !isRefreshing && driveFiles.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.size(54.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    "No files found in Google Drive",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Files uploaded through Cloud Set Storage will appear here.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (!isLoading && !isRefreshing && driveFiles.isNotEmpty()) {
                    items(driveFiles, key = { it.fileId }) { file ->
                        DriveFileItemRow(
                            file = file,
                            onClick = {
                                if (file.isFolder) {
                                    viewModel.openFolder(file)
                                } else if (!file.webViewLink.isNullOrBlank()) {
                                    try {
                                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(file.webViewLink))
                                        context.startActivity(browserIntent)
                                    } catch (_: Exception) {}
                                }
                            },
                            onDeleteFolder = if (file.isFolder) { { folderToDelete = file } } else null
                        )
                    }
                }
            }
        }

        // Floating Bubble Bottom Navigation
        FloatingBubbleNavBar(
            currentTab = BubbleNavTab.DRIVE,
            onTabSelected = onNavigateToTab,
            modifier = Modifier.align(Alignment.BottomCenter),
            isVisible = isNavBarVisible
        )
    }
}

@Composable
private fun DriveFileItemRow(
    file: DriveFileResult,
    onClick: () -> Unit,
    onDeleteFolder: (() -> Unit)? = null
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (file.isFolder) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.DarkGray),
                contentAlignment = Alignment.Center
            ) {
                if (file.isFolder) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = "Folder",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                } else if (!file.thumbnailLink.isNullOrBlank()) {
                    AsyncImage(
                        model = file.thumbnailLink,
                        contentDescription = file.fileName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    val icon = when {
                        file.mimeType.contains("video", ignoreCase = true) -> Icons.Default.Movie
                        file.mimeType.contains("image", ignoreCase = true) -> Icons.Default.Image
                        else -> Icons.Default.Description
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.fileName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (file.isFolder) {
                        Text("Folder", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    } else if (file.sizeBytes > 0) {
                        Text(MediaFileManager.formatBytes(file.sizeBytes), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (file.isFolder && onDeleteFolder != null) {
                IconButton(onClick = onDeleteFolder) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Folder",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else if (!file.isFolder && !file.webViewLink.isNullOrBlank()) {
                IconButton(onClick = onClick) {
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = "Open in Drive",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
