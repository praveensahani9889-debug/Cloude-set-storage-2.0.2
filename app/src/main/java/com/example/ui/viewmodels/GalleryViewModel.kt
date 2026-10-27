package com.example.ui.viewmodels

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.GoogleAccountEntity
import com.example.data.repository.MediaRepository
import com.example.drive.model.DriveFileResult
import com.example.util.DeviceMediaItem
import com.example.util.LocalMediaDiscovery
import com.example.util.MediaAlbum
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class GalleryViewModel(
    private val context: Context,
    private val repository: MediaRepository
) : ViewModel() {

    val activeAccount: StateFlow<GoogleAccountEntity?> = repository.activeAccount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allAccounts: StateFlow<List<GoogleAccountEntity>> = repository.allAccounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _albums = MutableStateFlow<List<MediaAlbum>>(emptyList())
    val albums: StateFlow<List<MediaAlbum>> = _albums.asStateFlow()

    private val _selectedAlbumIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedAlbumIds: StateFlow<Set<String>> = _selectedAlbumIds.asStateFlow()

    private val _selectedAlbum = MutableStateFlow<MediaAlbum?>(null)
    val selectedAlbum: StateFlow<MediaAlbum?> = _selectedAlbum.asStateFlow()

    private val _albumItems = MutableStateFlow<List<DeviceMediaItem>>(emptyList())
    val albumItems: StateFlow<List<DeviceMediaItem>> = _albumItems.asStateFlow()

    private val _selectedItemIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedItemIds: StateFlow<Set<Long>> = _selectedItemIds.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _driveFolders = MutableStateFlow<List<DriveFileResult>>(emptyList())
    val driveFolders: StateFlow<List<DriveFileResult>> = _driveFolders.asStateFlow()

    private val _sortOrder = MutableStateFlow(com.example.util.AlbumSortOrder.NAME_ASC)
    val sortOrder: StateFlow<com.example.util.AlbumSortOrder> = _sortOrder.asStateFlow()

    init {
        loadAlbums()
        loadDriveFolders()
    }

    fun setSortOrder(order: com.example.util.AlbumSortOrder) {
        _sortOrder.value = order
        loadAlbums()
    }

    fun loadAlbums() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val list = LocalMediaDiscovery.loadAlbums(context, _sortOrder.value)
                _albums.value = list
            } catch (e: Exception) {
                _statusMessage.value = "Failed to load albums: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteAlbum(album: MediaAlbum) {
        viewModelScope.launch {
            val (success, msg) = LocalMediaDiscovery.deleteAlbumFiles(context, album)
            _statusMessage.value = msg
            if (success) {
                loadAlbums()
            }
        }
    }

    fun openAlbum(album: MediaAlbum) {
        _selectedAlbum.value = album
        _selectedItemIds.value = emptySet()
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val items = LocalMediaDiscovery.loadAlbumItems(context, album.id)
                _albumItems.value = items
            } catch (e: Exception) {
                _statusMessage.value = "Failed to open album: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun backToAlbums() {
        _selectedAlbum.value = null
        _albumItems.value = emptyList()
        _selectedItemIds.value = emptySet()
        loadAlbums()
    }

    fun toggleAlbumSelection(albumId: String) {
        val current = _selectedAlbumIds.value
        _selectedAlbumIds.value = if (current.contains(albumId)) current - albumId else current + albumId
    }

    fun selectAllAlbums() {
        val allIds = _albums.value.map { it.id }.toSet()
        _selectedAlbumIds.value = if (_selectedAlbumIds.value.size == allIds.size) emptySet() else allIds
    }

    fun clearAlbumSelection() {
        _selectedAlbumIds.value = emptySet()
    }

    fun deleteSelectedAlbums() {
        val toDelete = _albums.value.filter { _selectedAlbumIds.value.contains(it.id) }
        viewModelScope.launch {
            _isLoading.value = true
            var deletedCount = 0
            for (album in toDelete) {
                val (success, _) = LocalMediaDiscovery.deleteAlbumFiles(context, album)
                if (success) deletedCount++
            }
            clearAlbumSelection()
            loadAlbums()
            _statusMessage.value = if (deletedCount > 0) "Deleted $deletedCount folder(s)" else "System folders cannot be deleted"
            _isLoading.value = false
        }
    }

    fun toggleItemSelection(id: Long) {
        val current = _selectedItemIds.value
        _selectedItemIds.value = if (current.contains(id)) current - id else current + id
    }

    fun selectAllItems() {
        val allIds = _albumItems.value.map { it.id }.toSet()
        _selectedItemIds.value = if (_selectedItemIds.value.size == allIds.size) emptySet() else allIds
    }

    fun clearSelection() {
        _selectedItemIds.value = emptySet()
    }

    fun deleteSelectedItems() {
        val ids = _selectedItemIds.value
        val itemsToDelete = _albumItems.value.filter { ids.contains(it.id) }
        if (itemsToDelete.isEmpty()) return

        viewModelScope.launch {
            _isLoading.value = true
            var deleted = 0
            for (item in itemsToDelete) {
                try {
                    if (item.path.isNotBlank()) {
                        val file = File(item.path)
                        if (file.exists() && file.delete()) {
                            deleted++
                        }
                    }
                    repository.mediaDao.deleteByPathOrUri(item.path, item.uri.toString())
                } catch (_: Exception) {}
            }
            _albumItems.value = _albumItems.value.filterNot { ids.contains(it.id) }
            clearSelection()
            _statusMessage.value = "Deleted $deleted item(s)"
            _isLoading.value = false
        }
    }

    fun moveSelectedItems(targetAlbumName: String) {
        val ids = _selectedItemIds.value
        val itemsToMove = _albumItems.value.filter { ids.contains(it.id) }
        if (itemsToMove.isEmpty()) return

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val (count, msg) = LocalMediaDiscovery.moveMediaItems(
                    context = context,
                    items = itemsToMove,
                    destinationAlbumName = targetAlbumName,
                    mediaDao = repository.mediaDao
                )
                clearSelection()
                _selectedAlbum.value?.let { currentAlbum ->
                    val updated = LocalMediaDiscovery.loadAlbumItems(context, currentAlbum.id)
                    _albumItems.value = updated
                }
                val albumsList = LocalMediaDiscovery.loadAlbums(context, _sortOrder.value)
                _albums.value = albumsList
                _statusMessage.value = msg
            } catch (e: Exception) {
                _statusMessage.value = "Failed to move items: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun uploadItemToDrive(
        item: DeviceMediaItem,
        targetEmail: String,
        folderId: String?
    ) {
        viewModelScope.launch {
            try {
                val result = if (item.path.isNotBlank() && File(item.path).exists()) {
                    repository.saveCapturedMedia(
                        file = File(item.path),
                        uri = item.uri,
                        mediaType = if (item.isVideo) "VIDEO" else "PHOTO",
                        targetFolderId = folderId,
                        forceUpload = true,
                        targetAccountId = targetEmail
                    )
                } else {
                    repository.importUserSelectedMedia(
                        sourceUri = item.uri,
                        targetFolderId = folderId,
                        forceUpload = true,
                        targetAccountId = targetEmail
                    )
                }

                if (result.isSuccess) {
                    _statusMessage.value = "Upload queued for ${item.displayName}"
                } else {
                    _statusMessage.value = "Failed to queue upload: ${result.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                _statusMessage.value = "Upload error: ${e.message}"
            }
        }
    }

    fun uploadSelectedToDrive(targetEmail: String, folderId: String?) {
        val selectedIds = _selectedItemIds.value
        val itemsToUpload = _albumItems.value.filter { selectedIds.contains(it.id) }
        viewModelScope.launch {
            for (item in itemsToUpload) {
                uploadItemToDrive(item, targetEmail, folderId)
            }
            clearSelection()
            _statusMessage.value = "Queued ${itemsToUpload.size} items for Drive upload"
        }
    }

    fun importMediaFromUri(uri: Uri) {
        viewModelScope.launch {
            val result = repository.importUserSelectedMedia(uri, forceUpload = false)
            if (result.isSuccess) {
                _statusMessage.value = "Media imported to Cloud Set Storage"
                loadAlbums()
                _selectedAlbum.value?.let { openAlbum(it) }
            } else {
                _statusMessage.value = "Import failed: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun importDocument(uri: Uri) {
        viewModelScope.launch {
            val result = repository.importDocument(uri)
            if (result.isSuccess) {
                _statusMessage.value = "Document imported and queued for Drive"
                loadAlbums()
            } else {
                _statusMessage.value = "Document import failed: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun deleteItem(item: DeviceMediaItem) {
        viewModelScope.launch {
            try {
                if (item.path.isNotBlank()) {
                    val file = File(item.path)
                    if (file.exists()) {
                        file.delete()
                    }
                }
                _albumItems.value = _albumItems.value.filter { it.id != item.id }
                _statusMessage.value = "Deleted ${item.displayName}"
            } catch (e: Exception) {
                _statusMessage.value = "Could not delete file: ${e.message}"
            }
        }
    }

    fun loadDriveFolders() {
        viewModelScope.launch {
            try {
                val result = repository.listDriveFiles(category = "ALL")
                result.getOrNull()?.let { files ->
                    _driveFolders.value = files.filter { it.isFolder }
                }
            } catch (_: Exception) {}
        }
    }

    fun createDriveFolder(name: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = repository.createDriveFolder(name)
                if (result.isSuccess) {
                    _statusMessage.value = "Folder '$name' created in Google Drive"
                    loadDriveFolders()
                } else {
                    _statusMessage.value = "Failed to create folder: ${result.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                _statusMessage.value = "Error creating folder: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }
}
