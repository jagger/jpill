// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.core.util

import com.jagger.timer.core.data.local.dao.DoseHistoryRow
import com.jagger.timer.core.data.local.entity.DoseStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale

class AdherenceCalcTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 25)

    private fun row(date: LocalDate, status: DoseStatus, schedule: Long? = 1, hour: Int = 8) = DoseHistoryRow(
        logId = 0, pillId = 1, pillName = "A", pillUnit = "mg", isArchived = false,
        timestamp = LocalDateTime.of(date, java.time.LocalTime.of(hour, 0)).toInstant(zone).toEpochMilli(),
        status = status, amount = null, note = null, scheduleId = schedule
    )

    private fun days(rows: List<DoseHistoryRow>) = AdherenceCalc.last(30, rows, zone, today)

    @Test fun thirtyDaysOldestFirstEndingToday() {
        val d = days(emptyList())
        assertEquals(30, d.size)
        assertEquals(today, d.last().date)
        assertEquals(today.minusDays(29), d.first().date)
        assertTrue(d.none { it.hasData })
    }

    @Test fun allTakenIsFull() {
        val d = days(listOf(row(today, DoseStatus.TAKEN), row(today, DoseStatus.TAKEN, hour = 20))).last()
        assertEquals(2, d.expected); assertEquals(1f, d.fraction); assertEquals(100, d.percent)
    }

    @Test fun mixOfTakenSkippedMissed() {
        val y = today.minusDays(1)
        val d = days(listOf(row(y, DoseStatus.TAKEN), row(y, DoseStatus.SKIPPED), row(y, DoseStatus.MISSED), row(y, DoseStatus.TAKEN))).first { it.date == y }
        assertEquals(4, d.expected); assertEquals(2, d.taken); assertEquals(50, d.percent)
    }

    @Test fun asNeededDosesDoNotCount() {
        val d = days(listOf(row(today, DoseStatus.TAKEN, schedule = null))).last()
        assertFalse(d.hasData)
    }

    @Test fun logsOutsideTheWindowAreIgnored() {
        val d = days(listOf(row(today.minusDays(30), DoseStatus.TAKEN), row(today.plusDays(1), DoseStatus.TAKEN)))
        assertTrue(d.none { it.hasData })
    }

    @Test fun extraDoseCannotExceedOneHundredPercent() {
        val d = DayAdherence(today, taken = 3, expected = 2)
        assertEquals(1f, d.fraction)
    }

    @Test fun overallPercentWeightsByDoses() {
        val d = listOf(DayAdherence(today, 4, 4), DayAdherence(today.minusDays(1), 0, 4))
        assertEquals(50, AdherenceCalc.overallPercent(d))
        assertNull(AdherenceCalc.overallPercent(listOf(DayAdherence(today, 0, 0))))
    }

    @Test fun describesDays() {
        assertEquals("Wed, Nov 25: 3 of 4 scheduled doses taken (75%)", AdherenceCalc.describe(DayAdherence(today, 3, 4), Locale.US))
        assertEquals("Wed, Nov 25: no scheduled doses recorded", AdherenceCalc.describe(DayAdherence(today, 0, 0), Locale.US))
        assertEquals("Wed, Nov 25: 1 of 1 scheduled dose taken (100%)", AdherenceCalc.describe(DayAdherence(today, 1, 1), Locale.US))
    }
}
