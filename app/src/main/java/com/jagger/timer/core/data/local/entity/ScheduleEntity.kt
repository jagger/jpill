package com.jagger.timer.core.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ScheduleType {
    FIXED_TIME,    // e.g., 8:00 AM
    TIME_SLOT,     // e.g., "Morning"
    INTERVAL       // e.g., Every 8 hours
}

enum class RecurrencePattern {
    DAILY,
    WEEKLY,        // Days of week
    MONTHLY,       // Day of month
    NTH_WEEKDAY    // e.g., 3rd Wednesday
}

@Entity(
    tableName = "schedules",
    foreignKeys = [
        ForeignKey(
            entity = PillEntity::class,
            parentColumns = ["id"],
            childColumns = ["pillId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("pillId")]
)
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val pillId: Long,
    val type: ScheduleType,
    val pattern: RecurrencePattern,
    
    // For FIXED_TIME (Minutes from midnight, e.g., 480 = 8:00 AM)
    val timeMinutes: Int? = null,
    
    // For TIME_SLOT (e.g., "Morning", "Afternoon", "Night")
    val timeSlotName: String? = null,
    
    // For INTERVAL (In minutes, e.g., 480 = 8 hours)
    val intervalMinutes: Int? = null,
    
    // For WEEKLY (Binary mask or comma-separated days, e.g., "1,3,5" for Mon,Wed,Fri)
    val daysOfWeek: String? = null,
    
    // For MONTHLY (Day of month 1-31)
    val dayOfMonth: Int? = null,
    
    // For NTH_WEEKDAY (e.g., 3 for "3rd", and day index)
    val nthOccurrence: Int? = null,
    val dayOfWeek: Int? = null,
    
    val reminderAdvanceMinutes: Int = 30, // Default 30 min before
    val isCritical: Boolean = true        // Non-dismissable
)
