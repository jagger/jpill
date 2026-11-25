package com.jagger.timer.core.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import com.jagger.timer.MainActivity
import com.jagger.timer.core.data.local.entity.PillEntity
import com.jagger.timer.core.util.CooldownCalculator
import com.jagger.timer.core.util.CooldownStatus
import com.jagger.timer.core.util.MissedDoseDetector
import com.jagger.timer.core.util.RefillPlanner
import com.jagger.timer.core.util.RefillState
import com.jagger.timer.core.util.SupplyCalculator
import com.jagger.timer.core.util.SupplyStatus
import com.jagger.timer.core.util.RxFormLogic
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.jagger.timer.core.data.local.entity.DoseLogEntity
import com.jagger.timer.core.data.local.entity.DoseStatus
import com.jagger.timer.core.data.local.entity.doseAmount
import com.jagger.timer.core.data.repository.PillRepository
import com.jagger.timer.core.service.PillReminderService
import com.jagger.timer.core.util.AlarmScheduler
import com.jagger.timer.core.util.ScheduleManager
import com.jagger.timer.core.settings.AppSettings
import com.jagger.timer.core.util.hideNameOnLockScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_PILL_REMINDER = "com.jagger.timer.ACTION_PILL_REMINDER"
        const val ACTION_TAKEN = "com.jagger.timer.ACTION_TAKEN"
        const val ACTION_SKIPPED = "com.jagger.timer.ACTION_SKIPPED"
        const val ACTION_SNOOZE = "com.jagger.timer.ACTION_SNOOZE"
        const val ACTION_COOLDOWN_DONE = "com.jagger.timer.ACTION_COOLDOWN_DONE"
        const val ACTION_REFILL_DUE = "com.jagger.timer.ACTION_REFILL_DUE"
        const val ACTION_MISSED_CHECK = "com.jagger.timer.ACTION_MISSED_CHECK"
        const val ACTION_TIMER_DONE = "com.jagger.timer.ACTION_TIMER_DONE"
        const val TIMER_CHANNEL_ID = "timers"
        const val EXTRA_TIMER_ID = "TIMER_ID"

        const val COOLDOWN_CHANNEL_ID = "dose_available"
        const val REFILL_CHANNEL_ID = "refill_reminders"

        const val EXTRA_PILL_ID = "PILL_ID"
        const val EXTRA_SCHEDULE_ID = "SCHEDULE_ID"
        const val EXTRA_DUE_AT = "DUE_AT"
    }

    @Inject lateinit var scheduleManager: ScheduleManager
    @Inject lateinit var pillRepository: PillRepository
    @Inject lateinit var alarmScheduler: AlarmScheduler
    @Inject lateinit var refillState: RefillState
    @Inject lateinit var appSettings: AppSettings
    @Inject lateinit var timerManager: com.jagger.timer.core.util.TimerManager

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val pillId = intent.getLongExtra(EXTRA_PILL_ID, -1L)
        val scheduleId = intent.getLongExtra(EXTRA_SCHEDULE_ID, -1L)

        when (action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                val pendingResult = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        scheduleManager.refreshAlarms()
                        timerManager.restoreAfterBoot().forEach { postTimerDoneNotification(context, it) }
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            ACTION_PILL_REMINDER -> startService(context, intent)
            ACTION_TAKEN -> handleDoseAction(context, pillId, scheduleId, DoseStatus.TAKEN)
            ACTION_SKIPPED -> handleDoseAction(context, pillId, scheduleId, DoseStatus.SKIPPED)
            ACTION_SNOOZE -> handleSnoozeAction(context, pillId, scheduleId)
            ACTION_COOLDOWN_DONE -> handleCooldownDone(context, pillId)
            ACTION_REFILL_DUE -> handleRefillDue(context, pillId)
            ACTION_TIMER_DONE -> handleTimerDone(context, intent.getLongExtra(EXTRA_TIMER_ID, -1L))
            ACTION_MISSED_CHECK -> handleMissedCheck(pillId, scheduleId, intent.getLongExtra(EXTRA_DUE_AT, 0L))
        }
    }

    private fun handleDoseAction(context: Context, pillId: Long, scheduleId: Long, status: DoseStatus) {
        if (pillId == -1L) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                cancelNotification(context, pillId)
                val pill = pillRepository.getPillById(pillId)
                pillRepository.logDose(
                    DoseLogEntity(
                        pillId = pillId,
                        scheduleId = if (scheduleId == -1L) null else scheduleId,
                        timestamp = System.currentTimeMillis(),
                        status = status,
                        amount = pill?.doseAmount()
                    )
                )
                pill?.let { scheduleManager.refreshAlarmsForPill(it) }
                context.stopService(Intent(context, PillReminderService::class.java))
            } finally {
                pendingResult.finish()
            }
        }
    }

    /** The wait for an as-needed pill is over: tell the user, unless a newer dose moved the time. */
    private fun handleCooldownDone(context: Context, pillId: Long) {
        if (pillId == -1L) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val pill = pillRepository.getPillById(pillId) ?: return@launch
                if (!pill.asNeeded) return@launch
                val now = System.currentTimeMillis()
                val logs = pillRepository.getTakenLogsSince(pillId, now - CooldownCalculator.WINDOW_MILLIS).first()
                val cooldown = CooldownCalculator.forPill(pill, logs, now)
                if (cooldown.status == CooldownStatus.READY) {
                    postDoseAvailableNotification(context, pill)
                } else {
                    // Fired early or the schedule changed: wait for the real time.
                    scheduleManager.refreshCooldownAlarm(pill)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * A scheduled dose's grace window ended. If nothing was logged for the pill since it came due, record it as
     * MISSED, then re-plan so the next dose is scheduled even though this reminder was ignored.
     */
    private fun handleMissedCheck(pillId: Long, scheduleId: Long, dueAt: Long) {
        if (pillId == -1L || dueAt <= 0L) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val pill = pillRepository.getPillById(pillId)
                if (pill == null || pill.isArchived || pill.asNeeded) return@launch
                val schedule = pillRepository.getSchedulesForPill(pillId).first().find { it.id == scheduleId }
                    ?: return@launch
                val logs = pillRepository.getLogsForPill(pillId).first()
                if (MissedDoseDetector.shouldLogMissed(
                        logTimestamps = logs.map { it.timestamp },
                        dueAtMillis = dueAt,
                        reminderAdvanceMinutes = schedule.reminderAdvanceMinutes,
                        graceMillis = MissedDoseDetector.graceMillis(schedule),
                        nowMillis = System.currentTimeMillis()
                    )
                ) {
                    pillRepository.logDose(
                        DoseLogEntity(
                            pillId = pillId,
                            scheduleId = scheduleId,
                            timestamp = dueAt,
                            status = DoseStatus.MISSED,
                            amount = pill.doseAmount()
                        )
                    )
                }
                scheduleManager.refreshAlarmsForPill(pill)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /** Supply reached the refill threshold: remind once, then wait until supply is healthy again. */
    private fun handleRefillDue(context: Context, pillId: Long) {
        if (pillId == -1L) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val pill = pillRepository.getPillById(pillId) ?: return@launch
                val schedules = pillRepository.getSchedulesForPill(pillId).first()
                val status = SupplyCalculator.status(pill, schedules)
                val level = RefillPlanner.levelFor(status)
                if (pill.isPrescription && pill.supplyCount != null &&
                    status != SupplyStatus.OK && refillState.level(pillId) < level
                ) {
                    postRefillNotification(context, pill, SupplyCalculator.daysLeft(pill, schedules), status)
                    refillState.setLevel(pillId, level)
                } else {
                    // Fired early or things changed (e.g. refill entered): re-plan.
                    scheduleManager.refreshRefillAlarm(pillId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun postRefillNotification(context: Context, pill: PillEntity, daysLeft: Double?, status: SupplyStatus) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(REFILL_CHANNEL_ID, "Refill reminders", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Tells you when a prescription is running low" }
        )
        val id = safeNotificationId(pill.id) or 0x20000000 // distinct from reminder and cooldown ids
        val open = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val supply = RxFormLogic.formatCount(pill.supplyCount ?: 0.0)
        val (title, text) = if (status == SupplyStatus.EMPTY) {
            "You're out of ${pill.name}" to "Time to get a refill."
        } else {
            val days = daysLeft?.let { "about ${Math.ceil(it).toInt()} days" } ?: "a few days"
            "Refill ${pill.name} soon" to "$supply left, $days of supply. Contact your pharmacy."
        }
        val notification = NotificationCompat.Builder(context, REFILL_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .hideNameOnLockScreen(context, REFILL_CHANNEL_ID, appSettings.hideNamesOnLockScreen)
            .build()
        nm.notify(id, notification)
    }

    private fun handleTimerDone(context: Context, timerId: Long) {
        if (timerId == -1L) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                timerManager.onAlarm(timerId)?.let { postTimerDoneNotification(context, it) }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun postTimerDoneNotification(context: Context, timer: com.jagger.timer.core.data.local.entity.TimerEntity) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(TIMER_CHANNEL_ID, "Timers", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Tells you when a countdown timer finishes" }
        )
        val id = com.jagger.timer.core.util.AlarmNotificationIds.timer(timer.id)
        val open = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("route", "timers")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val name = timer.label.ifBlank { "Timer" }
        nm.notify(
            id,
            NotificationCompat.Builder(context, TIMER_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("$name is done")
                .setContentText("Your ${com.jagger.timer.core.util.TimerLogic.format(timer.durationMillis)} timer finished.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()
        )
    }

    private fun postDoseAvailableNotification(context: Context, pill: PillEntity) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                COOLDOWN_CHANNEL_ID,
                "Dose available",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Tells you when the wait between as-needed doses is over" }
        )
        val id = safeNotificationId(pill.id) or 0x40000000 // distinct from scheduled-reminder ids
        val open = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, COOLDOWN_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("${pill.name} is available again")
            .setContentText("The wait is over. Check your label before taking another dose.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .hideNameOnLockScreen(context, COOLDOWN_CHANNEL_ID, appSettings.hideNamesOnLockScreen)
            .build()
        nm.notify(id, notification)
    }

    private fun handleSnoozeAction(context: Context, pillId: Long, scheduleId: Long) {
        if (pillId == -1L) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                cancelNotification(context, pillId)
                val pill = pillRepository.getPillById(pillId)
                val schedules = pillRepository.getSchedulesForPill(pillId).first()
                val schedule = schedules.find { it.id == scheduleId }
                if (pill != null && schedule != null) {
                    alarmScheduler.scheduleAlarm(pill, schedule, LocalDateTime.now().plusMinutes(10))
                }
                // If schedule not found, silently cancel — alarm will re-fire at its next scheduled time
                context.stopService(Intent(context, PillReminderService::class.java))
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun cancelNotification(context: Context, pillId: Long) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(safeNotificationId(pillId))
    }

    private fun startService(context: Context, intent: Intent) {
        val serviceIntent = Intent(context, PillReminderService::class.java).apply {
            putExtras(intent)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}

fun safeNotificationId(id: Long): Int = (id and 0x7FFFFFFF).toInt()
