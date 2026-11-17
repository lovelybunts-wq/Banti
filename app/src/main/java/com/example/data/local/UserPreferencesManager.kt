package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.StudyThemeOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "learn_talent_prefs")

data class LocalAppSettings(
    val themeOption: StudyThemeOption = StudyThemeOption.BUNTS_ROYAL_LIGHT,
    val lastLightThemeId: String = StudyThemeOption.BUNTS_ROYAL_LIGHT.id,
    val lastDarkThemeId: String = StudyThemeOption.BUNTS_NAVY_DARK.id,
    val biometricLockEnabled: Boolean = false,
    val cloudSyncEnabled: Boolean = true,
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 8,
    val reminderMinute: Int = 0,
    val lastYesterdaySummary: String = "",
    val offlineModeAccepted: Boolean = false,
    val adminWhatsAppNumber: String = "",
    val customLogoFileName: String = "",
    val lastDriveBackupMillis: Long = 0L,
    val driveUserId: String = "",
    val driveUserEmail: String = "",
    val driveUserName: String = "",
    val driveUserPhotoUrl: String = "",
    val sharedCatalogUrl: String = "",
    val lastDriveSyncMessage: String = "",
)

class UserPreferencesManager(private val context: Context) {
    private object Keys {
        val THEME_ID = stringPreferencesKey("theme_id")
        val LAST_LIGHT_THEME_ID = stringPreferencesKey("last_light_theme_id")
        val LAST_DARK_THEME_ID = stringPreferencesKey("last_dark_theme_id")
        val BIOMETRIC_LOCK = booleanPreferencesKey("biometric_lock")
        val CLOUD_SYNC = booleanPreferencesKey("cloud_sync")
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
        val LAST_YESTERDAY_SUMMARY = stringPreferencesKey("last_yesterday_summary")
        val OFFLINE_MODE_ACCEPTED = booleanPreferencesKey("offline_mode_accepted")
        val ADMIN_WHATSAPP_NUMBER = stringPreferencesKey("admin_whatsapp_number")
        val CUSTOM_LOGO_FILE_NAME = stringPreferencesKey("custom_logo_file_name")
        val LAST_DRIVE_BACKUP_MILLIS = longPreferencesKey("last_drive_backup_millis")
        val DRIVE_USER_ID = stringPreferencesKey("drive_user_id")
        val DRIVE_USER_EMAIL = stringPreferencesKey("drive_user_email")
        val DRIVE_USER_NAME = stringPreferencesKey("drive_user_name")
        val DRIVE_USER_PHOTO_URL = stringPreferencesKey("drive_user_photo_url")
        val SHARED_CATALOG_URL = stringPreferencesKey("shared_catalog_url")
        val LAST_DRIVE_SYNC_MESSAGE = stringPreferencesKey("last_drive_sync_message")
        fun seededKey(uid: String) = booleanPreferencesKey("seeded_$uid")
    }

    val settingsFlow: Flow<LocalAppSettings> = context.dataStore.data.map { prefs ->
        LocalAppSettings(
            themeOption = StudyThemeOption.fromId(prefs[Keys.THEME_ID]),
            lastLightThemeId = prefs[Keys.LAST_LIGHT_THEME_ID] ?: StudyThemeOption.BUNTS_ROYAL_LIGHT.id,
            lastDarkThemeId = prefs[Keys.LAST_DARK_THEME_ID] ?: StudyThemeOption.BUNTS_NAVY_DARK.id,
            biometricLockEnabled = prefs[Keys.BIOMETRIC_LOCK] ?: false,
            cloudSyncEnabled = prefs[Keys.CLOUD_SYNC] ?: true,
            reminderEnabled = prefs[Keys.REMINDER_ENABLED] ?: false,
            reminderHour = prefs[Keys.REMINDER_HOUR] ?: 8,
            reminderMinute = prefs[Keys.REMINDER_MINUTE] ?: 0,
            lastYesterdaySummary = prefs[Keys.LAST_YESTERDAY_SUMMARY] ?: "",
            offlineModeAccepted = prefs[Keys.OFFLINE_MODE_ACCEPTED] ?: false,
            adminWhatsAppNumber = prefs[Keys.ADMIN_WHATSAPP_NUMBER] ?: "",
            customLogoFileName = prefs[Keys.CUSTOM_LOGO_FILE_NAME] ?: "",
            lastDriveBackupMillis = prefs[Keys.LAST_DRIVE_BACKUP_MILLIS] ?: 0L,
            driveUserId = prefs[Keys.DRIVE_USER_ID] ?: "",
            driveUserEmail = prefs[Keys.DRIVE_USER_EMAIL] ?: "",
            driveUserName = prefs[Keys.DRIVE_USER_NAME] ?: "",
            driveUserPhotoUrl = prefs[Keys.DRIVE_USER_PHOTO_URL] ?: "",
            sharedCatalogUrl = prefs[Keys.SHARED_CATALOG_URL] ?: "",
            lastDriveSyncMessage = prefs[Keys.LAST_DRIVE_SYNC_MESSAGE] ?: "",
        )
    }

