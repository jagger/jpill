// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.archive.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jagger.timer.core.data.local.entity.DoseStatus
import com.jagger.timer.core.data.local.entity.PillEntity
import com.jagger.timer.core.data.repository.PillRepository
import com.jagger.timer.core.util.ScheduleManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** An archived pill with a short summary of the history that was kept. */
data class ArchivedItem(
    val pill: PillEntity,
    val dosesTaken: Int,
    val lastTakenMillis: Long?
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ArchivedViewModel @Inject constructor(
    private val pillRepository: PillRepository,
    private val scheduleManager: ScheduleManager
) : ViewModel() {

    val items: StateFlow<List<ArchivedItem>> = pillRepository.getArchivedPills()
        .flatMapLatest { pills ->
            if (pills.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(pills.map { pillRepository.getLogsForPill(it.id) }) { logsPerPill ->
                    pills.zip(logsPerPill.toList()).map { (pill, logs) ->
                        val taken = logs.filter { it.status == DoseStatus.TAKEN }
                        ArchivedItem(pill, taken.size, taken.maxOfOrNull { it.timestamp })
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun restore(pill: PillEntity) {
        viewModelScope.launch { scheduleManager.restorePill(pill) }
    }

    fun deleteForever(pill: PillEntity) {
        viewModelScope.launch { scheduleManager.deletePill(pill) }
    }
}
