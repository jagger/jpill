// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.core.util

import com.jagger.timer.core.data.catalog.CatalogSearch
import com.jagger.timer.core.data.local.entity.PillEntity
import com.jagger.timer.core.data.local.entity.doseAmount

/** What the shared 24h total is counted in. */
enum class IngredientMeasure { NONE, AMOUNT, DOSES }

/**
 * Two or more active medicines share an ingredient. [measure] NONE = no cap to count against (just a heads-up);
 * otherwise [combined] is the 24h total across all of them versus the lowest [cap]. Never blocks anything.
 */
data class IngredientWarning(
    val shared: List<String>,
    val otherNames: List<String>,
    val measure: IngredientMeasure,
    val combined: Double,
    val cap: Int?,
    val unit: String,
    /** Taking this pill's next dose would push the combined total past [cap]. */
    val wouldExceed: Boolean,
    /** The combined total has already reached [cap]. */
    val atOrOver: Boolean
)

/** Pure duplicate-ingredient logic. Ingredients are stored on the pill as a "|"-joined normalized list. */
object IngredientLogic {

    private val SPLIT = Regex("[/+,;&|]| and ")

    /** Lower-case, salt-free, one entry per ingredient: "Hydrocodone/Acetaminophen" -> [hydrocodone, acetaminophen]. */
    fun normalize(raw: List<String>): List<String> =
        raw.flatMap { it.split(SPLIT) }.map { CatalogSearch.key(it) }.filter { it.isNotBlank() }.distinct()

    fun serialize(ingredients: List<String>): String? =
        normalize(ingredients).takeIf { it.isNotEmpty() }?.joinToString("|")

    fun parse(stored: String?): List<String> =
        stored?.split("|")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

    /**
     * Warnings by pill id, for every pill that shares an ingredient with another one in [pills].
     * [doses] are TAKEN doses per pill id (any age; trimmed to the rolling 24h window here).
     */
    fun warnings(pills: List<PillEntity>, doses: Map<Long, List<TakenDose>>, now: Long): Map<Long, IngredientWarning> {
        val parsed = pills.associate { it.id to parse(it.ingredients).toSet() }
        val result = mutableMapOf<Long, IngredientWarning>()
        for (p in pills) {
            val mine = parsed[p.id].orEmpty()
            if (mine.isEmpty()) continue
            val others = pills.filter { it.id != p.id && parsed[it.id].orEmpty().any(mine::contains) }
            if (others.isEmpty()) continue
            val group = listOf(p) + others
            val shared = mine.filter { ing -> others.any { ing in parsed[it.id].orEmpty() } }

            fun inWindow(id: Long) = doses[id].orEmpty()
                .filter { it.timestampMillis > now - CooldownCalculator.WINDOW_MILLIS && it.timestampMillis <= now }

            val amountCaps = group.filter {
                it.asNeeded && it.maxPer24h != null && !it.maxIsDoses && it.unit.equals(p.unit, ignoreCase = true)
            }
            val doseCaps = group.filter { it.asNeeded && it.maxPer24h != null && it.maxIsDoses }
            val (measure, cap) = when {
                amountCaps.isNotEmpty() -> IngredientMeasure.AMOUNT to amountCaps.minOf { it.maxPer24h!! }
                doseCaps.isNotEmpty() -> IngredientMeasure.DOSES to doseCaps.minOf { it.maxPer24h!! }
                else -> IngredientMeasure.NONE to null
            }
            val combined = when (measure) {
                IngredientMeasure.AMOUNT -> group.filter { it.unit.equals(p.unit, ignoreCase = true) }
                    .sumOf { g -> inWindow(g.id).sumOf { it.amount } }
                IngredientMeasure.DOSES -> group.sumOf { inWindow(it.id).size }.toDouble()
                IngredientMeasure.NONE -> 0.0
            }
            val next = if (measure == IngredientMeasure.AMOUNT) p.doseAmount() ?: 0.0 else 1.0
            result[p.id] = IngredientWarning(
                shared = shared,
                otherNames = others.map { it.name },
                measure = measure,
                combined = combined,
                cap = cap,
                unit = p.unit,
                wouldExceed = cap != null && combined + next > cap,
                atOrOver = cap != null && combined >= cap
            )
        }
        return result
    }

    /** One-line banner text. */
    fun message(w: IngredientWarning): String {
        val what = w.shared.joinToString(" and ")
        val who = w.otherNames.joinToString(", ")
        val base = "Contains $what, same as $who."
        if (w.measure == IngredientMeasure.NONE || w.cap == null) return "$base Don't double up."
        val total = if (w.measure == IngredientMeasure.AMOUNT)
            "${w.combined.toInt()} of ${w.cap} ${w.unit}" else "${w.combined.toInt()} of ${w.cap} doses"
        return "$base Combined $total in the last 24h (shared limit)."
    }
}
