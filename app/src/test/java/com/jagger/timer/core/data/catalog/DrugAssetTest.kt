package com.jagger.timer.core.data.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Checks the real bundled asset (unit tests run with the module directory as the working dir). */
class DrugAssetTest {

    private val records: List<DrugRecord> by lazy {
        File("src/main/assets/drugs.csv").useLines { DrugDatasetParser.parse(it) }
    }

    @Test fun asset_hasHundredsOfCleanRows() {
        assertTrue("rows=${records.size}", records.size in 500..2000)
        records.forEach {
            assertTrue(it.genericName, it.genericName.length <= 60 && it.genericName.none(Char::isDigit))
            assertTrue(it.genericName, it.strengths.isNotEmpty())
            assertTrue(it.genericName, it.strengths.none { s -> s.contains("/ml") || s.contains("[") })
        }
    }

    @Test fun asset_namesAreUniqueIgnoringCase() {
        val names = records.map { it.genericName.lowercase() }
        assertEquals(names.size, names.toSet().size)
    }

    @Test fun asset_fillsGapsBeyondTheCuratedList() {
        val hit = CatalogSearch.search("zolmitriptan", records).single()
        assertEquals(CatalogSource.DATASET, hit.source)
        assertTrue(hit.strengths.isNotEmpty())
    }

    @Test fun asset_curatedStillWinsForCommonDrugs() {
        val hits = CatalogSearch.search("atorvastatin", records)
        assertEquals(CatalogSource.RX, hits.first().source)
        // dataset copies of the same ingredient (e.g. "Atorvastatin Calcium") are deduped away
        assertEquals(1, hits.count { CatalogSearch.key(it.genericName) == CatalogSearch.key("Atorvastatin") })
    }

    @Test fun asset_fileSizeStaysSmall() {
        assertTrue(File("src/main/assets/drugs.csv").length() < 500_000)
    }
}
