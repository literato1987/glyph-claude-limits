package com.nothinglondon.sdkdemo.claude

import android.content.Context
import org.json.JSONObject

object ClaudeLimitsProvider {

    suspend fun refresh(context: Context): ClaudeLimits {
        val cached = TokenStore.loadCachedLimits(context).takeIf { it.usedPercentage >= 0 }

        val credentials = ClaudeAuthManager.ensureValidCredentials(context)
        if (credentials == null) {
            return ClaudeLimits.EMPTY.copy(status = LimitsStatus.LOGIN)
        }

        try {
            val limits = ClaudeUsageFetcher.fetch(credentials.accessToken)
            cacheLimits(context, limits)
            return limits.withStatus(LimitsStatus.OK)
        } catch (e: ClaudeUsageException) {
            if (e.code == 401) {
                val renewed = ClaudeAuthManager.refreshOnUnauthorized(context)
                if (renewed != null) {
                    try {
                        val limits = ClaudeUsageFetcher.fetch(renewed.accessToken)
                        cacheLimits(context, limits)
                        return limits.withStatus(LimitsStatus.OK)
                    } catch (_: Exception) {
                    }
                }
                return ClaudeLimits.EMPTY.copy(status = LimitsStatus.LOGIN)
            }
            return cached?.withStatus(LimitsStatus.CACHE)
                ?: ClaudeLimits.EMPTY.copy(status = LimitsStatus.ERROR)
        } catch (_: Exception) {
            return cached?.withStatus(LimitsStatus.CACHE)
                ?: ClaudeLimits.EMPTY.copy(status = LimitsStatus.ERROR)
        }
    }

    private fun ClaudeLimits.withStatus(status: LimitsStatus): ClaudeLimits {
        return copy(status = status, sourceLabel = if (status == LimitsStatus.CACHE) "cache" else sourceLabel)
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