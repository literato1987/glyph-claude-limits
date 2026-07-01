package com.nothinglondon.sdkdemo.claude

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object TokenStore {

    private const val PREFS = "claude_glyph_auth"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_EXPIRES_AT = "expires_at_ms"
    private const val KEY_CACHED_LIMITS = "cached_limits_json"

    private fun prefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        PREFS,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun saveCredentials(context: Context, credentials: OAuthCredentials) {
        prefs(context).edit()
            .putString(KEY_ACCESS_TOKEN, credentials.accessToken)
            .putString(KEY_REFRESH_TOKEN, credentials.refreshToken)
            .putLong(KEY_EXPIRES_AT, credentials.expiresAtMs)
            .apply()
    }

    fun loadCredentials(context: Context): OAuthCredentials? {
        val access = prefs(context).getString(KEY_ACCESS_TOKEN, null)?.trim().orEmpty()
        val refresh = prefs(context).getString(KEY_REFRESH_TOKEN, null)?.trim().orEmpty()
        val expiresAt = prefs(context).getLong(KEY_EXPIRES_AT, 0L)
        if (access.isEmpty() || refresh.isEmpty()) return null
        return OAuthCredentials(access, refresh, expiresAt)
    }

    fun clearCredentials(context: Context) {
        prefs(context).edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_EXPIRES_AT)
            .apply()
    }

    fun hasCredentials(context: Context): Boolean = loadCredentials(context) != null

    fun saveCachedLimits(context: Context, json: String) {
        prefs(context).edit().putString(KEY_CACHED_LIMITS, json).apply()
    }

    fun loadCachedLimits(context: Context): ClaudeLimits {
        val json = prefs(context).getString(KEY_CACHED_LIMITS, null) ?: return ClaudeLimits.EMPTY
        return ClaudeLimitsFile.parseJson(json) ?: ClaudeLimits.EMPTY
    }
}