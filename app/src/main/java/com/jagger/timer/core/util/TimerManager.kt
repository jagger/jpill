// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.core.util

import android.app.NotificationManager
import android.content.Context
import com.jagger.timer.core.data.local.dao.TimerDao
import com.jagger.timer.core.data.local.entity.TimerEntity
import com.jagger.timer.core.data.local.entity.TimerState
import com.jagger.timer.core.receiver.safeNotificationId
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Orchestrates timer rows and their finish alarms. */
@Singleton
class TimerManager @Inject constructor(
    private val dao: TimerDao,
    private val alarms: AlarmScheduler,
    @ApplicationContext private val context: Context
) {
    fun observeAll() = dao.observeAll()

    suspend fun start(label: String, durationMillis: Long, now: Long = System.currentTimeMillis()): Long {
        val t = TimerLogic.start(label, durationMillis, now)
        val id = dao.insert(t)
        alarms.scheduleTimerAlarm(id, t.endAtMillis!!)
        return id
    }

    suspend fun getByNfcTag(tagId: String): TimerEntity? = dao.getByNfcTag(tagId)

    /** Starts a new timer that owns [tagId] (a scanned tag whose timer does not exist yet, e.g. after a reinstall). */
    suspend fun startFromTag(tagId: String, label: String, durationMillis: Long, now: Long = System.currentTimeMillis()): Long {
        val t = TimerLogic.start(label, durationMillis, now).copy(nfcTagId = tagId)
        val id = dao.insert(t)
        alarms.scheduleTimerAlarm(id, t.endAtMillis!!)
        return id
    }

    /** Points the timer at [tagId] (or clears it with null). The caller makes sure no other timer or medicine owns the id. */
    suspend fun setNfcTag(id: Long, tagId: String?) {
        val t = dao.getById(id) ?: return
        dao.update(t.copy(nfcTagId = tagId))
    }

    suspend fun pause(id: Long, now: Long = System.currentTimeMillis()) = change(id) { TimerLogic.pause(it, now) }
    suspend fun resume(id: Long, now: Long = System.currentTimeMillis()) = change(id) { TimerLogic.resume(it, now) }
    suspend fun restart(id: Long, now: Long = System.currentTimeMillis()) = change(id) { TimerLogic.restart(it, now) }

    suspend fun delete(id: Long) {
        alarms.cancelTimerAlarm(id)
        cancelNotification(id)
        dao.delete(id)
    }

    /** The finish alarm fired. Returns the timer if it really finished (false alarms re-plan instead). */
    suspend fun onAlarm(id: Long, now: Long = System.currentTimeMillis()): TimerEntity? {
        val t = dao.getById(id) ?: return null
        if (t.state != TimerState.RUNNING) return null
        if (!TimerLogic.isDue(t, now)) { alarms.scheduleTimerAlarm(id, t.endAtMillis!!); return null }
        val done = TimerLogic.finish(t)
        dao.update(done)
        return done
    }

    /** After boot: finish overdue timers (returned so the caller can notify) and re-arm the rest. */
    suspend fun restoreAfterBoot(now: Long = System.currentTimeMillis()): List<TimerEntity> {
        val finished = mutableListOf<TimerEntity>()
        for (t in dao.getAll().filter { it.state == TimerState.RUNNING }) {
            if (TimerLogic.isDue(t, now)) { val d = TimerLogic.finish(t); dao.update(d); finished += d }
            else alarms.scheduleTimerAlarm(t.id, t.endAtMillis!!)
        }
        return finished
    }

    private suspend fun change(id: Long, f: (TimerEntity) -> TimerEntity) {
        val old = dao.getById(id) ?: return
        val new = f(old)
        dao.update(new)
        cancelNotification(id)
        if (new.state == TimerState.RUNNING) alarms.scheduleTimerAlarm(id, new.endAtMillis!!)
        else alarms.cancelTimerAlarm(id)
    }

    private fun cancelNotification(id: Long) {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(AlarmNotificationIds.timer(id))
    }
}

object AlarmNotificationIds {
    /** Distinct from reminder (none), refill 0x20000000 and cooldown 0x40000000 ids. */
    fun timer(id: Long): Int = safeNotificationId(id) or 0x10000000
}
