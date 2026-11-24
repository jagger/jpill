package com.jagger.timer.core.di

import android.content.Context
import androidx.room.Room
import com.jagger.timer.core.data.local.AppDatabase
import com.jagger.timer.core.data.local.dao.PillDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "jaggernautimer_db"
        ).addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5).build()
    }

    @Provides
    fun provideTimerDao(database: AppDatabase) = database.timerDao()

    @Provides
    fun providePillDao(database: AppDatabase): PillDao {
        return database.pillDao()
    }
}
