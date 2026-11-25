// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.reliability

/** Things that can stop jPill's reminders arriving on time. */
enum class ReminderIssue { NOTIFICATIONS, BATTERY, FULL_SCREEN, EXACT_ALARMS }

/**
 * What the phone currently allows. Pure, so what the banner and the status screen say is unit-tested; the Android
 * calls that fill it in are in [ReminderHealthChecker].
 */
data class ReminderHealth(
    val notificationsAllowed: Boolean,
    val batteryUnrestricted: Boolean,
    val fullScreenAllowed: Boolean,
    val exactAlarmsAllowed: Boolean
) {
    /** Problems in the order worth fixing: no notifications at all is the worst, then battery, then the rest. */
    val issues: List<ReminderIssue> = buildList {
        if (!notificationsAllowed) add(ReminderIssue.NOTIFICATIONS)
        if (!exactAlarmsAllowed) add(ReminderIssue.EXACT_ALARMS)
        if (!batteryUnrestricted) add(ReminderIssue.BATTERY)
        if (!fullScreenAllowed) add(ReminderIssue.FULL_SCREEN)
    }

    val allGood: Boolean get() = issues.isEmpty()

    companion object {
        val Unknown = ReminderHealth(true, true, true, true)

        /** Short phrase for the banner. */
        fun shortReason(issue: ReminderIssue): String = when (issue) {
            ReminderIssue.NOTIFICATIONS -> "notifications are off"
            ReminderIssue.EXACT_ALARMS -> "exact alarms are blocked"
            ReminderIssue.BATTERY -> "battery use isn't unrestricted"
            ReminderIssue.FULL_SCREEN -> "full-screen reminders are off"
        }

        /** "Reminders may be late: battery use isn't unrestricted" / "...and 2 more". */
        fun bannerText(health: ReminderHealth): String? {
            val first = health.issues.firstOrNull() ?: return null
            val more = health.issues.size - 1
            return "Reminders may be late or missing: ${shortReason(first)}" + if (more > 0) " (and $more more)" else ""
        }
    }
}

/** The banner reappears a week after "Not now" (and straight away if the problem list changes elsewhere). */
object BannerSnooze {
    const val WEEK_MILLIS = 7L * 24 * 60 * 60 * 1000
    fun isSnoozed(untilMillis: Long, nowMillis: Long) = nowMillis < untilMillis
}
