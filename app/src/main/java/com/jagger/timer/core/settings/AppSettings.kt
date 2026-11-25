// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.core.settings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** App settings (privacy and appearance), kept in preferences (not the database) so receivers and services can read them cheaply. */
@Singleton
class AppSettings @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    private val _lockEnabled = MutableStateFlow(prefs.getBoolean(KEY_LOCK, false))
    private val _lockTimeoutSeconds = MutableStateFlow(prefs.getInt(KEY_TIMEOUT, DEFAULT_TIMEOUT_SECONDS))
    private val _hideNames = MutableStateFlow(prefs.getBoolean(KEY_HIDE_NAMES, false))

    private val _themeMode = MutableStateFlow(
        runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: "") }.getOrDefault(ThemeMode.SYSTEM)
    )
    val themeModeFlow: StateFlow<ThemeMode> = _themeMode

    var themeMode: ThemeMode
        get() = _themeMode.value
        set(v) { prefs.edit().putString(KEY_THEME, v.name).apply(); _themeMode.value = v }

    val lockEnabledFlow: StateFlow<Boolean> = _lockEnabled
    val lockTimeoutFlow: StateFlow<Int> = _lockTimeoutSeconds
    val hideNamesFlow: StateFlow<Boolean> = _hideNames

    var lockEnabled: Boolean
        get() = _lockEnabled.value
        set(v) { prefs.edit().putBoolean(KEY_LOCK, v).apply(); _lockEnabled.value = v }

    /** How long the app can be in the background before it asks to unlock again. 0 = every time. */
    var lockTimeoutSeconds: Int
        get() = _lockTimeoutSeconds.value
        set(v) { prefs.edit().putInt(KEY_TIMEOUT, v).apply(); _lockTimeoutSeconds.value = v }

    /** Show a generic "Medication reminder" instead of the medicine name while the phone is locked. */
    var hideNamesOnLockScreen: Boolean
        get() = _hideNames.value
        set(v) { prefs.edit().putBoolean(KEY_HIDE_NAMES, v).apply(); _hideNames.value = v }

    companion object {
        const val DEFAULT_TIMEOUT_SECONDS = 60
        val TIMEOUT_CHOICES = listOf(0 to "Immediately", 30 to "After 30 seconds", 60 to "After 1 minute", 300 to "After 5 minutes", 900 to "After 15 minutes")
        private const val KEY_THEME = "theme_mode"
        private const val KEY_LOCK = "lock_enabled"
        private const val KEY_TIMEOUT = "lock_timeout_seconds"
        private const val KEY_HIDE_NAMES = "hide_names_lock_screen"
    }
}
