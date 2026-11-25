// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.core.util

import com.jagger.timer.core.data.local.entity.doseAmount
import com.jagger.timer.core.data.repository.PillRepository
import kotlinx.coroutines.flow.first

/** Shared-ingredient warnings by pill id, computed once for any caller that is not the Home screen (widget, NFC scan). */
object IngredientWarningLoader {
    suspend fun load(repo: PillRepository, nowMillis: Long): Map<Long, IngredientWarning> {
        val since = nowMillis - CooldownCalculator.WINDOW_MILLIS
        val withIngredients = repo.getAllPills().first().filter { IngredientLogic.parse(it.ingredients).isNotEmpty() }
        if (withIngredients.size < 2) return emptyMap()
        val doses = withIngredients.associate { pill ->
            val fallback = pill.doseAmount() ?: 1.0
            pill.id to repo.getTakenLogsSince(pill.id, since).first().map { TakenDose(it.timestamp, it.amount ?: fallback) }
        }
        return IngredientLogic.warnings(withIngredients, doses, nowMillis)
    }
}
