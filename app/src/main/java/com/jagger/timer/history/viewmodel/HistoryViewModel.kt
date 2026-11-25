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

    /** CSV of everything the current filters match (not just the 1000 rows on screen). */
    suspend fun exportCsv(zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String {
        val all = pillRepository.getHistory(_pillFilter.value, _statusFilter.value?.name ?: "", Int.MAX_VALUE).first()
        return com.jagger.timer.core.util.HistoryCsv.build(all, zone)
    }

    fun setPillFilter(pillId: Long) { _pillFilter.value = pillId }
    fun setStatusFilter(status: DoseStatus?) { _statusFilter.value = status }
}
