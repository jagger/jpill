// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.core.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.jagger.timer.core.data.local.entity.PillEntity
import com.jagger.timer.core.data.local.entity.ScheduleEntity
import com.jagger.timer.core.receiver.AlarmReceiver
import com.jagger.timer.core.receiver.safeNotificationId
import android.app.NotificationManager
import dagger.hilt.android.qualifiers.ApplicationContext
import com.jagger.timer.reminderlog.ReminderLog
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleAlarm(pill: PillEntity, schedule: ScheduleEntity, nextOccurrence: LocalDateTime) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.jagger.timer.ACTION_PILL_REMINDER"
            putExtra("PILL_ID", pill.id)
            putExtra("PILL_NAME", pill.name)
            putExtra("SCHEDULE_ID", schedule.id)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            schedule.id.toInt(), // Use schedule ID as request code
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAtMillis = nextOccurrence
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerAtMillis, pendingIntent)
        alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
        ReminderLog.log(
            "ARM", "pill=${pill.id} (${pill.name}) sched=${schedule.id} fires=${ReminderLog.clock(triggerAtMillis)} " +
                "exactAllowed=${alarmManager.canScheduleExactAlarms()}"
        )
    }

    /** True if this schedule's reminder alarm is currently registered with Android. */
    fun isArmed(scheduleId: Long): Boolean = PendingIntent.getBroadcast(
        context, scheduleId.toInt(),
        Intent(context, AlarmReceiver::class.java).apply { action = "com.jagger.timer.ACTION_PILL_REMINDER" },
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
    ) != null

    /** Android's own next alarm-clock time (what the status-bar alarm icon shows), or null. */
    fun nextAlarmClockMillis(): Long? = alarmManager.nextAlarmClock?.triggerTime

    /** Removes just the scheduled-dose reminder notification. */
    fun cancelDueReminder(pillId: Long) {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(safeNotificationId(pillId))
        ReminderLog.log("DISMISS", "pill=$pillId reminder cleared (dose handled in the app)")
    }

    /** Removes any notification this pill already posted (scheduled reminder, cooldown-done, refill). */
    fun cancelPillNotifications(pillId: Long) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val base = safeNotificationId(pillId)
        nm.cancel(base)               // scheduled reminder
        nm.cancel(base or 0x40000000) // "available again"
        nm.cancel(base or 0x20000000) // refill
    }

    /** One-shot alarm for "your wait is over" on an as-needed pill. Replaces any earlier one. */
    fun scheduleCooldownAlarm(pillId: Long, triggerAtMillis: Long) {
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            cooldownPendingIntent(pillId, PendingIntent.FLAG_UPDATE_CURRENT)!!
        )
    }

    fun cancelCooldownAlarm(pillId: Long) {
        cooldownPendingIntent(pillId, PendingIntent.FLAG_NO_CREATE)?.let { alarmManager.cancel(it) }
    }

    // Distinct action keeps this from colliding with schedule alarms, which use schedule.id as request code.
    private fun cooldownPendingIntent(pillId: Long, flag: Int): PendingIntent? {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_COOLDOWN_DONE
            putExtra(AlarmReceiver.EXTRA_PILL_ID, pillId)
        }
        return PendingIntent.getBroadcast(context, pillId.toInt(), intent, flag or PendingIntent.FLAG_IMMUTABLE)
    }

    /**
     * Fires once after a scheduled dose came due plus its grace window, to log it as missed if nothing was
     * logged. One pending check per schedule (a later refresh replaces it with the next dose's).
     */
    fun scheduleMissedCheck(pillId: Long, scheduleId: Long, dueAtMillis: Long, triggerAtMillis: Long) {
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            missedCheckPendingIntent(pillId, scheduleId, dueAtMillis, PendingIntent.FLAG_UPDATE_CURRENT)!!
        )
        ReminderLog.log("ARM missed-check", "pill=$pillId sched=$scheduleId due=${ReminderLog.clock(dueAtMillis)} checks=${ReminderLog.clock(triggerAtMillis)}")
    }

    fun cancelMissedCheck(scheduleId: Long) {
        missedCheckPendingIntent(-1L, scheduleId, 0L, PendingIntent.FLAG_NO_CREATE)?.let { alarmManager.cancel(it) }
    }

    private fun missedCheckPendingIntent(pillId: Long, scheduleId: Long, dueAtMillis: Long, flag: Int): PendingIntent? {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_MISSED_CHECK
            putExtra(AlarmReceiver.EXTRA_PILL_ID, pillId)
            putExtra(AlarmReceiver.EXTRA_SCHEDULE_ID, scheduleId)
            putExtra(AlarmReceiver.EXTRA_DUE_AT, dueAtMillis)
        }
        return PendingIntent.getBroadcast(context, scheduleId.toInt(), intent, flag or PendingIntent.FLAG_IMMUTABLE)
    }

    /** One-shot alarm for the prescription refill reminder. Replaces any earlier one for this pill. */
    fun scheduleRefillAlarm(pillId: Long, triggerAtMillis: Long) {
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            refillPendingIntent(pillId, PendingIntent.FLAG_UPDATE_CURRENT)!!
        )
    }

    fun cancelRefillAlarm(pillId: Long) {
        refillPendingIntent(pillId, PendingIntent.FLAG_NO_CREATE)?.let { alarmManager.cancel(it) }
    }

    private fun refillPendingIntent(pillId: Long, flag: Int): PendingIntent? {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_REFILL_DUE
            putExtra(AlarmReceiver.EXTRA_PILL_ID, pillId)
        }
        return PendingIntent.getBroadcast(context, pillId.toInt(), intent, flag or PendingIntent.FLAG_IMMUTABLE)
    }

    /** One-shot alarm for a countdown timer finishing. Replaces any earlier one for this timer. */
    fun scheduleTimerAlarm(timerId: Long, triggerAtMillis: Long) {
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP, triggerAtMillis, timerPendingIntent(timerId, PendingIntent.FLAG_UPDATE_CURRENT)!!
        )
    }

    fun cancelTimerAlarm(timerId: Long) {
        timerPendingIntent(timerId, PendingIntent.FLAG_NO_CREATE)?.let { alarmManager.cancel(it) }
    }

    // Distinct action: request codes only need to be unique per action (see the cooldown alarm).
    private fun timerPendingIntent(timerId: Long, flag: Int): PendingIntent? {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_TIMER_DONE
            putExtra(AlarmReceiver.EXTRA_TIMER_ID, timerId)
        }
        return PendingIntent.getBroadcast(context, timerId.toInt(), intent, flag or PendingIntent.FLAG_IMMUTABLE)
    }

    fun cancelAlarm(schedule: ScheduleEntity) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.jagger.timer.ACTION_PILL_REMINDER"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            schedule.id.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.let {
            alarmManager.cancel(it)
        }
    }
}
