package com.jagger.timer.home.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jagger.timer.core.data.local.entity.PillEntity
import com.jagger.timer.core.data.local.entity.RecurrencePattern
import com.jagger.timer.core.data.local.entity.ScheduleEntity
import com.jagger.timer.core.data.local.entity.ScheduleType
import com.jagger.timer.core.data.repository.PillRepository
import com.jagger.timer.core.util.ScheduleManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val pillRepository: PillRepository,
    private val scheduleManager: ScheduleManager
) : ViewModel() {

    val pills: StateFlow<List<PillEntity>> = pillRepository.getAllPills()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addTestPill() {
        viewModelScope.launch {
            val pill = PillEntity(
                name = "Test Pill",
                dosage = "10",
                unit = "mg",
                color = 0xFF6200EE.toInt()
            )
            val pillId = pillRepository.savePill(pill)
            
            // Schedule for 1 minute from now
            val now = LocalDateTime.now().plusMinutes(1)
            val schedule = ScheduleEntity(
                pillId = pillId,
                type = ScheduleType.FIXED_TIME,
                pattern = RecurrencePattern.DAILY,
                timeMinutes = now.hour * 60 + now.minute,
                reminderAdvanceMinutes = 0, // Trigger immediately at the time
                isCritical = true
            )
            pillRepository.saveSchedule(schedule)
            scheduleManager.refreshAlarmsForPill(pill.copy(id = pillId))
        }
    }

    fun deletePill(pill: PillEntity) {
        viewModelScope.launch {
            scheduleManager.cancelAlarmsForPill(pill)
            pillRepository.deletePill(pill)
        }
    }
}
