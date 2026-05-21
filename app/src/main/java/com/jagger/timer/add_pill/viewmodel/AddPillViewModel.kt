package com.jagger.timer.add_pill.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jagger.timer.core.data.local.entity.PillEntity
import com.jagger.timer.core.data.local.entity.RecurrencePattern
import com.jagger.timer.core.data.local.entity.ScheduleEntity
import com.jagger.timer.core.data.local.entity.ScheduleType
import com.jagger.timer.core.data.repository.PillRepository
import com.jagger.timer.core.util.ScheduleManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddPillUiState(
    val name: String = "",
    val dosage: String = "",
    val unit: String = "mg",
    val scheduleType: ScheduleType = ScheduleType.FIXED_TIME,
    val recurrencePattern: RecurrencePattern = RecurrencePattern.DAILY,
    val timeMinutes: Int = 480, // 8:00 AM
    val intervalMinutes: Int = 480, // 8 hours
    val timeSlotName: String = "Morning",
    val selectedDays: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7), // 1=Mon, 7=Sun
    val isSaving: Boolean = false,
    val isSaved: Boolean = false
)

@HiltViewModel
class AddPillViewModel @Inject constructor(
    private val pillRepository: PillRepository,
    private val scheduleManager: ScheduleManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddPillUiState())
    val uiState: StateFlow<AddPillUiState> = _uiState.asStateFlow()

    fun updateName(name: String) = _uiState.update { it.copy(name = name) }
    fun updateDosage(dosage: String) = _uiState.update { it.copy(dosage = dosage) }
    fun updateScheduleType(type: ScheduleType) = _uiState.update { it.copy(scheduleType = type) }
    fun updateTime(hour: Int, minute: Int) = _uiState.update { it.copy(timeMinutes = hour * 60 + minute) }
    fun updateInterval(hours: Int) = _uiState.update { it.copy(intervalMinutes = hours * 60) }
    fun updateTimeSlot(slot: String) = _uiState.update { it.copy(timeSlotName = slot) }

    fun savePill() {
        val state = _uiState.value
        if (state.name.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            
            val pill = PillEntity(
                name = state.name,
                dosage = state.dosage,
                unit = state.unit,
                color = 0xFF6200EE.toInt() // Default color
            )
            val pillId = pillRepository.savePill(pill)

            val schedule = ScheduleEntity(
                pillId = pillId,
                type = state.scheduleType,
                pattern = if (state.scheduleType == ScheduleType.FIXED_TIME) state.recurrencePattern else RecurrencePattern.DAILY,
                timeMinutes = if (state.scheduleType == ScheduleType.FIXED_TIME) state.timeMinutes else null,
                intervalMinutes = if (state.scheduleType == ScheduleType.INTERVAL) state.intervalMinutes else null,
                timeSlotName = if (state.scheduleType == ScheduleType.TIME_SLOT) state.timeSlotName else null,
                daysOfWeek = if (state.recurrencePattern == RecurrencePattern.WEEKLY) state.selectedDays.joinToString(",") else null
            )
            pillRepository.saveSchedule(schedule)
            scheduleManager.refreshAlarmsForPill(pill.copy(id = pillId))

            _uiState.update { it.copy(isSaving = false, isSaved = true) }
        }
    }
}
