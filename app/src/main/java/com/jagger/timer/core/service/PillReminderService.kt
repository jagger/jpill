// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.jagger.timer.MainActivity
import com.jagger.timer.core.receiver.AlarmReceiver
import com.jagger.timer.core.receiver.safeNotificationId
import com.jagger.timer.core.settings.AppSettings
import com.jagger.timer.core.util.hideNameOnLockScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class PillReminderService : Service() {

    @Inject lateinit var appSettings: AppSettings

    override fun onBind(intent: Intent?): IBinder? = null

    private val timeoutHandler = Handler(Looper.getMainLooper())
    private val timeoutRunnable = Runnable { stopSelf() }

    companion object {
        private const val CHANNEL_ID = "pill_reminders"
        private const val TIMEOUT_MS = 15 * 60 * 1000L // 15 minutes
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val pillId = intent?.getLongExtra(AlarmReceiver.EXTRA_PILL_ID, -1L) ?: -1L
        val scheduleId = intent?.getLongExtra(AlarmReceiver.EXTRA_SCHEDULE_ID, -1L) ?: -1L
        val pillName = intent?.getStringExtra("PILL_NAME") ?: "Medication"
        
        if (pillId != -1L) {
            showNotification(pillId, scheduleId, pillName)
            
            // Reset timeout
            timeoutHandler.removeCallbacks(timeoutRunnable)
            timeoutHandler.postDelayed(timeoutRunnable, TIMEOUT_MS)
        } else {
            stopSelf()
        }
        
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        timeoutHandler.removeCallbacks(timeoutRunnable)
        super.onDestroy()
    }

    private fun showNotification(pillId: Long, scheduleId: Long, pillName: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Pill Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Urgent alerts for your medication"
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Action: Mark Taken
        val takenIntent = Intent(this, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_TAKEN
            putExtra(AlarmReceiver.EXTRA_PILL_ID, pillId)
            putExtra(AlarmReceiver.EXTRA_SCHEDULE_ID, scheduleId)
        }
        val notifId = safeNotificationId(pillId)
        val takenPendingIntent = PendingIntent.getBroadcast(
            this, notifId * 10 + 1, takenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Skip
        val skipIntent = Intent(this, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_SKIPPED
            putExtra(AlarmReceiver.EXTRA_PILL_ID, pillId)
            putExtra(AlarmReceiver.EXTRA_SCHEDULE_ID, scheduleId)
        }
        val skipPendingIntent = PendingIntent.getBroadcast(
            this, notifId * 10 + 2, skipIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Snooze
        val snoozeIntent = Intent(this, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_SNOOZE
            putExtra(AlarmReceiver.EXTRA_PILL_ID, pillId)
            putExtra(AlarmReceiver.EXTRA_SCHEDULE_ID, scheduleId)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            this, notifId * 10 + 3, snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Full Screen Intent (Opens App)
        val fullScreenIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, notifId, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Time for your $pillName")
            .setContentText("Please take your scheduled dose.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(0, "Taken", takenPendingIntent)
            .addAction(0, "Skip", skipPendingIntent)
            .addAction(0, "Snooze (10m)", snoozePendingIntent)
            .hideNameOnLockScreen(
                this, CHANNEL_ID, appSettings.hideNamesOnLockScreen,
                listOf("Taken" to takenPendingIntent, "Skip" to skipPendingIntent, "Snooze (10m)" to snoozePendingIntent)
            )
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                notifId,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else {
                    0
                }
            )
        } else {
            startForeground(notifId, notification)
        }
    }
}
