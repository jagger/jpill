// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.nfc

import com.jagger.timer.core.data.local.entity.DoseLogEntity
import com.jagger.timer.core.data.local.entity.DoseStatus
import com.jagger.timer.core.data.local.entity.PillEntity
import com.jagger.timer.core.data.local.entity.RecurrencePattern
import com.jagger.timer.core.data.local.entity.ScheduleEntity
import com.jagger.timer.core.data.local.entity.ScheduleType
import com.jagger.timer.core.util.CooldownState
import com.jagger.timer.core.util.CooldownStatus
import com.jagger.timer.core.util.IngredientMeasure
import com.jagger.timer.core.util.IngredientWarning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class TagScanResolverTest {
    private val zone = ZoneOffset.UTC
    private fun at(h: Int, m: Int = 0) = LocalDateTime.of(2026, 11, 25, h, m).atZone(zone).toInstant().toEpochMilli()

    private fun pill(asNeeded: Boolean = false, archived: Boolean = false) = PillEntity(
        id = 1, name = "Amlodipine", dosage = "10", unit = "mg", color = 0, asNeeded = asNeeded, isArchived = archived
    )

    private fun daily8am(id: Long = 1) = ScheduleEntity(
        id = id, pillId = 1, type = ScheduleType.FIXED_TIME, pattern = RecurrencePattern.DAILY, timeMinutes = 8 * 60
    )

    private fun log(ts: Long, status: DoseStatus = DoseStatus.TAKEN, schedule: Long? = 1) =
        DoseLogEntity(pillId = 1, scheduleId = schedule, timestamp = ts, status = status)

    private fun resolve(
        p: PillEntity? = pill(), schedules: List<ScheduleEntity> = listOf(daily8am()), logs: List<DoseLogEntity> = emptyList(),
        cd: CooldownState? = null, w: IngredientWarning? = null, now: Long
    ) = TagScanResolver.resolve(p, schedules, logs, cd, w, now, zone)

    @Test fun unknownTag() = assertEquals(ScanDecision.UnknownTag, resolve(p = null, now = at(7, 40)))

    @Test fun archivedMedicineIsNotLogged() = assertTrue(resolve(p = pill(archived = true), now = at(7, 40)) is ScanDecision.Archived)

    @Test fun dueDoseInsideEarlyWindow() {
        val d = resolve(now = at(7, 40)) as ScanDecision.ScheduledDue
        assertEquals(1, d.options.size)
        assertEquals(at(8), d.options[0].dueAtMillis)
        assertFalse(d.options[0].late)
    }

    @Test fun pastDueDoseIsOfferedAsLate() {
        val d = resolve(now = at(9, 30)) as ScanDecision.ScheduledDue
        assertTrue(d.options[0].late)
        assertEquals(at(8), d.options[0].dueAtMillis)
    }

    @Test fun doseTakenEarlyIsAlreadyTaken() {
        val d = resolve(logs = listOf(log(at(7, 28))), now = at(7, 40)) as ScanDecision.AlreadyTaken
        assertEquals(at(7, 28), d.atMillis)
    }

    @Test fun takenThisMorningOffersTonightsAsNext() {
        val d = resolve(logs = listOf(log(at(8, 5))), now = at(14)) as ScanDecision.AlreadyTaken
        assertEquals(at(8, 5), d.atMillis)
        assertEquals(at(20).let { it + 0 }, d.nextDueMillis?.let { _ -> at(20) })
    }

    @Test fun farFromNextDoseIsEarly() {
        val d = resolve(now = at(16)) as ScanDecision.ScheduledEarly
        assertEquals(1L, d.scheduleId)
        assertEquals(at(8) + 24 * 3_600_000L, d.nextDueMillis)
    }

    @Test fun twoSchedulesBothDueGiveTwoOptions() {
        val s = listOf(daily8am(1), daily8am(2).copy(timeMinutes = 8 * 60 + 30))
        val d = resolve(schedules = s, now = at(7, 50)) as ScanDecision.ScheduledDue
        assertEquals(listOf(1L, 2L), d.options.map { it.scheduleId })
    }

    @Test fun quickSecondScanIsDuplicate() {
        val d = resolve(logs = listOf(log(at(7, 59))), now = at(8, 0))
        assertTrue(d is ScanDecision.AlreadyTaken)
    }

    @Test fun asNeededReady() = assertTrue(resolve(p = pill(asNeeded = true), schedules = emptyList(), now = at(10),
        cd = CooldownState(CooldownStatus.READY, null, 0, 0.0, null)) is ScanDecision.AsNeededReady)

    @Test fun asNeededWaitingNeedsConfirmation() {
        val cd = CooldownState(CooldownStatus.WAITING, at(12), 1, 200.0, at(8))
        val d = resolve(p = pill(asNeeded = true), schedules = emptyList(), now = at(10), cd = cd) as ScanDecision.AsNeededEarly
        assertEquals(CooldownStatus.WAITING, d.status)
        assertEquals(at(12), d.nextAtMillis)
    }

    @Test fun sharedIngredientLimitMakesReadyDoseNeedConfirmation() {
        val w = IngredientWarning(listOf("acetaminophen"), listOf("Cold Relief"), IngredientMeasure.AMOUNT, 3000.0, 3000, "mg", wouldExceed = true, atOrOver = false)
        val cd = CooldownState(CooldownStatus.READY, null, 0, 0.0, null)
        val d = resolve(p = pill(asNeeded = true), schedules = emptyList(), now = at(10), cd = cd, w = w) as ScanDecision.AsNeededEarly
        assertEquals(w, d.sharedWarning)
    }
}
