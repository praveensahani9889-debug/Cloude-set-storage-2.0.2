package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.preferences.BackupPreferences
import com.example.data.preferences.BackupPreferencesRepository
import com.example.data.repository.MediaRepository
import com.example.ui.components.UploadToDriveDialog
import com.example.ui.navigation.AppNavigation
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.TermsAndConditionsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.util.MediaFileManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class IncomingSharedFile(
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val size: Long
)

class MainActivity : ComponentActivity() {

    private lateinit var preferencesRepo: BackupPreferencesRepository
    private lateinit var mediaRepository: MediaRepository

    // State for incoming shared files
    private val incomingFilesState = mutableStateOf<List<IncomingSharedFile>>(emptyList())
    private val sourceAppNameState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        preferencesRepo = BackupPreferencesRepository(this)
        mediaRepository = MediaRepository(this)

        handleShareIntent(intent)

        setContent {
            val initialAccepted = remember { preferencesRepo.isTermsAcceptedSync() }
            var termsAcceptedLocally by remember { mutableStateOf(initialAccepted) }

            val prefs by preferencesRepo.preferencesFlow.collectAsStateWithLifecycle(
                initialValue = BackupPreferences(termsAccepted = initialAccepted)
            )

            val isDark = when (prefs.themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> isSystemInDarkTheme()
            }

            val incomingFiles by incomingFilesState
            val sourceAppName by sourceAppNameState
            val scope = rememberCoroutineScope()

            var showSplash by remember { mutableStateOf(true) }
            LaunchedEffect(Unit) {
                delay(1200)
                showSplash = false
            }

            MyApplicationTheme(darkTheme = isDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (showSplash) {
                        SplashScreen()
                    } else if (!termsAcceptedLocally && !prefs.termsAccepted) {
                        TermsAndConditionsScreen(
                            preferencesRepo = preferencesRepo,
                            onAccepted = {
                                termsAcceptedLocally = true
                            }
                        )
                    } else {
                        AppNavigation()

                        // Dialog for handling incoming shared files from other apps
                        if (incomingFiles.isNotEmpty()) {
                            IncomingShareDialog(
                                sourceAppName = sourceAppName ?: "External Application",
                                files = incomingFiles,
                                mediaRepository = mediaRepository,
                                onDismiss = {
                                    incomingFilesState.value = emptyList()
                                    sourceAppNameState.value = null
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val type = intent.type ?: return

        if (action == Intent.ACTION_SEND || action == Intent.ACTION_SEND_MULTIPLE) {
            val sourceApp = resolveSourceAppName(intent)
            sourceAppNameState.value = sourceApp

            val uris = mutableListOf<Uri>()
            if (action == Intent.ACTION_SEND) {
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (uri != null) {
                    uris.add(uri)
                }
            } else if (action == Intent.ACTION_SEND_MULTIPLE) {
                val uriList = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                if (uriList != null) {
                    uris.addAll(uriList)
                }
            }

            val parsedFiles = uris.map { uri ->
                val (name, size) = queryFileInfo(uri)
                val mime = contentResolver.getType(uri) ?: type
                IncomingSharedFile(uri, name, mime, size)
            }

            if (parsedFiles.isNotEmpty()) {
                incomingFilesState.value = parsedFiles
            }
        }
    }

    private fun resolveSourceAppName(intent: Intent): String {
        val callingPkg = callingPackage
            ?: intent.getStringExtra(Intent.EXTRA_REFERRER_NAME)
            ?: referrer?.authority

        if (!callingPkg.isNullOrBlank()) {
            try {
                val pm = packageManager
                val appInfo = pm.getApplicationInfo(callingPkg, 0)
                return pm.getApplicationLabel(appInfo).toString()
            } catch (_: Exception) {
                return callingPkg
            }
        }
        return "External Application"
    }

    private fun queryFileInfo(uri: Uri): Pair<String, Long> {
        var name = "Shared_File_${System.currentTimeMillis()}"
        var size = 0L
        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) name = cursor.getString(nameIndex) ?: name
                    if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
                }
            }
        } catch (_: Exception) {}
        return Pair(name, size)
    }
}

@Composable
fun IncomingShareDialog(
    sourceAppName: String,
    files: List<IncomingSharedFile>,
    mediaRepository: MediaRepository,
    onDismiss: () -> Unit
) {
    val accounts by mediaRepository.allAccounts.collectAsStateWithLifecycle(initialValue = emptyList())
    val activeAccount by mediaRepository.activeAccount.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    var isUploading by remember { mutableStateOf(false) }

    UploadToDriveDialog(
        fileName = if (files.size == 1) files[0].name else "${files.size} items from $sourceAppName",
        mediaType = if (files.any { it.mimeType.startsWith("video/") }) "VIDEO" else "PHOTO",
        accounts = accounts,
        activeAccount = activeAccount,
        existingFolders = emptyList(),
        onDismiss = onDismiss,
        onConfirmUpload = { targetEmail, folderId ->
            scope.launch {
                isUploading = true
                for (file in files) {
                    if (file.mimeType.startsWith("image/") || file.mimeType.startsWith("video/")) {
                        mediaRepository.importUserSelectedMedia(
                            sourceUri = file.uri,
                            targetFolderId = folderId,
                            forceUpload = true,
                            targetAccountId = targetEmail,
                            sourceApp = sourceAppName
                        )
                    } else {
                        mediaRepository.importDocument(
                            sourceUri = file.uri,
                            targetFolderId = folderId,
                            targetAccountId = targetEmail
                        )
                    }
                }
                isUploading = false
                onDismiss()
            }
        }
    )
}
