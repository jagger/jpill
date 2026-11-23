package com.jagger.timer.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Numeric dose from [PillEntity.dosage], or null if it isn't a plain number (e.g. "1/2"). */
fun PillEntity.doseAmount(): Double? = dosage.trim().toDoubleOrNull()

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
    /** Id of the OtcCatalog entry this pill was created from, if any. */
    val catalogId: String? = null
)
