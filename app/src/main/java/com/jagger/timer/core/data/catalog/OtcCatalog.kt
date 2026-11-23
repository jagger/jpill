package com.jagger.timer.core.data.catalog

/**
 * Static catalog of common over-the-counter medicines (adult label defaults).
 *
 * Informational only. Values are typical adult OTC label directions; the package
 * label always wins, and every value can be edited after a medicine is picked.
 */
data class OtcMed(
    val id: String,
    val genericName: String,
    val brandNames: List<String>,
    val defaultDose: String,
    val unit: String,
    val minIntervalMinutes: Int,
    /** Rolling-24h cap. Meaning depends on [maxIsDoses]: dose count if true, else total in [unit]. */
    val maxPer24h: Int,
    val maxIsDoses: Boolean,
    val notes: String? = null
) {
    /** Display name used when creating a pill, e.g. "Ibuprofen (Advil, Motrin)". */
    val displayName: String
        get() = if (brandNames.isEmpty()) genericName else "$genericName (${brandNames.joinToString(", ")})"

    fun matches(query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        return genericName.contains(q, ignoreCase = true) ||
            brandNames.any { it.contains(q, ignoreCase = true) }
    }
}

object OtcCatalog {

    const val DISCLAIMER =
        "Informational only, not medical advice. Always follow the label on your package " +
            "and ask a pharmacist or doctor if unsure."

    val meds: List<OtcMed> = listOf(
        OtcMed(
            id = "ibuprofen",
            genericName = "Ibuprofen",
            brandNames = listOf("Advil", "Motrin"),
            defaultDose = "200", unit = "mg",
            minIntervalMinutes = 4 * 60,
            maxPer24h = 1200, maxIsDoses = false,
            notes = "200-400 mg per dose. Take with food."
        ),
        OtcMed(
            id = "acetaminophen",
            genericName = "Acetaminophen",
            brandNames = listOf("Tylenol", "Paracetamol"),
            defaultDose = "500", unit = "mg",
            minIntervalMinutes = 4 * 60,
            maxPer24h = 3000, maxIsDoses = false,
            notes = "Extra Strength label cap is 3,000 mg/day. Many cold products also contain it."
        ),
        OtcMed(
            id = "naproxen",
            genericName = "Naproxen sodium",
            brandNames = listOf("Aleve"),
            defaultDose = "220", unit = "mg",
            minIntervalMinutes = 8 * 60,
            maxPer24h = 660, maxIsDoses = false,
            notes = "One tablet every 8-12 hours."
        ),
        OtcMed(
            id = "aspirin",
            genericName = "Aspirin",
            brandNames = listOf("Bayer", "Ecotrin", "Bufferin"),
            defaultDose = "325", unit = "mg",
            minIntervalMinutes = 4 * 60,
            maxPer24h = 4000, maxIsDoses = false,
            notes = "Not for children or teens."
        ),
        OtcMed(
            id = "diphenhydramine",
            genericName = "Diphenhydramine",
            brandNames = listOf("Benadryl"),
            defaultDose = "25", unit = "mg",
            minIntervalMinutes = 4 * 60,
            maxPer24h = 300, maxIsDoses = false,
            notes = "Causes drowsiness."
        ),
        OtcMed(
            id = "loratadine",
            genericName = "Loratadine",
            brandNames = listOf("Claritin"),
            defaultDose = "10", unit = "mg",
            minIntervalMinutes = 24 * 60,
            maxPer24h = 1, maxIsDoses = true
        ),
        OtcMed(
            id = "cetirizine",
            genericName = "Cetirizine",
            brandNames = listOf("Zyrtec"),
            defaultDose = "10", unit = "mg",
            minIntervalMinutes = 24 * 60,
            maxPer24h = 1, maxIsDoses = true
        ),
        OtcMed(
            id = "famotidine",
            genericName = "Famotidine",
            brandNames = listOf("Pepcid"),
            defaultDose = "10", unit = "mg",
            minIntervalMinutes = 12 * 60,
            maxPer24h = 2, maxIsDoses = true,
            notes = "Up to 2 doses in 24 hours."
        ),
        OtcMed(
            id = "guaifenesin",
            genericName = "Guaifenesin",
            brandNames = listOf("Mucinex"),
            defaultDose = "400", unit = "mg",
            minIntervalMinutes = 4 * 60,
            maxPer24h = 2400, maxIsDoses = false,
            notes = "Extended-release 12-hour tablets: use a 12 h interval and 2 doses max."
        ),
        OtcMed(
            id = "dextromethorphan",
            genericName = "Dextromethorphan",
            brandNames = listOf("Robitussin DM", "Delsym"),
            defaultDose = "20", unit = "mg",
            minIntervalMinutes = 4 * 60,
            maxPer24h = 120, maxIsDoses = false,
            notes = "Delsym (extended release) is every 12 hours."
        )
    )

    fun byId(id: String?): OtcMed? = meds.firstOrNull { it.id == id }

    fun search(query: String): List<OtcMed> = meds.filter { it.matches(query) }
}
