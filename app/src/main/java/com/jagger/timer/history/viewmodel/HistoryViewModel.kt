package com.jagger.timer.history.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jagger.timer.core.data.model.DoseLogWithPill
import com.jagger.timer.core.data.repository.PillRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val pillRepository: PillRepository
) : ViewModel() {

    val groupedLogs: StateFlow<Map<LocalDate, List<DoseLogWithPill>>> = pillRepository.getLogsWithPillInfo()
        .map { logs ->
            logs.groupBy { logWithPill ->
                Instant.ofEpochMilli(logWithPill.log.timestamp)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
}
