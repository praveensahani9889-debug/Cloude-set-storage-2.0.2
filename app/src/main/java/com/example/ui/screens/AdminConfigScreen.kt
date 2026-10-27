package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.repository.MediaRepository
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

const val AUTHORIZED_ADMIN_EMAIL = "praveensahani9889@gmail.com"
const val FIREBASE_RTDB_URL = "https://cloud-set-storage-default-rtdb.firebaseio.com"

@Composable
fun AdminConfigScreen(
    repository: MediaRepository,
    onNavigateBack: () -> Unit
) {
    val activeAccount by repository.activeAccount.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val userEmail = activeAccount?.accountEmail.orEmpty().trim()
    val isAuthorized = userEmail.equals(AUTHORIZED_ADMIN_EMAIL, ignoreCase = true)

    // Form inputs
    var driveApiVersion by remember { mutableStateOf("v3") }
    var maxUploadRetries by remember { mutableStateOf("3") }
    var lowStorageThresholdMb by remember { mutableStateOf("100") }
    var defaultDriveFolder by remember { mutableStateOf("Cloud Set Storage") }

    var isTestingConnection by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var connectionStatus by remember { mutableStateOf<String?>(null) }
    var isConnectionSuccess by remember { mutableStateOf<Boolean?>(null) }

    val httpClient = remember {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    Scaffold(
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
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("admin_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.AdminPanelSettings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Admin API Diagnostics",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Authorized: $AUTHORIZED_ADMIN_EMAIL",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        if (!isAuthorized) {
            // Access Denied screen for any non-admin account
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Access Restricted",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "This configuration panel is strictly restricted to administrator account:\n$AUTHORIZED_ADMIN_EMAIL\n\nCurrent session: ${if (userEmail.isNotBlank()) userEmail else "Not Authenticated"}",
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Button(
                            onClick = onNavigateBack,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Return to App")
                        }
                    }
                }
            }
        } else {
            // Authorized Administrator Panel
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Testing Feature Notice Banner
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Temporary Diagnostic Mode",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Enforced by verified administrator email. Confidential secrets and private keys are never exposed in client storage.",
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Backend Endpoint Card
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Backend Configuration", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Firebase Project: cloud-set-storage", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                        Text("Application ID: com.cloude.set.storage.praveen", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                        Text("RTDB Endpoint: $FIREBASE_RTDB_URL", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }

                // API Parameters Form
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("API & Sync Thresholds", fontWeight = FontWeight.Bold, fontSize = 14.sp)

                        OutlinedTextField(
                            value = driveApiVersion,
                            onValueChange = { driveApiVersion = it },
                            label = { Text("Google Drive API Version") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = maxUploadRetries,
                            onValueChange = { if (it.all { char -> char.isDigit() }) maxUploadRetries = it },
                            label = { Text("Max Upload Worker Retries (1-5)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = lowStorageThresholdMb,
                            onValueChange = { if (it.all { char -> char.isDigit() }) lowStorageThresholdMb = it },
                            label = { Text("Low Storage Warning Threshold (MB)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = defaultDriveFolder,
                            onValueChange = { defaultDriveFolder = it },
                            label = { Text("Default Remote Drive Folder Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Connectivity & Verification Status
                if (connectionStatus != null) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isConnectionSuccess == true) StatusSuccess.copy(alpha = 0.15f) else StatusError.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isConnectionSuccess == true) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = null,
                                tint = if (isConnectionSuccess == true) StatusSuccess else StatusError,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = connectionStatus.orEmpty(),
                                fontSize = 12.sp,
                                color = if (isConnectionSuccess == true) StatusSuccess else StatusError
                            )
                        }
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                isTestingConnection = true
                                connectionStatus = "Testing Firebase RTDB connectivity..."
                                withContext(Dispatchers.IO) {
                                    try {
                                        val req = Request.Builder()
                                            .url("$FIREBASE_RTDB_URL/.json?shallow=true")
                                            .get()
                                            .build()
                                        httpClient.newCall(req).execute().use { res ->
                                            if (res.isSuccessful || res.code == 401 || res.code == 403) {
                                                isConnectionSuccess = true
                                                connectionStatus = "Firebase RTDB reachable (HTTP ${res.code}). Ready for secure admin transactions."
                                            } else {
                                                isConnectionSuccess = false
                                                connectionStatus = "Server response: HTTP ${res.code}"
                                            }
                                        }
                                    } catch (e: Exception) {
                                        isConnectionSuccess = false
                                        connectionStatus = "Connection error: ${e.message}"
                                    } finally {
                                        isTestingConnection = false
                                    }
                                }
                            }
                        },
                        enabled = !isTestingConnection && !isSaving,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isTestingConnection) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                        } else {
                            Text("Test Connectivity")
                        }
                    }

                    Button(
                        onClick = {
                            // Validate before saving
                            val retries = maxUploadRetries.toIntOrNull()
                            val threshold = lowStorageThresholdMb.toIntOrNull()
                            if (retries == null || retries !in 1..10) {
                                scope.launch { snackbarHostState.showSnackbar("Invalid retries: must be between 1 and 10") }
                                return@Button
                            }
                            if (threshold == null || threshold !in 20..5000) {
                                scope.launch { snackbarHostState.showSnackbar("Invalid threshold: must be between 20MB and 5000MB") }
                                return@Button
                            }

                            scope.launch {
                                isSaving = true
                                connectionStatus = "Saving verified configuration to Firebase..."
                                withContext(Dispatchers.IO) {
                                    try {
                                        val payload = JSONObject().apply {
                                            put("drive_api_version", driveApiVersion.trim())
                                            put("max_upload_retries", retries)
                                            put("low_storage_warning_mb", threshold)
                                            put("default_drive_folder", defaultDriveFolder.trim())
                                            put("updated_by", AUTHORIZED_ADMIN_EMAIL)
                                            put("updated_timestamp", System.currentTimeMillis())
                                        }
                                        val req = Request.Builder()
                                            .url("$FIREBASE_RTDB_URL/app_config.json")
                                            .put(payload.toString().toRequestBody("application/json; charset=UTF-8".toMediaType()))
                                            .build()
                                        httpClient.newCall(req).execute().use { res ->
                                            if (res.isSuccessful) {
                                                isConnectionSuccess = true
                                                connectionStatus = "Successfully updated configuration in Firebase!"
                                            } else {
                                                isConnectionSuccess = false
                                                connectionStatus = "Save failed (HTTP ${res.code}): ${res.body?.string().orEmpty().take(80)}"
                                            }
                                        }
                                    } catch (e: Exception) {
                                        isConnectionSuccess = false
                                        connectionStatus = "Save error: ${e.message}"
                                    } finally {
                                        isSaving = false
                                    }
                                }
                            }
                        },
                        enabled = !isTestingConnection && !isSaving,
                        colors = ButtonDefaults.buttonColors(containerColor = StatusSuccess),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                        } else {
                            Text("Save Config")
                        }
                    }
                }
            }
        }
    }
}
