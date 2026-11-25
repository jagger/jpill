// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.core.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.Coronavirus
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.ui.graphics.vector.ImageVector

/** Colors, icons and units a medication can be given. Stored as plain values (ARGB Int, name String, unit String). */
object PillAppearance {

    /** Preset swatches (ARGB). The first is the default. */
    val colors: List<Int> = listOf(
        0xFF6200EE, 0xFF1E88E5, 0xFF00897B, 0xFF43A047, 0xFFF9A825, 0xFFFB8C00,
        0xFFE53935, 0xFFD81B60, 0xFF8E24AA, 0xFF6D4C41, 0xFF546E7A, 0xFF212121
    ).map { it.toInt() }

    private val colorNames = listOf("Indigo", "Blue", "Teal", "Green", "Amber", "Orange", "Red", "Pink", "Purple", "Brown", "Slate", "Black")

    /** Spoken name for a swatch, so TalkBack doesn't just say "button". */
    fun colorName(color: Int): String = colors.indexOf(color).let { if (it in colorNames.indices) colorNames[it] else "Custom" }

    val defaultColor: Int = colors.first()

    /** Icon name (stored in `PillEntity.icon`) to vector. Names are stable: never rename one. */
    val icons: LinkedHashMap<String, ImageVector> = linkedMapOf(
        "medication" to Icons.Default.Medication,
        "vaccines" to Icons.Default.Vaccines,
        "liquid" to Icons.Default.Science,
        "pharmacy" to Icons.Default.LocalPharmacy,
        "medical" to Icons.Default.MedicalServices,
        "heart" to Icons.Default.Favorite,
        "drop" to Icons.Default.WaterDrop,
        "blood" to Icons.Default.Bloodtype,
        "sleep" to Icons.Default.Bedtime,
        "mind" to Icons.Default.Psychology,
        "food" to Icons.Default.Restaurant,
        "virus" to Icons.Default.Coronavirus
    )

    /** Icon for a stored name; null or an unknown name falls back to the pill icon. */
    fun icon(name: String?): ImageVector = icons[name] ?: Icons.Default.Medication

    val units: List<String> = listOf("mg", "ml", "pill", "IU", "mcg", "other")

    /**
     * Units shown in the dropdown: the presets plus [current] when it is something else
     * (catalog picks and strength chips can set units like "g" or "%"), so it still displays.
     */
    fun unitOptions(current: String): List<String> =
        if (current.isBlank() || current in units) units else units.dropLast(1) + current + units.last()
}
