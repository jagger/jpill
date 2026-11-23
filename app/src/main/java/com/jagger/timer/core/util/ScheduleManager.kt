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
    private val alarmScheduler: AlarmScheduler
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

    suspend fun refreshAlarmsForPill(pill: PillEntity) {
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

    suspend fun cancelAlarmsForPill(pill: PillEntity) {
        alarmScheduler.cancelCooldownAlarm(pill.id)
        val schedules = pillRepository.getSchedulesForPill(pill.id).first()
        for (schedule in schedules) {
            alarmScheduler.cancelAlarm(schedule)
        }
    }
}
