package com.example.ui.viewmodels

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.GoogleAccountEntity
import com.example.data.local.entity.MediaItemEntity
import com.example.data.local.model.UploadState
import com.example.data.repository.MediaRepository
import com.example.drive.DriveApiException
import com.example.drive.model.DriveFileResult
import com.example.drive.model.QuotaInfo
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class DriveCategoryTab {
    ALL,
    IMAGES,
    VIDEOS,
    DOCUMENTS
}

class DriveViewModel(
    private val context: Context,
    private val repository: MediaRepository
) : ViewModel() {

    val activeAccount: StateFlow<GoogleAccountEntity?> = repository.activeAccount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allAccounts: StateFlow<List<GoogleAccountEntity>> = repository.allAccounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingUploads: StateFlow<List<MediaItemEntity>> = repository.allMedia
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val waitingCount: StateFlow<Int> = repository.waitingCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val uploadingCount: StateFlow<Int> = repository.uploadingCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val failedCount: StateFlow<Int> = repository.failedCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _currentTab = MutableStateFlow(DriveCategoryTab.ALL)
    val currentTab: StateFlow<DriveCategoryTab> = _currentTab.asStateFlow()

    private val _currentFolder = MutableStateFlow<DriveFileResult?>(null)
    val currentFolder: StateFlow<DriveFileResult?> = _currentFolder.asStateFlow()

    private val _driveFiles = MutableStateFlow<List<DriveFileResult>>(emptyList())
    val driveFiles: StateFlow<List<DriveFileResult>> = _driveFiles.asStateFlow()

    private val _quotaInfo = MutableStateFlow<QuotaInfo?>(null)
    val quotaInfo: StateFlow<QuotaInfo?> = _quotaInfo.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _apiDisabledDialogInfo = MutableStateFlow<String?>(null)
    val apiDisabledDialogInfo: StateFlow<String?> = _apiDisabledDialogInfo.asStateFlow()

    private val _isCreatingFolder = MutableStateFlow(false)
    val isCreatingFolder: StateFlow<Boolean> = _isCreatingFolder.asStateFlow()

    init {
        refreshAll()
    }

    fun getSignInIntent(): Intent = repository.authManager.getSignInIntent()

    fun handleSignInResult(intent: Intent?) {
        viewModelScope.launch {
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(intent)
                val account = task.getResult(ApiException::class.java)
                if (account != null) {
                    repository.authManager.handleSignInResult(account)
                    _statusMessage.value = "Connected Google Drive for ${account.email}"
                    refreshAll()
                } else {
                    _statusMessage.value = "Google sign-in was canceled"
                }
            } catch (e: Exception) {
                _statusMessage.value = "Sign-in error: ${e.message}"
            }
        }
    }

    fun switchAccount(email: String) {
        viewModelScope.launch {
            repository.switchActiveAccount(email)
            _statusMessage.value = "Switched to account $email"
            _currentFolder.value = null
            refreshAll()
        }
    }

    fun disconnectAccount(email: String) {
        viewModelScope.launch {
            repository.disconnectAccount(email)
            _statusMessage.value = "Disconnected $email"
            _currentFolder.value = null
            _driveFiles.value = emptyList()
            _quotaInfo.value = null
        }
    }

    fun setCategory(tab: DriveCategoryTab) {
        _currentTab.value = tab
        _currentFolder.value = null
        loadDriveFiles()
    }

    fun openFolder(folder: DriveFileResult) {
        _currentFolder.value = folder
        loadDriveFiles()
    }

    fun navigateUp() {
        _currentFolder.value = null
        loadDriveFiles()
    }

    fun refreshAll() {
        viewModelScope.launch {
            _isRefreshing.value = true
            loadQuota()
            loadDriveFiles()
            _isRefreshing.value = false
        }
    }

    private fun loadQuota() {
        viewModelScope.launch {
            val result = repository.refreshQuotaForActiveAccount()
            result.getOrNull()?.let {
                _quotaInfo.value = it
            }
        }
    }

    fun loadDriveFiles() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val categoryStr = when (_currentTab.value) {
                    DriveCategoryTab.ALL -> null
                    DriveCategoryTab.IMAGES -> "IMAGES"
                    DriveCategoryTab.VIDEOS -> "VIDEOS"
                    DriveCategoryTab.DOCUMENTS -> "DOCUMENTS"
                }
                val folderId = _currentFolder.value?.fileId
                val result = repository.listDriveFiles(category = categoryStr, folderId = folderId)
                if (result.isSuccess) {
                    _driveFiles.value = result.getOrThrow()
                } else {
                    _driveFiles.value = emptyList()
                    val err = result.exceptionOrNull()
                    if (err is DriveApiException && err.isApiDisabled) {
                        _apiDisabledDialogInfo.value = err.getActionableUserMessage()
                    }
                }
            } catch (e: Exception) {
                if (e is DriveApiException && e.isApiDisabled) {
                    _apiDisabledDialogInfo.value = e.getActionableUserMessage()
                } else {
                    _statusMessage.value = "Failed to load Drive files: ${e.message}"
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun createFolder(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            _statusMessage.value = "Folder name cannot be empty"
            return
        }
        if (trimmed.contains("/") || trimmed.contains("\\")) {
            _statusMessage.value = "Folder name cannot contain slashes"
            return
        }

        viewModelScope.launch {
            _isCreatingFolder.value = true
            _isLoading.value = true
            try {
                val parentId = _currentFolder.value?.fileId
                val result = repository.createDriveFolder(trimmed, parentFolderId = parentId)
                if (result.isSuccess) {
                    _statusMessage.value = "Folder '$trimmed' created successfully in Google Drive"
                    loadDriveFiles()
                } else {
                    val err = result.exceptionOrNull()
                    if (err is DriveApiException && err.isApiDisabled) {
                        _apiDisabledDialogInfo.value = err.getActionableUserMessage()
                        _statusMessage.value = "Google Drive API is disabled in Cloud Console"
                    } else if (err is DriveApiException && err.isQuotaExceeded) {
                        _statusMessage.value = "Google Drive storage quota exceeded"
                    } else {
                        _statusMessage.value = "Failed to create folder: ${err?.message ?: "Unknown error"}"
                    }
                }
            } catch (e: Exception) {
                if (e is DriveApiException && e.isApiDisabled) {
                    _apiDisabledDialogInfo.value = e.getActionableUserMessage()
                } else {
                    _statusMessage.value = "Folder creation error: ${e.message}"
                }
            } finally {
                _isCreatingFolder.value = false
                _isLoading.value = false
            }
        }
    }

    fun dismissApiDisabledDialog() {
        _apiDisabledDialogInfo.value = null
    }

    fun retryUpload(id: Long) {
        viewModelScope.launch {
            repository.retryUpload(id)
            _statusMessage.value = "Retrying upload..."
        }
    }

    fun trashFolder(folder: DriveFileResult) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = repository.trashDriveFolder(folder.fileId)
                if (result.isSuccess) {
                    _statusMessage.value = "Moved folder '${folder.fileName}' to Google Drive Trash"
                    loadDriveFiles()
                } else {
                    _statusMessage.value = "Failed to trash folder: ${result.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                _statusMessage.value = "Error trashing folder: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun retryAllFailed() {
        viewModelScope.launch {
            repository.retryAllFailed()
            _statusMessage.value = "Retrying all failed uploads..."
        }
    }

    fun cancelUpload(id: Long) {
        viewModelScope.launch {
            repository.cancelUpload(id)
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }
}
