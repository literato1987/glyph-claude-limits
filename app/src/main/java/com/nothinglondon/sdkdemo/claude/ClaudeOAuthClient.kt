package com.nothinglondon.sdkdemo.claude

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object ClaudeOAuthClient {

    fun buildAuthorizationUrl(session: PendingOAuthSession): String {
        val challenge = ClaudePkce.challenge(session.verifier)
        return buildString {
            append(ClaudeOAuthConfig.AUTHORIZE_URL)
            append("?code=true")
            append("&client_id=").append(encode(ClaudeOAuthConfig.CLIENT_ID))
            append("&response_type=code")
            append("&redirect_uri=").append(encode(session.redirectUri))
            append("&scope=").append(encode(ClaudeOAuthConfig.SCOPES))
            append("&code_challenge=").append(encode(challenge))
            append("&code_challenge_method=S256")
            append("&state=").append(encode(session.state))
        }
    }

    fun startSession(redirectUri: String = ClaudeOAuthConfig.REDIRECT_URI): PendingOAuthSession {
        return PendingOAuthSession(
            verifier = ClaudePkce.generateVerifier(),
            state = ClaudePkce.generateState(),
            redirectUri = redirectUri,
        )
    }

    suspend fun exchangeCode(
        code: String,
        state: String,
        session: PendingOAuthSession,
    ): OAuthCredentials = withContext(Dispatchers.IO) {
        if (state != session.state) {
            throw ClaudeUsageException(400, "State OAuth no coincide")
        }
        postToken(
            buildString {
                append("grant_type=authorization_code")
                append("&client_id=").append(encode(ClaudeOAuthConfig.CLIENT_ID))
                append("&code=").append(encode(code.trim()))
                append("&redirect_uri=").append(encode(session.redirectUri))
                append("&code_verifier=").append(encode(session.verifier))
                append("&state=").append(encode(state))
            },
        )
    }

    suspend fun refresh(refreshToken: String): OAuthCredentials = withContext(Dispatchers.IO) {
        postToken(
            buildString {
                append("grant_type=refresh_token")
                append("&client_id=").append(encode(ClaudeOAuthConfig.CLIENT_ID))
                append("&refresh_token=").append(encode(refreshToken))
            },
            previousRefreshToken = refreshToken,
        )
    }

    fun parseAuthorizationResponse(raw: String): Pair<String, String?> {
        val trimmed = raw.trim()
        val hashIndex = trimmed.indexOf('#')
        return if (hashIndex >= 0) {
            trimmed.substring(0, hashIndex) to trimmed.substring(hashIndex + 1)
        } else {
            trimmed to null
        }
    }

    private suspend fun postToken(
        body: String,
        previousRefreshToken: String? = null,
    ): OAuthCredentials = withContext(Dispatchers.IO) {
        val connection = (URL(ClaudeOAuthConfig.TOKEN_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("Accept", "application/json, text/plain, */*")
            setRequestProperty("User-Agent", ClaudeOAuthConfig.USER_AGENT)
        }

        try {
            connection.outputStream.use { it.write(body.toByteArray()) }
            val status = connection.responseCode
            val raw = when {
                status in 200..299 -> connection.inputStream.bufferedReader().use { it.readText() }
                else -> connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }
            if (status !in 200..299) {
                throw ClaudeUsageException(status, parseTokenError(raw, status))
            }
            parseTokenResponse(raw, previousRefreshToken)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseTokenResponse(raw: String, previousRefreshToken: String?): OAuthCredentials {
        val json = JSONObject(raw)
        val access = json.optString("access_token").ifEmpty { json.optString("accessToken") }
        val refresh = json.optString("refresh_token")
            .ifEmpty { json.optString("refreshToken") }
            .ifEmpty { previousRefreshToken.orEmpty() }
        if (access.isEmpty() || refresh.isEmpty()) {
            throw ClaudeUsageException(500, "Respuesta OAuth incompleta")
        }
        val expiresInSec = json.optLong("expires_in", json.optLong("expiresIn", 28_800L))
        val expiresAtMs = System.currentTimeMillis() + expiresInSec * 1000L
        return OAuthCredentials(access, refresh, expiresAtMs)
    }

    private fun parseTokenError(raw: String, status: Int): String {
        return runCatching {
            val json = JSONObject(raw)
            json.optString("error_description")
                .ifEmpty { json.optString("message") }
                .ifEmpty { json.optString("error") }
        }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: "OAuth error $status"
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}