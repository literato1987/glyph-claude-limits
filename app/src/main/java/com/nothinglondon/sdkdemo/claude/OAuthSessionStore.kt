package com.nothinglondon.sdkdemo.claude

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class PendingOAuthSession(
    val verifier: String,
    val state: String,
    val redirectUri: String,
)

object OAuthSessionStore {

    private const val PREFS = "claude_glyph_oauth_session"
    private const val KEY_VERIFIER = "verifier"
    private const val KEY_STATE = "state"
    private const val KEY_REDIRECT = "redirect_uri"

    private fun prefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        PREFS,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun save(context: Context, session: PendingOAuthSession) {
        prefs(context).edit()
            .putString(KEY_VERIFIER, session.verifier)
            .putString(KEY_STATE, session.state)
            .putString(KEY_REDIRECT, session.redirectUri)
            .apply()
    }

    fun load(context: Context): PendingOAuthSession? {
        val verifier = prefs(context).getString(KEY_VERIFIER, null)?.trim().orEmpty()
        val state = prefs(context).getString(KEY_STATE, null)?.trim().orEmpty()
        val redirect = prefs(context).getString(KEY_REDIRECT, null)?.trim().orEmpty()
        if (verifier.isEmpty() || state.isEmpty() || redirect.isEmpty()) return null
        return PendingOAuthSession(verifier, state, redirect)
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}