package com.jagger.timer.core.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "dose_logs",
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
data class DoseLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val pillId: Long,
    val scheduleId: Long? = null, // Link to schedule if it was a scheduled dose
    val timestamp: Long,          // When it was taken/skipped
    val status: DoseStatus,
    val note: String? = null,
    /** Amount taken, in the pill's unit at the time of logging. Null for logs from before v2. */
    val amount: Double? = null
)

enum class DoseStatus {
    TAKEN,
    SKIPPED,
    MISSED
}
