package com.jagger.timer.core.util

/** When the app should ask to unlock. Pure, so it is unit-tested. */
object AppLockPolicy {
    /**
     * @param backgroundedAtMillis when the app last left the screen, or null on a cold start (always locks)
     * @param timeoutSeconds how long it may stay away before locking; 0 = every time
     */
    fun shouldLock(enabled: Boolean, backgroundedAtMillis: Long?, nowMillis: Long, timeoutSeconds: Int): Boolean {
        if (!enabled) return false
        if (backgroundedAtMillis == null) return true
        return nowMillis - backgroundedAtMillis >= timeoutSeconds.coerceAtLeast(0) * 1000L
    }
}
