// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.core.data.model

import com.jagger.timer.core.data.local.entity.DoseStatus
import com.jagger.timer.core.data.local.entity.PillEntity
import com.jagger.timer.core.data.local.entity.ScheduleEntity
import java.time.LocalDateTime

data class TimelineEntry(
    val pill: PillEntity,
    val schedule: ScheduleEntity,
    val dueTime: LocalDateTime,
    /** Set when this dose was already taken/skipped (possibly early): the card is greyed out and can't be logged again. */
    val handledStatus: DoseStatus? = null,
    val handledAt: Long? = null
) {
    val isHandled: Boolean get() = handledStatus != null
}
