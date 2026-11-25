// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TimerState { RUNNING, PAUSED, FINISHED }

/**
 * A general-purpose countdown timer (DB v5). While RUNNING the truth is [endAtMillis] (wall clock, so it survives
 * process death and reboot); while PAUSED it is [remainingMillis].
 */
@Entity(tableName = "timers")
data class TimerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val durationMillis: Long,
    val state: TimerState,
    val endAtMillis: Long? = null,
    val remainingMillis: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
