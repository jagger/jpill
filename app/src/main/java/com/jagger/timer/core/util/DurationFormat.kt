package com.jagger.timer.core.util

/** Minute precision, rounded up: "2h 14m", "2h", "14m". Never shows less than "1m" while time remains. */
fun formatCountdown(millis: Long): String {
    val totalMinutes = (millis.coerceAtLeast(0L) + 59_999) / 60_000
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}
