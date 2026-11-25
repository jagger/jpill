// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.core.util

import com.jagger.timer.core.data.local.dao.DoseHistoryRow
import com.jagger.timer.core.data.local.entity.DoseStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class DayGroup(val label: String, val rows: List<DoseHistoryRow>)

/** Pure labels and day grouping for the History screen. */
object HistoryFormatter {

    fun dayLabel(date: LocalDate, today: LocalDate, locale: Locale = Locale.getDefault()): String = when {
        date == today -> "Today"
        date == today.minusDays(1) -> "Yesterday"
        date.year == today.year -> DateTimeFormatter.ofPattern("EEE, MMM d", locale).format(date)
        else -> DateTimeFormatter.ofPattern("MMM d, yyyy", locale).format(date)
    }

    /** Groups newest-first rows by local day, keeping their order. */
    fun groupByDay(
        rows: List<DoseHistoryRow>,
        zone: ZoneId,
        today: LocalDate,
        locale: Locale = Locale.getDefault()
    ): List<DayGroup> {
        val byDate = LinkedHashMap<LocalDate, MutableList<DoseHistoryRow>>()
        rows.forEach { r ->
            byDate.getOrPut(Instant.ofEpochMilli(r.timestamp).atZone(zone).toLocalDate()) { mutableListOf() }.add(r)
        }
        return byDate.map { (date, list) -> DayGroup(dayLabel(date, today, locale), list) }
    }

    fun timeLabel(timestampMillis: Long, zone: ZoneId, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofPattern("h:mm a", locale).format(Instant.ofEpochMilli(timestampMillis).atZone(zone))

    /** "20 mg", "1000 mg", "27.5 mg", or null when no amount was recorded. */
    fun amountLabel(row: DoseHistoryRow): String? =
        row.amount?.let { RxFormLogic.formatCount(it) + " " + row.pillUnit }

    fun statusLabel(status: DoseStatus): String = when (status) {
        DoseStatus.TAKEN -> "Taken"
        DoseStatus.SKIPPED -> "Skipped"
        DoseStatus.MISSED -> "Missed"
    }
}
