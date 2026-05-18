package com.jagger.timer.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pills")
data class PillEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val dosage: String,
    val unit: String, // e.g., "mg", "pill", "ml"
    val color: Int,   // ARGB color for UI
    val icon: String? = null // Resource name or icon identifier
)
