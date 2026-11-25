// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.core.util

import com.jagger.timer.core.data.local.entity.PillEntity
import com.jagger.timer.core.data.local.entity.RecurrencePattern
import com.jagger.timer.core.data.local.entity.ScheduleEntity
import com.jagger.timer.core.data.local.entity.ScheduleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RxFormLogicTest {

    @Test fun parseStrength_splitsNumberAndUnit() {
        assertEquals("10" to "mg", RxFormLogic.parseStrength("10 mg"))
        assertEquals("0.125" to "mg", RxFormLogic.parseStrength("0.125 mg"))
        assertEquals("20" to "mEq", RxFormLogic.parseStrength("20 mEq"))
        assertEquals("88" to "mcg", RxFormLogic.parseStrength("88 mcg"))
    }

    @Test fun parseStrength_combinationStaysWhole() {
        assertEquals("5 mg / 325 mg" to null, RxFormLogic.parseStrength("5 mg / 325 mg"))
    }

    @Test fun addInstruction_appendsOnceCaseInsensitive() {
        assertEquals("With food", RxFormLogic.addInstruction(null, "With food"))
        assertEquals("Morning. With food", RxFormLogic.addInstruction("Morning", "With food"))
        assertEquals("take with food daily", RxFormLogic.addInstruction("take with food daily", "With food"))
    }

    @Test fun parsers_fallBackSafely() {
        assertEquals(1.0, RxFormLogic.parseTablets(""), 0.0)
        assertEquals(1.0, RxFormLogic.parseTablets("0"), 0.0)
        assertEquals(0.5, RxFormLogic.parseTablets("0,5"), 0.0)
        assertNull(RxFormLogic.parseSupply(""))
        assertEquals(0.0, RxFormLogic.parseSupply("0")!!, 0.0)
        assertEquals(7, RxFormLogic.parseRefillDays("abc"))
        assertEquals(10, RxFormLogic.parseRefillDays("10"))
        assertEquals(7, RxFormLogic.parseRefillDays("999"))
    }

    @Test fun formatting() {
        assertEquals("28", RxFormLogic.formatCount(28.0))
        assertEquals("27.5", RxFormLogic.formatCount(27.5))
        val p = PillEntity(name = "M", dosage = "500", unit = "mg", color = 0, tabletsPerDose = 2.0)
        assertEquals("2 × 500 mg", RxFormLogic.doseLabel(p))
        assertEquals("500 mg", RxFormLogic.doseLabel(p.copy(tabletsPerDose = 1.0)))
        assertEquals("Dose not set", RxFormLogic.doseLabel(p.copy(dosage = "")))
    }

    // --- RefillPlanner ---
    private val now = 1_000_000_000_000L
    private fun rx(supply: Double?, rxFlag: Boolean = true) =
        PillEntity(id = 1, name = "X", dosage = "10", unit = "mg", color = 0, isPrescription = rxFlag, supplyCount = supply, refillThresholdDays = 7)
    private val daily = listOf(ScheduleEntity(pillId = 1, type = ScheduleType.FIXED_TIME, pattern = RecurrencePattern.DAILY, timeMinutes = 480))

    @Test fun plan_cancelsWhenNotTracking() {
        assertEquals(RefillAction.Cancel, RefillPlanner.plan(rx(null), daily, 0, now))
        assertEquals(RefillAction.Cancel, RefillPlanner.plan(rx(30.0, rxFlag = false), daily, 0, now))
    }

    @Test fun plan_schedulesFutureAlertWhenSupplyHealthy() {
        val action = RefillPlanner.plan(rx(30.0), daily, 0, now) as RefillAction.ScheduleAt
        assertEquals(now + 23 * 86_400_000L, action.atMillis)
    }

    @Test fun plan_firesOncePerLevel_lowThenOut() {
        assertEquals(RefillAction.FireNow, RefillPlanner.plan(rx(5.0), daily, RefillPlanner.LEVEL_NONE, now))
        assertEquals(RefillAction.Cancel, RefillPlanner.plan(rx(5.0), daily, RefillPlanner.LEVEL_LOW, now))    // already told "low"
        assertEquals(RefillAction.FireNow, RefillPlanner.plan(rx(0.0), daily, RefillPlanner.LEVEL_LOW, now))   // escalates to "out"
        assertEquals(RefillAction.Cancel, RefillPlanner.plan(rx(0.0), daily, RefillPlanner.LEVEL_EMPTY, now)) // already told "out"
        assertEquals(RefillAction.FireNow, RefillPlanner.plan(rx(0.0), daily, RefillPlanner.LEVEL_NONE, now))  // straight to out
    }

    @Test fun levelFor_matchesStatus() {
        assertEquals(RefillPlanner.LEVEL_NONE, RefillPlanner.levelFor(SupplyStatus.OK))
        assertEquals(RefillPlanner.LEVEL_LOW, RefillPlanner.levelFor(SupplyStatus.LOW))
        assertEquals(RefillPlanner.LEVEL_EMPTY, RefillPlanner.levelFor(SupplyStatus.EMPTY))
    }

    @Test fun clearNotified_onceSupplyIsHealthyAgain() {
        assertTrue(RefillPlanner.shouldClearNotified(rx(30.0), daily))
        assertFalse(RefillPlanner.shouldClearNotified(rx(5.0), daily))
        assertTrue(RefillPlanner.shouldClearNotified(rx(null), daily))
    }
}
