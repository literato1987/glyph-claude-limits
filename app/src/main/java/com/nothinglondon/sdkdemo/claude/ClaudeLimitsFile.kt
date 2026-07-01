package com.nothinglondon.sdkdemo.claude

import android.content.Context
import android.os.Environment
import org.json.JSONObject
import java.io.File
import java.time.Instant

// Fallback si no hay token OAuth — Syncthing / caché local.

object ClaudeLimitsFile {

    private val candidatePaths = listOf(
        "/storage/emulated/0/Sync/asistente/claude-rate-limits.json",
        "${Environment.getExternalStorageDirectory()}/Sync/asistente/claude-rate-limits.json",
    )

    fun refresh(context: Context): ClaudeLimits {
        val cacheFile = File(context.filesDir, "claude-rate-limits.json")

        for (path in candidatePaths) {
            val file = File(path)
            if (!file.isFile) continue
            try {
                parse(file)?.let { limits ->
                    runCatching { file.copyTo(cacheFile, overwrite = true) }
                    return limits
                }
            } catch (_: SecurityException) {
            } catch (_: Exception) {
            }
        }

        if (cacheFile.isFile) {
            parse(cacheFile)?.let { return it }
        }

        return ClaudeLimits.EMPTY
    }

    fun parseJson(text: String): ClaudeLimits? {
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
                sourceLabel = "file",
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parse(file: File): ClaudeLimits? = parseJson(file.readText())
}