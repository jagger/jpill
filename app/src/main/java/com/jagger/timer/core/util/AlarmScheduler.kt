package com.jagger.timer.core.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.jagger.timer.core.data.local.entity.PillEntity
import com.jagger.timer.core.data.local.entity.ScheduleEntity
import com.jagger.timer.core.receiver.AlarmReceiver
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
