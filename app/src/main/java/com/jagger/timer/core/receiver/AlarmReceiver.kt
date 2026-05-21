package com.jagger.timer.core.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.jagger.timer.core.data.local.entity.DoseLogEntity
import com.jagger.timer.core.data.local.entity.DoseStatus
import com.jagger.timer.core.data.repository.PillRepository
import com.jagger.timer.core.service.PillReminderService
import com.jagger.timer.core.util.AlarmScheduler
import com.jagger.timer.core.util.ScheduleManager
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

        const val EXTRA_PILL_ID = "PILL_ID"
        const val EXTRA_SCHEDULE_ID = "SCHEDULE_ID"
    }

    @Inject
    lateinit var scheduleManager: ScheduleManager

    @Inject
    lateinit var pillRepository: PillRepository

    @Inject
    lateinit var alarmScheduler: AlarmScheduler

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val pillId = intent.getLongExtra(EXTRA_PILL_ID, -1L)
        val scheduleId = intent.getLongExtra(EXTRA_SCHEDULE_ID, -1L)
        
        when (action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                scope.launch {
                    scheduleManager.refreshAlarms()
                }
            }
            ACTION_PILL_REMINDER -> {
                startService(context, intent)
            }
            ACTION_TAKEN -> {
                handleDoseAction(context, pillId, scheduleId, DoseStatus.TAKEN)
            }
            ACTION_SKIPPED -> {
                handleDoseAction(context, pillId, scheduleId, DoseStatus.SKIPPED)
            }
            ACTION_SNOOZE -> {
                handleSnoozeAction(context, pillId, scheduleId)
            }
        }
    }

    private fun handleDoseAction(context: Context, pillId: Long, scheduleId: Long, status: DoseStatus) {
        if (pillId == -1L) return
        scope.launch {
            pillRepository.logDose(
                DoseLogEntity(
                    pillId = pillId,
                    scheduleId = if (scheduleId == -1L) null else scheduleId,
                    timestamp = System.currentTimeMillis(),
                    status = status
                )
            )
            val pill = pillRepository.getPillById(pillId)
            pill?.let { scheduleManager.refreshAlarmsForPill(it) }
            stopService(context)
        }
    }

    private fun handleSnoozeAction(context: Context, pillId: Long, scheduleId: Long) {
        if (pillId == -1L) return
        scope.launch {
            val pill = pillRepository.getPillById(pillId)
            val schedules = pillRepository.getSchedulesForPill(pillId).first()
            val schedule = schedules.find { it.id == scheduleId } ?: schedules.firstOrNull()
            
            if (pill != null && schedule != null) {
                // Reschedule for 10 minutes from now
                alarmScheduler.scheduleAlarm(pill, schedule, LocalDateTime.now().plusMinutes(10))
            }
            stopService(context)
        }
    }

    private fun stopService(context: Context) {
        context.stopService(Intent(context, PillReminderService::class.java))
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
