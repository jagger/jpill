// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.core.util

import com.jagger.timer.core.data.local.dao.DoseHistoryRow
import com.jagger.timer.core.data.local.entity.DoseStatus
import com.jagger.timer.core.data.local.entity.ScheduleEntity
import com.jagger.timer.core.data.local.entity.ScheduleType
import com.jagger.timer.core.data.local.entity.RecurrencePattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

class HistoryFormatterTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 11, 23)
    private fun ms(y: Int, mo: Int, d: Int, h: Int, mi: Int) = LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()
    private fun row(ts: Long, status: DoseStatus = DoseStatus.TAKEN, name: String = "Ibuprofen", amount: Double? = 200.0, archived: Boolean = false) =
        DoseHistoryRow(1, 1, name, "mg", archived, ts, status, amount, null)

    @Test fun dayLabels() {
        assertEquals("Today", HistoryFormatter.dayLabel(today, today, Locale.US))
        assertEquals("Yesterday", HistoryFormatter.dayLabel(today.minusDays(1), today, Locale.US))
        assertEquals("Fri, Nov 20", HistoryFormatter.dayLabel(LocalDate.of(2026, 11, 20), today, Locale.US))
        assertEquals("Dec 31, 2025", HistoryFormatter.dayLabel(LocalDate.of(2025, 12, 31), today, Locale.US))
    }

    @Test fun groupsByDayKeepingNewestFirstOrder() {
        val rows = listOf(row(ms(2026, 11, 23, 14, 0)), row(ms(2026, 11, 23, 8, 0)), row(ms(2026, 11, 22, 21, 30)), row(ms(2026, 11, 20, 9, 0)))
        val groups = HistoryFormatter.groupByDay(rows, zone, today, Locale.US)
        assertEquals(listOf("Today", "Yesterday", "Fri, Nov 20"), groups.map { it.label })
        assertEquals(listOf(2, 1, 1), groups.map { it.rows.size })
        assertEquals(ms(2026, 11, 23, 14, 0), groups[0].rows[0].timestamp)
    }

    @Test fun groupingUsesTheZoneNotUtc() {
        // 23:30 UTC is already the next day in UTC+2
        val ts = ms(2026, 11, 22, 23, 30)
        val g = HistoryFormatter.groupByDay(listOf(row(ts)), ZoneId.of("Europe/Berlin"), today, Locale.US)
        assertEquals("Today", g.single().label)
    }

    @Test fun emptyInputGivesNoGroups() = assertTrue(HistoryFormatter.groupByDay(emptyList(), zone, today).isEmpty())

    @Test fun labels() {
        assertEquals("200 mg", HistoryFormatter.amountLabel(row(0)))
        assertEquals("27.5 mg", HistoryFormatter.amountLabel(row(0, amount = 27.5)))
        assertNull(HistoryFormatter.amountLabel(row(0, amount = null)))
        assertEquals("2:05 PM", HistoryFormatter.timeLabel(ms(2026, 11, 23, 14, 5), zone, Locale.US))
        assertEquals(listOf("Taken", "Skipped", "Missed"), DoseStatus.values().map { HistoryFormatter.statusLabel(it) })
    }
}

class MissedDoseDetectorTest {
    private val min = 60_000L
    private val due = 1_000_000_000_000L
    private fun sched(type: ScheduleType = ScheduleType.FIXED_TIME, interval: Int? = null) =
        ScheduleEntity(pillId = 1, type = type, pattern = RecurrencePattern.DAILY, timeMinutes = 480, intervalMinutes = interval)

    @Test fun graceIsAnHourByDefault() = assertEquals(60 * min, MissedDoseDetector.graceMillis(sched()))

    @Test fun graceIsCappedAtHalfAShortInterval() {
        assertEquals(60 * min, MissedDoseDetector.graceMillis(sched(ScheduleType.INTERVAL, 12 * 60)))
        assertEquals(60 * min, MissedDoseDetector.graceMillis(sched(ScheduleType.INTERVAL, 120)))
        assertEquals(30 * min, MissedDoseDetector.graceMillis(sched(ScheduleType.INTERVAL, 60)))
    }

    @Test fun notMissedBeforeTheGraceWindowEnds() {
        assertFalse(MissedDoseDetector.shouldLogMissed(emptyList(), due, 30, 60 * min, due + 59 * min))
    }

    @Test fun missedWhenNothingLoggedAfterTheGraceWindow() {
        assertTrue(MissedDoseDetector.shouldLogMissed(emptyList(), due, 30, 60 * min, due + 60 * min))
        assertTrue(MissedDoseDetector.shouldLogMissed(listOf(due - 5 * 3_600_000L), due, 30, 60 * min, due + 61 * min)) // an old log doesn't count
    }

    @Test fun anyLogInTheWindowHandlesTheDose() {
        assertFalse(MissedDoseDetector.shouldLogMissed(listOf(due + 10 * min), due, 30, 60 * min, due + 61 * min))   // taken late-ish
        assertFalse(MissedDoseDetector.shouldLogMissed(listOf(due - 20 * min), due, 30, 60 * min, due + 61 * min))   // taken a bit early
        assertTrue(MissedDoseDetector.shouldLogMissed(listOf(due - 31 * min), due, 30, 60 * min, due + 61 * min))    // too early: previous dose
    }
}
