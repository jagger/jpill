// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.timers.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jagger.timer.core.data.local.entity.TimerEntity
import com.jagger.timer.core.util.TimerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A timer plus the clock reading it was rendered against (carried so a 1 s tick always re-emits). */
data class TimerItem(val timer: TimerEntity, val nowMillis: Long)

@HiltViewModel
class TimersViewModel @Inject constructor(
    private val manager: TimerManager,
    private val tagDirectory: com.jagger.timer.nfc.NfcTagDirectory
) : ViewModel() {

    private val ticker = flow { while (true) { emit(System.currentTimeMillis()); delay(500) } }

    val timers: StateFlow<List<TimerItem>> = combine(manager.observeAll(), ticker) { list, now ->
        list.map { TimerItem(it, now) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun start(label: String, durationMillis: Long) {
        if (durationMillis > 0) viewModelScope.launch { manager.start(label, durationMillis) }
    }
    fun pause(id: Long) = viewModelScope.launch { manager.pause(id) }
    fun resume(id: Long) = viewModelScope.launch { manager.resume(id) }
    fun restart(id: Long) = viewModelScope.launch { manager.restart(id) }
    fun setNfcTag(id: Long, tagId: String?) = viewModelScope.launch { manager.setNfcTag(id, tagId) }
    suspend fun tagOwner(tagId: String, timerId: Long): String? = tagDirectory.ownerName(tagId, exceptTimerId = timerId)
    fun delete(id: Long) = viewModelScope.launch { manager.delete(id) }
}
