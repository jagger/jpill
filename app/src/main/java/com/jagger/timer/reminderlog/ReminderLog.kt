// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.reminderlog

import android.content.Context
import android.util.Log
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Persistent on-device trace of the reminder machinery: alarms armed, alarms that fired, notifications posted,
 * actions tapped, and why anything failed. Reminders are the point of the app, so when one goes missing this file
 * says which step broke. Lives in the app's own folder (not readable by other apps), is capped in size, and can be
 * shared or cleared from Settings > Developer tools > Reminder log. Also mirrored to logcat under the tag [TAG].
 */
object ReminderLog {
    const val TAG = "jPillAlarm"
    private const val FILE_NAME = "reminder_log.txt"
    private const val MAX_BYTES = 200_000L
    private const val KEEP_LINES = 1200

    private var file: File? = null
    private val lock = Any()

    fun init(context: Context) {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        file = File(dir, FILE_NAME)
    }

    fun log(event: String, detail: String = "") {
        val line = format(System.currentTimeMillis(), ZoneId.systemDefault(), event, detail)
        Log.i(TAG, line)
        val f = file ?: return
        synchronized(lock) {
            runCatching {
                f.appendText(line + "\n")
                if (f.length() > MAX_BYTES) f.writeText(trim(f.readLines(), KEEP_LINES).joinToString("\n", postfix = "\n"))
            }
        }
    }

    /** The whole log, oldest first; empty if there is none. */
    fun read(): String = synchronized(lock) { runCatching { file?.takeIf { it.exists() }?.readText() ?: "" }.getOrDefault("") }

    fun clear() = synchronized(lock) { runCatching { file?.writeText("") } }

    fun path(): String? = file?.absolutePath

    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    /** One log line: local timestamp, event name, free-form detail. Pure so it is unit-tested. */
    fun format(nowMillis: Long, zone: ZoneId, event: String, detail: String): String =
        stamp.format(Instant.ofEpochMilli(nowMillis).atZone(zone)) + "  " + event.padEnd(18) + detail

    /** Keeps the newest [keep] lines. */
    fun trim(lines: List<String>, keep: Int): List<String> = if (lines.size <= keep) lines else lines.takeLast(keep)

    /** Millis -> "HH:mm:ss" local, for alarm times inside log details. */
    fun clock(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        DateTimeFormatter.ofPattern("MM-dd HH:mm:ss").format(Instant.ofEpochMilli(millis).atZone(zone))
}
