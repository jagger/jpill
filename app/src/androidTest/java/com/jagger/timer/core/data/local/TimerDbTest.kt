// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.core.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TimerDbTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun migrate4To5_addsTimersTableAndKeepsPills() {
        helper.createDatabase("mig-timers", 4).apply {
            execSQL(
                "INSERT INTO pills (id, name, dosage, unit, color, asNeeded, maxIsDoses, isPrescription, tabletsPerDose, " +
                    "refillThresholdDays, isArchived) VALUES (1, 'Zoloft', '50', 'mg', 0, 0, 0, 1, 1.0, 7, 0)"
            )
            close()
        }
        val db = helper.runMigrationsAndValidate("mig-timers", 5, true, AppDatabase.MIGRATION_4_5)
        db.execSQL("INSERT INTO timers (label, durationMillis, state, endAtMillis, remainingMillis, createdAt) VALUES ('tea', 60000, 'RUNNING', 5, NULL, 1)")
        db.query("SELECT COUNT(*) FROM timers").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        db.query("SELECT name FROM pills WHERE id = 1").use { it.moveToFirst(); assertEquals("Zoloft", it.getString(0)) }
    }
}
