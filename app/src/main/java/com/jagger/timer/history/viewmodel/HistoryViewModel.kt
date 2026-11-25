// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.history.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jagger.timer.core.data.local.dao.DoseHistoryRow
import com.jagger.timer.core.data.local.entity.DoseStatus
import com.jagger.timer.core.data.local.entity.PillEntity
import com.jagger.timer.core.data.repository.PillRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val pillRepository: PillRepository
) : ViewModel() {

    companion object {
        const val ALL_PILLS = -1L
        private const val LIMIT = 1000
        const val HEATMAP_DAYS = 30
    }

    private val _pillFilter = MutableStateFlow(ALL_PILLS)
    val pillFilter: StateFlow<Long> = _pillFilter.asStateFlow()

    private val _statusFilter = MutableStateFlow<DoseStatus?>(null)
    val statusFilter: StateFlow<DoseStatus?> = _statusFilter.asStateFlow()

    /** Every medication, archived included, for the filter. */
    val pills: StateFlow<List<PillEntity>> = pillRepository.getAllPillsIncludingArchived()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rows: StateFlow<List<DoseHistoryRow>> = combine(_pillFilter, _statusFilter) { p, s -> p to s }
        .flatMapLatest { (p, s) -> pillRepository.getHistory(p, s?.name ?: "", LIMIT) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Daily adherence for the last 30 days, for the heatmap. Follows the medication filter but not the status filter. */
    val adherence: StateFlow<List<com.jagger.timer.core.util.DayAdherence>> = _pillFilter
        .flatMapLatest { p ->
            val zone = java.time.ZoneId.systemDefault()
            val since = java.time.LocalDate.now(zone).minusDays(HEATMAP_DAYS - 1L).atStartOfDay(zone).toInstant().toEpochMilli()
            pillRepository.getHistorySince(p, since).map { rows ->
                com.jagger.timer.core.util.AdherenceCalc.last(HEATMAP_DAYS, rows, zone, java.time.LocalDate.now(zone))
            }
        }
        // Start with 30 empty days (not an empty list): the heatmap then has its full height from the first frame, so
        // the list below doesn't jump when the data arrives and push the heatmap off the top of the screen.
        .stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000),
            com.jagger.timer.core.util.AdherenceCalc.last(HEATMAP_DAYS, emptyList(), java.time.ZoneId.systemDefault(), java.time.LocalDate.now())
        )

    /** CSV of everything the current filters match (not just the 1000 rows on screen). */
    suspend fun exportCsv(zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String {
        val all = pillRepository.getHistory(_pillFilter.value, _statusFilter.value?.name ?: "", Int.MAX_VALUE).first()
        return com.jagger.timer.core.util.HistoryCsv.build(all, zone)
    }

    fun setPillFilter(pillId: Long) { _pillFilter.value = pillId }
    fun setStatusFilter(status: DoseStatus?) { _statusFilter.value = status }
}
