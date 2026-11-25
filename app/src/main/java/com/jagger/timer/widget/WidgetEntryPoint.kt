// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.widget

import com.jagger.timer.core.data.repository.PillRepository
import com.jagger.timer.core.util.ScheduleManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Lets the widget (not created by Hilt) reach the app's repository and scheduler. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun pillRepository(): PillRepository
    fun scheduleManager(): ScheduleManager
}
