// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.core.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.jagger.timer.MainActivity
import com.jagger.timer.core.util.hideNameOnLockScreen
import com.jagger.timer.reminderlog.ReminderLog

/**
 * Posts the "Time for your <medicine>" reminder straight from the alarm receiver. (It used to go through a
 * foreground service, but a second reminder at the same minute replaced the first one's notification, tapping
 * Taken on one stopped the service for all, and a refused service start lost the reminder silently.) One
 * notification per medicine, id = the medicine's id, so reminders never clobber each other.
 */
object ReminderNotifier {
    const val CHANNEL_ID = "pill_reminders"
    private const val PREFS = "reminder_trace"

    /** Posts the reminder. Returns true if Android accepted it; every outcome is written to the reminder log. */
    fun post(context: Context, pillId: Long, scheduleId: Long, pillName: String, hideNames: Boolean): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return try {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Pill Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Urgent alerts for your medication"
                    enableLights(true)
                    enableVibration(true)
                }
            )
            val id = safeNotificationId(pillId)
            fun action(action: String, slot: Int) = PendingIntent.getBroadcast(
                context, id * 10 + slot,
                Intent(context, AlarmReceiver::class.java).apply {
                    this.action = action
                    putExtra(AlarmReceiver.EXTRA_PILL_ID, pillId)
                    putExtra(AlarmReceiver.EXTRA_SCHEDULE_ID, scheduleId)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val taken = action(AlarmReceiver.ACTION_TAKEN, 1)
            val skip = action(AlarmReceiver.ACTION_SKIPPED, 2)
            val snooze = action(AlarmReceiver.ACTION_SNOOZE, 3)
            val open = PendingIntent.getActivity(
                context, id,
                Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Time for your $pillName")
                .setContentText("Please take your scheduled dose.")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentIntent(open)
                .setFullScreenIntent(open, true)
                .addAction(0, "Taken", taken)
                .addAction(0, "Skip", skip)
                .addAction(0, "Snooze (10m)", snooze)
                .hideNameOnLockScreen(
                    context, CHANNEL_ID, hideNames,
                    listOf("Taken" to taken, "Skip" to skip, "Snooze (10m)" to snooze)
                )
                .build()
            nm.notify(id, notification)
            markFired(context, scheduleId)
            val posted = nm.activeNotifications.any { it.id == id }
            ReminderLog.log(
                "NOTIFY", "pill=$pillId sched=$scheduleId posted=$posted ${deliveryState(context)}"
            )
            posted
        } catch (t: Throwable) {
            ReminderLog.log("NOTIFY FAILED", "pill=$pillId sched=$scheduleId ${t::class.java.simpleName}: ${t.message}")
            false
        }
    }

    /** What could stop a posted notification from being seen or heard, for the log. */
    fun deliveryState(context: Context): String {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = nm.getNotificationChannel(CHANNEL_ID)
        val filter = when (nm.currentInterruptionFilter) {
            NotificationManager.INTERRUPTION_FILTER_ALL -> "off"
            NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "PRIORITY-ONLY"
            NotificationManager.INTERRUPTION_FILTER_ALARMS -> "ALARMS-ONLY"
            NotificationManager.INTERRUPTION_FILTER_NONE -> "TOTAL-SILENCE"
            else -> "unknown"
        }
        return "appNotifs=${nm.areNotificationsEnabled()} channelImportance=${channel?.importance ?: "none"} " +
            "bypassDnd=${channel?.canBypassDnd() ?: "?"} dnd=$filter"
    }

    fun markFired(context: Context, scheduleId: Long, now: Long = System.currentTimeMillis()) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong("fired_$scheduleId", now).apply()
    }

    fun lastFired(context: Context, scheduleId: Long): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("fired_$scheduleId", 0L)
}
