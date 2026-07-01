package com.nothinglondon.sdkdemo.claude

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

class ClaudeUsageException(val code: Int, message: String) : Exception(message)

object ClaudeUsageFetcher {

    private const val USAGE_URL = "https://api.anthropic.com/api/oauth/usage"
    private const val BETA_HEADER = "oauth-2025-04-20"

    suspend fun fetch(accessToken: String): ClaudeLimits = withContext(Dispatchers.IO) {
        val connection = (URL(USAGE_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("anthropic-beta", BETA_HEADER)
        }

        try {
            val status = connection.responseCode
            val body = when {
                status in 200..299 -> connection.inputStream.bufferedReader().use { it.readText() }
                else -> connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }

            when (status) {
                401 -> throw ClaudeUsageException(401, "Token inválido o caducado")
                in 200..299 -> parse(body)
                else -> throw ClaudeUsageException(status, "API error $status")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(body: String): ClaudeLimits {
        val root = JSONObject(body)
        val fiveHour = root.getJSONObject("five_hour")
        val utilization = fiveHour.getDouble("utilization")
        val resetsAt = fiveHour.getString("resets_at").let(::parseIsoEpoch)
        return ClaudeLimits(
            usedPercentage = utilization.toInt().coerceIn(0, 100),
            resetsAtEpochSec = resetsAt,
            sourceLabel = "api",
        )
    }

    private fun parseIsoEpoch(iso: String): Long {
        return Instant.parse(iso).epochSecond
    }
}