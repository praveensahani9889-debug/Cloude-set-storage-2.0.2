package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "backup_preferences")

data class BackupPreferences(
    val autoBackupEnabled: Boolean = true,
    val wifiOnly: Boolean = true,
    val backupWhileCharging: Boolean = false,
    val backupPhotos: Boolean = true,
    val backupVideos: Boolean = true,
    val backupDocuments: Boolean = true,
    val showNotifications: Boolean = true,
    val storageWarningThresholdPercent: Int = 90,
    val cloudFirstMode: Boolean = true,
    val autoCleanupVerified: Boolean = true,
    val maxStagingStorageMb: Int = 500,
    val allowMobileDataConsent: Boolean = false,
    val themeMode: String = "SYSTEM", // "SYSTEM", "LIGHT", "DARK"
    val language: String = "en", // "en", "hi"
    val termsAccepted: Boolean = false,
    val termsAcceptedTimestamp: Long = 0L,
    val galleryGridColumns: Int = 3,
    val gallerySortOrder: String = "NAME_ASC" // "NAME_ASC", "NAME_DESC", "DATE_DESC", "DATE_ASC", "COUNT_DESC"
)

class BackupPreferencesRepository(private val context: Context) {
    private object Keys {
        val AUTO_BACKUP = booleanPreferencesKey("auto_backup_enabled")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val WHILE_CHARGING = booleanPreferencesKey("backup_while_charging")
        val BACKUP_PHOTOS = booleanPreferencesKey("backup_photos")
        val BACKUP_VIDEOS = booleanPreferencesKey("backup_videos")
        val BACKUP_DOCUMENTS = booleanPreferencesKey("backup_documents")
        val SHOW_NOTIFICATIONS = booleanPreferencesKey("show_notifications")
        val STORAGE_WARNING_THRESHOLD = intPreferencesKey("storage_warning_threshold")
        val CLOUD_FIRST_MODE = booleanPreferencesKey("cloud_first_mode")
        val AUTO_CLEANUP_VERIFIED = booleanPreferencesKey("auto_cleanup_verified")
        val MAX_STAGING_STORAGE_MB = intPreferencesKey("max_staging_storage_mb")
        val ALLOW_MOBILE_DATA_CONSENT = booleanPreferencesKey("allow_mobile_data_consent")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("app_language")
        val TERMS_ACCEPTED = booleanPreferencesKey("terms_accepted")
        val TERMS_ACCEPTED_TIMESTAMP = longPreferencesKey("terms_accepted_timestamp")
        val GALLERY_GRID_COLUMNS = intPreferencesKey("gallery_grid_columns")
        val GALLERY_SORT_ORDER = stringPreferencesKey("gallery_sort_order")
    }

    private val sharedPrefs = context.getSharedPreferences("cloud_set_storage_launch", Context.MODE_PRIVATE)

    fun isTermsAcceptedSync(): Boolean {
        return sharedPrefs.getBoolean("terms_accepted", false)
    }

