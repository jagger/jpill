// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.widget

import com.jagger.timer.core.data.repository.PillRepository
import com.jagger.timer.core.util.CooldownCalculator
import kotlinx.coroutines.flow.first

/** Loads what the widget shows: the same cooldown and shared-ingredient rules the Home screen uses. */
object WidgetData {
    suspend fun load(repo: PillRepository, nowMillis: Long): List<WidgetRow> {
        val since = nowMillis - CooldownCalculator.WINDOW_MILLIS
        val asNeeded = repo.getAsNeededPills().first()
        if (asNeeded.isEmpty()) return emptyList()
        val logs = asNeeded.associate { it.id to repo.getTakenLogsSince(it.id, since).first() }

        val warnings = com.jagger.timer.core.util.IngredientWarningLoader.load(repo, nowMillis)
        return WidgetModel.build(asNeeded, logs, warnings, nowMillis)
    }
}
