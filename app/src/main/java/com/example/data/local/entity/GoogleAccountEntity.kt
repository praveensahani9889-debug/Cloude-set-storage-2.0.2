package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "google_accounts")
data class GoogleAccountEntity(
    @PrimaryKey
    val accountEmail: String,
    val displayName: String? = null,
    val photoUrl: String? = null,
    val totalQuotaBytes: Long = -1L, // -1 means unknown / not provided by API
    val usedQuotaBytes: Long = -1L,
    val quotaRefreshTimestamp: Long = 0L,
    val isActive: Boolean = false,
    val authGranted: Boolean = false,
    val addedTimestamp: Long = System.currentTimeMillis()
)
