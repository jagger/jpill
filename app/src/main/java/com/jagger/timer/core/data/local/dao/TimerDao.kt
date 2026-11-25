// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.jagger.timer.core.data.local.entity.TimerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TimerDao {
    @Query("SELECT * FROM timers ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<TimerEntity>>

    @Query("SELECT * FROM timers")
    suspend fun getAll(): List<TimerEntity>

    @Query("SELECT * FROM timers WHERE id = :id")
    suspend fun getById(id: Long): TimerEntity?

    @Insert
    suspend fun insert(timer: TimerEntity): Long

    @Update
    suspend fun update(timer: TimerEntity)

    @Query("DELETE FROM timers WHERE id = :id")
    suspend fun delete(id: Long)
}
