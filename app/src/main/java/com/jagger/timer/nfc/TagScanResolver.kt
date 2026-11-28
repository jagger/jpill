// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.nfc

import com.jagger.timer.core.data.local.entity.DoseLogEntity
import com.jagger.timer.core.data.local.entity.DoseStatus
import com.jagger.timer.core.data.local.entity.PillEntity
import com.jagger.timer.core.data.local.entity.ScheduleEntity
import com.jagger.timer.core.data.local.entity.ScheduleType
import com.jagger.timer.core.util.CooldownState
import com.jagger.timer.core.util.CooldownStatus
import com.jagger.timer.core.util.DoseCoverage
import com.jagger.timer.core.util.IngredientWarning
import com.jagger.timer.core.util.ScheduleCalculator
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** One scheduled dose a scan could log: which schedule, when it was due, and whether it is already past due. */
data class DueOption(val scheduleId: Long, val dueAtMillis: Long, val late: Boolean)

/** What the confirm card should offer after a tag is scanned. */
sealed interface ScanDecision {
    /** Nobody owns this tag (not linked, or not a jPill tag id we know). */
    data object UnknownTag : ScanDecision
    data class Archived(val pill: PillEntity) : ScanDecision

    /** A scheduled dose is due (or inside the early window): confirm and log it. Several options mean the user picks one. */
    data class ScheduledDue(val pill: PillEntity, val options: List<DueOption>) : ScanDecision

    /** Nothing is due yet; logging is allowed but is an early dose. */
    data class ScheduledEarly(val pill: PillEntity, val scheduleId: Long, val nextDueMillis: Long) : ScanDecision

    /** The dose was already taken or skipped. [nextDueMillis] and [scheduleId] let the card offer "log another anyway". */
    data class AlreadyTaken(
        val pill: PillEntity,
        val atMillis: Long,
        val nextDueMillis: Long?,
        val scheduleId: Long?
    ) : ScanDecision

    /** As-needed medicine whose wait is over and that would not break a shared-ingredient limit. */
    data class AsNeededReady(val pill: PillEntity) : ScanDecision

    /** As-needed medicine that is still waiting, at its daily max, or would pass a shared-ingredient limit. */
    data class AsNeededEarly(
        val pill: PillEntity,
        val status: CooldownStatus,
        val nextAtMillis: Long?,
        val sharedWarning: IngredientWarning?
    ) : ScanDecision
}

/**
 * Decides what a scan of a medicine's NFC tag should do. Pure, so the rules are unit-tested. It reuses the app's
 * existing rules: the 2 h early window of [DoseCoverage] for scheduled doses and the cooldown and shared-ingredient
 * checks of the as-needed Take now button. The user always confirms before anything is logged.
 */
object TagScanResolver {
    /** A second scan inside this window of a logged dose is treated as a repeat, not a new dose. */
    const val DUPLICATE_WINDOW_MILLIS = 2 * 60_000L
    private const val LATE_LOOKBACK_HOURS = 6L
    private const val EARLY_MILLIS = DoseCoverage.EARLY_WINDOW_MINUTES * 60_000L

    fun resolve(
        pill: PillEntity?,
        schedules: List<ScheduleEntity>,
        logs: List<DoseLogEntity>,
        cooldown: CooldownState?,
        warning: IngredientWarning?,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): ScanDecision {
        if (pill == null) return ScanDecision.UnknownTag
        if (pill.isArchived) return ScanDecision.Archived(pill)

        val lastTaken = logs.filter { it.status == DoseStatus.TAKEN && it.timestamp <= nowMillis }.maxByOrNull { it.timestamp }
        if (lastTaken != null && nowMillis - lastTaken.timestamp < DUPLICATE_WINDOW_MILLIS) {
            return ScanDecision.AlreadyTaken(pill, lastTaken.timestamp, nextDue(schedules, logs, nowMillis, zone)?.second, null)
        }

        if (pill.asNeeded) {
            val cd = cooldown ?: return ScanDecision.AsNeededReady(pill)
            return if (cd.status == CooldownStatus.READY && warning?.wouldExceed != true) ScanDecision.AsNeededReady(pill)
            else ScanDecision.AsNeededEarly(pill, cd.status, cd.nextDoseAtMillis, warning?.takeIf { it.wouldExceed })
        }

        return resolveScheduled(pill, schedules, logs, nowMillis, zone)
    }

