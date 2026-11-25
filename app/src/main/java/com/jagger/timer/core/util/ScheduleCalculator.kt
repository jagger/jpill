// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.core.util

import com.jagger.timer.core.data.local.entity.RecurrencePattern
import com.jagger.timer.core.data.local.entity.ScheduleEntity
import com.jagger.timer.core.data.local.entity.ScheduleType
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

object ScheduleCalculator {

    fun calculateNextOccurrence(
        schedule: ScheduleEntity,
        lastDoseTimestamp: Long?,
        now: LocalDateTime = LocalDateTime.now()
    ): LocalDateTime? {
        return when (schedule.type) {
            ScheduleType.FIXED_TIME -> calculateFixedTimeNext(schedule, now)
            ScheduleType.TIME_SLOT -> calculateTimeSlotNext(schedule, now)
            ScheduleType.INTERVAL -> calculateIntervalNext(schedule, lastDoseTimestamp, now)
        }
    }

    private fun calculateFixedTimeNext(
        schedule: ScheduleEntity,
        now: LocalDateTime
    ): LocalDateTime {
        val time = LocalTime.ofSecondOfDay((schedule.timeMinutes ?: 0).toLong() * 60)
        
        return when (schedule.pattern) {
            RecurrencePattern.DAILY -> {
                val todayTime = now.with(time)
                if (todayTime.isAfter(now)) todayTime else todayTime.plusDays(1)
            }
            RecurrencePattern.WEEKLY -> {
                val allowedDays = schedule.daysOfWeek?.split(",")?.map { it.toInt() } ?: emptyList()
                var next = now.with(time)
                while (!allowedDays.contains(next.dayOfWeek.value) || !next.isAfter(now)) {
                    next = next.plusDays(1)
                }
                next
            }
            RecurrencePattern.MONTHLY -> {
                val dayOfMonth = schedule.dayOfMonth ?: 1
                var next = now.withDayOfMonth(dayOfMonth).with(time)
                if (!next.isAfter(now)) {
                    next = next.plusMonths(1)
                }
                next
            }
            RecurrencePattern.NTH_WEEKDAY -> {
                val nth = schedule.nthOccurrence ?: 1
                val dayOfWeek = DayOfWeek.of(schedule.dayOfWeek ?: 1)
                var next = now.with(TemporalAdjusters.dayOfWeekInMonth(nth, dayOfWeek)).with(time)
                if (!next.isAfter(now)) {
                    next = now.plusMonths(1).with(TemporalAdjusters.dayOfWeekInMonth(nth, dayOfWeek)).with(time)
                }
                next
            }
        }
    }

    private fun calculateTimeSlotNext(
        schedule: ScheduleEntity,
        now: LocalDateTime
    ): LocalDateTime {
        // Default time slot mappings (can be made configurable later)
        val minutes = when (schedule.timeSlotName?.lowercase()) {
            "morning" -> 8 * 60      // 8:00 AM
            "afternoon" -> 13 * 60   // 1:00 PM
            "evening" -> 18 * 60     // 6:00 PM
            "night" -> 22 * 60       // 10:00 PM
            else -> 8 * 60
        }
        val time = LocalTime.ofSecondOfDay(minutes.toLong() * 60)
        val todayTime = now.with(time)
        return if (todayTime.isAfter(now)) todayTime else todayTime.plusDays(1)
    }

    private fun calculateIntervalNext(
        schedule: ScheduleEntity,
        lastDoseTimestamp: Long?,
        now: LocalDateTime
    ): LocalDateTime {
        val intervalMinutes = schedule.intervalMinutes ?: (8 * 60)
        if (lastDoseTimestamp == null) {
            return now // Take immediately if never taken
        }
        val lastDose = LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(lastDoseTimestamp),
            ZoneId.systemDefault()
        )
        val next = lastDose.plusMinutes(intervalMinutes.toLong())
        return if (next.isAfter(now)) next else now
    }
}
