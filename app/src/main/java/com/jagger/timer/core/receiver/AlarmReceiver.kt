package com.jagger.timer.core.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.jagger.timer.core.service.PillReminderService
import com.jagger.timer.core.util.ScheduleManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var scheduleManager: ScheduleManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        
        when (action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                scope.launch {
                    scheduleManager.refreshAlarms()
                }
            }
            "com.jagger.timer.ACTION_PILL_REMINDER" -> {
                startService(context, intent)
            }
        }
    }

    private fun startService(context: Context, intent: Intent) {
        val serviceIntent = Intent(context, PillReminderService::class.java).apply {
            putExtras(intent)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
