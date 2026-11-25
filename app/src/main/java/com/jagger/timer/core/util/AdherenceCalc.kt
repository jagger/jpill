// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.core.util

import com.jagger.timer.core.data.local.dao.DoseHistoryRow
import com.jagger.timer.core.data.local.entity.DoseStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One calendar day of scheduled-dose adherence. [expected] counts scheduled doses with a recorded outcome that day. */
data class DayAdherence(val date: LocalDate, val taken: Int, val expected: Int) {
    val hasData: Boolean get() = expected > 0

    /** 0..1 share of scheduled doses taken (an extra "log another anyway" dose can't push it past 1). */
    val fraction: Float get() = if (expected == 0) 0f else (taken.toFloat() / expected).coerceIn(0f, 1f)
    val percent: Int get() = Math.round(fraction * 100)
}

/**
 * Daily adherence for the History heatmap. Only scheduled doses count (as-needed doses have no "expected" number):
 * a day's percentage is TAKEN divided by TAKEN + SKIPPED + MISSED among doses that belong to a schedule, filed under
 * the day they were logged. Pure, so it is unit-tested.
 */
object AdherenceCalc {
    fun last(days: Int, rows: List<DoseHistoryRow>, zone: ZoneId, today: LocalDate): List<DayAdherence> {
        val start = today.minusDays((days - 1).toLong())
        val taken = HashMap<LocalDate, Int>()
        val expected = HashMap<LocalDate, Int>()
        for (r in rows) {
            if (r.scheduleId == null) continue
            val d = Instant.ofEpochMilli(r.timestamp).atZone(zone).toLocalDate()
            if (d.isBefore(start) || d.isAfter(today)) continue
            expected.merge(d, 1, Int::plus)
            if (r.status == DoseStatus.TAKEN) taken.merge(d, 1, Int::plus)
        }
        return (0 until days).map { i ->
            val d = start.plusDays(i.toLong())
            DayAdherence(d, taken[d] ?: 0, expected[d] ?: 0)
        }
    }

    /** Overall percentage across the days that had scheduled doses, or null if none did. */
    fun overallPercent(days: List<DayAdherence>): Int? {
        val expected = days.sumOf { it.expected }
        if (expected == 0) return null
        return Math.round(days.sumOf { minOf(it.taken, it.expected) } * 100f / expected)
    }

    /** "Tue, Nov 24: 3 of 4 doses taken (75%)", also used as the cell's spoken description. */
    fun describe(d: DayAdherence, locale: Locale = Locale.getDefault()): String {
        val day = DateTimeFormatter.ofPattern("EEE, MMM d", locale).format(d.date)
        return if (!d.hasData) "$day: no scheduled doses recorded"
        else "$day: ${minOf(d.taken, d.expected)} of ${d.expected} scheduled dose${if (d.expected == 1) "" else "s"} taken (${d.percent}%)"
    }
}
