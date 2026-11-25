// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Redraws the home-screen widget after a dose changes or a wait ends. */
@Singleton
class WidgetRefresher @Inject constructor(@ApplicationContext private val context: Context) {
    suspend fun update() {
        try {
            TakeNowWidget().updateAll(context)
        } catch (_: Exception) {
            // No widget placed, or the launcher is busy: nothing to refresh.
        }
    }
}
