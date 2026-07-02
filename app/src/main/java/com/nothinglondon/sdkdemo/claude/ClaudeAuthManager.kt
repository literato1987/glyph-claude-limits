package com.nothinglondon.sdkdemo.claude

import android.content.Context

object ClaudeAuthManager {

    suspend fun ensureValidCredentials(context: Context): OAuthCredentials? {
        val stored = TokenStore.loadCredentials(context) ?: return null
        if (!stored.isExpiringSoon()) return stored

        return try {
            val refreshed = ClaudeOAuthClient.refresh(stored.refreshToken)
            TokenStore.saveCredentials(context, refreshed)
            refreshed
        } catch (_: Exception) {
            if (!stored.isExpiringSoon(bufferMs = 0)) stored else null
        }
    }

    suspend fun refreshOnUnauthorized(context: Context): OAuthCredentials? {
        val stored = TokenStore.loadCredentials(context) ?: return null
        return try {
            val refreshed = ClaudeOAuthClient.refresh(stored.refreshToken)
            TokenStore.saveCredentials(context, refreshed)
            refreshed
        } catch (_: Exception) {
            null
        }
    }

    suspend fun completeAuthorization(
        context: Context,
        code: String,
        state: String?,
    ): OAuthCredentials {
        val session = OAuthSessionStore.load(context)
            ?: throw ClaudeUsageException(400, "No hay sesión OAuth activa — inicia sesión de nuevo")
        val resolvedState = state?.trim().orEmpty().ifEmpty { session.state }
        val credentials = ClaudeOAuthClient.exchangeCode(code.trim(), resolvedState, session)
        TokenStore.saveCredentials(context, credentials)
        OAuthSessionStore.clear(context)
        return credentials
    }
}