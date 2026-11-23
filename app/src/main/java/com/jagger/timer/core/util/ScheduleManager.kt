package com.jagger.timer.core.util

import com.jagger.timer.core.data.local.entity.PillEntity
import com.jagger.timer.core.data.local.entity.ScheduleEntity
import com.jagger.timer.core.data.model.TimelineEntry
import com.jagger.timer.core.data.repository.PillRepository
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleManager @Inject constructor(
    private val pillRepository: PillRepository,
    private val alarmScheduler: AlarmScheduler,
    private val refillState: RefillState
) {

    suspend fun getUpcomingDoses(hours: Long = 24): List<TimelineEntry> {
        val entries = mutableListOf<TimelineEntry>()
        val pills = pillRepository.getAllPills().first()
        val now = LocalDateTime.now()
        val end = now.plusHours(hours)

        for (pill in pills) {
            val schedules = pillRepository.getSchedulesForPill(pill.id).first()
            val logs = pillRepository.getLogsForPill(pill.id).first()
            val lastDoseTimestamp = logs.firstOrNull()?.timestamp

            for (schedule in schedules) {
                val nextOccurrence = ScheduleCalculator.calculateNextOccurrence(
                    schedule,
                    lastDoseTimestamp,
                    now
                )
                
                nextOccurrence?.let {
                    if (it.isBefore(end)) {
                        entries.add(TimelineEntry(pill, schedule, it))
                    }
                }
            }
        }
        return entries.sortedBy { it.dueTime }
    }

    suspend fun refreshAlarms() {
        val pills = pillRepository.getAllPills().first()
        for (pill in pills) {
            refreshAlarmsForPill(pill)
        }
    }

    /** (Re)schedules the "available again" alarm for an as-needed pill, or cancels it if already ready. */
    suspend fun refreshCooldownAlarm(pill: PillEntity) {
        if (!pill.asNeeded) {
            alarmScheduler.cancelCooldownAlarm(pill.id)
            return
        }
        val now = System.currentTimeMillis()
        val logs = pillRepository.getTakenLogsSince(pill.id, now - CooldownCalculator.WINDOW_MILLIS).first()
        val cooldown = CooldownCalculator.forPill(pill, logs, now)
        val next = cooldown.nextDoseAtMillis
        if (cooldown.status == CooldownStatus.READY || next == null) {
            alarmScheduler.cancelCooldownAlarm(pill.id)
        } else {
            alarmScheduler.scheduleCooldownAlarm(pill.id, next)
        }
    }

    /**
     * (Re)plans the prescription refill reminder. Re-reads the pill because callers often hold a copy from
     * before the latest dose decremented supply.
     */
    suspend fun refreshRefillAlarm(pillId: Long) {
        val pill = pillRepository.getPillById(pillId)
        if (pill == null) {
            alarmScheduler.cancelRefillAlarm(pillId)
            refillState.clear(pillId)
            return
        }
        val schedules = pillRepository.getSchedulesForPill(pillId).first()
        if (RefillPlanner.shouldClearNotified(pill, schedules)) refillState.clear(pillId)
        val now = System.currentTimeMillis()
        when (val action = RefillPlanner.plan(pill, schedules, refillState.level(pillId), now)) {
            RefillAction.Cancel -> alarmScheduler.cancelRefillAlarm(pillId)
            RefillAction.FireNow -> alarmScheduler.scheduleRefillAlarm(pillId, now + 1_000)
            is RefillAction.ScheduleAt -> alarmScheduler.scheduleRefillAlarm(pillId, action.atMillis)
        }
    }

    suspend fun refreshAlarmsForPill(pill: PillEntity) {
        refreshRefillAlarm(pill.id)
        if (pill.asNeeded) refreshCooldownAlarm(pill)
        val schedules = pillRepository.getSchedulesForPill(pill.id).first()
        val logs = pillRepository.getLogsForPill(pill.id).first()
        val lastDoseTimestamp = logs.firstOrNull()?.timestamp

        for (schedule in schedules) {
            val nextOccurrence = ScheduleCalculator.calculateNextOccurrence(
                schedule,
                lastDoseTimestamp
            )
            
            nextOccurrence?.let {
                // If the user wants a reminder 30 min before, we should actually schedule that
                val reminderTime = it.minusMinutes(schedule.reminderAdvanceMinutes.toLong())
                val finalTrigger = if (reminderTime.isAfter(LocalDateTime.now())) reminderTime else it
                
                alarmScheduler.scheduleAlarm(pill, schedule, finalTrigger)
            }
        }
    }

    /**
     * Archives a pill: hides it and stops everything that would nag you (alarms, cooldown/refill alarms, posted
     * notifications). Schedules and dose history are kept so it can be restored later.
     */
    suspend fun archivePill(pill: PillEntity) {
        cancelAlarmsForPill(pill)
        alarmScheduler.cancelPillNotifications(pill.id)
        pillRepository.setArchived(pill.id, true, System.currentTimeMillis())
    }

    /** Restores an archived pill and re-arms its reminders. */
    suspend fun restorePill(pill: PillEntity) {
        pillRepository.setArchived(pill.id, false, null)
        pillRepository.getPillById(pill.id)?.let { refreshAlarmsForPill(it) }
    }

    /**
     * Deletes a pill and everything hanging off it: alarms, posted notifications and the refill flag.
     * Its schedules and dose history are removed by the database (foreign-key cascade).
     */
    suspend fun deletePill(pill: PillEntity) {
        cancelAlarmsForPill(pill)
        alarmScheduler.cancelPillNotifications(pill.id)
        refillState.clear(pill.id)
        pillRepository.deletePill(pill)
    }

    suspend fun cancelAlarmsForPill(pill: PillEntity) {
        alarmScheduler.cancelCooldownAlarm(pill.id)
        alarmScheduler.cancelRefillAlarm(pill.id)
        val schedules = pillRepository.getSchedulesForPill(pill.id).first()
        for (schedule in schedules) {
            alarmScheduler.cancelAlarm(schedule)
        }
    }
}
