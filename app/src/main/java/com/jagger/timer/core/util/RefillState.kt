// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.core.util

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers how far each prescription's refill reminders have escalated: 0 = none, 1 = "low" reminder sent,
 * 2 = "out of pills" reminder sent. So you get at most one of each per low spell, not one after every dose.
 * Cleared when supply is healthy again. Kept in preferences, not the database.
 */
@Singleton
class RefillState @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("refill_state", Context.MODE_PRIVATE)

    fun level(pillId: Long): Int = prefs.getInt(key(pillId), 0)
    fun setLevel(pillId: Long, level: Int) = prefs.edit().putInt(key(pillId), level).apply()
    fun clear(pillId: Long) = prefs.edit().remove(key(pillId)).apply()

    private fun key(pillId: Long) = "level_$pillId"
}
