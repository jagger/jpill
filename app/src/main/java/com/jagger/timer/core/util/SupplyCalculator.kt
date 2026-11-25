// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.core.util

import com.jagger.timer.core.data.local.entity.PillEntity
import com.jagger.timer.core.data.local.entity.RecurrencePattern
import com.jagger.timer.core.data.local.entity.ScheduleEntity
import com.jagger.timer.core.data.local.entity.ScheduleType

enum class SupplyStatus { OK, LOW, EMPTY }

/** Pure supply / refill math for prescriptions. */
object SupplyCalculator {

    private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

    /** Supply after one taken dose of [tabletsPerDose], never below zero. */
    fun afterDose(supply: Double, tabletsPerDose: Double): Double =
        (supply - tabletsPerDose).coerceAtLeast(0.0)

    /** Average doses per day for one schedule. */
    fun dosesPerDay(schedule: ScheduleEntity): Double = when (schedule.type) {
        ScheduleType.INTERVAL -> schedule.intervalMinutes?.takeIf { it > 0 }?.let { 1440.0 / it } ?: 0.0
        ScheduleType.FIXED_TIME, ScheduleType.TIME_SLOT -> when (schedule.pattern) {
            RecurrencePattern.DAILY -> 1.0
            RecurrencePattern.WEEKLY -> {
                val days = schedule.daysOfWeek?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.toSet()?.size ?: 7
                days / 7.0
            }
            RecurrencePattern.MONTHLY, RecurrencePattern.NTH_WEEKDAY -> 12.0 / 365.0
        }
    }

    /** Average doses per day across all of a pill's schedules (0 if none). */
    fun dosesPerDay(schedules: List<ScheduleEntity>): Double = schedules.sumOf { dosesPerDay(it) }

    /**
     * Days of supply left, or null when it can't be estimated: supply not tracked,
     * an as-needed pill (no regular use), or no schedule.
     */
    fun daysLeft(pill: PillEntity, schedules: List<ScheduleEntity>): Double? {
        val supply = pill.supplyCount ?: return null
        if (pill.asNeeded) return null
        val perDay = dosesPerDay(schedules) * pill.tabletsPerDose
        if (perDay <= 0.0) return null
        return supply / perDay
    }

    fun status(pill: PillEntity, schedules: List<ScheduleEntity>): SupplyStatus {
        val supply = pill.supplyCount ?: return SupplyStatus.OK
        if (supply <= 0.0) return SupplyStatus.EMPTY
        val days = daysLeft(pill, schedules) ?: return SupplyStatus.OK
        return if (days <= pill.refillThresholdDays) SupplyStatus.LOW else SupplyStatus.OK
    }

    /**
     * Epoch millis when the refill reminder should fire (supply reaches the threshold), or null if
     * there is nothing to schedule. Returns [nowMillis] when already at or below the threshold.
     */
    fun refillAlertAtMillis(pill: PillEntity, schedules: List<ScheduleEntity>, nowMillis: Long): Long? {
        val days = daysLeft(pill, schedules) ?: return null
        val daysUntilAlert = days - pill.refillThresholdDays
        return if (daysUntilAlert <= 0.0) nowMillis else nowMillis + (daysUntilAlert * MILLIS_PER_DAY).toLong()
    }
}
