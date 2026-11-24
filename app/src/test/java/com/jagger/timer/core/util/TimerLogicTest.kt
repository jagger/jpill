package com.jagger.timer.core.util

import com.jagger.timer.core.data.local.entity.TimerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerLogicTest {
    private val t0 = 1_000_000L
    private val min = 60_000L

    @Test fun startRunsUntilNowPlusDuration() {
        val t = TimerLogic.start(" tea ", 3 * min, t0)
        assertEquals("tea", t.label)
        assertEquals(TimerState.RUNNING, t.state)
        assertEquals(t0 + 3 * min, t.endAtMillis)
        assertEquals(2 * min, TimerLogic.remaining(t, t0 + min))
    }

    @Test(expected = IllegalArgumentException::class) fun zeroDurationRejected() { TimerLogic.start("x", 0, t0) }

    @Test fun pauseFreezesRemainingAndResumeContinuesFromIt() {
        val running = TimerLogic.start("", 10 * min, t0)
        val paused = TimerLogic.pause(running, t0 + 4 * min)
        assertEquals(TimerState.PAUSED, paused.state)
        assertNull(paused.endAtMillis)
        assertEquals(6 * min, TimerLogic.remaining(paused, t0 + 99 * min)) // wall clock no longer matters
        val resumed = TimerLogic.resume(paused, t0 + 20 * min)
        assertEquals(t0 + 26 * min, resumed.endAtMillis)
        assertNull(resumed.remainingMillis)
    }

    @Test fun pausingAnOverdueTimerFinishesItInstead() {
        val running = TimerLogic.start("", min, t0)
        assertEquals(TimerState.FINISHED, TimerLogic.pause(running, t0 + 5 * min).state)
    }

    @Test fun isDueOnlyWhenRunningAndPastEnd() {
        val r = TimerLogic.start("", min, t0)
        assertFalse(TimerLogic.isDue(r, t0 + min - 1))
        assertTrue(TimerLogic.isDue(r, t0 + min))
        assertFalse(TimerLogic.isDue(TimerLogic.pause(r, t0 + 1), t0 + 9 * min))
    }

    @Test fun finishedHasNothingRemainingAndRestartRunsFullDurationAgain() {
        val done = TimerLogic.finish(TimerLogic.start("", 2 * min, t0))
        assertEquals(0L, TimerLogic.remaining(done, t0 + 9 * min))
        val again = TimerLogic.restart(done, t0 + 10 * min)
        assertEquals(TimerState.RUNNING, again.state)
        assertEquals(t0 + 12 * min, again.endAtMillis)
    }

    @Test fun resumeAndPauseIgnoreWrongStates() {
        val r = TimerLogic.start("", min, t0)
        assertEquals(r, TimerLogic.resume(r, t0))
        val p = TimerLogic.pause(r, t0 + 1)
        assertEquals(p, TimerLogic.pause(p, t0 + 2))
    }

    @Test fun formatRoundsUpAndSwitchesToHours() {
        assertEquals("0:01", TimerLogic.format(1))
        assertEquals("1:00", TimerLogic.format(60_000))
        assertEquals("59:59", TimerLogic.format(3_599_000))
        assertEquals("1:00:00", TimerLogic.format(3_600_000))
        assertEquals("0:00", TimerLogic.format(-5))
    }
}
