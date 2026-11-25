package com.jagger.timer.widget

import com.jagger.timer.core.data.local.entity.doseAmount
import com.jagger.timer.core.data.repository.PillRepository
import com.jagger.timer.core.util.CooldownCalculator
import com.jagger.timer.core.util.IngredientLogic
import com.jagger.timer.core.util.TakenDose
import kotlinx.coroutines.flow.first

/** Loads what the widget shows: the same cooldown and shared-ingredient rules the Home screen uses. */
object WidgetData {
    suspend fun load(repo: PillRepository, nowMillis: Long): List<WidgetRow> {
        val since = nowMillis - CooldownCalculator.WINDOW_MILLIS
        val asNeeded = repo.getAsNeededPills().first()
        if (asNeeded.isEmpty()) return emptyList()
        val logs = asNeeded.associate { it.id to repo.getTakenLogsSince(it.id, since).first() }

        val withIngredients = repo.getAllPills().first().filter { IngredientLogic.parse(it.ingredients).isNotEmpty() }
        val warnings = if (withIngredients.size < 2) emptyMap() else {
            val doses = withIngredients.associate { pill ->
                val fallback = pill.doseAmount() ?: 1.0
                pill.id to repo.getTakenLogsSince(pill.id, since).first().map { TakenDose(it.timestamp, it.amount ?: fallback) }
            }
            IngredientLogic.warnings(withIngredients, doses, nowMillis)
        }
        return WidgetModel.build(asNeeded, logs, warnings, nowMillis)
    }
}
