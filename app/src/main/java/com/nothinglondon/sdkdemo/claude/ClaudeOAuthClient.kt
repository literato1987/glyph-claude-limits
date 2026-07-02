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

    fun startSession(): PendingOAuthSession {
        return PendingOAuthSession(
            verifier = ClaudePkce.generateVerifier(),
            state = ClaudePkce.generateState(),
            redirectUri = ClaudeOAuthConfig.REDIRECT_URI,
        )
    }

    suspend fun exchangeCode(
        code: String,
        state: String,
        session: PendingOAuthSession,
    ): OAuthCredentials = withContext(Dispatchers.IO) {
        if (state != session.state) {
            throw ClaudeUsageException(400, "State OAuth no coincide — vuelve a iniciar sesión")
        }
        val trimmedCode = code.trim()
        try {
            exchangeCodeForm(trimmedCode, state, session)
        } catch (first: ClaudeUsageException) {
            try {
                exchangeCodeJson(trimmedCode, state, session)
            } catch (_: Exception) {
                throw first
            }
        }
    }

    suspend fun refresh(refreshToken: String): OAuthCredentials = withContext(Dispatchers.IO) {
        postForm(
            ClaudeOAuthConfig.TOKEN_URL,
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

    private fun exchangeCodeForm(
        code: String,
        state: String,
        session: PendingOAuthSession,
    ): OAuthCredentials {
        return postForm(
            ClaudeOAuthConfig.TOKEN_URL,
            buildString {
                append("grant_type=authorization_code")
                append("&client_id=").append(encode(ClaudeOAuthConfig.CLIENT_ID))
                append("&code=").append(encode(code))
                append("&redirect_uri=").append(encode(session.redirectUri))
                append("&code_verifier=").append(encode(session.verifier))
                append("&state=").append(encode(state))
            },
        )
    }

    private fun exchangeCodeJson(
        code: String,
        state: String,
        session: PendingOAuthSession,
    ): OAuthCredentials {
        val body = JSONObject()
            .put("grant_type", "authorization_code")
            .put("client_id", ClaudeOAuthConfig.CLIENT_ID)
            .put("code", code)
            .put("redirect_uri", session.redirectUri)
            .put("code_verifier", session.verifier)
            .put("state", state)
            .toString()
        return postJson(ClaudeOAuthConfig.TOKEN_URL_CONSOLE, body)
    }

    private fun postForm(
        tokenUrl: String,
        body: String,
        previousRefreshToken: String? = null,
    ): OAuthCredentials {
        val connection = openConnection(tokenUrl, "application/x-www-form-urlencoded")
        try {
            connection.outputStream.use { it.write(body.toByteArray()) }
            return readTokenResponse(connection, previousRefreshToken)
        } finally {
            connection.disconnect()
        }
    }

    private fun postJson(tokenUrl: String, body: String): OAuthCredentials {
        val connection = openConnection(tokenUrl, "application/json")
        try {
            connection.outputStream.use { it.write(body.toByteArray()) }
            return readTokenResponse(connection, null)
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(tokenUrl: String, contentType: String): HttpURLConnection {
        return (URL(tokenUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", contentType)
            setRequestProperty("Accept", "application/json, text/plain, */*")
            setRequestProperty("User-Agent", ClaudeOAuthConfig.USER_AGENT)
        }
    }

    private fun readTokenResponse(
        connection: HttpURLConnection,
        previousRefreshToken: String?,
    ): OAuthCredentials {
        val status = connection.responseCode
        val raw = when {
            status in 200..299 -> connection.inputStream.bufferedReader().use { it.readText() }
            else -> connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        }
        if (status !in 200..299) {
            throw ClaudeUsageException(status, parseTokenError(raw, status))
        }
        return parseTokenResponse(raw, previousRefreshToken)
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