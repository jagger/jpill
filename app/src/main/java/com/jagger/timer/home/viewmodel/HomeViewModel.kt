package com.jagger.timer.home.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jagger.timer.core.data.local.entity.DoseLogEntity
import com.jagger.timer.core.data.local.entity.DoseStatus
import com.jagger.timer.core.data.local.entity.PillEntity
import com.jagger.timer.core.data.local.entity.doseAmount
import com.jagger.timer.core.data.local.entity.RecurrencePattern
import com.jagger.timer.core.data.local.entity.ScheduleEntity
import com.jagger.timer.core.data.local.entity.ScheduleType
import com.jagger.timer.core.data.model.TimelineEntry
import com.jagger.timer.core.data.repository.PillRepository
import com.jagger.timer.core.util.CooldownCalculator
import com.jagger.timer.core.util.CooldownState
import com.jagger.timer.core.util.IngredientLogic
import com.jagger.timer.core.util.IngredientWarning
import com.jagger.timer.core.util.ScheduleManager
import com.jagger.timer.core.util.SupplyCalculator
import com.jagger.timer.core.util.SupplyStatus
import com.jagger.timer.core.util.TakenDose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

/** An as-needed pill with its live cooldown state. */
/**
 * An as-needed pill with its cooldown at [nowMillis]. [nowMillis] is part of the value on purpose:
 * without it, equal states are de-duplicated by StateFlow and the countdown text never redraws.
 */
data class AsNeededItem(val pill: PillEntity, val cooldown: CooldownState, val nowMillis: Long)

/** Supply for a tracked prescription: count on hand, estimated days left (null if not estimable), status. */
data class SupplyInfo(val supply: Double, val daysLeft: Double?, val status: SupplyStatus)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val pillRepository: PillRepository,
    private val scheduleManager: ScheduleManager
) : ViewModel() {

    val pills: StateFlow<List<PillEntity>> = pillRepository.getAllPills()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val timeline: StateFlow<List<TimelineEntry>> = pillRepository.getAllPills()
        .flatMapLatest {
            flow {
                emit(scheduleManager.getUpcomingDoses())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val ticker: Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(5000) // countdown shows whole minutes; this also bounds how late a card flips to Ready
        }
    }

    /** As-needed pills with a countdown that ticks every second while the screen is visible. */
    val asNeeded: StateFlow<List<AsNeededItem>> = pillRepository.getAsNeededPills()
        .flatMapLatest { pills ->
            if (pills.isEmpty()) {
                flowOf(emptyList())
            } else {
                // Fixed lower bound is a superset of the rolling 24h window; the calculator trims it.
                val since = System.currentTimeMillis() - CooldownCalculator.WINDOW_MILLIS
                val logFlows = pills.map { pillRepository.getTakenLogsSince(it.id, since) }
                combine(combine(logFlows) { it.toList() }, ticker) { logsPerPill, now ->
                    pills.zip(logsPerPill).map { (pill, logs) ->
                        AsNeededItem(pill, CooldownCalculator.forPill(pill, logs, now), now)
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Duplicate-ingredient warnings by pill id: pills sharing an active ingredient, with the combined 24h total. */
    val ingredientWarnings: StateFlow<Map<Long, IngredientWarning>> = pillRepository.getAllPills()
        .flatMapLatest { all ->
            val withIng = all.filter { IngredientLogic.parse(it.ingredients).isNotEmpty() }
            if (withIng.size < 2) {
                flowOf(emptyMap())
            } else {
                val since = System.currentTimeMillis() - CooldownCalculator.WINDOW_MILLIS
                val logFlows = withIng.map { pillRepository.getTakenLogsSince(it.id, since) }
                combine(combine(logFlows) { it.toList() }, ticker) { logsPerPill, now ->
                    val doses = withIng.zip(logsPerPill).associate { (pill, logs) ->
                        val fallback = pill.doseAmount() ?: 1.0
                        pill.id to logs.map { TakenDose(it.timestamp, it.amount ?: fallback) }
                    }
                    IngredientLogic.warnings(withIng, doses, now)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** How many archived pills exist (drives the Archived button on the home screen). */
    val archivedCount: StateFlow<Int> = pillRepository.getArchivedPills()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Supply and days-left for prescriptions that track it, by pill id. Updates as doses are logged. */
    val supply: StateFlow<Map<Long, SupplyInfo>> = pillRepository.getAllPills()
        .flatMapLatest { pills ->
            val tracked = pills.filter { it.isPrescription && it.supplyCount != null }
            if (tracked.isEmpty()) {
                flowOf(emptyMap())
            } else {
                combine(tracked.map { pillRepository.getSchedulesForPill(it.id) }) { schedulesPerPill ->
                    tracked.zip(schedulesPerPill.toList()).associate { (pill, schedules) ->
                        pill.id to SupplyInfo(
                            supply = pill.supplyCount!!,
                            daysLeft = SupplyCalculator.daysLeft(pill, schedules),
                            status = SupplyCalculator.status(pill, schedules)
                        )
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** Logs an as-needed dose as taken right now. */
    fun takeNow(pill: PillEntity) {
        viewModelScope.launch {
            pillRepository.logDose(
                DoseLogEntity(
                    pillId = pill.id,
                    scheduleId = null,
                    timestamp = System.currentTimeMillis(),
                    status = DoseStatus.TAKEN,
                    amount = pill.doseAmount()
                )
            )
            scheduleManager.refreshAlarmsForPill(pill)
        }
    }

    fun markAsTaken(entry: TimelineEntry) {
        viewModelScope.launch {
            val log = DoseLogEntity(
                pillId = entry.pill.id,
                scheduleId = entry.schedule.id,
                timestamp = System.currentTimeMillis(),
                status = DoseStatus.TAKEN,
                amount = entry.pill.doseAmount()
            )
            pillRepository.logDose(log)
            scheduleManager.refreshAlarmsForPill(entry.pill)
        }
    }

    fun markAsSkipped(entry: TimelineEntry) {
        viewModelScope.launch {
            val log = DoseLogEntity(
                pillId = entry.pill.id,
                scheduleId = entry.schedule.id,
                timestamp = System.currentTimeMillis(),
                status = DoseStatus.SKIPPED,
                amount = entry.pill.doseAmount()
            )
            pillRepository.logDose(log)
            scheduleManager.refreshAlarmsForPill(entry.pill)
        }
    }

    fun addTestPill() {
        viewModelScope.launch {
            val pill = PillEntity(
                name = "Test Pill",
                dosage = "10",
                unit = "mg",
                color = 0xFF6200EE.toInt()
            )
            val now = LocalDateTime.now().plusMinutes(1)
            val schedule = ScheduleEntity(
                pillId = 0L,
                type = ScheduleType.FIXED_TIME,
                pattern = RecurrencePattern.DAILY,
                timeMinutes = now.hour * 60 + now.minute,
                reminderAdvanceMinutes = 0,
                isCritical = true
            )
            val pillId = pillRepository.savePillWithSchedule(pill, schedule)
            scheduleManager.refreshAlarmsForPill(pill.copy(id = pillId))
        }
    }

    fun deletePill(pill: PillEntity) {
        viewModelScope.launch {
            scheduleManager.deletePill(pill)
        }
    }
}
