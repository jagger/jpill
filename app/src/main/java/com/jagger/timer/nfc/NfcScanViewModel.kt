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
    data class Done(val message: String, val timerStarted: Boolean = false) : ScanUi
    /** A medicine tag was scanned while the NFC beta switch is off. (Timer tags always work: see [handle].) */
    data object BetaOff : ScanUi
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

    /**
     * Timer tags are scan-and-go and work on any phone with jPill, whatever the beta switch says: the tag carries
     * the name and length, and an idle or new timer starts straight away with no confirm tap (a stray scan only
     * starts a countdown). Running/paused timers still ask, so a scan can't silently reset one. Medicine tags log
     * doses, so they stay behind the beta switch and a confirm card.
     */
    fun handle(uri: String?, nfcBetaOn: Boolean) {
        _ui.value = ScanUi.Loading
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            when (val payload = NfcPayload.parse(uri)) {
                null -> _ui.value = ScanUi.Invalid("That isn't a jPill tag.")
                is NfcPayload.Timer -> {
                    val decision = TimerScanResolver.resolve(payload, timerManager.getByNfcTag(payload.tagId), now)
                    when {
                        !TimerScanResolver.startsImmediately(decision) -> _ui.value = ScanUi.Timer(decision)
                        decision is TimerScanDecision.CreateAndStart -> startNow(payload.tagId, payload.label, payload.seconds * 1000)
                        decision is TimerScanDecision.Start -> restartNow(decision.timer.id, decision.timer.label, decision.timer.durationMillis)
                    }
                }
                is NfcPayload.Medicine -> {
                    if (!nfcBetaOn) { _ui.value = ScanUi.BetaOff; return@launch }
                    val pill = pillRepository.getPillByNfcTag(payload.tagId)
                    _ui.value = if (pill == null) ScanUi.UnknownMedicine(payload.tagId, payload.name)
                    else ScanUi.Medicine(resolveMedicine(pill, now))
                }
            }
        }
    }

    private suspend fun startNow(tagId: String, label: String, durationMillis: Long) {
        timerManager.startFromTag(tagId, label, durationMillis)
        _ui.value = ScanUi.Done("Started ${label.ifBlank { "timer" }}, ${com.jagger.timer.core.util.TimerLogic.format(durationMillis)}", timerStarted = true)
    }

    private suspend fun restartNow(id: Long, label: String, durationMillis: Long) {
        timerManager.restart(id)
        _ui.value = ScanUi.Done("Started ${label.ifBlank { "timer" }}, ${com.jagger.timer.core.util.TimerLogic.format(durationMillis)}", timerStarted = true)
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

    fun restartTimer(id: Long, label: String) = viewModelScope.launch {
        timerManager.restart(id)
        _ui.value = ScanUi.Done("Started ${label.ifBlank { "timer" }}", timerStarted = true)
    }

    fun resumeTimer(id: Long, label: String) = viewModelScope.launch {
        timerManager.resume(id)
        _ui.value = ScanUi.Done("Resumed ${label.ifBlank { "timer" }}")
    }
}
