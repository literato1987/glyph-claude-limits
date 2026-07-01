package com.nothinglondon.sdkdemo.claude

import android.content.Context
import org.json.JSONObject

object ClaudeLimitsProvider {

    suspend fun refresh(context: Context): ClaudeLimits {
        val credentials = ClaudeAuthManager.ensureValidCredentials(context)
        if (credentials != null) {
            try {
                val limits = ClaudeUsageFetcher.fetch(credentials.accessToken)
                cacheLimits(context, limits)
                return limits
            } catch (e: ClaudeUsageException) {
                if (e.code == 401) {
                    val renewed = ClaudeAuthManager.refreshOnUnauthorized(context)
                    if (renewed != null) {
                        try {
                            val limits = ClaudeUsageFetcher.fetch(renewed.accessToken)
                            cacheLimits(context, limits)
                            return limits
                        } catch (_: Exception) {
                        }
                    }
                }
                TokenStore.loadCachedLimits(context).takeIf { it.usedPercentage >= 0 }?.let { return it }
            } catch (_: Exception) {
                TokenStore.loadCachedLimits(context).takeIf { it.usedPercentage >= 0 }?.let { return it }
            }
        }

        return ClaudeLimitsFile.refresh(context).takeIf { it.usedPercentage >= 0 }
            ?: TokenStore.loadCachedLimits(context)
    }

    private fun cacheLimits(context: Context, limits: ClaudeLimits) {
        val json = JSONObject()
            .put(
                "five_hour",
                JSONObject()
                    .put("utilization", limits.usedPercentage)
                    .put("resets_at", limits.resetsAtEpochSec),
            )
            .toString()
        TokenStore.saveCachedLimits(context, json)
    }
}