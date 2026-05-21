package com.jagger.timer.core.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.jagger.timer.core.data.local.entity.TimerEntity
import com.jagger.timer.core.receiver.AlarmReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimerAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleTimer(timer: TimerEntity) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_TIMER_FIRED
            putExtra(AlarmReceiver.EXTRA_TIMER_ID, timer.id)
            putExtra(AlarmReceiver.EXTRA_TIMER_NAME, timer.name)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            timer.id.toInt() + 20000, // Offset to avoid collision with schedules
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAtMillis = System.currentTimeMillis() + (timer.durationSeconds * 1000L)

        val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerAtMillis, pendingIntent)
        alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
    }

    fun cancelTimer(timer: TimerEntity) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_TIMER_FIRED
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            timer.id.toInt() + 20000,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.let {
            alarmManager.cancel(it)
        }
    }
}
