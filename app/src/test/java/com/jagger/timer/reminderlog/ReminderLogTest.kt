// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.reminderlog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class ReminderLogTest {
    @Test fun format_hasTimestampPaddedEventAndDetail() {
        val line = ReminderLog.format(0L, ZoneOffset.UTC, "ARM", "pill=1")
        assertEquals("1970-01-01 00:00:00.000  ARM               pill=1", line)
    }

    @Test fun format_longEventNamesAreNotTruncated() {
        assertTrue(ReminderLog.format(0L, ZoneOffset.UTC, "MISSED, NO ALARM", "x").contains("MISSED, NO ALARM"))
    }

    @Test fun trim_keepsNewestLines() {
        assertEquals(listOf("c", "d"), ReminderLog.trim(listOf("a", "b", "c", "d"), 2))
        assertEquals(listOf("a"), ReminderLog.trim(listOf("a"), 5))
    }

    @Test fun clock_isMonthDayAndSeconds() {
        assertEquals("01-01 00:00:00", ReminderLog.clock(0L, ZoneOffset.UTC))
    }
}