    val preferencesFlow: Flow<BackupPreferences> = context.dataStore.data.map { pref ->
        BackupPreferences(
            autoBackupEnabled = pref[Keys.AUTO_BACKUP] ?: true,
            wifiOnly = pref[Keys.WIFI_ONLY] ?: true,
            backupWhileCharging = pref[Keys.WHILE_CHARGING] ?: false,
            backupPhotos = pref[Keys.BACKUP_PHOTOS] ?: true,
            backupVideos = pref[Keys.BACKUP_VIDEOS] ?: true,
            backupDocuments = pref[Keys.BACKUP_DOCUMENTS] ?: true,
            showNotifications = pref[Keys.SHOW_NOTIFICATIONS] ?: true,
            storageWarningThresholdPercent = pref[Keys.STORAGE_WARNING_THRESHOLD] ?: 90,
            cloudFirstMode = pref[Keys.CLOUD_FIRST_MODE] ?: true,
            autoCleanupVerified = pref[Keys.AUTO_CLEANUP_VERIFIED] ?: true,
            maxStagingStorageMb = pref[Keys.MAX_STAGING_STORAGE_MB] ?: 500,
            allowMobileDataConsent = pref[Keys.ALLOW_MOBILE_DATA_CONSENT] ?: false,
            themeMode = pref[Keys.THEME_MODE] ?: "SYSTEM",
            language = pref[Keys.LANGUAGE] ?: "en",
            termsAccepted = pref[Keys.TERMS_ACCEPTED] ?: isTermsAcceptedSync(),
            termsAcceptedTimestamp = pref[Keys.TERMS_ACCEPTED_TIMESTAMP] ?: 0L,
            galleryGridColumns = pref[Keys.GALLERY_GRID_COLUMNS] ?: 3,
            gallerySortOrder = pref[Keys.GALLERY_SORT_ORDER] ?: "NAME_ASC"
        )
    }

    suspend fun setAutoBackup(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_BACKUP] = enabled }
    }

    suspend fun setWifiOnly(wifiOnly: Boolean) {
        context.dataStore.edit { it[Keys.WIFI_ONLY] = wifiOnly }
    }

    suspend fun setBackupWhileCharging(whileCharging: Boolean) {
        context.dataStore.edit { it[Keys.WHILE_CHARGING] = whileCharging }
    }

    suspend fun setBackupPhotos(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BACKUP_PHOTOS] = enabled }
    }

    suspend fun setBackupVideos(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BACKUP_VIDEOS] = enabled }
    }

    suspend fun setBackupDocuments(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BACKUP_DOCUMENTS] = enabled }
    }

    suspend fun setShowNotifications(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_NOTIFICATIONS] = enabled }
    }

    suspend fun setStorageWarningThreshold(threshold: Int) {
        context.dataStore.edit { it[Keys.STORAGE_WARNING_THRESHOLD] = threshold }
    }

    suspend fun setCloudFirstMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.CLOUD_FIRST_MODE] = enabled }
    }

    suspend fun setAutoCleanupVerified(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_CLEANUP_VERIFIED] = enabled }
    }

    suspend fun setMaxStagingStorageMb(mb: Int) {
        context.dataStore.edit { it[Keys.MAX_STAGING_STORAGE_MB] = mb }
    }

    suspend fun setAllowMobileDataConsent(allowed: Boolean) {
        context.dataStore.edit { it[Keys.ALLOW_MOBILE_DATA_CONSENT] = allowed }
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode }
    }

    suspend fun setLanguage(lang: String) {
        context.dataStore.edit { it[Keys.LANGUAGE] = lang }
    }

    suspend fun setTermsAccepted(accepted: Boolean) {
        try {
            sharedPrefs.edit()
                .putBoolean("terms_accepted", accepted)
                .putLong("terms_accepted_timestamp", System.currentTimeMillis())
                .commit()
        } catch (_: Exception) {}
        context.dataStore.edit {
            it[Keys.TERMS_ACCEPTED] = accepted
            if (accepted) {
                it[Keys.TERMS_ACCEPTED_TIMESTAMP] = System.currentTimeMillis()
            }
        }
    }

    fun setTermsAcceptedSync(accepted: Boolean) {
        try {
            sharedPrefs.edit()
                .putBoolean("terms_accepted", accepted)
                .putLong("terms_accepted_timestamp", System.currentTimeMillis())
                .commit()
        } catch (_: Exception) {}
    }

    suspend fun setGalleryGridColumns(columns: Int) {
        context.dataStore.edit { it[Keys.GALLERY_GRID_COLUMNS] = columns.coerceIn(2, 4) }
    }

    suspend fun setGallerySortOrder(order: String) {
        context.dataStore.edit { it[Keys.GALLERY_SORT_ORDER] = order }
    }
}
