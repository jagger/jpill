package com.jagger.timer.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TimerType {
    COUNTDOWN,
    REPEATING
}

@Entity(tableName = "timers")
data class TimerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val durationSeconds: Int,
    val intervalSeconds: Int?, // For repeating timers
    val type: TimerType,
    val isActive: Boolean = false,
    val color: Int,
    val icon: String? = null,
    val startTimeMillis: Long? = null // When the timer was started
)
