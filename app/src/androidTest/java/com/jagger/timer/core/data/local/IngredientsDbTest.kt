package com.jagger.timer.core.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IngredientsDbTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun migrate5To6_addsNullableIngredientsAndKeepsRows() {
        helper.createDatabase("mig-ing", 5).apply {
            execSQL(
                "INSERT INTO pills (id, name, dosage, unit, color, asNeeded, maxIsDoses, isPrescription, tabletsPerDose, " +
                    "refillThresholdDays, isArchived) VALUES (1, 'Tylenol', '500', 'mg', 0, 1, 0, 0, 1.0, 7, 0)"
            )
            close()
        }
        val db = helper.runMigrationsAndValidate("mig-ing", 6, true, AppDatabase.MIGRATION_5_6)
        db.query("SELECT name, ingredients FROM pills WHERE id = 1").use {
            it.moveToFirst()
            assertEquals("Tylenol", it.getString(0))
            assertTrue(it.isNull(1))
        }
        db.execSQL("UPDATE pills SET ingredients = 'acetaminophen' WHERE id = 1")
        db.query("SELECT ingredients FROM pills").use { it.moveToFirst(); assertEquals("acetaminophen", it.getString(0)) }
    }
}
