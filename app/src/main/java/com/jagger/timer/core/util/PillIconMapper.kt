package com.jagger.timer.core.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

object PillIconMapper {
    private val iconMap = mapOf(
        "Medication" to Icons.Default.Medication,
        "Vaccines" to Icons.Default.Vaccines,
        "Science" to Icons.Default.Science,
        "Favorite" to Icons.Default.Favorite,
        "Healing" to Icons.Default.Healing,
        "WaterDrop" to Icons.Default.WaterDrop,
        "Sick" to Icons.Default.Sick,
        "Spa" to Icons.Default.Spa,
        "LocalPharmacy" to Icons.Default.LocalPharmacy,
        "HealthAndSafety" to Icons.Default.HealthAndSafety,
        "MonitorHeart" to Icons.Default.MonitorHeart,
        "MedicalServices" to Icons.Default.MedicalServices
    )

    fun getIcon(name: String?): ImageVector {
        return iconMap[name] ?: Icons.Default.Medication
    }

    val availableIcons = iconMap.keys.toList()
}
