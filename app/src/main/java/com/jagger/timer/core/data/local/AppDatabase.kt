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
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pillDao(): PillDao

    companion object {
        /** v2 -> v3: prescriptions (tablets per dose, supply/refills, notes, prescriber details). */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pills ADD COLUMN isPrescription INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE pills ADD COLUMN tabletsPerDose REAL NOT NULL DEFAULT 1.0")
                db.execSQL("ALTER TABLE pills ADD COLUMN supplyCount REAL")
                db.execSQL("ALTER TABLE pills ADD COLUMN refillThresholdDays INTEGER NOT NULL DEFAULT 7")
                db.execSQL("ALTER TABLE pills ADD COLUMN instructions TEXT")
                db.execSQL("ALTER TABLE pills ADD COLUMN prescriber TEXT")
                db.execSQL("ALTER TABLE pills ADD COLUMN pharmacy TEXT")
                db.execSQL("ALTER TABLE pills ADD COLUMN rxNumber TEXT")
            }
        }

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
