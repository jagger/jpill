// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.core.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockPolicyTest {
    @Test fun disabledNeverLocks() = assertFalse(AppLockPolicy.shouldLock(false, null, 1000, 0))
    @Test fun coldStartLocks() = assertTrue(AppLockPolicy.shouldLock(true, null, 1000, 300))
    @Test fun withinTimeoutStaysOpen() = assertFalse(AppLockPolicy.shouldLock(true, 0, 29_000, 30))
    @Test fun atTimeoutLocks() = assertTrue(AppLockPolicy.shouldLock(true, 0, 30_000, 30))
    @Test fun zeroTimeoutAlwaysLocks() = assertTrue(AppLockPolicy.shouldLock(true, 5_000, 5_000, 0))
}
