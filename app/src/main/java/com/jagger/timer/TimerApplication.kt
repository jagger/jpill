// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TimerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        com.jagger.timer.reminderlog.ReminderLog.init(this)
        com.jagger.timer.core.receiver.ReminderNotifier.ensureChannel(this)
    }
}
