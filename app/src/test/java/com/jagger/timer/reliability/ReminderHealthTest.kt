// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.reliability

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderHealthTest {
    private val good = ReminderHealth(true, true, true, true)

    @Test fun allGoodHasNoIssuesAndNoBanner() {
        assertTrue(good.allGood); assertTrue(good.issues.isEmpty()); assertNull(ReminderHealth.bannerText(good))
    }

    @Test fun batteryOnlyIsOneIssue() {
        val h = good.copy(batteryUnrestricted = false)
        assertEquals(listOf(ReminderIssue.BATTERY), h.issues)
        assertEquals("Reminders may be late or missing: battery use isn't unrestricted", ReminderHealth.bannerText(h))
    }

    @Test fun issuesAreOrderedWorstFirst() {
        val h = ReminderHealth(false, false, false, false)
        assertEquals(listOf(ReminderIssue.NOTIFICATIONS, ReminderIssue.EXACT_ALARMS, ReminderIssue.BATTERY, ReminderIssue.FULL_SCREEN), h.issues)
    }

    @Test fun bannerCountsTheRest() {
        val h = good.copy(batteryUnrestricted = false, fullScreenAllowed = false)
        assertEquals("Reminders may be late or missing: battery use isn't unrestricted (and 1 more)", ReminderHealth.bannerText(h))
    }

    @Test fun unknownStateRaisesNothing() = assertTrue(ReminderHealth.Unknown.allGood)

    @Test fun snoozeLastsAWeek() {
        val until = 1_000L + BannerSnooze.WEEK_MILLIS
        assertTrue(BannerSnooze.isSnoozed(until, 1_000L + 1))
        assertFalse(BannerSnooze.isSnoozed(until, until))
        assertFalse(BannerSnooze.isSnoozed(0L, 5L))
    }
}
