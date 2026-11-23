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
    val color: Int = 0xFF6200EE.toInt(),
    val icon: String? = null,
    val scheduleType: ScheduleType = ScheduleType.FIXED_TIME,
    val recurrencePattern: RecurrencePattern = RecurrencePattern.DAILY,
    val timeMinutes: Int = 480,
    val intervalMinutes: Int = 480,
    val timeSlotName: String = "Morning",
    val selectedDays: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val existingScheduleId: Long? = null,
    val asNeeded: Boolean = false,
    val minIntervalMinutes: Int? = null,
    val maxPer24h: Int? = null,
    val maxIsDoses: Boolean = false,
    val catalogId: String? = null
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
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val pill = pillRepository.getPillById(id)
            val schedules = pill?.let { pillRepository.getSchedulesForPill(it.id).first() }
            val schedule = schedules?.firstOrNull()

            if (pill != null && (schedule != null || pill.asNeeded)) {
                _uiState.update {
                    it.copy(
                        pillId = pill.id,
                        name = pill.name,
                        dosage = pill.dosage,
                        unit = pill.unit,
                        color = pill.color,
                        icon = pill.icon,
                        asNeeded = pill.asNeeded,
                        minIntervalMinutes = pill.minIntervalMinutes,
                        maxPer24h = pill.maxPer24h,
                        maxIsDoses = pill.maxIsDoses,
                        catalogId = pill.catalogId,
                        scheduleType = schedule?.type ?: it.scheduleType,
                        recurrencePattern = schedule?.pattern ?: it.recurrencePattern,
                        timeMinutes = schedule?.timeMinutes ?: 480,
                        intervalMinutes = schedule?.intervalMinutes ?: 480,
                        timeSlotName = schedule?.timeSlotName ?: "Morning",
                        selectedDays = schedule?.daysOfWeek?.split(",")
                            ?.mapNotNull { d -> d.toIntOrNull() }
                            ?: listOf(1, 2, 3, 4, 5, 6, 7),
                        existingScheduleId = schedule?.id,
                        isLoading = false
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun updateName(name: String) = _uiState.update { it.copy(name = name) }
    fun updateAsNeeded(asNeeded: Boolean) = _uiState.update { it.copy(asNeeded = asNeeded) }
    fun updateMinInterval(minutes: Int?) = _uiState.update { it.copy(minIntervalMinutes = minutes) }
    fun updateMaxPer24h(max: Int?, isDoses: Boolean) =
        _uiState.update { it.copy(maxPer24h = max, maxIsDoses = isDoses) }
    fun applyCatalogMed(med: com.jagger.timer.core.data.catalog.OtcMed) = _uiState.update {
        it.copy(
            name = med.genericName,
            dosage = med.defaultDose,
            unit = med.unit,
            asNeeded = true,
            minIntervalMinutes = med.minIntervalMinutes,
            maxPer24h = med.maxPer24h,
            maxIsDoses = med.maxIsDoses,
            catalogId = med.id
        )
    }
    fun updateDosage(dosage: String) = _uiState.update { it.copy(dosage = dosage) }
    fun updateScheduleType(type: ScheduleType) = _uiState.update { it.copy(scheduleType = type) }
    fun updateTime(hour: Int, minute: Int) = _uiState.update { it.copy(timeMinutes = hour * 60 + minute) }
    fun updateInterval(hours: Int) = _uiState.update { it.copy(intervalMinutes = hours * 60) }
    fun updateTimeSlot(slot: String) = _uiState.update { it.copy(timeSlotName = slot) }

    fun savePill() {
        val state = _uiState.value
        if (state.name.isBlank() || state.isLoading || state.isSaving) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            val pill = PillEntity(
                id = state.pillId ?: 0L,
                name = state.name,
                dosage = state.dosage,
                unit = state.unit,
                color = state.color,
                icon = state.icon,
                asNeeded = state.asNeeded,
                minIntervalMinutes = if (state.asNeeded) state.minIntervalMinutes else null,
                maxPer24h = if (state.asNeeded) state.maxPer24h else null,
                maxIsDoses = state.asNeeded && state.maxIsDoses,
                catalogId = state.catalogId
            )

            if (state.asNeeded) {
                // Cancel any alarms from a previous scheduled version before its schedules are removed
                if (state.pillId != null) scheduleManager.cancelAlarmsForPill(pill)
                val savedId = pillRepository.saveAsNeededPill(pill)
                scheduleManager.refreshAlarmsForPill(pill.copy(id = savedId))
                _uiState.update { it.copy(isSaving = false, isSaved = true) }
                return@launch
            }

            val schedule = ScheduleEntity(
                id = state.existingScheduleId ?: 0L,
                pillId = state.pillId ?: 0L,
                type = state.scheduleType,
                pattern = if (state.scheduleType == ScheduleType.FIXED_TIME) state.recurrencePattern else RecurrencePattern.DAILY,
                timeMinutes = if (state.scheduleType == ScheduleType.FIXED_TIME) state.timeMinutes else null,
                intervalMinutes = if (state.scheduleType == ScheduleType.INTERVAL) state.intervalMinutes else null,
                timeSlotName = if (state.scheduleType == ScheduleType.TIME_SLOT) state.timeSlotName else null,
                daysOfWeek = if (state.recurrencePattern == RecurrencePattern.WEEKLY) state.selectedDays.joinToString(",") else null
            )

            // Cancel before atomic DB write so we don't leave stale alarms on failure
            if (state.pillId != null) {
                scheduleManager.cancelAlarmsForPill(pill)
            }

            // Atomic: pill + schedule written in a single @Transaction
            val savedPillId = pillRepository.savePillWithSchedule(pill, schedule)

            scheduleManager.refreshAlarmsForPill(pill.copy(id = savedPillId))
            _uiState.update { it.copy(isSaving = false, isSaved = true) }
        }
    }
}
