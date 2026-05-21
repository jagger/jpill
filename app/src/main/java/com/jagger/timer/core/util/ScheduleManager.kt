package com.jagger.timer.core.util

import com.jagger.timer.core.data.local.entity.DoseLogEntity
import com.jagger.timer.core.data.local.entity.DoseStatus
import com.jagger.timer.core.data.local.entity.PillEntity
import com.jagger.timer.core.data.local.entity.ScheduleEntity
import com.jagger.timer.core.data.model.TimelineEntry
import com.jagger.timer.core.data.repository.PillRepository
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
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

    suspend fun checkMissedDoses() {
        val pills = pillRepository.getAllPills().first()
        val now = LocalDateTime.now()

        for (pill in pills) {
            val schedules = pillRepository.getSchedulesForPill(pill.id).first()
            val logs = pillRepository.getLogsForPill(pill.id).first()
            val lastDoseTimestamp = logs.firstOrNull()?.timestamp

            for (schedule in schedules) {
                if (schedule.timeMinutes != null) {
                    val lastDoseTime = lastDoseTimestamp?.let { 
                        LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()) 
                    }
                    
                    val referenceTime = lastDoseTime?.plusMinutes(1) ?: now.minusDays(1)
                    val expectedDose = ScheduleCalculator.calculateNextOccurrence(schedule, lastDoseTimestamp, referenceTime)
                    
                    if (expectedDose != null && expectedDose.isBefore(now.minusMinutes(15))) {
                        pillRepository.logDose(
                            DoseLogEntity(
                                pillId = pill.id,
                                scheduleId = schedule.id,
                                timestamp = expectedDose.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                                status = DoseStatus.MISSED
                            )
                        )
                    }
                }
            }
        }
    }

    suspend fun refreshAlarms() {
        checkMissedDoses()
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
