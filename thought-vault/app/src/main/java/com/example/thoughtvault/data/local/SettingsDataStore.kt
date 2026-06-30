package com.example.thoughtvault.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 安全存储 NAS 连接配置（使用 EncryptedSharedPreferences）。
 */
@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val masterKeyAlias: String = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        "thought_vault_settings",
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    companion object {
        private const val KEY_USERNAME = "webdav_username"
        private const val KEY_PASSWORD = "webdav_password"
        private const val KEY_HAS_CONFIG = "has_config"

        // NAS 地址 — 使用域名 + HTTPS WebDAV
        const val NAS_BASE_URL = "https://<your-nas-domain>:<your-nas-webdav-https-port>/homes"
    }

    /** 根据用户名拼接完整的 WebDAV 地址 */
    fun buildBaseUrl(username: String): String {
        return "$NAS_BASE_URL/$username/thoughts"
    }

    /** 保存 NAS 配置（只需用户名和密码） */
    fun saveConfig(username: String, password: String) {
        prefs.edit()
            .putString(KEY_USERNAME, username)
            .putString(KEY_PASSWORD, password)
            .putBoolean(KEY_HAS_CONFIG, true)
            .apply()
    }

    /** 获取 NAS 配置 */
    fun getConfig(): NasConfig? {
        if (!prefs.getBoolean(KEY_HAS_CONFIG, false)) return null
        val username = prefs.getString(KEY_USERNAME, null) ?: return null
        val password = prefs.getString(KEY_PASSWORD, null) ?: return null
        return NasConfig(buildBaseUrl(username), username, password)
    }

    /** 检查是否已配置 */
    fun hasConfig(): Boolean = prefs.getBoolean(KEY_HAS_CONFIG, false)

    /** 清除配置 */
    fun clearConfig() {
        prefs.edit().clear().apply()
    }

    // --- 提醒开关 ---
    private val reminderPrefs = context.getSharedPreferences(
        "thought_vault_reminder", Context.MODE_PRIVATE
    )

    fun isReminderEnabled(): Boolean =
        reminderPrefs.getBoolean("reminder_enabled", true)

    fun setReminderEnabled(enabled: Boolean) {
        reminderPrefs.edit().putBoolean("reminder_enabled", enabled).apply()
    }
}

data class NasConfig(
    val baseUrl: String,
    val username: String,
    val password: String,
)
