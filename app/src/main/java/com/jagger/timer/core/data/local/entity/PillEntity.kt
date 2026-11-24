package com.jagger.timer.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Amount taken per dose in [PillEntity.unit]: strength ([PillEntity.dosage]) x [PillEntity.tabletsPerDose].
 * Null if the strength isn't a plain number (e.g. "1/2").
 */
fun PillEntity.doseAmount(): Double? = dosage.trim().toDoubleOrNull()?.let { it * tabletsPerDose }

@Entity(tableName = "pills")
data class PillEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val dosage: String,
    val unit: String, // e.g., "mg", "pill", "ml"
    val color: Int,   // ARGB color for UI
    val icon: String? = null, // Resource name or icon identifier
    /** As-needed (OTC-style) pill: no schedule/alarms, driven by a cooldown timer instead. */
    @ColumnInfo(defaultValue = "0")
    val asNeeded: Boolean = false,
    /** Minimum wait between doses, in minutes (as-needed pills). */
    val minIntervalMinutes: Int? = null,
    /** Rolling 24h cap: dose count if [maxIsDoses], else total amount in [unit]. */
    val maxPer24h: Int? = null,
    @ColumnInfo(defaultValue = "0")
    val maxIsDoses: Boolean = false,
    /** Id of the catalog entry (OTC or prescription) this pill was created from, if any. */
    val catalogId: String? = null,

    // --- Prescriptions (DB v3) ---
    @ColumnInfo(defaultValue = "0")
    val isPrescription: Boolean = false,
    /** Tablets/units taken per dose. [dosage] is the strength of one tablet. */
    @ColumnInfo(defaultValue = "1.0")
    val tabletsPerDose: Double = 1.0,
    /** Tablets/units on hand; null = not tracking supply. Decremented when a dose is logged as taken. */
    val supplyCount: Double? = null,
    /** Send the refill reminder when this many days of supply remain. */
    @ColumnInfo(defaultValue = "7")
    val refillThresholdDays: Int = 7,
    /** Food/timing notes, e.g. "With food". */
    val instructions: String? = null,
    val prescriber: String? = null,
    val pharmacy: String? = null,
    val rxNumber: String? = null,

    // --- Archive (DB v4) ---
    /** Archived pills are hidden and stop all reminders, but keep their schedules and dose history. */
    @ColumnInfo(defaultValue = "0")
    val isArchived: Boolean = false,
    val archivedAt: Long? = null,

    // --- Ingredients (DB v6) ---
    /** Normalized active ingredients joined by "|" (e.g. "acetaminophen|dextromethorphan"); null = unknown. */
    val ingredients: String? = null
)
