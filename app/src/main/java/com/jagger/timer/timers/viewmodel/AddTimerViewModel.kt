package com.jagger.timer.timers.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jagger.timer.core.data.local.entity.TimerEntity
import com.jagger.timer.core.data.local.entity.TimerType
import com.jagger.timer.core.data.repository.TimerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddTimerUiState(
    val name: String = "",
    val durationMinutes: String = "5",
    val type: TimerType = TimerType.COUNTDOWN,
    val isSaved: Boolean = false
)

@HiltViewModel
class AddTimerViewModel @Inject constructor(
    private val timerRepository: TimerRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddTimerUiState())
    val uiState: StateFlow<AddTimerUiState> = _uiState.asStateFlow()

    fun updateName(name: String) = _uiState.update { it.copy(name = name) }
    fun updateDuration(minutes: String) = _uiState.update { it.copy(durationMinutes = minutes) }
    fun updateType(type: TimerType) = _uiState.update { it.copy(type = type) }

    fun saveTimer() {
        val state = _uiState.value
        val mins = state.durationMinutes.toIntOrNull() ?: 5
        viewModelScope.launch {
            timerRepository.saveTimer(
                TimerEntity(
                    name = if (state.name.isBlank()) "Timer" else state.name,
                    durationSeconds = mins * 60,
                    intervalSeconds = if (state.type == TimerType.REPEATING) mins * 60 else null,
                    type = state.type,
                    isActive = false,
                    color = 0xFF2196F3.toInt()
                )
            )
            _uiState.update { it.copy(isSaved = true) }
        }
    }
}
