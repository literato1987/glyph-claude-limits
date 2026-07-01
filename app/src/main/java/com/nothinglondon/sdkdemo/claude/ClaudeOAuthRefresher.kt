package com.nothinglondon.sdkdemo.claude

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object ClaudeOAuthRefresher {

    private const val TOKEN_URL = "https://claude.ai/v1/oauth/token"
    private const val CLIENT_ID = "9d1c250a-e61b-44d9-88ed-5944d1962f5e"
    private const val USER_AGENT = "axios/1.13.6"

    suspend fun refresh(refreshToken: String): OAuthCredentials = withContext(Dispatchers.IO) {
        val body = buildString {
            append("grant_type=refresh_token")
            append("&client_id=").append(URLEncoder.encode(CLIENT_ID, "UTF-8"))
            append("&refresh_token=").append(URLEncoder.encode(refreshToken, "UTF-8"))
        }

        val connection = (URL(TOKEN_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 10_000
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("Accept", "application/json, text/plain, */*")
            setRequestProperty("User-Agent", USER_AGENT)
        }

        try {
            connection.outputStream.use { it.write(body.toByteArray()) }
            val status = connection.responseCode
            val raw = when {
                status in 200..299 -> connection.inputStream.bufferedReader().use { it.readText() }
                else -> connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }

            if (status !in 200..299) {
                throw ClaudeUsageException(status, "OAuth refresh failed ($status)")
            }

            parseRefreshResponse(raw, refreshToken)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseRefreshResponse(raw: String, previousRefreshToken: String): OAuthCredentials {
        val json = JSONObject(raw)
        val access = json.getString("access_token")
        val refresh = json.optString("refresh_token", previousRefreshToken)
        val expiresInSec = json.optLong("expires_in", 28_800L)
        val expiresAtMs = System.currentTimeMillis() + expiresInSec * 1000L
        return OAuthCredentials(access, refresh, expiresAtMs)
    }
}