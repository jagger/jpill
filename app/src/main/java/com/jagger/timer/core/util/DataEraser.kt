package com.jagger.timer.core.util

import com.jagger.timer.core.data.repository.PillRepository
import com.jagger.timer.widget.WidgetRefresher
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wipes everything the user entered: every medicine (archived too) with its schedules and dose history, and every timer.
 * Goes through the normal delete paths so alarms, posted notifications and refill reminders are cancelled as well.
 * App settings (lock, theme) are kept.
 */
@Singleton
class DataEraser @Inject constructor(
    private val pillRepository: PillRepository,
    private val scheduleManager: ScheduleManager,
    private val timerManager: TimerManager,
    private val widgetRefresher: WidgetRefresher
) {
    suspend fun eraseAll() {
        pillRepository.getAllPillsIncludingArchived().first().forEach { scheduleManager.deletePill(it) }
        timerManager.observeAll().first().forEach { timerManager.delete(it.id) }
        widgetRefresher.update()
    }
}
