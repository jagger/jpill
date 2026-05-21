package com.jagger.timer.core.data.local.dao

import androidx.room.*
import com.jagger.timer.core.data.local.entity.DoseLogEntity
import com.jagger.timer.core.data.local.entity.PillEntity
import com.jagger.timer.core.data.local.entity.ScheduleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PillDao {
    @Query("SELECT * FROM pills")
    fun getAllPills(): Flow<List<PillEntity>>

    @Query("SELECT * FROM pills WHERE id = :id")
    suspend fun getPillById(id: Long): PillEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPill(pill: PillEntity): Long

    @Update
    suspend fun updatePill(pill: PillEntity)

    @Delete
    suspend fun deletePill(pill: PillEntity)

    // Schedule Queries
    @Query("SELECT * FROM schedules WHERE pillId = :pillId")
    fun getSchedulesForPill(pillId: Long): Flow<List<ScheduleEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSchedule(schedule: ScheduleEntity): Long

    @Update
    suspend fun updateSchedule(schedule: ScheduleEntity)

    @Transaction
    suspend fun savePillWithSchedule(pill: PillEntity, schedule: ScheduleEntity): Long {
        return if (pill.id == 0L) {
            val newPillId = insertPill(pill)
            insertSchedule(schedule.copy(pillId = newPillId))
            newPillId
        } else {
            updatePill(pill)
            if (schedule.id == 0L) insertSchedule(schedule) else updateSchedule(schedule)
            pill.id
        }
    }

    // Dose Log Queries
    @Query("SELECT * FROM dose_logs WHERE pillId = :pillId ORDER BY timestamp DESC")
    fun getLogsForPill(pillId: Long): Flow<List<DoseLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDoseLog(log: DoseLogEntity): Long

    @Query("SELECT * FROM dose_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogs(limit: Int): Flow<List<DoseLogEntity>>
}
