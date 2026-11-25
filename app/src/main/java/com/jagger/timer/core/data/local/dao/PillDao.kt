// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.core.data.local.dao

import androidx.room.*
import com.jagger.timer.core.data.local.entity.DoseLogEntity
import com.jagger.timer.core.data.local.entity.DoseStatus
import com.jagger.timer.core.util.SupplyCalculator
import com.jagger.timer.core.data.local.entity.PillEntity
import com.jagger.timer.core.data.local.entity.ScheduleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PillDao {
    /** Active (non-archived) pills: what the home screen, alarms and boot restore work with. */
    @Query("SELECT * FROM pills WHERE isArchived = 0")
    fun getAllPills(): Flow<List<PillEntity>>

    /** Every pill, archived or not, for the History filter. */
    @Query("SELECT * FROM pills ORDER BY name COLLATE NOCASE")
    fun getAllPillsIncludingArchived(): Flow<List<PillEntity>>

    /**
     * Dose history, newest first. [pillId] -1 = all medications; [status] "" = all statuses, else a DoseStatus name.
     * Joins the pill so archived medications are listed too.
     */
    @Query("""
        SELECT l.id AS logId, l.pillId AS pillId, p.name AS pillName, p.unit AS pillUnit, p.isArchived AS isArchived,
               l.timestamp AS timestamp, l.status AS status, l.amount AS amount, l.note AS note
        FROM dose_logs l JOIN pills p ON p.id = l.pillId
        WHERE (:pillId = -1 OR l.pillId = :pillId) AND (:status = '' OR l.status = :status)
        ORDER BY l.timestamp DESC
        LIMIT :limit
    """)
    fun getHistory(pillId: Long, status: String, limit: Int): Flow<List<DoseHistoryRow>>

    @Query("SELECT * FROM pills WHERE isArchived = 1 ORDER BY archivedAt DESC")
    fun getArchivedPills(): Flow<List<PillEntity>>

    @Query("UPDATE pills SET isArchived = :archived, archivedAt = :at WHERE id = :pillId")
    suspend fun setArchived(pillId: Long, archived: Boolean, at: Long?)

    @Query("SELECT * FROM pills WHERE id = :id")
    suspend fun getPillById(id: Long): PillEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPill(pill: PillEntity): Long

    @Update
    suspend fun updatePill(pill: PillEntity)

    @Delete
    suspend fun deletePill(pill: PillEntity)

    @Query("SELECT * FROM pills WHERE asNeeded = 1 AND isArchived = 0")
    fun getAsNeededPills(): Flow<List<PillEntity>>

    /** Atomically saves an as-needed pill and removes any schedules it previously had. */
    @Transaction
    suspend fun saveAsNeededPill(pill: PillEntity): Long {
        val id = if (pill.id == 0L) {
            insertPill(pill)
        } else {
            updatePill(pill)
            pill.id
        }
        deleteSchedulesForPill(id)
        return id
    }

    // Schedule Queries
    @Query("SELECT * FROM schedules WHERE pillId = :pillId")
    fun getSchedulesForPill(pillId: Long): Flow<List<ScheduleEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSchedule(schedule: ScheduleEntity): Long

    @Update
    suspend fun updateSchedule(schedule: ScheduleEntity)

    @Query("DELETE FROM schedules WHERE pillId = :pillId")
    suspend fun deleteSchedulesForPill(pillId: Long)

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
    @Query("UPDATE pills SET supplyCount = :supply WHERE id = :pillId")
    suspend fun updateSupply(pillId: Long, supply: Double?)

    /**
     * Inserts a dose log and, for a TAKEN dose on a pill that tracks supply, subtracts
     * `tabletsPerDose` from `supplyCount` (floored at 0) in the same transaction.
     */
    @Transaction
    suspend fun logDoseAndAdjustSupply(log: DoseLogEntity): Long {
        val id = insertDoseLog(log)
        if (log.status == DoseStatus.TAKEN) {
            val pill = getPillById(log.pillId)
            val supply = pill?.supplyCount
            if (pill != null && supply != null) {
                updateSupply(pill.id, SupplyCalculator.afterDose(supply, pill.tabletsPerDose))
            }
        }
        return id
    }

    @Query("SELECT * FROM dose_logs WHERE pillId = :pillId ORDER BY timestamp DESC")
    fun getLogsForPill(pillId: Long): Flow<List<DoseLogEntity>>

    @Query("SELECT * FROM dose_logs WHERE pillId = :pillId AND status = 'TAKEN' AND timestamp > :sinceMillis ORDER BY timestamp DESC")
    fun getTakenLogsSince(pillId: Long, sinceMillis: Long): Flow<List<DoseLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDoseLog(log: DoseLogEntity): Long

    @Query("SELECT * FROM dose_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogs(limit: Int): Flow<List<DoseLogEntity>>
}
