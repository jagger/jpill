package com.jagger.timer.core.data.repository

import com.jagger.timer.core.data.local.dao.TimerDao
import com.jagger.timer.core.data.local.entity.TimerEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimerRepository @Inject constructor(
    private val timerDao: TimerDao
) {
    fun getAllTimers(): Flow<List<TimerEntity>> = timerDao.getAllTimers()

    suspend fun getTimerById(id: Long): TimerEntity? = timerDao.getTimerById(id)

    suspend fun saveTimer(timer: TimerEntity): Long = timerDao.insertTimer(timer)

    suspend fun updateTimer(timer: TimerEntity) = timerDao.updateTimer(timer)

    suspend fun deleteTimer(timer: TimerEntity) = timerDao.deleteTimer(timer)
}
