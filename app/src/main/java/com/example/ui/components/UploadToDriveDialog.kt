package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.GoogleAccountEntity
import com.example.drive.model.DriveFileResult

data class DriveFolderOption(
    val id: String?,
    val name: String,
    val isCategory: Boolean = false
)

@Composable
fun UploadToDriveDialog(
    fileName: String,
    mediaType: String,
    accounts: List<GoogleAccountEntity>,
    activeAccount: GoogleAccountEntity?,
    existingFolders: List<DriveFileResult> = emptyList(),
    isCreatingFolder: Boolean = false,
    onCreateFolder: (String) -> Unit = {},
    onDismiss: () -> Unit,
    onConfirmUpload: (targetEmail: String, folderId: String?) -> Unit
) {
    var selectedAccountEmail by remember {
        mutableStateOf(activeAccount?.accountEmail ?: accounts.firstOrNull()?.accountEmail.orEmpty())
    }

    var selectedFolderId by remember { mutableStateOf<String?>(null) }
    var selectedFolderName by remember {
        mutableStateOf(
            when (mediaType.uppercase()) {
                "VIDEO" -> "Videos"
                "DOCUMENT" -> "Documents"
                else -> "Images"
            }
        )
    }

    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderNameInput by remember { mutableStateOf("") }

    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("Create Drive Folder", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Enter a folder name to create in Google Drive:", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newFolderNameInput,
                        onValueChange = { newFolderNameInput = it },
                        label = { Text("Folder Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFolderNameInput.isNotBlank()) {
                            onCreateFolder(newFolderNameInput.trim())
                            showCreateFolderDialog = false
                            newFolderNameInput = ""
                        }
                    },
                    enabled = newFolderNameInput.isNotBlank() && !isCreatingFolder
                ) {
                    if (isCreatingFolder) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                    } else {
                        Text("Create")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("Upload to Google Drive", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(fileName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (accounts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No Google account connected.\nPlease connect an account in Drive or Settings.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    // 1. Account Selector
                    Text("Select Google Account", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))

                    LazyColumn(modifier = Modifier.height(80.dp)) {
                        items(accounts, key = { it.accountEmail }) { acc ->
                            val isAccSelected = acc.accountEmail == selectedAccountEmail
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isAccSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                border = if (isAccSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clickable { selectedAccountEmail = acc.accountEmail }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AccountCircle,
                                        contentDescription = null,
                                        tint = if (isAccSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        acc.accountEmail,
                                        fontSize = 12.sp,
                                        fontWeight = if (isAccSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    if (isAccSelected) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 2. Destination Folder Selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Destination Folder", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = { showCreateFolderDialog = true }) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+ Add Folder", fontSize = 12.sp)
                        }
                    }

                    val defaultFolders = listOf(
                        DriveFolderOption(null, "Default (Root / All Files)", isCategory = true),
                        DriveFolderOption(null, "Images", isCategory = true),
                        DriveFolderOption(null, "Videos", isCategory = true),
                        DriveFolderOption(null, "Documents", isCategory = true)
                    )

                    val customFolders = existingFolders.filter { it.isFolder }.map {
                        DriveFolderOption(it.fileId, it.fileName, isCategory = false)
                    }

                    val allFolderOptions = defaultFolders + customFolders

                    LazyColumn(modifier = Modifier.height(120.dp)) {
                        items(allFolderOptions) { folder ->
                            val isFolderSelected = (folder.id == selectedFolderId && folder.name == selectedFolderName) ||
                                (folder.id == null && folder.name == selectedFolderName)

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isFolderSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .clickable {
                                        selectedFolderId = folder.id
                                        selectedFolderName = folder.name
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = if (isFolderSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        folder.name,
                                        fontSize = 12.sp,
                                        fontWeight = if (isFolderSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    if (isFolderSelected) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedAccountEmail.isNotBlank()) {
                        onConfirmUpload(selectedAccountEmail, selectedFolderId)
                        onDismiss()
                    }
                },
                enabled = accounts.isNotEmpty() && selectedAccountEmail.isNotBlank()
            ) {
                Text("Start Upload")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
