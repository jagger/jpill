package com.jagger.timer.core.data.repository

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

    suspend fun savePillWithSchedule(pill: PillEntity, schedule: ScheduleEntity): Long =
        pillDao.savePillWithSchedule(pill, schedule)

    suspend fun deletePill(pill: PillEntity) = pillDao.deletePill(pill)

    fun getSchedulesForPill(pillId: Long): Flow<List<ScheduleEntity>> =
        pillDao.getSchedulesForPill(pillId)

    fun getLogsForPill(pillId: Long): Flow<List<DoseLogEntity>> = pillDao.getLogsForPill(pillId)

    suspend fun logDose(log: DoseLogEntity): Long = pillDao.insertDoseLog(log)

    fun getRecentLogs(limit: Int): Flow<List<DoseLogEntity>> = pillDao.getRecentLogs(limit)
}
