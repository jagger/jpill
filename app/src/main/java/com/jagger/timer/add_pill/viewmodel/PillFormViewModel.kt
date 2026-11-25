// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.add_pill.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jagger.timer.core.data.catalog.CatalogResult
import com.jagger.timer.core.data.catalog.CatalogSearch
import com.jagger.timer.core.data.catalog.CatalogSource
import com.jagger.timer.core.data.catalog.DrugDatasetSource
import com.jagger.timer.core.data.local.entity.PillEntity
import com.jagger.timer.core.util.RxFormLogic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
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
    val color: Int = com.jagger.timer.core.util.PillAppearance.defaultColor,
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
    val isDeleted: Boolean = false,
    val isArchived: Boolean = false,
    val existingScheduleId: Long? = null,
    val asNeeded: Boolean = false,
    val minIntervalMinutes: Int? = null,
    val maxPer24h: Int? = null,
    val maxIsDoses: Boolean = false,
    val catalogId: String? = null,
    /** Normalized active ingredients from the catalog pick (kept when editing). */
    val ingredients: List<String> = emptyList(),
    // Prescription fields (text kept as typed; parsed on save)
    val isPrescription: Boolean = false,
    val tabletsText: String = "1",
    val supplyText: String = "",
    val refillDaysText: String = "7",
    val instructions: String = "",
    val prescriber: String = "",
    val pharmacy: String = "",
    val rxNumber: String = "",
    /** Strengths suggested by the picked catalog entry, shown as chips. */
    val strengthOptions: List<String> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PillFormViewModel @Inject constructor(
    private val pillRepository: PillRepository,
    private val scheduleManager: ScheduleManager,
    private val drugDataset: DrugDatasetSource,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** Curated Rx, then OTC, then bundled-dataset matches for the current search text. */
    val searchResults: StateFlow<List<CatalogResult>> = _query
        .mapLatest { q ->
            if (q.isBlank()) emptyList() else CatalogSearch.search(q, drugDataset.records())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateQuery(q: String) { _query.value = q }

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
                        ingredients = com.jagger.timer.core.util.IngredientLogic.parse(pill.ingredients),
                        isPrescription = pill.isPrescription,
                        tabletsText = RxFormLogic.formatCount(pill.tabletsPerDose),
                        supplyText = pill.supplyCount?.let { s -> RxFormLogic.formatCount(s) } ?: "",
                        refillDaysText = pill.refillThresholdDays.toString(),
                        instructions = pill.instructions.orEmpty(),
                        prescriber = pill.prescriber.orEmpty(),
                        pharmacy = pill.pharmacy.orEmpty(),
                        rxNumber = pill.rxNumber.orEmpty(),
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
            catalogId = med.id,
            ingredients = com.jagger.timer.core.util.IngredientLogic.normalize(med.ingredients)
        )
    }
    fun updateColor(color: Int) = _uiState.update { it.copy(color = color) }
    fun updateIcon(icon: String?) = _uiState.update { it.copy(icon = icon) }
    fun updateUnit(unit: String) = _uiState.update { it.copy(unit = unit) }
    fun updateDosage(dosage: String) = _uiState.update { it.copy(dosage = dosage) }
    fun updateIsPrescription(v: Boolean) = _uiState.update { it.copy(isPrescription = v) }
    fun updateTablets(v: String) = _uiState.update { it.copy(tabletsText = v) }
    fun updateSupply(v: String) = _uiState.update { it.copy(supplyText = v) }
    fun updateRefillDays(v: String) = _uiState.update { it.copy(refillDaysText = v.filter { c -> c.isDigit() }.take(3)) }
    fun updateInstructions(v: String) = _uiState.update { it.copy(instructions = v) }
    fun addInstructionPreset(phrase: String) =
        _uiState.update { it.copy(instructions = com.jagger.timer.core.util.InstructionTags.toggle(it.instructions, phrase)) }
    fun updatePrescriber(v: String) = _uiState.update { it.copy(prescriber = v) }
    fun updatePharmacy(v: String) = _uiState.update { it.copy(pharmacy = v) }
    fun updateRxNumber(v: String) = _uiState.update { it.copy(rxNumber = v) }
    fun updateRecurrence(pattern: RecurrencePattern) = _uiState.update { it.copy(recurrencePattern = pattern) }
    fun toggleDay(day: Int) = _uiState.update {
        val days = if (day in it.selectedDays) it.selectedDays - day else it.selectedDays + day
        // Keep at least one day selected so a weekly schedule always has an occurrence.
        it.copy(selectedDays = if (days.isEmpty()) it.selectedDays else days.sorted())
    }

    /** Picks a strength chip, e.g. "10 mg" -> dosage 10, unit mg. Combination strengths fill the dosage whole. */
    fun applyStrength(strength: String) {
        val (number, unit) = RxFormLogic.parseStrength(strength)
        _uiState.update { it.copy(dosage = number, unit = unit ?: it.unit) }
    }

    /** Applies a search hit from any catalog. Prescriptions never get a dose or limits, only a typical schedule. */
    fun applyCatalogResult(result: CatalogResult) {
        _query.value = ""
        when (result.source) {
            CatalogSource.OTC -> {
                result.otc?.let { applyCatalogMed(it) }
                _uiState.update { it.copy(isPrescription = false, strengthOptions = emptyList()) }
            }
            CatalogSource.RX -> {
                val rx = result.rx ?: return
                val defaults = rx.frequency.scheduleDefaults()
                _uiState.update {
                    val base = it.copy(
                        name = rx.genericName,
                        catalogId = rx.id,
                        ingredients = com.jagger.timer.core.util.IngredientLogic.normalize(rx.ingredients),
                        isPrescription = true,
                        instructions = rx.instructions ?: "",
                        strengthOptions = rx.strengths,
                        // Dose and limits are the prescriber's call: clear anything an earlier pick filled in.
                        dosage = "",
                        minIntervalMinutes = null,
                        maxPer24h = null,
                        maxIsDoses = false
                    )
                    if (defaults == null) {
                        base.copy(asNeeded = true)
                    } else {
                        base.copy(
                            asNeeded = false,
                            scheduleType = defaults.type,
                            recurrencePattern = defaults.pattern,
                            timeMinutes = defaults.timeMinutes ?: it.timeMinutes,
                            intervalMinutes = defaults.intervalMinutes ?: it.intervalMinutes,
                            timeSlotName = defaults.timeSlotName ?: it.timeSlotName,
                            selectedDays = defaults.daysOfWeek ?: listOf(1, 2, 3, 4, 5, 6, 7)
                        )
                    }
                }
            }
            CatalogSource.DATASET -> _uiState.update {
                it.copy(
                    name = result.genericName,
                    catalogId = null,
                    ingredients = com.jagger.timer.core.util.IngredientLogic.normalize(result.ingredients),
                    isPrescription = true,
                    strengthOptions = result.strengths,
                    dosage = "",
                    asNeeded = false
                )
            }
        }
    }
    fun updateScheduleType(type: ScheduleType) = _uiState.update { it.copy(scheduleType = type) }
    fun updateTime(hour: Int, minute: Int) = _uiState.update { it.copy(timeMinutes = hour * 60 + minute) }
    fun updateInterval(hours: Int) = _uiState.update { it.copy(intervalMinutes = hours * 60) }
    fun updateTimeSlot(slot: String) = _uiState.update { it.copy(timeSlotName = slot) }

    /** Archives the pill being edited: hidden and silent, but history is kept. */
    fun archivePill() {
        val id = _uiState.value.pillId ?: return
        if (_uiState.value.isSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            pillRepository.getPillById(id)?.let { scheduleManager.archivePill(it) }
            _uiState.update { it.copy(isSaving = false, isArchived = true) }
        }
    }

    /** Deletes the pill being edited, with its alarms, notifications, schedules and dose history. */
    fun deletePill() {
        val id = _uiState.value.pillId ?: return
        if (_uiState.value.isSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            pillRepository.getPillById(id)?.let { scheduleManager.deletePill(it) }
            _uiState.update { it.copy(isSaving = false, isDeleted = true) }
        }
    }

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
                catalogId = state.catalogId,
                ingredients = com.jagger.timer.core.util.IngredientLogic.serialize(state.ingredients),
                tabletsPerDose = RxFormLogic.parseTablets(state.tabletsText),
                isPrescription = state.isPrescription,
                supplyCount = if (state.isPrescription) RxFormLogic.parseSupply(state.supplyText) else null,
                refillThresholdDays = RxFormLogic.parseRefillDays(state.refillDaysText),
                instructions = state.instructions.trim().takeIf { it.isNotEmpty() },
                prescriber = state.prescriber.trim().takeIf { state.isPrescription && it.isNotEmpty() },
                pharmacy = state.pharmacy.trim().takeIf { state.isPrescription && it.isNotEmpty() },
                rxNumber = state.rxNumber.trim().takeIf { state.isPrescription && it.isNotEmpty() }
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
