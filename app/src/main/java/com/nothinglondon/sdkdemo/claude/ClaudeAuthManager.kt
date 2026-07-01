package com.nothinglondon.sdkdemo.claude

import android.content.Context

object ClaudeAuthManager {

    suspend fun ensureValidCredentials(context: Context): OAuthCredentials? {
        val stored = TokenStore.loadCredentials(context) ?: return null
        if (!stored.isExpiringSoon()) return stored

        return try {
            val refreshed = ClaudeOAuthRefresher.refresh(stored.refreshToken)
            TokenStore.saveCredentials(context, refreshed)
            refreshed
        } catch (_: Exception) {
            if (!stored.isExpiringSoon(bufferMs = 0)) stored else null
        }
    }

    suspend fun refreshOnUnauthorized(context: Context): OAuthCredentials? {
        val stored = TokenStore.loadCredentials(context) ?: return null
        return try {
            val refreshed = ClaudeOAuthRefresher.refresh(stored.refreshToken)
            TokenStore.saveCredentials(context, refreshed)
            refreshed
        } catch (_: Exception) {
            null
        }
    }
}