package com.jagger.timer.timers.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jagger.timer.core.data.local.entity.TimerEntity
import com.jagger.timer.core.data.repository.TimerRepository
import com.jagger.timer.core.util.TimerAlarmScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TimersViewModel @Inject constructor(
    private val timerRepository: TimerRepository,
    private val timerAlarmScheduler: TimerAlarmScheduler
) : ViewModel() {

    val timers: StateFlow<List<TimerEntity>> = timerRepository.getAllTimers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleTimer(timer: TimerEntity) {
        viewModelScope.launch {
            val updated = timer.copy(
                isActive = !timer.isActive,
                startTimeMillis = if (!timer.isActive) System.currentTimeMillis() else null
            )
            timerRepository.updateTimer(updated)
            if (updated.isActive) {
                timerAlarmScheduler.scheduleTimer(updated)
            } else {
                timerAlarmScheduler.cancelTimer(updated)
            }
        }
    }

    fun deleteTimer(timer: TimerEntity) {
        viewModelScope.launch {
            timerAlarmScheduler.cancelTimer(timer)
            timerRepository.deleteTimer(timer)
        }
    }
}
