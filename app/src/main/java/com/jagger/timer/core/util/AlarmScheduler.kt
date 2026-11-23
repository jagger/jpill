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
