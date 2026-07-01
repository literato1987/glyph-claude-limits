package com.nothinglondon.sdkdemo.claude

import org.json.JSONObject

data class OAuthCredentials(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMs: Long,
) {
    fun isExpiringSoon(bufferMs: Long = 120_000L): Boolean {
        return System.currentTimeMillis() + bufferMs >= expiresAtMs
    }

    companion object {
        fun parseJson(input: String): OAuthCredentials? {
            return try {
                val trimmed = input.trim()
                val root = JSONObject(trimmed)
                val oauth = when {
                    root.has("claudeAiOauth") -> root.getJSONObject("claudeAiOauth")
                    root.has("accessToken") -> root
                    else -> return null
                }

                val access = oauth.optString("accessToken", "").trim()
                val refresh = oauth.optString("refreshToken", "").trim()
                if (access.isEmpty() || refresh.isEmpty()) return null

                val expiresAt = when {
                    oauth.has("expiresAt") -> oauth.getLong("expiresAt")
                    else -> System.currentTimeMillis() + 8 * 60 * 60 * 1000L
                }

                OAuthCredentials(access, refresh, expiresAt)
            } catch (_: Exception) {
                null
            }
        }
    }
}