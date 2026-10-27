package com.example.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.MediaItemEntity
import com.example.data.local.model.UploadState
import com.example.data.repository.MediaRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class UploadCenterViewModel(private val repository: MediaRepository) : ViewModel() {

    val allUploadTasks: StateFlow<List<MediaItemEntity>> = repository.allMedia
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeUploads: StateFlow<List<MediaItemEntity>> = allUploadTasks.map { list ->
        list.filter { it.uploadState in listOf(UploadState.QUEUED, UploadState.UPLOADING, UploadState.RETRY_WAIT) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val failedUploads: StateFlow<List<MediaItemEntity>> = allUploadTasks.map { list ->
        list.filter { it.uploadState in listOf(UploadState.FAILED, UploadState.NEEDS_AUTH) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completedUploads: StateFlow<List<MediaItemEntity>> = allUploadTasks.map { list ->
        list.filter { it.uploadState == UploadState.UPLOADED }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun retryUpload(id: Long) {
        viewModelScope.launch {
            repository.retryUpload(id)
        }
    }

    fun cancelUpload(id: Long) {
        viewModelScope.launch {
            repository.cancelUpload(id)
        }
    }

    fun retryAllFailed() {
        viewModelScope.launch {
            repository.retryAllFailed()
        }
    }
}
