package com.example.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.GoogleAccountEntity
import com.example.data.local.entity.MediaItemEntity
import com.example.data.preferences.BackupPreferences
import com.example.data.repository.MediaRepository
import com.example.util.MediaFileManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class StorageViewModel(
    private val context: Context,
    private val repository: MediaRepository
) : ViewModel() {

    private val _availableInternalBytes = MutableStateFlow(MediaFileManager.getFreeInternalStorageBytes(context))
    val availableInternalBytes: StateFlow<Long> = _availableInternalBytes.asStateFlow()

    private val _totalInternalBytes = MutableStateFlow(MediaFileManager.getTotalInternalStorageBytes(context))
    val totalInternalBytes: StateFlow<Long> = _totalInternalBytes.asStateFlow()

    private val _stagingStorageBytes = MutableStateFlow(MediaFileManager.getAppStagingStorageBytes(context))
    val stagingStorageBytes: StateFlow<Long> = _stagingStorageBytes.asStateFlow()

    val cleanableCount: StateFlow<Int> = repository.cleanableCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val cleanableBytes: StateFlow<Long> = repository.cleanableBytes
        .map { it ?: 0L }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val pendingBytes: StateFlow<Long> = repository.pendingBytes
        .map { it ?: 0L }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val waitingCount: StateFlow<Int> = repository.waitingCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val failedCount: StateFlow<Int> = repository.failedCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val activeAccount: StateFlow<GoogleAccountEntity?> = repository.activeAccount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val preferences: StateFlow<BackupPreferences> = repository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BackupPreferences())

    private val _nonCleanableItems = MutableStateFlow<List<MediaItemEntity>>(emptyList())
    val nonCleanableItems: StateFlow<List<MediaItemEntity>> = _nonCleanableItems.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    init {
        refreshStorageMetrics()
    }

    fun refreshStorageMetrics() {
        viewModelScope.launch {
            _availableInternalBytes.value = MediaFileManager.getFreeInternalStorageBytes(context)
            _totalInternalBytes.value = MediaFileManager.getTotalInternalStorageBytes(context)
            _stagingStorageBytes.value = MediaFileManager.getAppStagingStorageBytes(context)
            _nonCleanableItems.value = repository.getNonCleanableItems()
            repository.refreshQuotaForActiveAccount()
        }
    }

    fun cleanUpVerifiedFiles() {
        viewModelScope.launch {
            val count = repository.cleanUpAllVerifiedItems()
            refreshStorageMetrics()
            _statusMessage.value = if (count > 0) {
                "Safely freed local storage for $count verified Drive files"
            } else {
                "No verified local copies ready for cleanup"
            }
        }
    }

    fun retryAllFailed() {
        viewModelScope.launch {
            repository.retryAllFailed()
            _statusMessage.value = "Scheduled retry for all failed uploads"
        }
    }

    fun setStagingLimitMb(mb: Int) {
        viewModelScope.launch {
            repository.preferencesRepository.setMaxStagingStorageMb(mb)
        }
    }

    fun setAutoCleanupVerified(enabled: Boolean) {
        viewModelScope.launch {
            repository.preferencesRepository.setAutoCleanupVerified(enabled)
        }
    }

    fun setCloudFirstMode(enabled: Boolean) {
        viewModelScope.launch {
            repository.preferencesRepository.setCloudFirstMode(enabled)
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }
}
