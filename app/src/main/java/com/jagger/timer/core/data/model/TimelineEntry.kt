package com.jagger.timer.core.data.model

import com.jagger.timer.core.data.local.entity.PillEntity
import com.jagger.timer.core.data.local.entity.ScheduleEntity
import java.time.LocalDateTime

data class TimelineEntry(
    val pill: PillEntity,
    val schedule: ScheduleEntity,
    val dueTime: LocalDateTime
)
