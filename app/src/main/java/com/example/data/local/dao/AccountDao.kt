package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.local.entity.GoogleAccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM google_accounts ORDER BY addedTimestamp ASC")
    fun getAllAccounts(): Flow<List<GoogleAccountEntity>>

    @Query("SELECT * FROM google_accounts WHERE isActive = 1 LIMIT 1")
    fun getActiveAccount(): Flow<GoogleAccountEntity?>

    @Query("SELECT * FROM google_accounts WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveAccountSync(): GoogleAccountEntity?

    @Query("SELECT * FROM google_accounts WHERE accountEmail = :email LIMIT 1")
    suspend fun getByEmail(email: String): GoogleAccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(account: GoogleAccountEntity)

    @Update
    suspend fun update(account: GoogleAccountEntity)

    @Query("UPDATE google_accounts SET isActive = 0")
    suspend fun deactivateAll()

    @Query("UPDATE google_accounts SET isActive = 1 WHERE accountEmail = :email")
    suspend fun activate(email: String)

    @Transaction
    suspend fun setActiveAccount(email: String) {
        deactivateAll()
        activate(email)
    }

    @Query("UPDATE google_accounts SET totalQuotaBytes = :totalBytes, usedQuotaBytes = :usedBytes, quotaRefreshTimestamp = :refreshTime WHERE accountEmail = :email")
    suspend fun updateQuota(email: String, totalBytes: Long, usedBytes: Long, refreshTime: Long)

    @Query("DELETE FROM google_accounts WHERE accountEmail = :email")
    suspend fun deleteAccount(email: String)
}
