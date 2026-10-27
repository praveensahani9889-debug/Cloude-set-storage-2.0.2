package com.example.ui.viewmodels

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.GoogleAccountEntity
import com.example.data.repository.MediaRepository
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AccountViewModel(private val repository: MediaRepository) : ViewModel() {

    val activeAccount: StateFlow<GoogleAccountEntity?> = repository.activeAccount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allAccounts: StateFlow<List<GoogleAccountEntity>> = repository.allAccounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun getSignInIntent(): Intent = repository.authManager.getSignInIntent()

    fun handleSignInResult(intent: Intent?) {
        viewModelScope.launch {
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(intent)
                val account = task.getResult(ApiException::class.java)
                if (account != null) {
                    repository.authManager.handleSignInResult(account)
                    repository.refreshQuotaForActiveAccount()
                    _statusMessage.value = "Connected as ${account.email}"
                } else {
                    _statusMessage.value = "Sign in was canceled or failed"
                }
            } catch (e: Exception) {
                _statusMessage.value = "Authentication error: ${e.message}"
            }
        }
    }

    fun switchAccount(email: String) {
        viewModelScope.launch {
            repository.switchActiveAccount(email)
            _statusMessage.value = "Switched active backup account to $email"
        }
    }

    fun disconnectAccount(email: String) {
        viewModelScope.launch {
            repository.disconnectAccount(email)
            _statusMessage.value = "Disconnected account $email"
        }
    }

    fun refreshQuota() {
        viewModelScope.launch {
            repository.refreshQuotaForActiveAccount()
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }
}
