package com.backlognudge.app.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Holds the Claude API key using an Android Keystore-backed encrypted file.
 * The key is never logged and never leaves the device except as an
 * Authorization-style header value on the direct call to the Anthropic API.
 */
class SecurePrefs(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    var claudeApiKey: String?
        get() = prefs.getString(KEY_API_KEY, null)
        set(value) = prefs.edit().putString(KEY_API_KEY, value).apply()

    fun hasApiKey(): Boolean = !claudeApiKey.isNullOrBlank()

    fun clearApiKey() = prefs.edit().remove(KEY_API_KEY).apply()

    companion object {
        private const val KEY_API_KEY = "claude_api_key"
    }
}
