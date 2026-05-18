package com.jagger.timer.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.jagger.timer.core.data.local.dao.PillDao
import com.jagger.timer.core.data.local.entity.DoseLogEntity
import com.jagger.timer.core.data.local.entity.PillEntity
import com.jagger.timer.core.data.local.entity.ScheduleEntity

@Database(
    entities = [
        PillEntity::class,
        ScheduleEntity::class,
        DoseLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pillDao(): PillDao
}
