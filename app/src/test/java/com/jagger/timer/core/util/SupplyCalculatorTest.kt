// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.core.util

import com.jagger.timer.core.data.local.entity.PillEntity
import com.jagger.timer.core.data.local.entity.RecurrencePattern
import com.jagger.timer.core.data.local.entity.ScheduleEntity
import com.jagger.timer.core.data.local.entity.ScheduleType
import com.jagger.timer.core.data.local.entity.doseAmount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SupplyCalculatorTest {

    private fun pill(
        supply: Double? = 30.0,
        tablets: Double = 1.0,
        threshold: Int = 7,
        asNeeded: Boolean = false,
        dosage: String = "500"
    ) = PillEntity(
        id = 1, name = "X", dosage = dosage, unit = "mg", color = 0,
        asNeeded = asNeeded, tabletsPerDose = tablets, supplyCount = supply, refillThresholdDays = threshold
    )

    private fun daily() = ScheduleEntity(
        pillId = 1, type = ScheduleType.FIXED_TIME, pattern = RecurrencePattern.DAILY, timeMinutes = 480
    )

    private fun everyHours(h: Int) = ScheduleEntity(
        pillId = 1, type = ScheduleType.INTERVAL, pattern = RecurrencePattern.DAILY, intervalMinutes = h * 60
    )

    @Test fun afterDose_subtractsTablets() = assertEquals(28.0, SupplyCalculator.afterDose(30.0, 2.0), 0.0)
    @Test fun afterDose_halfTablet() = assertEquals(29.5, SupplyCalculator.afterDose(30.0, 0.5), 0.0)
    @Test fun afterDose_floorsAtZero() = assertEquals(0.0, SupplyCalculator.afterDose(1.0, 2.0), 0.0)

    @Test fun dosesPerDay_dailyAndInterval() {
        assertEquals(1.0, SupplyCalculator.dosesPerDay(daily()), 0.0)
        assertEquals(3.0, SupplyCalculator.dosesPerDay(everyHours(8)), 0.0)
        assertEquals(2.0, SupplyCalculator.dosesPerDay(listOf(daily(), daily())), 0.0)
    }

    @Test fun dosesPerDay_weeklyCountsSelectedDays() {
        val mwf = daily().copy(pattern = RecurrencePattern.WEEKLY, daysOfWeek = "1,3,5")
        assertEquals(3.0 / 7.0, SupplyCalculator.dosesPerDay(mwf), 1e-9)
    }

    @Test fun daysLeft_usesTabletsPerDose() {
        // 30 tablets, 2 per dose, once daily -> 15 days
        assertEquals(15.0, SupplyCalculator.daysLeft(pill(tablets = 2.0), listOf(daily()))!!, 1e-9)
        // 30 tablets, twice daily -> 15 days
        assertEquals(15.0, SupplyCalculator.daysLeft(pill(), listOf(daily(), daily()))!!, 1e-9)
    }

    @Test fun daysLeft_nullWhenNotEstimable() {
        assertNull(SupplyCalculator.daysLeft(pill(supply = null), listOf(daily())))
        assertNull(SupplyCalculator.daysLeft(pill(asNeeded = true), emptyList()))
        assertNull(SupplyCalculator.daysLeft(pill(), emptyList()))
    }

    @Test fun status_okLowEmpty() {
        assertEquals(SupplyStatus.OK, SupplyCalculator.status(pill(supply = 30.0), listOf(daily())))
        assertEquals(SupplyStatus.LOW, SupplyCalculator.status(pill(supply = 7.0), listOf(daily())))
        assertEquals(SupplyStatus.LOW, SupplyCalculator.status(pill(supply = 3.0), listOf(daily())))
        assertEquals(SupplyStatus.EMPTY, SupplyCalculator.status(pill(supply = 0.0), listOf(daily())))
        assertEquals(SupplyStatus.OK, SupplyCalculator.status(pill(supply = null), listOf(daily())))
    }

    @Test fun status_asNeededWithSupplyIsOkUntilEmpty() {
        assertEquals(SupplyStatus.OK, SupplyCalculator.status(pill(supply = 2.0, asNeeded = true), emptyList()))
        assertEquals(SupplyStatus.EMPTY, SupplyCalculator.status(pill(supply = 0.0, asNeeded = true), emptyList()))
    }

    @Test fun refillAlert_firesWhenThresholdReached() {
        val now = 1_000_000_000_000L
        val day = 86_400_000L
        // 30 days left, threshold 7 -> alert in 23 days
        assertEquals(now + 23 * day, SupplyCalculator.refillAlertAtMillis(pill(supply = 30.0), listOf(daily()), now))
        // already at/below threshold -> now
        assertEquals(now, SupplyCalculator.refillAlertAtMillis(pill(supply = 5.0), listOf(daily()), now))
        assertNull(SupplyCalculator.refillAlertAtMillis(pill(supply = null), listOf(daily()), now))
    }

    @Test fun doseAmount_isStrengthTimesTablets() {
        assertEquals(1000.0, pill(tablets = 2.0, dosage = "500").doseAmount()!!, 0.0)
        assertEquals(200.0, pill(tablets = 1.0, dosage = "200").doseAmount()!!, 0.0)
        assertNull(pill(dosage = "1/2").doseAmount())
    }
}
