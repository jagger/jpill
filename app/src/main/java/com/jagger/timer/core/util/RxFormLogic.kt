package com.jagger.timer.core.util

import com.jagger.timer.core.data.local.entity.PillEntity
import com.jagger.timer.core.data.local.entity.ScheduleEntity

/** Pure helpers for the prescription part of the pill form. */
object RxFormLogic {

    /** Food/timing preset phrases offered as chips. */
    val INSTRUCTION_PRESETS = listOf("With food", "Empty stomach", "At bedtime", "Avoid grapefruit")

    /**
     * Splits a catalog strength like "10 mg" into (number, unit). Combination strengths such as
     * "5 mg / 325 mg" are not a single number, so they come back whole with a null unit.
     */
    fun parseStrength(strength: String): Pair<String, String?> {
        val m = Regex("""^\s*([0-9]+(?:\.[0-9]+)?)\s*([A-Za-z]+)\s*$""").matchEntire(strength)
        return if (m != null) m.groupValues[1] to m.groupValues[2] else strength.trim() to null
    }

    /** Adds [phrase] to the notes if not already there (case-insensitive); keeps existing text. */
    fun addInstruction(existing: String?, phrase: String): String {
        val current = existing?.trim().orEmpty()
        if (current.contains(phrase, ignoreCase = true)) return current
        return if (current.isEmpty()) phrase else "$current. $phrase"
    }

    fun parseTablets(text: String): Double =
        text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 } ?: 1.0

    fun parseSupply(text: String): Double? =
        text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }

    fun parseRefillDays(text: String): Int = text.trim().toIntOrNull()?.takeIf { it in 1..365 } ?: 7

    /** "28" for whole numbers, "27.5" otherwise. */
    fun formatCount(value: Double): String =
        if (value == Math.floor(value)) value.toLong().toString() else "%.1f".format(java.util.Locale.US, value)

    /** "2 x 500 mg" when more than one tablet per dose, else "500 mg". */
    fun doseLabel(pill: PillEntity): String = when {
        pill.dosage.isBlank() -> "Dose not set"
        pill.tabletsPerDose != 1.0 -> "${formatCount(pill.tabletsPerDose)} × ${pill.dosage} ${pill.unit}"
        else -> "${pill.dosage} ${pill.unit}"
    }
}

/** What to do with a prescription's refill reminder. */
sealed class RefillAction {
    /** Nothing to remind about (not tracking supply) or already reminded: remove any pending alarm. */
    object Cancel : RefillAction()
    /** Supply already at/below the threshold and not yet reminded: remind right away. */
    object FireNow : RefillAction()
    /** Supply is fine for now: wake up at [atMillis] when it will reach the threshold. */
    data class ScheduleAt(val atMillis: Long) : RefillAction()
}

object RefillPlanner {
    /**
     * Decides the reminder action. [alreadyNotified] makes the reminder fire once per low spell: it is cleared
     * when supply goes back above the threshold (e.g. after entering a refill), re-arming the next reminder.
     */
    fun plan(
        pill: PillEntity,
        schedules: List<ScheduleEntity>,
        alreadyNotified: Boolean,
        nowMillis: Long
    ): RefillAction {
        if (!pill.isPrescription || pill.supplyCount == null) return RefillAction.Cancel
        return when (SupplyCalculator.status(pill, schedules)) {
            SupplyStatus.OK -> {
                val at = SupplyCalculator.refillAlertAtMillis(pill, schedules, nowMillis)
                if (at == null) RefillAction.Cancel else RefillAction.ScheduleAt(at)
            }
            SupplyStatus.LOW, SupplyStatus.EMPTY -> if (alreadyNotified) RefillAction.Cancel else RefillAction.FireNow
        }
    }

    /** Whether the "already notified" flag should be cleared (supply is healthy again or no longer tracked). */
    fun shouldClearNotified(pill: PillEntity, schedules: List<ScheduleEntity>): Boolean =
        !pill.isPrescription || pill.supplyCount == null || SupplyCalculator.status(pill, schedules) == SupplyStatus.OK
}
