// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.nfc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jagger.timer.core.data.local.entity.DoseLogEntity
import com.jagger.timer.core.data.local.entity.DoseStatus
import com.jagger.timer.core.data.local.entity.PillEntity
import com.jagger.timer.core.data.local.entity.doseAmount
import com.jagger.timer.core.data.repository.PillRepository
import com.jagger.timer.core.util.CooldownCalculator
import com.jagger.timer.core.util.IngredientWarningLoader
import com.jagger.timer.core.util.ScheduleManager
import com.jagger.timer.core.util.TimerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the scan card shows. */
sealed interface ScanUi {
    data object Loading : ScanUi
    data class Medicine(val decision: ScanDecision) : ScanUi
    data class Timer(val decision: TimerScanDecision) : ScanUi
    /** A jPill medicine tag this phone doesn't know (new phone, or the medicine was deleted). */
    data class UnknownMedicine(val tagId: String, val name: String?) : ScanUi
    /** Success: shown briefly, then the screen closes. */
    data class Done(val message: String) : ScanUi
    data class Invalid(val message: String) : ScanUi
}

@HiltViewModel
class NfcScanViewModel @Inject constructor(
    private val pillRepository: PillRepository,
    private val scheduleManager: ScheduleManager,
    private val timerManager: TimerManager
) : ViewModel() {

    private val _ui = MutableStateFlow<ScanUi>(ScanUi.Loading)
    val ui: StateFlow<ScanUi> = _ui.asStateFlow()

    fun handle(uri: String?) {
        _ui.value = ScanUi.Loading
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            _ui.value = when (val payload = NfcPayload.parse(uri)) {
                null -> ScanUi.Invalid("That isn't a jPill tag.")
                is NfcPayload.Timer -> ScanUi.Timer(TimerScanResolver.resolve(payload, timerManager.getByNfcTag(payload.tagId), now))
                is NfcPayload.Medicine -> {
                    val pill = pillRepository.getPillByNfcTag(payload.tagId)
                    if (pill == null) ScanUi.UnknownMedicine(payload.tagId, payload.name)
                    else ScanUi.Medicine(resolveMedicine(pill, now))
                }
            }
        }
    }

    private suspend fun resolveMedicine(pill: PillEntity, now: Long): ScanDecision {
        val schedules = pillRepository.getSchedulesForPill(pill.id).first()
        val logs = pillRepository.getLogsForPill(pill.id).first()
        val cooldown = if (pill.asNeeded) {
            CooldownCalculator.forPill(pill, pillRepository.getTakenLogsSince(pill.id, now - CooldownCalculator.WINDOW_MILLIS).first(), now)
        } else null
        val warning = IngredientWarningLoader.load(pillRepository, now)[pill.id]
        return TagScanResolver.resolve(pill, schedules, logs, cooldown, warning, now)
    }

    /** Logs a TAKEN dose for [pill] against [scheduleId] (null for as-needed or an off-schedule dose). */
    fun logDose(pill: PillEntity, scheduleId: Long?, onLogged: () -> Unit) {
        viewModelScope.launch {
            pillRepository.logDose(
                DoseLogEntity(
                    pillId = pill.id, scheduleId = scheduleId, timestamp = System.currentTimeMillis(),
                    status = DoseStatus.TAKEN, amount = pill.doseAmount()
                )
            )
            scheduleManager.refreshAlarmsForPill(pill) // re-plans the next dose and redraws the widget
            scheduleManager.dismissReminder(pill.id)
            onLogged()
            _ui.value = ScanUi.Done("Logged ${pill.name}")
        }
    }

    fun startTimerFromTag(p: NfcPayload.Timer) = viewModelScope.launch {
        timerManager.startFromTag(p.tagId, p.label, p.seconds * 1000)
        _ui.value = ScanUi.Done("Started ${p.label.ifBlank { "timer" }}")
    }

    fun restartTimer(id: Long, label: String) = viewModelScope.launch {
        timerManager.restart(id)
        _ui.value = ScanUi.Done("Started ${label.ifBlank { "timer" }}")
    }

    fun resumeTimer(id: Long, label: String) = viewModelScope.launch {
        timerManager.resume(id)
        _ui.value = ScanUi.Done("Resumed ${label.ifBlank { "timer" }}")
    }
}
