package com.example.thoughtvault.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
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
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "thought_vault_settings",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    companion object {
        private const val KEY_BASE_URL = "webdav_base_url"
        private const val KEY_USERNAME = "webdav_username"
        private const val KEY_PASSWORD = "webdav_password"
        private const val KEY_HAS_CONFIG = "has_config"

        // 默认端口: 群晖 WebDAV 默认 5005 (HTTPS) / 5000 (HTTP)
    }

    /** 保存 NAS 配置 */
    fun saveConfig(baseUrl: String, username: String, password: String) {
        prefs.edit()
            .putString(KEY_BASE_URL, baseUrl)
            .putString(KEY_USERNAME, username)
            .putString(KEY_PASSWORD, password)
            .putBoolean(KEY_HAS_CONFIG, true)
            .apply()
    }

    /** 获取 NAS 配置，如果未配置则返回 null */
    fun getConfig(): NasConfig? {
        if (!prefs.getBoolean(KEY_HAS_CONFIG, false)) return null
        val url = prefs.getString(KEY_BASE_URL, null) ?: return null
        val username = prefs.getString(KEY_USERNAME, null) ?: return null
        val password = prefs.getString(KEY_PASSWORD, null) ?: return null
        return NasConfig(url, username, password)
    }

    /** 检查是否已配置 */
    fun hasConfig(): Boolean = prefs.getBoolean(KEY_HAS_CONFIG, false)

    /** 清除配置 */
    fun clearConfig() {
        prefs.edit().clear().apply()
    }
}

data class NasConfig(
    val baseUrl: String,
    val username: String,
    val password: String,
)
