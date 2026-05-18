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
import androidx.core.app.NotificationCompat
import com.jagger.timer.MainActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PillReminderService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "pill_reminders"
        private const val NOTIFICATION_ID = 1001
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val pillName = intent?.getStringExtra("PILL_NAME") ?: "Medication"
        showNotification(pillName)
        
        return START_STICKY
    }

    private fun showNotification(pillName: String) {
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

        val fullScreenIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 0, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Actions
        val takenIntent = Intent(this, MainActivity::class.java).apply {
            action = "ACTION_TAKEN"
            // Pass pillId etc.
        }
        val takenPendingIntent = PendingIntent.getActivity(
            this, 1, takenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm) // Use a better icon later
            .setContentTitle("Time for your medication")
            .setContentText("It's time to take $pillName")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(0, "Taken", takenPendingIntent)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }
}
