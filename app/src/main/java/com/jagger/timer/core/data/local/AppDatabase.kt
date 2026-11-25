// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jagger.timer.core.data.local.dao.PillDao
import com.jagger.timer.core.data.local.dao.TimerDao
import com.jagger.timer.core.data.local.entity.TimerEntity
import com.jagger.timer.core.data.local.entity.DoseLogEntity
import com.jagger.timer.core.data.local.entity.PillEntity
import com.jagger.timer.core.data.local.entity.ScheduleEntity

@Database(
    entities = [
        PillEntity::class,
        ScheduleEntity::class,
        DoseLogEntity::class,
        TimerEntity::class
    ],
    version = 6,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pillDao(): PillDao
    abstract fun timerDao(): TimerDao

    companion object {
        /** v5 -> v6: active ingredients on pills, for duplicate-ingredient warnings. Existing pills get none. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pills ADD COLUMN ingredients TEXT")
            }
        }

        /** v4 -> v5: general-purpose countdown timers (new table only; nothing existing is touched). */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS timers (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "label TEXT NOT NULL, durationMillis INTEGER NOT NULL, state TEXT NOT NULL, " +
                        "endAtMillis INTEGER, remainingMillis INTEGER, createdAt INTEGER NOT NULL)"
                )
            }
        }

        /** v3 -> v4: archiving (hide a pill and stop reminders without deleting its history). */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pills ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE pills ADD COLUMN archivedAt INTEGER")
            }
        }

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
