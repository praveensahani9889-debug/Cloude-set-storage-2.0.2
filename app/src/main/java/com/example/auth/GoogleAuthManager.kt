package com.example.auth

import android.accounts.Account
import android.content.Context
import android.content.Intent
import com.example.data.local.dao.AccountDao
import com.example.data.local.entity.GoogleAccountEntity
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UserRecoverableException(val recoveryIntent: Intent?, message: String?) : Exception(message)

class GoogleAuthManager(
    private val context: Context,
    private val accountDao: AccountDao
) {
    companion object {
        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
        const val DRIVE_FULL_SCOPE = "https://www.googleapis.com/auth/drive"
    }

    private val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestProfile()
        .requestScopes(Scope(DRIVE_FILE_SCOPE))
        .build()

    val signInClient: GoogleSignInClient = GoogleSignIn.getClient(context, signInOptions)

    fun getSignInIntent(): Intent {
        return signInClient.signInIntent
    }

    suspend fun handleSignInResult(account: GoogleSignInAccount): GoogleAccountEntity {
        val email = account.email.orEmpty()
        val displayName = account.displayName
        val photoUrl = account.photoUrl?.toString()

        val entity = GoogleAccountEntity(
            accountEmail = email,
            displayName = displayName,
            photoUrl = photoUrl,
            isActive = true,
            authGranted = true,
            addedTimestamp = System.currentTimeMillis()
        )

        accountDao.insertOrUpdate(entity)
        accountDao.setActiveAccount(email)
        return entity
    }

    suspend fun getAccessToken(accountEmail: String): Result<String> = withContext(Dispatchers.IO) {
        if (accountEmail.isBlank() || accountEmail == "unassigned") {
            return@withContext Result.failure(IllegalArgumentException("No Google account specified for Drive access"))
        }

        try {
            val account = Account(accountEmail, "com.google")
            val oauthScope = "oauth2:$DRIVE_FILE_SCOPE"
            val token = GoogleAuthUtil.getToken(context, account, oauthScope)
            Result.success(token)
        } catch (e: UserRecoverableAuthException) {
            Result.failure(UserRecoverableException(e.intent, e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun invalidateToken(token: String) = withContext(Dispatchers.IO) {
        try {
            GoogleAuthUtil.clearToken(context, token)
        } catch (_: Exception) {
            // Ignore token clear errors
        }
    }

    suspend fun signOut(accountEmail: String) = withContext(Dispatchers.IO) {
        try {
            signInClient.signOut()
        } catch (_: Exception) {}
        accountDao.deleteAccount(accountEmail)
    }
}
