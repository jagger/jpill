package com.jagger.timer.core.util

import com.jagger.timer.core.data.local.entity.PillEntity
import com.jagger.timer.core.data.local.entity.ScheduleEntity
import com.jagger.timer.core.data.repository.PillRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleManager @Inject constructor(
    private val pillRepository: PillRepository,
    private val alarmScheduler: AlarmScheduler
) {

    suspend fun refreshAlarms() {
        val pills = pillRepository.getAllPills().first()
        for (pill in pills) {
            refreshAlarmsForPill(pill)
        }
    }

    suspend fun refreshAlarmsForPill(pill: PillEntity) {
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
        val schedules = pillRepository.getSchedulesForPill(pill.id).first()
        for (schedule in schedules) {
            alarmScheduler.cancelAlarm(schedule)
        }
    }
}
