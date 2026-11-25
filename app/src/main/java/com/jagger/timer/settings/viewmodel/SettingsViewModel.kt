package com.jagger.timer.settings.viewmodel

import androidx.lifecycle.ViewModel
import com.jagger.timer.core.settings.AppSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(val settings: AppSettings) : ViewModel()
