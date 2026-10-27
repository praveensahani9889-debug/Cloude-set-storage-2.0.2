package com.example.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.GoogleAccountEntity
import com.example.data.repository.MediaRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class DashboardUiState(
    val activeAccount: GoogleAccountEntity? = null,
    val waitingCount: Int = 0,
    val uploadingCount: Int = 0,
    val uploadedCount: Int = 0,
    val failedCount: Int = 0,
    val lastBackupTimestamp: Long? = null,
    val isRefreshingQuota: Boolean = false,
    val errorMessage: String? = null
)

class DashboardViewModel(private val repository: MediaRepository) : ViewModel() {

    val activeAccount: StateFlow<GoogleAccountEntity?> = repository.activeAccount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val waitingCount: StateFlow<Int> = repository.waitingCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val uploadingCount: StateFlow<Int> = repository.uploadingCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val uploadedCount: StateFlow<Int> = repository.uploadedCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val failedCount: StateFlow<Int> = repository.failedCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val lastBackupTimestamp: StateFlow<Long?> = repository.lastSuccessfulUploadTime
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val preferences = repository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.example.data.preferences.BackupPreferences())

    val pendingBytes: StateFlow<Long> = repository.pendingBytes
        .map { it ?: 0L }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val cleanableBytes: StateFlow<Long> = repository.cleanableBytes
        .map { it ?: 0L }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    fun refreshQuota() {
        viewModelScope.launch {
            repository.refreshQuotaForActiveAccount()
        }
    }

    fun retryAllFailed() {
        viewModelScope.launch {
            repository.retryAllFailed()
        }
    }
}
