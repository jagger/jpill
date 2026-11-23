package com.jagger.timer.core.data.catalog

import com.jagger.timer.core.data.local.entity.RecurrencePattern
import com.jagger.timer.core.data.local.entity.ScheduleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogTest {

    private val dataset = listOf(
        DrugRecord("Atorvastatin Calcium", listOf("Lipitor"), listOf("10 mg", "20 mg"), listOf("atorvastatin calcium")),
        DrugRecord("Zolmitriptan", listOf("Zomig"), listOf("2.5 mg"), listOf("zolmitriptan")),
        DrugRecord("Lisinopril and Hydrochlorothiazide", listOf("Zestoretic"), listOf("10 mg / 12.5 mg"), listOf("lisinopril", "hydrochlorothiazide")),
        DrugRecord("Ibuprofen", emptyList(), listOf("800 mg"), listOf("ibuprofen"))
    )

    // --- RxCatalog ---
    @Test fun rx_idsUniqueAndAboutAHundred() {
        val ids = RxCatalog.meds.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(RxCatalog.meds.size in 90..140)
    }

    @Test fun rx_everyEntryHasNamesStrengthsAndIngredients() {
        RxCatalog.meds.forEach {
            assertTrue(it.id, it.genericName.isNotBlank() && it.strengths.isNotEmpty() && it.ingredients.isNotEmpty())
        }
    }

    @Test fun rx_searchByBrand() {
        assertEquals("atorvastatin", RxCatalog.search("Lipitor").single().id)
        assertEquals("sertraline", RxCatalog.search("zoloft").single().id)
    }

    @Test fun frequency_mapsToScheduleDefaults() {
        assertEquals(ScheduleType.INTERVAL, RxFrequency.TWICE_DAILY.scheduleDefaults()!!.type)
        assertEquals(720, RxFrequency.TWICE_DAILY.scheduleDefaults()!!.intervalMinutes)
        assertEquals(480, RxFrequency.THREE_TIMES_DAILY.scheduleDefaults()!!.intervalMinutes)
        assertEquals("Morning", RxFrequency.ONCE_DAILY.scheduleDefaults()!!.timeSlotName)
        assertEquals("Night", RxFrequency.BEDTIME.scheduleDefaults()!!.timeSlotName)
        assertEquals(RecurrencePattern.WEEKLY, RxFrequency.WEEKLY.scheduleDefaults()!!.pattern)
        assertNull(RxFrequency.AS_NEEDED.scheduleDefaults())
    }

    // --- Dataset parser ---
    @Test fun parser_readsRowsAndSkipsBlank() {
        val rows = DrugDatasetParser.parse(sequenceOf(
            "Atorvastatin Calcium;Lipitor;10 mg|20 mg;atorvastatin calcium",
            "",
            "Metformin;Glucophage|Fortamet;500 mg;metformin"
        ))
        assertEquals(2, rows.size)
        assertEquals(listOf("10 mg", "20 mg"), rows[0].strengths)
        assertEquals(listOf("Glucophage", "Fortamet"), rows[1].brandNames)
    }

    @Test fun parser_handlesQuotedSemicolonsAndMissingColumns() {
        val rows = DrugDatasetParser.parse(sequenceOf("\"Odd; Name\";;;", "JustAName"))
        assertEquals("Odd; Name", rows[0].genericName)
        assertTrue(rows[0].brandNames.isEmpty())
        assertEquals("JustAName", rows[1].genericName)
    }

    // --- CatalogSearch ---
    @Test fun search_emptyQueryReturnsNothing() {
        assertTrue(CatalogSearch.search("   ", dataset).isEmpty())
    }

    @Test fun search_ordersCuratedRxThenOtcThenDataset() {
        val sources = CatalogSearch.search("zo", dataset).map { it.source }
        assertEquals(sources, sources.sortedBy { it.ordinal })
        assertTrue(CatalogSource.DATASET in sources)       // Zolmitriptan/Zomig only in dataset
        assertTrue(CatalogSource.RX in sources)            // Zoloft, Zocor, ...
    }

    @Test fun search_datasetFillsGapsAndDedupesAgainstCurated() {
        val results = CatalogSearch.search("atorva", dataset)
        assertEquals(1, results.size)                       // dataset Atorvastatin Calcium deduped
        assertEquals(CatalogSource.RX, results.single().source)
        val gap = CatalogSearch.search("zomig", dataset).single()
        assertEquals(CatalogSource.DATASET, gap.source)
        assertEquals("Zolmitriptan", gap.genericName)
    }

    @Test fun search_otcDedupesDatasetDuplicate() {
        val results = CatalogSearch.search("ibuprofen", dataset)
        assertEquals(listOf(CatalogSource.OTC), results.map { it.source })
    }

    @Test fun search_combinationsAreNotDedupedAgainstSingles() {
        val results = CatalogSearch.search("lisinopril", dataset)
        assertTrue(results.any { it.source == CatalogSource.RX && it.genericName == "Lisinopril" })
        assertTrue(results.any { it.source == CatalogSource.DATASET && it.genericName.startsWith("Lisinopril and") })
    }

    @Test fun search_datasetResultsAreCapped() {
        val big = ('a'..'z').flatMap { a -> ('a'..'b').map { b -> DrugRecord("Zzdrug$a$b", emptyList(), emptyList(), emptyList()) } }
        assertEquals(20, CatalogSearch.search("zzdrug", big).size)
    }

    @Test fun key_ignoresSaltsAndOrder() {
        assertEquals(CatalogSearch.key("Hydrocodone/Acetaminophen"), CatalogSearch.key("Hydrocodone Bitartrate and Acetaminophen"))
        assertFalse(CatalogSearch.key("Lisinopril") == CatalogSearch.key("Lisinopril and Hydrochlorothiazide"))
    }

    @Test fun rx_neverCarriesDoseLimits() {
        // Structural guarantee: RxMed has no dose/max/interval fields; schedule defaults are frequency only.
        val fields = RxMed::class.java.declaredFields.map { it.name }
        assertFalse(fields.any { it.contains("max", true) || it.contains("interval", true) || it.contains("defaultDose", true) })
    }
}
