// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TimerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        com.jagger.timer.reminderlog.ReminderLog.init(this)
        // Shared exports (history CSV, reminder log) hold health data in plain text; don't leave them in the cache.
        runCatching { java.io.File(cacheDir, "exports").deleteRecursively() }
        com.jagger.timer.core.receiver.ReminderNotifier.ensureChannel(this)
        runCatching { com.jagger.timer.backup.BackupManager.purgeStaleSafetyCopy(this) }
    }
}
