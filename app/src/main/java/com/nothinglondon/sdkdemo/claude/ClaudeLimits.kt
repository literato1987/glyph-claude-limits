package com.nothinglondon.sdkdemo.claude

data class ClaudeLimits(
    val usedPercentage: Int,
    val resetsAtEpochSec: Long,
    val sourceLabel: String,
) {
    companion object {
        val EMPTY = ClaudeLimits(-1, 0L, "none")
    }
}

fun formatResetCountdown(resetsAtEpochSec: Long, nowEpochSec: Long = System.currentTimeMillis() / 1000L): String {
    val seconds = (resetsAtEpochSec - nowEpochSec).coerceAtLeast(0L)
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return when {
        hours >= 48 -> "${hours / 24}d"
        else -> "${hours}:${minutes.toString().padStart(2, '0')}"
    }
}