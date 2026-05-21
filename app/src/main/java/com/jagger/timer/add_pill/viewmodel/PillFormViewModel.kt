package com.jagger.timer.add_pill.viewmodel

import androidx.lifecycle.SavedStateHandle
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PillFormUiState(
    val pillId: Long? = null,
    val name: String = "",
    val dosage: String = "",
    val unit: String = "mg",
    val color: Int = 0xFF6200EE.toInt(), // Default color
    val icon: String = "Medication",
    val scheduleType: ScheduleType = ScheduleType.FIXED_TIME,
    val recurrencePattern: RecurrencePattern = RecurrencePattern.DAILY,
    val timeMinutes: Int = 480, // 8:00 AM
    val intervalMinutes: Int = 480, // 8 hours
    val timeSlotName: String = "Morning",
    val selectedDays: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7), // 1=Mon, 7=Sun
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val existingScheduleId: Long? = null
)

@HiltViewModel
class PillFormViewModel @Inject constructor(
    private val pillRepository: PillRepository,
    private val scheduleManager: ScheduleManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(PillFormUiState())
    val uiState: StateFlow<PillFormUiState> = _uiState.asStateFlow()

    init {
        val pillId: Long? = savedStateHandle["pillId"]
        if (pillId != null && pillId != -1L) {
            loadPill(pillId)
        }
    }

    private fun loadPill(id: Long) {
        viewModelScope.launch {
            val pill = pillRepository.getPillById(id) ?: return@launch
            val schedules = pillRepository.getSchedulesForPill(id).first()
            val schedule = schedules.firstOrNull() ?: return@launch

            _uiState.update {
                it.copy(
                    pillId = pill.id,
                    name = pill.name,
                    dosage = pill.dosage,
                    unit = pill.unit,
                    color = pill.color,
                    icon = pill.icon ?: "Medication",
                    scheduleType = schedule.type,
                    recurrencePattern = schedule.pattern,
                    timeMinutes = schedule.timeMinutes ?: 480,
                    intervalMinutes = schedule.intervalMinutes ?: 480,
                    timeSlotName = schedule.timeSlotName ?: "Morning",
                    selectedDays = schedule.daysOfWeek?.split(",")?.mapNotNull { d -> d.toIntOrNull() } ?: listOf(1, 2, 3, 4, 5, 6, 7),
                    existingScheduleId = schedule.id
                )
            }
        }
    }

    fun updateName(name: String) = _uiState.update { it.copy(name = name) }
    fun updateDosage(dosage: String) = _uiState.update { it.copy(dosage = dosage) }
    fun updateUnit(unit: String) = _uiState.update { it.copy(unit = unit) }
    fun updateColor(color: Int) = _uiState.update { it.copy(color = color) }
    fun updateIcon(icon: String) = _uiState.update { it.copy(icon = icon) }
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
                id = state.pillId ?: 0L,
                name = state.name,
                dosage = state.dosage,
                unit = state.unit,
                color = state.color,
                icon = state.icon
            )
            
            // If editing, cancel existing alarms first
            if (state.pillId != null) {
                scheduleManager.cancelAlarmsForPill(pill)
            }

            val pillId = pillRepository.savePill(pill)

            val schedule = ScheduleEntity(
                id = state.existingScheduleId ?: 0L,
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
