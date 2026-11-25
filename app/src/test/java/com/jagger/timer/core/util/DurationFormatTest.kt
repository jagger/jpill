// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class DurationFormatTest {
    @Test fun hoursAndMinutes() = assertEquals("2h 14m", formatCountdown((2 * 3600 + 14 * 60) * 1000L))
    @Test fun wholeHours() = assertEquals("4h", formatCountdown(4 * 3600 * 1000L))
    @Test fun minutes() = assertEquals("14m", formatCountdown(14 * 60 * 1000L))
    @Test fun partialMinuteRoundsUp() = assertEquals("15m", formatCountdown((14 * 60 + 5) * 1000L))
    @Test fun underAMinuteShowsOne() = assertEquals("1m", formatCountdown(42_000L))
    @Test fun justOverAMinute() = assertEquals("2m", formatCountdown(60_001L))
    @Test fun zeroAndNegative() {
        assertEquals("0m", formatCountdown(0L))
        assertEquals("0m", formatCountdown(-5L))
    }
}
