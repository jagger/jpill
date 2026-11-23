package com.jagger.timer.core.util

import com.jagger.timer.core.data.catalog.OtcCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CooldownCalculatorTest {

    private val hour = 60L * 60 * 1000
    private val now = 1_000_000_000_000L

    private fun calc(
        doses: List<TakenDose>,
        interval: Int? = 240,
        max: Int? = 1200,
        isDoses: Boolean = false,
        next: Double = 200.0
    ) = CooldownCalculator.calculate(doses, now, interval, max, isDoses, next)

    @Test fun noDoses_isReady() {
        val s = calc(emptyList())
        assertEquals(CooldownStatus.READY, s.status)
        assertNull(s.nextDoseAtMillis)
        assertEquals(0, s.dosesIn24h)
    }

    @Test fun recentDose_isWaitingUntilIntervalEnds() {
        val s = calc(listOf(TakenDose(now - 1 * hour, 200.0)))
        assertEquals(CooldownStatus.WAITING, s.status)
        assertEquals(now + 3 * hour, s.nextDoseAtMillis)
        assertEquals(3 * hour, s.millisUntilNext(now))
    }

    @Test fun intervalElapsed_isReady() {
        val s = calc(listOf(TakenDose(now - 4 * hour, 200.0)))
        assertEquals(CooldownStatus.READY, s.status)
    }

    @Test fun amountCap_reached_waitsForOldestDoseToAgeOut() {
        // 6 x 200 mg in the last ~22h = 1200 mg, interval long past.
        val doses = (0 until 6).map { TakenDose(now - (22 - it * 4) * hour + 0, 200.0) }
        val s = calc(doses)
        assertEquals(CooldownStatus.AT_DAILY_MAX, s.status)
        assertEquals(doses.minOf { it.timestampMillis } + 24 * hour, s.nextDoseAtMillis)
        assertEquals(1200.0, s.amountIn24h, 0.0)
    }

    @Test fun amountCap_notReached_isReady() {
        val doses = listOf(TakenDose(now - 10 * hour, 400.0), TakenDose(now - 5 * hour, 400.0))
        assertEquals(CooldownStatus.READY, calc(doses).status)
    }

    @Test fun doseCountCap_loratadineOncePerDay() {
        val s = calc(listOf(TakenDose(now - 6 * hour, 10.0)), interval = 24 * 60, max = 1, isDoses = true, next = 10.0)
        assertEquals(now + 18 * hour, s.nextDoseAtMillis)
    }

    @Test fun doseOlderThan24h_isIgnoredInTotal() {
        val s = calc(listOf(TakenDose(now - 25 * hour, 1000.0)))
        assertEquals(0, s.dosesIn24h)
        assertEquals(CooldownStatus.READY, s.status)
    }

    @Test fun noLimits_alwaysReady() {
        assertEquals(CooldownStatus.READY, calc(listOf(TakenDose(now - 1000, 1.0)), interval = null, max = null).status)
    }

    @Test fun catalog_searchByBrandAndGeneric() {
        assertEquals("ibuprofen", OtcCatalog.search("advil").single().id)
        assertEquals("acetaminophen", OtcCatalog.search("Tylenol").single().id)
        assertEquals("naproxen", OtcCatalog.search("aleve").single().id)
        assertEquals("aspirin", OtcCatalog.search("Bayer").single().id)
        assertTrue(OtcCatalog.search("").size == OtcCatalog.meds.size)
        assertTrue(OtcCatalog.search("zzz").isEmpty())
    }
}
