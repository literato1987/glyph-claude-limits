package com.nothinglondon.sdkdemo.claude

import org.json.JSONObject
import java.time.Instant

object ClaudeLimitsJson {

    fun parse(text: String, sourceLabel: String = "cache"): ClaudeLimits? {
        return try {
            val root = JSONObject(text)
            val fiveHour = root.optJSONObject("five_hour") ?: return null

            val used = when {
                fiveHour.has("utilization") -> fiveHour.getDouble("utilization")
                fiveHour.has("used_percentage") -> fiveHour.getDouble("used_percentage")
                else -> -1.0
            }

            val resetsAt = when {
                fiveHour.has("resets_at") -> {
                    val raw = fiveHour.get("resets_at")
                    when (raw) {
                        is Number -> raw.toLong()
                        is String -> if (raw.contains('T')) {
                            Instant.parse(raw).epochSecond
                        } else {
                            raw.toLongOrNull() ?: 0L
                        }
                        else -> 0L
                    }
                }
                else -> 0L
            }

            if (used < 0 || resetsAt <= 0L) return null
            ClaudeLimits(
                usedPercentage = used.toInt().coerceIn(0, 100),
                resetsAtEpochSec = resetsAt,
                sourceLabel = sourceLabel,
                status = LimitsStatus.CACHE,
            )
        } catch (_: Exception) {
            null
        }
    }
}