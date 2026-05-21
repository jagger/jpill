package com.jagger.timer.core.data.local.dao

import androidx.room.*
import com.jagger.timer.core.data.local.entity.TimerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TimerDao {
    @Query("SELECT * FROM timers")
    fun getAllTimers(): Flow<List<TimerEntity>>

    @Query("SELECT * FROM timers WHERE id = :id")
    suspend fun getTimerById(id: Long): TimerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTimer(timer: TimerEntity): Long

    @Update
    suspend fun updateTimer(timer: TimerEntity)

    @Delete
    suspend fun deleteTimer(timer: TimerEntity)
}