    private fun resolveScheduled(
        pill: PillEntity,
        schedules: List<ScheduleEntity>,
        logs: List<DoseLogEntity>,
        nowMillis: Long,
        zone: ZoneId
    ): ScanDecision {
        if (schedules.isEmpty()) return ScanDecision.AsNeededReady(pill) // nothing scheduled: just log it
        val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zone)
        val lastDose = logs.maxOfOrNull { it.timestamp }

        val due = mutableListOf<DueOption>()
        var covered: DoseLogEntity? = null
        var soonest: Pair<Long, Long>? = null // scheduleId to dueAtMillis, for the "not due yet" case

        for (s in schedules) {
            val occurrences = buildList {
                if (s.type != ScheduleType.INTERVAL) {
                    val past = ScheduleCalculator.calculateNextOccurrence(s, lastDose, now.minusHours(LATE_LOOKBACK_HOURS).minusMinutes(1))
                    if (past != null && !past.isAfter(now)) add(millis(past, zone))
                }
                ScheduleCalculator.calculateNextOccurrence(s, lastDose, now)?.let { add(millis(it, zone)) }
            }.distinct()

            for (at in occurrences) {
                val cover = DoseCoverage.findCovering(logs, s.id, at, nowMillis)
                when {
                    cover != null -> if (covered == null || cover.timestamp > covered.timestamp) covered = cover
                    at <= nowMillis + EARLY_MILLIS -> due += DueOption(s.id, at, late = at < nowMillis)
                    else -> if (soonest == null || at < soonest.second) soonest = s.id to at
                }
            }
        }

        if (due.isNotEmpty()) return ScanDecision.ScheduledDue(pill, due.sortedBy { it.dueAtMillis })
        covered?.let { return ScanDecision.AlreadyTaken(pill, it.timestamp, soonest?.second, soonest?.first) }
        val next = soonest ?: nextDue(schedules, logs, nowMillis, zone)
            ?: return ScanDecision.AsNeededReady(pill)
        return ScanDecision.ScheduledEarly(pill, next.first, next.second)
    }

    /** The next occurrence over all schedules as (scheduleId, dueAtMillis). */
    private fun nextDue(schedules: List<ScheduleEntity>, logs: List<DoseLogEntity>, nowMillis: Long, zone: ZoneId): Pair<Long, Long>? {
        val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zone)
        val last = logs.maxOfOrNull { it.timestamp }
        return schedules.mapNotNull { s ->
            ScheduleCalculator.calculateNextOccurrence(s, last, now)?.let { s.id to millis(it, zone) }
        }.minByOrNull { it.second }
    }

    private fun millis(t: LocalDateTime, zone: ZoneId) = t.atZone(zone).toInstant().toEpochMilli()
}

/** What the confirm card offers after a timer tag is scanned. */
sealed interface TimerScanDecision {
    /** No timer owns this tag (first scan on a new phone, or the timer was deleted): create it from the tag's own data. */
    data class CreateAndStart(val payload: NfcPayload.Timer) : TimerScanDecision
    /** The timer is idle or finished: start it again for its full length. */
    data class Start(val timer: com.jagger.timer.core.data.local.entity.TimerEntity) : TimerScanDecision
    data class AlreadyRunning(val timer: com.jagger.timer.core.data.local.entity.TimerEntity, val remainingMillis: Long) : TimerScanDecision
    data class Paused(val timer: com.jagger.timer.core.data.local.entity.TimerEntity, val remainingMillis: Long) : TimerScanDecision
}

object TimerScanResolver {
    /** Scan-and-go: a new or idle/finished timer starts at once; a running or paused one asks first. */
    fun startsImmediately(d: TimerScanDecision): Boolean = d is TimerScanDecision.CreateAndStart || d is TimerScanDecision.Start

    fun resolve(
        payload: NfcPayload.Timer,
        existing: com.jagger.timer.core.data.local.entity.TimerEntity?,
        nowMillis: Long
    ): TimerScanDecision {
        if (existing == null) return TimerScanDecision.CreateAndStart(payload)
        val left = com.jagger.timer.core.util.TimerLogic.remaining(existing, nowMillis)
        return when {
            com.jagger.timer.core.util.TimerLogic.isDue(existing, nowMillis) -> TimerScanDecision.Start(existing)
            existing.state == com.jagger.timer.core.data.local.entity.TimerState.RUNNING -> TimerScanDecision.AlreadyRunning(existing, left)
            existing.state == com.jagger.timer.core.data.local.entity.TimerState.PAUSED -> TimerScanDecision.Paused(existing, left)
            else -> TimerScanDecision.Start(existing)
        }
    }
}
