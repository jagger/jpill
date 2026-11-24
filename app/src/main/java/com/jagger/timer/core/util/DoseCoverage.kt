package com.jagger.timer.core.util

import com.jagger.timer.core.data.local.entity.DoseLogEntity
import com.jagger.timer.core.data.local.entity.DoseStatus

/**
 * Decides whether a scheduled dose has already been dealt with (taken or skipped), including a little early,
 * so it can be greyed out and its alarm skipped instead of nagging again. Pure, so it is unit-tested.
 */
object DoseCoverage {
    /** A log this far before the due time still counts as handling the dose. */
    const val EARLY_WINDOW_MINUTES = 120
    private const val MINUTE = 60_000L

    fun findCovering(logs: List<DoseLogEntity>, scheduleId: Long, dueAtMillis: Long, nowMillis: Long): DoseLogEntity? {
        val start = dueAtMillis - EARLY_WINDOW_MINUTES * MINUTE
        return logs
            .filter {
                it.scheduleId == scheduleId &&
                    (it.status == DoseStatus.TAKEN || it.status == DoseStatus.SKIPPED) &&
                    it.timestamp in start..nowMillis
            }
            .maxByOrNull { it.timestamp }
    }
}
