package com.jagger.timer.core.data.local.dao

import com.jagger.timer.core.data.local.entity.DoseStatus

/** One logged dose joined with its medication, for the History screen (archived medications included). */
data class DoseHistoryRow(
    val logId: Long,
    val pillId: Long,
    val pillName: String,
    val pillUnit: String,
    val isArchived: Boolean,
    val timestamp: Long,
    val status: DoseStatus,
    val amount: Double?,
    val note: String?
)
