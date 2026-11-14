package com.omergenc.mobilenews.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.omergenc.mobilenews.model.SourceConfig

/**
 * App settings, stored with EncryptedSharedPreferences (AES-256, key in Android Keystore)
 * because they include API keys. Nothing here is ever sent anywhere except the API the key
 * belongs to.
 */
class AppSettings(context: Context) {
    private val prefs: SharedPreferences = openEncrypted(context)

    var anthropicApiKey: String
        get() = prefs.getString(KEY_ANTHROPIC, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_ANTHROPIC, value.trim()) }

    var youtubeApiKey: String
        get() = prefs.getString(KEY_YOUTUBE, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_YOUTUBE, value.trim()) }

    var telegramBridgeUrl: String
        get() = prefs.getString(KEY_TELEGRAM_BRIDGE, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_TELEGRAM_BRIDGE, value.trim()) }

    var notificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATIONS, true)
        set(value) = prefs.edit { putBoolean(KEY_NOTIFICATIONS, value) }

    var refreshIntervalMinutes: Int
        get() = prefs.getInt(KEY_INTERVAL, 15)
        set(value) = prefs.edit { putInt(KEY_INTERVAL, value.coerceAtLeast(15)) }

    fun isSourceEnabled(config: SourceConfig): Boolean =
        prefs.getBoolean(SOURCE_PREFIX + config.id, config.enabled)

    fun setSourceEnabled(config: SourceConfig, enabled: Boolean) =
        prefs.edit { putBoolean(SOURCE_PREFIX + config.id, enabled) }

    companion object {
        private const val FILE = "secure_settings"
        private const val KEY_ANTHROPIC = "anthropic_api_key"
        private const val KEY_YOUTUBE = "youtube_api_key"
        private const val KEY_TELEGRAM_BRIDGE = "telegram_bridge_url"
        private const val KEY_NOTIFICATIONS = "notifications_enabled"
        private const val KEY_INTERVAL = "refresh_interval_minutes"
        private const val SOURCE_PREFIX = "source_enabled_"

        private fun create(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }

        private fun openEncrypted(context: Context): SharedPreferences = try {
            create(context)
        } catch (e: Exception) {
            // Keystore key lost or file corrupted (e.g. after restore): start fresh rather
            // than ever falling back to plaintext storage.
            context.deleteSharedPreferences(FILE)
            create(context)
        }
    }
}
