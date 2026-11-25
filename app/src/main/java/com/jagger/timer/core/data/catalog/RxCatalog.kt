// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.core.data.catalog

import com.jagger.timer.core.data.local.entity.RecurrencePattern
import com.jagger.timer.core.data.local.entity.ScheduleType

/** How often a prescription is *typically* taken. Only ever suggests a schedule, never a dose or limit. */
enum class RxFrequency(val label: String, val dosesPerDay: Double) {
    ONCE_DAILY("Once daily", 1.0),
    ONCE_DAILY_EVENING("Once daily, evening", 1.0),
    BEDTIME("Once daily, at bedtime", 1.0),
    TWICE_DAILY("Twice daily", 2.0),
    THREE_TIMES_DAILY("Three times daily", 3.0),
    FOUR_TIMES_DAILY("Four times daily", 4.0),
    WEEKLY("Once weekly", 1.0 / 7.0),
    AS_NEEDED("As needed", 0.0);

    /** Schedule form defaults for this frequency, or null for [AS_NEEDED] (uses the cooldown timer instead). */
    fun scheduleDefaults(): ScheduleDefaults? = when (this) {
        ONCE_DAILY -> ScheduleDefaults(ScheduleType.TIME_SLOT, timeSlotName = "Morning")
        ONCE_DAILY_EVENING -> ScheduleDefaults(ScheduleType.TIME_SLOT, timeSlotName = "Evening")
        BEDTIME -> ScheduleDefaults(ScheduleType.TIME_SLOT, timeSlotName = "Night")
        TWICE_DAILY -> ScheduleDefaults(ScheduleType.INTERVAL, intervalMinutes = 12 * 60)
        THREE_TIMES_DAILY -> ScheduleDefaults(ScheduleType.INTERVAL, intervalMinutes = 8 * 60)
        FOUR_TIMES_DAILY -> ScheduleDefaults(ScheduleType.INTERVAL, intervalMinutes = 6 * 60)
        WEEKLY -> ScheduleDefaults(
            ScheduleType.FIXED_TIME, pattern = RecurrencePattern.WEEKLY,
            timeMinutes = 8 * 60, daysOfWeek = listOf(1)
        )
        AS_NEEDED -> null
    }
}

data class ScheduleDefaults(
    val type: ScheduleType,
    val pattern: RecurrencePattern = RecurrencePattern.DAILY,
    val timeMinutes: Int? = null,
    val intervalMinutes: Int? = null,
    val timeSlotName: String? = null,
    val daysOfWeek: List<Int>? = null
)

/**
 * A curated prescription medicine. Suggests names, brands, common strengths, a typical frequency and a
 * food/timing note. It NEVER supplies a dose, a daily maximum or a minimum interval: those come from the
 * user's own prescription.
 *
 * UNVERIFIED: strengths, frequencies and notes are typical adult values written from general knowledge and
 * must be reviewed against current labeling (DailyMed) before relying on them.
 */
data class RxMed(
    val id: String,
    val genericName: String,
    val brandNames: List<String>,
    val strengths: List<String>,
    val frequency: RxFrequency,
    val instructions: String? = null,
    /** Lower-case active ingredient names (used later for duplicate-ingredient checks). */
    val ingredients: List<String>
) {
    val displayName: String
        get() = if (brandNames.isEmpty()) genericName else "$genericName (${brandNames.joinToString(", ")})"

    fun matches(query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        return genericName.contains(q, ignoreCase = true) || brandNames.any { it.contains(q, ignoreCase = true) }
    }
}

object RxCatalog {

    private fun rx(
        generic: String,
        brands: List<String>,
        strengths: List<String>,
        freq: RxFrequency,
        note: String? = null,
        ingredients: List<String>? = null,
        id: String = generic.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
    ) = RxMed(id, generic, brands, strengths, freq, note, ingredients ?: listOf(generic.lowercase()))

    private val D = RxFrequency.ONCE_DAILY
    private val E = RxFrequency.ONCE_DAILY_EVENING
    private val N = RxFrequency.BEDTIME
    private val B = RxFrequency.TWICE_DAILY
    private val T = RxFrequency.THREE_TIMES_DAILY
    private val Q = RxFrequency.FOUR_TIMES_DAILY
    private val W = RxFrequency.WEEKLY
    private val P = RxFrequency.AS_NEEDED

