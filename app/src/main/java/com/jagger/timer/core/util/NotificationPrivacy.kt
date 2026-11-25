package com.jagger.timer.core.util

import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat

/**
 * When [hide] is on, the lock screen shows a generic "Medication reminder" instead of the medicine name.
 * The public version carries the same [actions], so Taken/Skip still work without unlocking.
 */
fun NotificationCompat.Builder.hideNameOnLockScreen(
    context: Context,
    channelId: String,
    hide: Boolean,
    actions: List<Pair<String, PendingIntent>> = emptyList()
): NotificationCompat.Builder {
    if (!hide) return this
    val public = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        .setContentTitle("Medication reminder")
        .setContentText("Open jPill for details.")
    actions.forEach { (label, intent) -> public.addAction(0, label, intent) }
    return setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(public.build())
}
