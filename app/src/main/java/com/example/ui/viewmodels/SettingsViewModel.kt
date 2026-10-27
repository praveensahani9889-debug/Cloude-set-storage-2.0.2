package com.example.ui.viewmodels

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.GoogleAccountEntity
import com.example.data.preferences.BackupPreferences
import com.example.data.repository.MediaRepository
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val context: Context,
    private val repository: MediaRepository
) : ViewModel() {

    val preferences: StateFlow<BackupPreferences> = repository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BackupPreferences())

    val activeAccount: StateFlow<GoogleAccountEntity?> = repository.activeAccount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allAccounts: StateFlow<List<GoogleAccountEntity>> = repository.allAccounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cleanableCount: StateFlow<Int> = repository.cleanableCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val cleanableBytes: StateFlow<Long?> = repository.cleanableBytes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun setAutoBackup(enabled: Boolean) {
        viewModelScope.launch {
            repository.preferencesRepository.setAutoBackup(enabled)
        }
    }

    fun setWifiOnly(wifiOnly: Boolean) {
        viewModelScope.launch {
            repository.preferencesRepository.setWifiOnly(wifiOnly)
        }
    }

    fun setBackupWhileCharging(charging: Boolean) {
        viewModelScope.launch {
            repository.preferencesRepository.setBackupWhileCharging(charging)
        }
    }

    fun setBackupPhotos(enabled: Boolean) {
        viewModelScope.launch {
            repository.preferencesRepository.setBackupPhotos(enabled)
        }
    }

    fun setBackupVideos(enabled: Boolean) {
        viewModelScope.launch {
            repository.preferencesRepository.setBackupVideos(enabled)
        }
    }

    fun setBackupDocuments(enabled: Boolean) {
        viewModelScope.launch {
            repository.preferencesRepository.setBackupDocuments(enabled)
        }
    }

    fun setShowNotifications(enabled: Boolean) {
        viewModelScope.launch {
            repository.preferencesRepository.setShowNotifications(enabled)
        }
    }

    fun setCloudFirstMode(enabled: Boolean) {
        viewModelScope.launch {
            repository.preferencesRepository.setCloudFirstMode(enabled)
        }
    }

    fun setAutoCleanupVerified(enabled: Boolean) {
        viewModelScope.launch {
            repository.preferencesRepository.setAutoCleanupVerified(enabled)
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            repository.preferencesRepository.setThemeMode(mode)
        }
    }

    fun setLanguage(lang: String) {
        viewModelScope.launch {
            repository.preferencesRepository.setLanguage(lang)
        }
    }

    fun setGalleryGridColumns(columns: Int) {
        viewModelScope.launch {
            repository.preferencesRepository.setGalleryGridColumns(columns)
        }
    }

    fun cleanVerifiedItems() {
        viewModelScope.launch {
            val cleaned = repository.cleanUpAllVerifiedItems()
            _statusMessage.value = "Cleaned $cleaned local verified file(s)"
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            repository.clearCache()
            _statusMessage.value = "Local staging cache cleared"
        }
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
                }
            } catch (e: Exception) {
                _statusMessage.value = "Sign in error: ${e.message}"
            }
        }
    }

    fun switchAccount(email: String) {
        viewModelScope.launch {
            repository.switchActiveAccount(email)
            _statusMessage.value = "Active Drive account switched to $email"
        }
    }

    fun disconnectAccount(email: String) {
        viewModelScope.launch {
            repository.disconnectAccount(email)
            _statusMessage.value = "Disconnected $email"
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }
}
