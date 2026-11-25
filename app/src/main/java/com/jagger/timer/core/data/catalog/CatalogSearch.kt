// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.core.data.catalog

enum class CatalogSource { RX, OTC, DATASET }

/** A search hit from any of the three catalogs; [rx]/[otc] carry the curated detail when there is any. */
data class CatalogResult(
    val source: CatalogSource,
    val genericName: String,
    val brandNames: List<String>,
    val strengths: List<String>,
    val ingredients: List<String>,
    val rx: RxMed? = null,
    val otc: OtcMed? = null
)

/**
 * One search over the curated prescription list, the OTC list and the bundled dataset.
 * Order: curated Rx, then OTC, then dataset matches (deduped against everything above).
 */
object CatalogSearch {

    private val SALT_WORDS = setOf(
        "and", "sodium", "potassium", "calcium", "magnesium", "hydrochloride", "hcl", "besylate", "succinate",
        "tartrate", "maleate", "mesylate", "sulfate", "phosphate", "hydrobromide", "acetate", "citrate",
        "fumarate", "medoxomil", "axetil", "disoproxil", "monohydrate", "dihydrate", "anhydrous",
        "bitartrate", "bromide", "chloride", "nitrate", "mononitrate", "oxalate", "lactate", "hydrate",
        "hyclate", "gluconate", "benzoate", "valerate", "pamoate", "napsylate", "saccharate", "hemifumarate",
        "dipropionate", "extended", "release", "delayed"
    )

    /** Identity of a drug for dedupe: its active ingredient words minus salt words, e.g. "hydrocodone acetaminophen". */
    internal fun key(name: String): String =
        name.lowercase().split(Regex("[^a-z]+"))
            .filter { it.isNotEmpty() && it !in SALT_WORDS }
            .sorted()
            .joinToString(" ")

    fun search(
        query: String,
        dataset: List<DrugRecord> = emptyList(),
        maxDataset: Int = 20
    ): List<CatalogResult> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()

        fun rank(name: String, brands: List<String>): Int =
            if (name.startsWith(q, ignoreCase = true) || brands.any { it.startsWith(q, ignoreCase = true) }) 0 else 1

        val rx = RxCatalog.search(q)
            .sortedBy { rank(it.genericName, it.brandNames) }
            .map { CatalogResult(CatalogSource.RX, it.genericName, it.brandNames, it.strengths, it.ingredients, rx = it) }

        val otc = OtcCatalog.search(q)
            .sortedBy { rank(it.genericName, it.brandNames) }
            .map {
                CatalogResult(
                    CatalogSource.OTC, it.genericName, it.brandNames, listOf("${it.defaultDose} ${it.unit}"),
                    it.ingredients, otc = it
                )
            }

        val seen = (rx + otc).map { key(it.genericName) }.toMutableSet()
        val fromDataset = dataset
            .filter { d ->
                d.genericName.contains(q, ignoreCase = true) || d.brandNames.any { it.contains(q, ignoreCase = true) }
            }
            .sortedBy { rank(it.genericName, it.brandNames) }
            .filter { seen.add(key(it.genericName)) }
            .take(maxDataset)
            .map { CatalogResult(CatalogSource.DATASET, it.genericName, it.brandNames, it.strengths, it.ingredients) }

        return rx + otc + fromDataset
    }
}
