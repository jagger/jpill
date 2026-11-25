// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.reliability

import android.content.Context
import androidx.lifecycle.ViewModel
import com.jagger.timer.core.settings.AppSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class ReliabilityViewModel @Inject constructor(
    private val checker: ReminderHealthChecker,
    private val settings: AppSettings,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _health = MutableStateFlow(checker.check())
    val health: StateFlow<ReminderHealth> = _health.asStateFlow()

    private val _snoozed = MutableStateFlow(BannerSnooze.isSnoozed(settings.reliabilitySnoozedUntil, System.currentTimeMillis()))
    val bannerSnoozed: StateFlow<Boolean> = _snoozed.asStateFlow()

    val isSamsung: Boolean get() = checker.isSamsung

    /** Re-read the phone's settings; call when the screen comes back to the front (e.g. after the user fixed one). */
    fun refresh() {
        _health.value = checker.check()
        _snoozed.value = BannerSnooze.isSnoozed(settings.reliabilitySnoozedUntil, System.currentTimeMillis())
    }

    fun snoozeBanner() {
        settings.reliabilitySnoozedUntil = System.currentTimeMillis() + BannerSnooze.WEEK_MILLIS
        _snoozed.value = true
    }

    fun fix(issue: ReminderIssue) {
        // Some phones lack a specific screen; fall back to jPill's own settings page.
        runCatching { context.startActivity(checker.fixIntent(issue)) }
            .onFailure { runCatching { context.startActivity(checker.appSettingsIntent()) } }
    }

    fun openAppSettings() { runCatching { context.startActivity(checker.appSettingsIntent()) } }
}
