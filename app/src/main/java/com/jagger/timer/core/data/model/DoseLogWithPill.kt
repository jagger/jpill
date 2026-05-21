package com.jagger.timer.core.data.model

import androidx.room.Embedded
import androidx.room.Relation
import com.jagger.timer.core.data.local.entity.DoseLogEntity
import com.jagger.timer.core.data.local.entity.PillEntity

data class DoseLogWithPill(
    @Embedded val log: DoseLogEntity,
    @Relation(
        parentColumn = "pillId",
        entityColumn = "id"
    )
    val pill: PillEntity
)
