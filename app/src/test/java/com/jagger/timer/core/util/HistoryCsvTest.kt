package com.jagger.timer.core.util

import com.jagger.timer.core.data.local.dao.DoseHistoryRow
import com.jagger.timer.core.data.local.entity.DoseStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class HistoryCsvTest {
    private fun row(name: String, status: DoseStatus = DoseStatus.TAKEN, amount: Double? = 10.0, note: String? = null, archived: Boolean = false) =
        DoseHistoryRow(logId = 1, pillId = 1, pillName = name, pillUnit = "mg", isArchived = archived,
            timestamp = 1_700_000_000_000L, status = status, amount = amount, note = note)

    @Test fun headerAndRow() {
        val lines = HistoryCsv.build(listOf(row("Amlodipine")), ZoneOffset.UTC).removePrefix("﻿").split("\r\n")
        assertEquals(HistoryCsv.HEADER, lines[0])
        assertEquals("2023-11-14,22:13,Amlodipine,Taken,10,mg,", lines[1])
    }

    @Test fun quotesCommasAndQuotes() = assertEquals("\"a, \"\"b\"\"\"", HistoryCsv.field("a, \"b\""))

    @Test fun neutralisesFormulas() {
        assertEquals("'=SUM(A1)", HistoryCsv.field("=SUM(A1)"))
        assertEquals("'@x", HistoryCsv.field("@x"))
    }

    @Test fun missedHasNoAmountOrUnit() {
        val csv = HistoryCsv.build(listOf(row("X", DoseStatus.MISSED, amount = null)), ZoneOffset.UTC)
        assertTrue(csv.contains(",Missed,,,"))
    }

    @Test fun archivedIsTagged() = assertTrue(HistoryCsv.build(listOf(row("Old", archived = true)), ZoneOffset.UTC).contains("Old (archived)"))
}
