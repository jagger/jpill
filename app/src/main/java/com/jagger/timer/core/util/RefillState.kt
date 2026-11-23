package com.jagger.timer.core.util

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers which prescriptions already got their "time to refill" reminder, so it fires once per low spell
 * (not after every dose). Cleared when supply is healthy again. Kept in preferences, not the database.
 */
@Singleton
class RefillState @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("refill_state", Context.MODE_PRIVATE)

    fun wasNotified(pillId: Long): Boolean = prefs.getBoolean(key(pillId), false)
    fun setNotified(pillId: Long) = prefs.edit().putBoolean(key(pillId), true).apply()
    fun clear(pillId: Long) = prefs.edit().remove(key(pillId)).apply()

    private fun key(pillId: Long) = "notified_$pillId"
}