    suspend fun setTheme(themeId: String) {
        val option = StudyThemeOption.fromId(themeId)
        context.dataStore.edit { prefs ->
            prefs[Keys.THEME_ID] = option.id
            if (option.isLight) {
                prefs[Keys.LAST_LIGHT_THEME_ID] = option.id
            } else {
                prefs[Keys.LAST_DARK_THEME_ID] = option.id
            }
        }
    }

    suspend fun setBiometricLockEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BIOMETRIC_LOCK] = enabled }
    }

    suspend fun setCloudSyncEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.CLOUD_SYNC] = enabled }
    }

    suspend fun setReminder(enabled: Boolean, hour: Int, minute: Int) {
        context.dataStore.edit {
            it[Keys.REMINDER_ENABLED] = enabled
            it[Keys.REMINDER_HOUR] = hour
            it[Keys.REMINDER_MINUTE] = minute
        }
    }

    suspend fun setYesterdaySummary(summary: String) {
        context.dataStore.edit { it[Keys.LAST_YESTERDAY_SUMMARY] = summary }
    }

    suspend fun setOfflineModeAccepted(accepted: Boolean) {
        context.dataStore.edit { it[Keys.OFFLINE_MODE_ACCEPTED] = accepted }
    }

    suspend fun setAdminWhatsAppNumber(number: String) {
        context.dataStore.edit { it[Keys.ADMIN_WHATSAPP_NUMBER] = number.trim() }
    }

    suspend fun setCustomLogoFileName(fileName: String) {
        context.dataStore.edit { it[Keys.CUSTOM_LOGO_FILE_NAME] = fileName }
    }

    suspend fun setLastDriveBackupMillis(millis: Long, message: String = "") {
        context.dataStore.edit {
            it[Keys.LAST_DRIVE_BACKUP_MILLIS] = millis
            if (message.isNotBlank()) it[Keys.LAST_DRIVE_SYNC_MESSAGE] = message
        }
    }

    suspend fun setSharedCatalogUrl(url: String) {
        context.dataStore.edit { it[Keys.SHARED_CATALOG_URL] = url.trim() }
    }

    suspend fun setDriveUserSession(userId: String, email: String, name: String, photoUrl: String = "") {
        context.dataStore.edit {
            it[Keys.DRIVE_USER_ID] = userId
            it[Keys.DRIVE_USER_EMAIL] = email
            it[Keys.DRIVE_USER_NAME] = name
            it[Keys.DRIVE_USER_PHOTO_URL] = photoUrl
        }
    }

    suspend fun clearDriveUserSession() {
        context.dataStore.edit {
            it.remove(Keys.DRIVE_USER_ID)
            it.remove(Keys.DRIVE_USER_EMAIL)
            it.remove(Keys.DRIVE_USER_NAME)
            it.remove(Keys.DRIVE_USER_PHOTO_URL)
            it.remove(Keys.LAST_DRIVE_SYNC_MESSAGE)
        }
    }

    fun isSeededFlow(uid: String): Flow<Boolean> =
        context.dataStore.data.map { it[Keys.seededKey(uid)] ?: false }

    suspend fun markSeeded(uid: String) {
        context.dataStore.edit { it[Keys.seededKey(uid)] = true }
    }

    companion object {
        const val ADMIN_EMAIL = "lovely.bunts@gmail.com"
        const val DEFAULT_ADMIN_WHATSAPP_DIGITS = "917049126747"

        fun normalizePhoneDigits(raw: String): String {
            val digits = raw.filter { it.isDigit() }
            if (digits.isEmpty()) return ""
            return if (digits.length == 10) "91$digits" else digits
        }
    }
}
