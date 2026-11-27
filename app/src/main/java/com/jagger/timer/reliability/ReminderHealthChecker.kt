// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.reliability

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Reads the phone's reminder-related settings and builds the intents that open the right place to fix each one. */
@Singleton
class ReminderHealthChecker @Inject constructor(@ApplicationContext private val context: Context) {

    fun check(): ReminderHealth {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // Notifications count as off if they are off for the app or the reminder channel itself was switched off.
        val channelBlocked = listOf(
            com.jagger.timer.core.receiver.ReminderNotifier.CHANNEL_ID,
            com.jagger.timer.core.receiver.ReminderNotifier.GENERAL_CHANNEL_ID
        ).any { id -> nm.getNotificationChannel(id)?.importance == NotificationManager.IMPORTANCE_NONE }
        return ReminderHealth(
            notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled() && !channelBlocked,
            batteryUnrestricted = power.isIgnoringBatteryOptimizations(context.packageName),
            fullScreenAllowed = nm.canUseFullScreenIntent(),
            exactAlarmsAllowed = alarms.canScheduleExactAlarms()
        )
    }

    /** The settings screen (or system prompt) that fixes [issue]. */
    fun fixIntent(issue: ReminderIssue): Intent {
        val pkg = Uri.parse("package:${context.packageName}")
        val intent = when (issue) {
            ReminderIssue.NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            // The system's own "Allow jPill to always run in the background?" prompt: one tap on Allow.
            ReminderIssue.BATTERY -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg)
            ReminderIssue.FULL_SCREEN -> Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg)
            ReminderIssue.EXACT_ALARMS -> Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg)
        }
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** jPill's own page in Android Settings (battery, notifications and permissions are one tap away from there). */
    fun appSettingsIntent(): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Samsung's "Sleeping apps" lists can stop alarms and have no API, so we can only point the user at them. */
    val isSamsung: Boolean get() = Build.MANUFACTURER.equals("samsung", ignoreCase = true)
}
