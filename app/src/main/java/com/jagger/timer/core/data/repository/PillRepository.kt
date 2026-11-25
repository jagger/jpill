// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.core.data.repository

import com.jagger.timer.core.data.local.dao.DoseHistoryRow
import com.jagger.timer.core.data.local.dao.PillDao
import com.jagger.timer.core.data.local.entity.DoseLogEntity
import com.jagger.timer.core.data.local.entity.PillEntity
import com.jagger.timer.core.data.local.entity.ScheduleEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PillRepository @Inject constructor(
    private val pillDao: PillDao
) {
    fun getAllPills(): Flow<List<PillEntity>> = pillDao.getAllPills()

    suspend fun getPillById(id: Long): PillEntity? = pillDao.getPillById(id)

    suspend fun getPillByNfcTag(tagId: String): PillEntity? = pillDao.getPillByNfcTag(tagId)

    suspend fun savePillWithSchedule(pill: PillEntity, schedule: ScheduleEntity): Long =
        pillDao.savePillWithSchedule(pill, schedule)

    fun getAllPillsIncludingArchived(): Flow<List<PillEntity>> = pillDao.getAllPillsIncludingArchived()

    fun getHistory(pillId: Long, status: String, limit: Int): Flow<List<DoseHistoryRow>> =
        pillDao.getHistory(pillId, status, limit)

    fun getHistorySince(pillId: Long, sinceMillis: Long): Flow<List<DoseHistoryRow>> = pillDao.getHistorySince(pillId, sinceMillis)

    fun getArchivedPills(): Flow<List<PillEntity>> = pillDao.getArchivedPills()

    suspend fun setArchived(pillId: Long, archived: Boolean, at: Long?) = pillDao.setArchived(pillId, archived, at)

    fun getAsNeededPills(): Flow<List<PillEntity>> = pillDao.getAsNeededPills()

    /** Saves an as-needed pill; drops any schedules it had. Cancel its alarms first. */
    suspend fun saveAsNeededPill(pill: PillEntity): Long = pillDao.saveAsNeededPill(pill)

    fun getTakenLogsSince(pillId: Long, sinceMillis: Long): Flow<List<DoseLogEntity>> =
        pillDao.getTakenLogsSince(pillId, sinceMillis)

    suspend fun deletePill(pill: PillEntity) = pillDao.deletePill(pill)

    fun getSchedulesForPill(pillId: Long): Flow<List<ScheduleEntity>> =
        pillDao.getSchedulesForPill(pillId)

    fun getLogsForPill(pillId: Long): Flow<List<DoseLogEntity>> = pillDao.getLogsForPill(pillId)

    /** Logs a dose; a TAKEN dose also decrements the pill's supply, atomically. */
    suspend fun logDose(log: DoseLogEntity): Long = pillDao.logDoseAndAdjustSupply(log)

    suspend fun setSupply(pillId: Long, supply: Double?) = pillDao.updateSupply(pillId, supply)

    fun getRecentLogs(limit: Int): Flow<List<DoseLogEntity>> = pillDao.getRecentLogs(limit)
}
