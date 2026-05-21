package com.jagger.timer.core.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jagger.timer.core.data.local.AppDatabase
import com.jagger.timer.core.data.local.dao.PillDao
import com.jagger.timer.core.data.local.dao.TimerDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `timers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `durationSeconds` INTEGER NOT NULL, `intervalSeconds` INTEGER, `type` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `color` INTEGER NOT NULL, `icon` TEXT, `startTimeMillis` INTEGER)"
            )
        }
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "jaggernautimer_db"
        )
        .addMigrations(MIGRATION_1_2)
        .build()
    }

    @Provides
    fun providePillDao(database: AppDatabase): PillDao {
        return database.pillDao()
    }

    @Provides
    fun provideTimerDao(database: AppDatabase): TimerDao {
        return database.timerDao()
    }
}
