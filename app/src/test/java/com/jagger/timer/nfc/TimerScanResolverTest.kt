// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.nfc

import com.jagger.timer.core.data.local.entity.TimerEntity
import com.jagger.timer.core.data.local.entity.TimerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerScanResolverTest {
    private val id = "AAAAAAAAAAAAAAAAAAAAAA"
    private val payload = NfcPayload.Timer(id, "Washer", 2700)
    private val now = 1_000_000L
    private fun timer(state: TimerState, endAt: Long? = null, remaining: Long? = null) =
        TimerEntity(id = 1, label = "Washer", durationMillis = 2_700_000, state = state, endAtMillis = endAt, remainingMillis = remaining, nfcTagId = id)

    @Test fun unknownTagOnThisPhoneStartsImmediately_fromTheTagsOwnNameAndLength() {
        val d = TimerScanResolver.resolve(payload, existing = null, nowMillis = now)
        assertTrue(d is TimerScanDecision.CreateAndStart)
        assertEquals("Washer", (d as TimerScanDecision.CreateAndStart).payload.label)
        assertEquals(2700L, d.payload.seconds)
        assertTrue(TimerScanResolver.startsImmediately(d))
    }

    @Test fun finishedTimerStartsImmediately() =
        assertTrue(TimerScanResolver.startsImmediately(TimerScanResolver.resolve(payload, timer(TimerState.FINISHED), now)))

    @Test fun runningTimerStillAsks() =
        assertFalse(TimerScanResolver.startsImmediately(TimerScanResolver.resolve(payload, timer(TimerState.RUNNING, endAt = now + 60_000), now)))

    @Test fun pausedTimerStillAsks() =
        assertFalse(TimerScanResolver.startsImmediately(TimerScanResolver.resolve(payload, timer(TimerState.PAUSED, remaining = 60_000), now)))
}