    val meds: List<RxMed> = listOf(
        // --- Cholesterol ---
        rx("Atorvastatin", listOf("Lipitor"), listOf("10 mg", "20 mg", "40 mg", "80 mg"), D, "Limit grapefruit juice"),
        rx("Rosuvastatin", listOf("Crestor"), listOf("5 mg", "10 mg", "20 mg", "40 mg"), D),
        rx("Simvastatin", listOf("Zocor"), listOf("10 mg", "20 mg", "40 mg", "80 mg"), E, "Evening. Limit grapefruit juice"),
        rx("Pravastatin", listOf("Pravachol"), listOf("10 mg", "20 mg", "40 mg", "80 mg"), D),
        rx("Lovastatin", listOf("Mevacor"), listOf("10 mg", "20 mg", "40 mg"), E, "With the evening meal"),
        rx("Ezetimibe", listOf("Zetia"), listOf("10 mg"), D),
        rx("Fenofibrate", listOf("Tricor", "Fenoglide"), listOf("48 mg", "54 mg", "145 mg", "160 mg"), D),
        rx("Gemfibrozil", listOf("Lopid"), listOf("600 mg"), B, "30 minutes before meals"),
        // --- Blood pressure / heart ---
        rx("Lisinopril", listOf("Prinivil", "Zestril"), listOf("2.5 mg", "5 mg", "10 mg", "20 mg", "40 mg"), D),
        rx("Enalapril", listOf("Vasotec"), listOf("2.5 mg", "5 mg", "10 mg", "20 mg"), D),
        rx("Ramipril", listOf("Altace"), listOf("1.25 mg", "2.5 mg", "5 mg", "10 mg"), D),
        rx("Benazepril", listOf("Lotensin"), listOf("5 mg", "10 mg", "20 mg", "40 mg"), D),
        rx("Losartan", listOf("Cozaar"), listOf("25 mg", "50 mg", "100 mg"), D),
        rx("Valsartan", listOf("Diovan"), listOf("40 mg", "80 mg", "160 mg", "320 mg"), D),
        rx("Olmesartan", listOf("Benicar"), listOf("5 mg", "20 mg", "40 mg"), D),
        rx("Irbesartan", listOf("Avapro"), listOf("75 mg", "150 mg", "300 mg"), D),
        rx("Telmisartan", listOf("Micardis"), listOf("20 mg", "40 mg", "80 mg"), D),
        rx("Amlodipine", listOf("Norvasc"), listOf("2.5 mg", "5 mg", "10 mg"), D),
        rx("Diltiazem", listOf("Cardizem", "Cardizem CD"), listOf("120 mg", "180 mg", "240 mg", "300 mg"), D),
        rx("Metoprolol tartrate", listOf("Lopressor"), listOf("25 mg", "50 mg", "100 mg"), B, "With or right after food", listOf("metoprolol")),
        rx("Metoprolol succinate", listOf("Toprol-XL"), listOf("25 mg", "50 mg", "100 mg", "200 mg"), D, "Swallow whole, do not crush", listOf("metoprolol")),
        rx("Carvedilol", listOf("Coreg"), listOf("3.125 mg", "6.25 mg", "12.5 mg", "25 mg"), B, "With food"),
        rx("Atenolol", listOf("Tenormin"), listOf("25 mg", "50 mg", "100 mg"), D),
        rx("Propranolol", listOf("Inderal"), listOf("10 mg", "20 mg", "40 mg", "80 mg"), B),
        rx("Bisoprolol", listOf("Zebeta"), listOf("2.5 mg", "5 mg", "10 mg"), D),
        rx("Hydrochlorothiazide", listOf("Microzide"), listOf("12.5 mg", "25 mg", "50 mg"), D, "Morning"),
        rx("Chlorthalidone", listOf("Thalitone"), listOf("25 mg", "50 mg"), D, "Morning"),
        rx("Furosemide", listOf("Lasix"), listOf("20 mg", "40 mg", "80 mg"), D, "Morning"),
        rx("Spironolactone", listOf("Aldactone"), listOf("25 mg", "50 mg", "100 mg"), D),
        rx("Clonidine", listOf("Catapres"), listOf("0.1 mg", "0.2 mg", "0.3 mg"), B),
        rx("Hydralazine", listOf("Apresoline"), listOf("10 mg", "25 mg", "50 mg", "100 mg"), T),
        rx("Isosorbide mononitrate", listOf("Imdur", "Ismo"), listOf("20 mg", "30 mg", "60 mg", "120 mg"), D, "Morning"),
        rx("Digoxin", listOf("Lanoxin"), listOf("0.125 mg", "0.25 mg"), D),
        rx("Warfarin", listOf("Coumadin", "Jantoven"), listOf("1 mg", "2 mg", "2.5 mg", "3 mg", "4 mg", "5 mg", "6 mg", "7.5 mg", "10 mg"), E, "Same time each day; keep vitamin K intake steady"),
        rx("Apixaban", listOf("Eliquis"), listOf("2.5 mg", "5 mg"), B),
        rx("Rivaroxaban", listOf("Xarelto"), listOf("2.5 mg", "10 mg", "15 mg", "20 mg"), D, "15 mg and 20 mg: with the evening meal"),
        rx("Clopidogrel", listOf("Plavix"), listOf("75 mg"), D),
        // --- Diabetes / thyroid / hormones ---
        rx("Metformin", listOf("Glucophage", "Glumetza"), listOf("500 mg", "850 mg", "1000 mg"), B, "With meals"),
        rx("Glipizide", listOf("Glucotrol"), listOf("2.5 mg", "5 mg", "10 mg"), D, "30 minutes before a meal"),
        rx("Glimepiride", listOf("Amaryl"), listOf("1 mg", "2 mg", "4 mg"), D, "With breakfast"),
        rx("Glyburide", listOf("Diabeta", "Micronase"), listOf("1.25 mg", "2.5 mg", "5 mg"), D, "With breakfast"),
        rx("Pioglitazone", listOf("Actos"), listOf("15 mg", "30 mg", "45 mg"), D),
        rx("Sitagliptin", listOf("Januvia"), listOf("25 mg", "50 mg", "100 mg"), D),
        rx("Linagliptin", listOf("Tradjenta"), listOf("5 mg"), D),
        rx("Empagliflozin", listOf("Jardiance"), listOf("10 mg", "25 mg"), D, "Morning"),
        rx("Dapagliflozin", listOf("Farxiga"), listOf("5 mg", "10 mg"), D, "Morning"),
        rx("Levothyroxine", listOf("Synthroid", "Levoxyl", "Unithroid", "Euthyrox"), listOf("25 mcg", "50 mcg", "75 mcg", "88 mcg", "100 mcg", "112 mcg", "125 mcg", "137 mcg", "150 mcg", "175 mcg", "200 mcg"), D, "Empty stomach, 30-60 minutes before breakfast"),
        rx("Methimazole", listOf("Tapazole"), listOf("5 mg", "10 mg"), D),
        rx("Prednisone", listOf("Deltasone", "Rayos"), listOf("1 mg", "2.5 mg", "5 mg", "10 mg", "20 mg", "50 mg"), D, "With food. Follow your prescriber's exact taper"),
        rx("Methylprednisolone", listOf("Medrol"), listOf("4 mg", "8 mg", "16 mg", "32 mg"), D, "With food. Follow your prescriber's exact taper"),
        rx("Alendronate", listOf("Fosamax"), listOf("10 mg", "35 mg", "70 mg"), W, "First thing in the morning with a full glass of water; stay upright 30 minutes"),
        rx("Estradiol", listOf("Estrace"), listOf("0.5 mg", "1 mg", "2 mg"), D),
        rx("Finasteride", listOf("Proscar", "Propecia"), listOf("1 mg", "5 mg"), D),
        rx("Tamsulosin", listOf("Flomax"), listOf("0.4 mg"), D, "30 minutes after the same meal each day"),
        rx("Allopurinol", listOf("Zyloprim"), listOf("100 mg", "300 mg"), D, "After a meal, with plenty of water"),
        rx("Colchicine", listOf("Colcrys", "Mitigare"), listOf("0.6 mg"), P),
        rx("Potassium chloride", listOf("Klor-Con", "K-Tab"), listOf("8 mEq", "10 mEq", "20 mEq"), D, "With food and a full glass of water; swallow whole"),
        // --- Mental health / sleep ---
        rx("Sertraline", listOf("Zoloft"), listOf("25 mg", "50 mg", "100 mg"), D),
        rx("Escitalopram", listOf("Lexapro"), listOf("5 mg", "10 mg", "20 mg"), D),
        rx("Citalopram", listOf("Celexa"), listOf("10 mg", "20 mg", "40 mg"), D),
        rx("Fluoxetine", listOf("Prozac"), listOf("10 mg", "20 mg", "40 mg"), D, "Morning"),
        rx("Paroxetine", listOf("Paxil"), listOf("10 mg", "20 mg", "30 mg", "40 mg"), D),
        rx("Bupropion", listOf("Wellbutrin", "Wellbutrin XL", "Wellbutrin SR"), listOf("75 mg", "100 mg", "150 mg", "300 mg"), D, "Morning; swallow extended-release whole"),
        rx("Venlafaxine", listOf("Effexor XR"), listOf("37.5 mg", "75 mg", "150 mg"), D, "With food"),
        rx("Duloxetine", listOf("Cymbalta"), listOf("20 mg", "30 mg", "60 mg"), D),
        rx("Trazodone", listOf("Desyrel"), listOf("50 mg", "100 mg", "150 mg"), N),
        rx("Mirtazapine", listOf("Remeron"), listOf("7.5 mg", "15 mg", "30 mg", "45 mg"), N),
        rx("Amitriptyline", listOf("Elavil"), listOf("10 mg", "25 mg", "50 mg"), N),
        rx("Buspirone", listOf("Buspar"), listOf("5 mg", "7.5 mg", "10 mg", "15 mg", "30 mg"), B),
        rx("Quetiapine", listOf("Seroquel"), listOf("25 mg", "50 mg", "100 mg", "200 mg", "300 mg"), N),
        rx("Aripiprazole", listOf("Abilify"), listOf("2 mg", "5 mg", "10 mg", "15 mg", "20 mg", "30 mg"), D),
        rx("Risperidone", listOf("Risperdal"), listOf("0.5 mg", "1 mg", "2 mg", "3 mg", "4 mg"), D),
        rx("Lithium", listOf("Lithobid"), listOf("150 mg", "300 mg", "450 mg"), B, "Keep fluid and salt intake consistent"),
        rx("Lorazepam", listOf("Ativan"), listOf("0.5 mg", "1 mg", "2 mg"), P),
        rx("Alprazolam", listOf("Xanax"), listOf("0.25 mg", "0.5 mg", "1 mg", "2 mg"), P),
        rx("Clonazepam", listOf("Klonopin"), listOf("0.5 mg", "1 mg", "2 mg"), B),
        rx("Zolpidem", listOf("Ambien"), listOf("5 mg", "10 mg"), P, "Only when you can stay in bed 7-8 hours"),
        // --- Neurology / pain ---
        rx("Lamotrigine", listOf("Lamictal"), listOf("25 mg", "100 mg", "150 mg", "200 mg"), D),
        rx("Levetiracetam", listOf("Keppra"), listOf("250 mg", "500 mg", "750 mg", "1000 mg"), B),
        rx("Topiramate", listOf("Topamax"), listOf("25 mg", "50 mg", "100 mg", "200 mg"), B),
        rx("Gabapentin", listOf("Neurontin"), listOf("100 mg", "300 mg", "400 mg", "600 mg", "800 mg"), T),
        rx("Donepezil", listOf("Aricept"), listOf("5 mg", "10 mg", "23 mg"), N),
        rx("Baclofen", listOf("Lioresal"), listOf("5 mg", "10 mg", "20 mg"), T),
        rx("Cyclobenzaprine", listOf("Flexeril"), listOf("5 mg", "10 mg"), P),
        rx("Tizanidine", listOf("Zanaflex"), listOf("2 mg", "4 mg"), P),
        rx("Meloxicam", listOf("Mobic"), listOf("7.5 mg", "15 mg"), D, "With food"),
        rx("Naproxen (prescription)", listOf("Naprosyn", "Anaprox DS"), listOf("250 mg", "375 mg", "500 mg", "550 mg"), B, "With food", listOf("naproxen"), id = "naproxen_rx"),
        rx("Celecoxib", listOf("Celebrex"), listOf("100 mg", "200 mg"), D, "With food"),
        rx("Tramadol", listOf("Ultram"), listOf("50 mg", "100 mg"), P),
        rx("Hydrocodone/Acetaminophen", listOf("Norco", "Vicodin"), listOf("5 mg / 325 mg", "7.5 mg / 325 mg", "10 mg / 325 mg"), P, "Contains acetaminophen; count it toward your daily total", listOf("hydrocodone", "acetaminophen")),
        rx("Oxycodone", listOf("Roxicodone"), listOf("5 mg", "10 mg", "15 mg", "30 mg"), P),
        rx("Sumatriptan", listOf("Imitrex"), listOf("25 mg", "50 mg", "100 mg"), P),
        rx("Rizatriptan", listOf("Maxalt"), listOf("5 mg", "10 mg"), P),
        rx("Ondansetron", listOf("Zofran"), listOf("4 mg", "8 mg"), P),
        // --- Stomach / allergy / lungs ---
        rx("Omeprazole", listOf("Prilosec"), listOf("10 mg", "20 mg", "40 mg"), D, "30-60 minutes before a meal"),
        rx("Pantoprazole", listOf("Protonix"), listOf("20 mg", "40 mg"), D, "Swallow whole"),
        rx("Esomeprazole", listOf("Nexium"), listOf("20 mg", "40 mg"), D, "At least 1 hour before a meal"),
        rx("Famotidine (prescription)", listOf("Pepcid"), listOf("20 mg", "40 mg"), B, null, listOf("famotidine"), id = "famotidine_rx"),
        rx("Montelukast", listOf("Singulair"), listOf("4 mg", "5 mg", "10 mg"), E),
        rx("Benzonatate", listOf("Tessalon Perles"), listOf("100 mg", "200 mg"), T, "Swallow whole"),
        // --- Infection ---
        rx("Amoxicillin", listOf("Amoxil"), listOf("250 mg", "500 mg", "875 mg"), B),
        rx("Amoxicillin/Clavulanate", listOf("Augmentin"), listOf("500 mg / 125 mg", "875 mg / 125 mg"), B, "At the start of a meal", listOf("amoxicillin", "clavulanate")),
        rx("Azithromycin", listOf("Zithromax", "Z-Pak"), listOf("250 mg", "500 mg"), D),
        rx("Ciprofloxacin", listOf("Cipro"), listOf("250 mg", "500 mg", "750 mg"), B, "Avoid dairy, antacids and iron within 2 hours"),
        rx("Levofloxacin", listOf("Levaquin"), listOf("250 mg", "500 mg", "750 mg"), D, "Avoid dairy, antacids and iron within 2 hours"),
        rx("Doxycycline", listOf("Vibramycin", "Doryx"), listOf("50 mg", "100 mg"), B, "Full glass of water; stay upright. Avoid dairy and antacids nearby"),
        rx("Cephalexin", listOf("Keflex"), listOf("250 mg", "500 mg"), Q),
        rx("Sulfamethoxazole/Trimethoprim", listOf("Bactrim", "Bactrim DS", "Septra"), listOf("400 mg / 80 mg", "800 mg / 160 mg"), B, "Drink plenty of water", listOf("sulfamethoxazole", "trimethoprim")),
        rx("Nitrofurantoin", listOf("Macrobid"), listOf("100 mg"), B, "With food"),
        rx("Metronidazole", listOf("Flagyl"), listOf("250 mg", "500 mg"), T, "Avoid alcohol"),
        rx("Fluconazole", listOf("Diflucan"), listOf("50 mg", "100 mg", "150 mg", "200 mg"), D),
        rx("Valacyclovir", listOf("Valtrex"), listOf("500 mg", "1 g"), B),
        rx("Acyclovir", listOf("Zovirax"), listOf("400 mg", "800 mg"), B),
        rx("Oseltamivir", listOf("Tamiflu"), listOf("30 mg", "45 mg", "75 mg"), B, "With food"),
        // --- Other ---
        rx("Sildenafil", listOf("Viagra", "Revatio"), listOf("20 mg", "25 mg", "50 mg", "100 mg"), P),
        rx("Tadalafil", listOf("Cialis", "Adcirca"), listOf("2.5 mg", "5 mg", "10 mg", "20 mg"), D),
        rx("Oxybutynin", listOf("Ditropan"), listOf("5 mg"), B),
        rx("Hydroxychloroquine", listOf("Plaquenil"), listOf("200 mg"), B, "With food"),
        rx("Folic acid", listOf("Folvite"), listOf("1 mg"), D)
    )

    fun byId(id: String?): RxMed? = meds.firstOrNull { it.id == id }

    fun search(query: String): List<RxMed> = meds.filter { it.matches(query) }
}
