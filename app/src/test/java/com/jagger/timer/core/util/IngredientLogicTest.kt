package com.jagger.timer.core.util

import com.jagger.timer.core.data.local.entity.PillEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IngredientLogicTest {
    private val now = 100L * 3_600_000
    private val hour = 3_600_000L

    private fun pill(id: Long, name: String, ing: String?, dose: String = "500", unit: String = "mg",
                     asNeeded: Boolean = true, max: Int? = 3000, maxIsDoses: Boolean = false) =
        PillEntity(id = id, name = name, dosage = dose, unit = unit, color = 0, asNeeded = asNeeded,
            maxPer24h = max, maxIsDoses = maxIsDoses, ingredients = ing)

    private val tylenol = pill(1, "Tylenol", "acetaminophen")
    private val coldMed = pill(2, "Cold Max", "acetaminophen|dextromethorphan", dose = "325", asNeeded = false, max = null)

    @Test fun normalizeSplitsCombosAndStripsSalts() {
        assertEquals(listOf("hydrocodone", "acetaminophen"), IngredientLogic.normalize(listOf("Hydrocodone/Acetaminophen")))
        assertEquals(listOf("naproxen"), IngredientLogic.normalize(listOf("Naproxen sodium", "naproxen")))
        assertEquals(listOf("a", "b"), IngredientLogic.parse(IngredientLogic.serialize(listOf("A", "B"))))
        assertNull(IngredientLogic.serialize(emptyList()))
    }

    @Test fun noWarningWithoutAnOverlap() {
        val ibu = pill(3, "Advil", "ibuprofen")
        assertTrue(IngredientLogic.warnings(listOf(tylenol, ibu), emptyMap(), now).isEmpty())
        assertTrue(IngredientLogic.warnings(listOf(tylenol, pill(4, "Mystery", null)), emptyMap(), now).isEmpty())
    }

    @Test fun bothCardsWarnAndCountAgainstTheLowestCap() {
        val doses = mapOf(
            1L to listOf(TakenDose(now - 2 * hour, 500.0), TakenDose(now - 6 * hour, 500.0)),
            2L to listOf(TakenDose(now - 1 * hour, 325.0))
        )
        val w = IngredientLogic.warnings(listOf(tylenol, coldMed), doses, now)
        assertEquals(setOf(1L, 2L), w.keys)
        val a = w.getValue(1L); val b = w.getValue(2L)
        assertEquals(1325.0, a.combined, 0.0)
        assertEquals(3000, a.cap)               // the cold med has no cap, Tylenol's applies to both
        assertEquals(1325.0, b.combined, 0.0)
        assertEquals(3000, b.cap)
        assertEquals(listOf("acetaminophen"), a.shared)
        assertEquals(listOf("Cold Max"), a.otherNames)
        assertFalse(a.wouldExceed)
    }

    @Test fun lowestCapWinsAndExceedIsFlaggedButNeverBlocking() {
        val strict = pill(5, "Strict", "acetaminophen", max = 1000)
        val doses = mapOf(1L to listOf(TakenDose(now - hour, 500.0)), 5L to listOf(TakenDose(now - 2 * hour, 400.0)))
        val w = IngredientLogic.warnings(listOf(tylenol, strict), doses, now).getValue(1L)
        assertEquals(1000, w.cap)
        assertEquals(900.0, w.combined, 0.0)
        assertTrue(w.wouldExceed)   // 900 + 500 > 1000
        assertFalse(w.atOrOver)
    }

    @Test fun dosesOlderThan24HoursDoNotCount() {
        val doses = mapOf(1L to listOf(TakenDose(now - 25 * hour, 500.0)), 2L to listOf(TakenDose(now - 23 * hour, 325.0)))
        assertEquals(325.0, IngredientLogic.warnings(listOf(tylenol, coldMed), doses, now).getValue(1L).combined, 0.0)
    }

    @Test fun sharedIngredientWithoutAnyCapIsAHeadsUpOnly() {
        val a = pill(6, "A", "ibuprofen", asNeeded = false, max = null)
        val b = pill(7, "B", "ibuprofen", asNeeded = false, max = null)
        val w = IngredientLogic.warnings(listOf(a, b), emptyMap(), now).getValue(6L)
        assertEquals(IngredientMeasure.NONE, w.measure)
        assertNull(w.cap)
        assertFalse(w.wouldExceed)
        assertTrue(IngredientLogic.message(w).contains("Don't double up"))
    }

    @Test fun differentUnitsAreNotAddedTogether() {
        val ml = pill(8, "Syrup", "acetaminophen", dose = "10", unit = "ml", asNeeded = false, max = null)
        val doses = mapOf(1L to listOf(TakenDose(now - hour, 500.0)), 8L to listOf(TakenDose(now - hour, 10.0)))
        assertEquals(500.0, IngredientLogic.warnings(listOf(tylenol, ml), doses, now).getValue(1L).combined, 0.0)
    }

    @Test fun doseCountCapsCombineAsDoseCounts() {
        val a = pill(9, "A", "x", max = 4, maxIsDoses = true)
        val b = pill(10, "B", "x", asNeeded = false, max = null)
        val doses = mapOf(9L to listOf(TakenDose(now - hour, 1.0), TakenDose(now - 2 * hour, 1.0)),
            10L to listOf(TakenDose(now - 3 * hour, 1.0), TakenDose(now - 4 * hour, 1.0)))
        val w = IngredientLogic.warnings(listOf(a, b), doses, now).getValue(10L)
        assertEquals(IngredientMeasure.DOSES, w.measure)
        assertEquals(4.0, w.combined, 0.0)
        assertTrue(w.atOrOver)
        assertTrue(w.wouldExceed)
    }
}
