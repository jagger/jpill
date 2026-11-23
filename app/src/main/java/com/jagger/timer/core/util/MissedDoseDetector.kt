package com.jagger.timer.core.util

import com.jagger.timer.core.data.local.entity.ScheduleEntity
import com.jagger.timer.core.data.local.entity.ScheduleType

/**
 * Decides when a scheduled dose counts as missed: nothing was logged for the pill by the time the dose
 * came due plus a grace window. Pure, so the rules are unit-tested.
 */
object MissedDoseDetector {
    const val DEFAULT_GRACE_MINUTES = 60
    private const val MINUTE = 60_000L

    /**
     * How long after the due time to wait before calling the dose missed. Normally 60 minutes, but never more
     * than half of an interval schedule's period, so a "every 2 hours" dose isn't still pending when the next is due.
     */
    fun graceMillis(schedule: ScheduleEntity): Long {
        val interval = schedule.intervalMinutes
        val cap = if (schedule.type == ScheduleType.INTERVAL && interval != null && interval > 0) interval / 2 else Int.MAX_VALUE
        return minOf(DEFAULT_GRACE_MINUTES, cap).coerceAtLeast(1) * MINUTE
    }

    /** Logs made at or after this moment count as handling the dose (a dose taken a little early still counts). */
    fun windowStartMillis(dueAtMillis: Long, reminderAdvanceMinutes: Int): Long =
        dueAtMillis - reminderAdvanceMinutes.coerceAtLeast(0) * MINUTE

    fun shouldLogMissed(
        logTimestamps: List<Long>,
        dueAtMillis: Long,
        reminderAdvanceMinutes: Int,
        graceMillis: Long,
        nowMillis: Long
    ): Boolean {
        if (nowMillis < dueAtMillis + graceMillis) return false
        val start = windowStartMillis(dueAtMillis, reminderAdvanceMinutes)
        return logTimestamps.none { it >= start }
    }
}
