package com.jagger.timer.core.util

import com.jagger.timer.core.data.local.entity.TimerEntity
import com.jagger.timer.core.data.local.entity.TimerState

/** Pure state transitions for [TimerEntity]. All take `now` so they are testable. */
object TimerLogic {

    fun start(label: String, durationMillis: Long, now: Long): TimerEntity {
        require(durationMillis > 0) { "duration must be positive" }
        return TimerEntity(
            label = label.trim(), durationMillis = durationMillis, state = TimerState.RUNNING,
            endAtMillis = now + durationMillis, createdAt = now
        )
    }

    /** Millis left (0 when finished or overdue). */
    fun remaining(t: TimerEntity, now: Long): Long = when (t.state) {
        TimerState.RUNNING -> ((t.endAtMillis ?: now) - now).coerceAtLeast(0L)
        TimerState.PAUSED -> (t.remainingMillis ?: t.durationMillis).coerceAtLeast(0L)
        TimerState.FINISHED -> 0L
    }

    /** True when a RUNNING timer has reached its end. */
    fun isDue(t: TimerEntity, now: Long): Boolean =
        t.state == TimerState.RUNNING && (t.endAtMillis ?: Long.MAX_VALUE) <= now

    fun pause(t: TimerEntity, now: Long): TimerEntity =
        if (t.state != TimerState.RUNNING) t
        else if (isDue(t, now)) finish(t)
        else t.copy(state = TimerState.PAUSED, remainingMillis = remaining(t, now), endAtMillis = null)

    fun resume(t: TimerEntity, now: Long): TimerEntity =
        if (t.state != TimerState.PAUSED) t
        else t.copy(state = TimerState.RUNNING, endAtMillis = now + remaining(t, now), remainingMillis = null)

    fun finish(t: TimerEntity): TimerEntity =
        t.copy(state = TimerState.FINISHED, endAtMillis = null, remainingMillis = 0L)

    /** Run the same duration again from now (works from any state). */
    fun restart(t: TimerEntity, now: Long): TimerEntity =
        t.copy(state = TimerState.RUNNING, endAtMillis = now + t.durationMillis, remainingMillis = null)

    /** "mm:ss", or "h:mm:ss" from an hour up. Rounds up so the display never shows 0:00 while time remains. */
    fun format(millis: Long): String {
        val total = (millis.coerceAtLeast(0L) + 999) / 1000
        val h = total / 3600; val m = (total % 3600) / 60; val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}
