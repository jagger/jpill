package com.jagger.timer.core.util

import com.jagger.timer.core.data.local.dao.DoseHistoryRow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Builds the History export. Pure, so quoting and formatting are unit-tested. */
object HistoryCsv {
    const val HEADER = "Date,Time,Medication,Status,Amount,Unit,Note"
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

    fun build(rows: List<DoseHistoryRow>, zone: ZoneId): String {
        val sb = StringBuilder("﻿").append(HEADER).append("\r\n") // BOM so Excel reads UTF-8 names correctly
        rows.forEach { r ->
            val at = Instant.ofEpochMilli(r.timestamp).atZone(zone)
            sb.append(
                listOf(
                    dateFormat.format(at),
                    timeFormat.format(at),
                    r.pillName + if (r.isArchived) " (archived)" else "",
                    HistoryFormatter.statusLabel(r.status),
                    r.amount?.let { RxFormLogic.formatCount(it) }.orEmpty(),
                    if (r.amount != null) r.pillUnit else "",
                    r.note.orEmpty()
                ).joinToString(",") { field(it) }
            ).append("\r\n")
        }
        return sb.toString()
    }

    /**
     * RFC 4180 quoting, plus a leading apostrophe on text that a spreadsheet would run as a formula
     * (starts with = + - @), so a medicine called "=SUM(...)" can't execute when opened.
     */
    fun field(raw: String): String {
        val safe = if (raw.isNotEmpty() && raw[0] in "=+-@\t\r") "'$raw" else raw
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }
}
