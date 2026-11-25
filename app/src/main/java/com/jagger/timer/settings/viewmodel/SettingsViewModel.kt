// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.settings.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jagger.timer.core.settings.AppSettings
import com.jagger.timer.core.util.DataEraser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    val settings: AppSettings,
    private val eraser: DataEraser
) : ViewModel() {
    fun eraseAllData(onDone: () -> Unit) {
        viewModelScope.launch {
            eraser.eraseAll()
            onDone()
        }
    }
}
