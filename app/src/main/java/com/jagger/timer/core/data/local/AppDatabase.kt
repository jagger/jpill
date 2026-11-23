package com.jagger.timer.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pillDao(): PillDao

    companion object {
        /** v1 -> v2: as-needed pills with cooldown settings, and per-dose amounts in the log. Existing rows keep working. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pills ADD COLUMN asNeeded INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE pills ADD COLUMN minIntervalMinutes INTEGER")
                db.execSQL("ALTER TABLE pills ADD COLUMN maxPer24h INTEGER")
                db.execSQL("ALTER TABLE pills ADD COLUMN maxIsDoses INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE pills ADD COLUMN catalogId TEXT")
                db.execSQL("ALTER TABLE dose_logs ADD COLUMN amount REAL")
            }
        }
    }
}
