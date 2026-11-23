package com.jagger.timer.core.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jagger.timer.core.data.local.entity.DoseLogEntity
import com.jagger.timer.core.data.local.entity.DoseStatus
import com.jagger.timer.core.data.local.entity.PillEntity
import com.jagger.timer.core.data.local.entity.RecurrencePattern
import com.jagger.timer.core.data.local.entity.ScheduleEntity
import com.jagger.timer.core.data.local.entity.ScheduleType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrescriptionDbTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate2To3_keepsExistingRowsAndAddsDefaults() {
        helper.createDatabase("mig-test", 2).apply {
            execSQL(
                "INSERT INTO pills (id, name, dosage, unit, color, asNeeded, minIntervalMinutes, maxPer24h, maxIsDoses, catalogId) " +
                    "VALUES (1, 'Ibuprofen', '200', 'mg', 0, 1, 240, 1200, 0, 'ibuprofen')"
            )
            execSQL("INSERT INTO dose_logs (id, pillId, scheduleId, timestamp, status, note, amount) VALUES (1, 1, NULL, 1000, 'TAKEN', NULL, 200.0)")
            close()
        }

        val db = helper.runMigrationsAndValidate("mig-test", 3, true, AppDatabase.MIGRATION_2_3)

        db.query("SELECT name, asNeeded, minIntervalMinutes, isPrescription, tabletsPerDose, supplyCount, refillThresholdDays, instructions FROM pills WHERE id = 1").use {
            it.moveToFirst()
            assertEquals("Ibuprofen", it.getString(0))
            assertEquals(1, it.getInt(1))
            assertEquals(240, it.getInt(2))
            assertEquals(0, it.getInt(3))          // isPrescription default
            assertEquals(1.0, it.getDouble(4), 0.0) // tabletsPerDose default
            assertEquals(true, it.isNull(5))        // supplyCount null
            assertEquals(7, it.getInt(6))           // refillThresholdDays default
            assertEquals(true, it.isNull(7))
        }
        db.query("SELECT amount FROM dose_logs WHERE id = 1").use {
            it.moveToFirst(); assertEquals(200.0, it.getDouble(0), 0.0)
        }
    }

    private fun memoryDb() = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(), AppDatabase::class.java
    ).allowMainThreadQueries().build()

    @Test
    fun takenDose_decrementsSupplyByTabletsPerDose() = runBlocking {
        val db = memoryDb()
        val dao = db.pillDao()
        val id = dao.insertPill(PillEntity(name = "Metformin", dosage = "500", unit = "mg", color = 0, supplyCount = 30.0, tabletsPerDose = 2.0))
        dao.logDoseAndAdjustSupply(DoseLogEntity(pillId = id, timestamp = 1, status = DoseStatus.TAKEN, amount = 1000.0))
        assertEquals(28.0, dao.getPillById(id)!!.supplyCount!!, 0.0)
        db.close()
    }

    @Test
    fun skippedDose_doesNotChangeSupply() = runBlocking {
        val db = memoryDb()
        val dao = db.pillDao()
        val id = dao.insertPill(PillEntity(name = "X", dosage = "10", unit = "mg", color = 0, supplyCount = 10.0))
        dao.logDoseAndAdjustSupply(DoseLogEntity(pillId = id, timestamp = 1, status = DoseStatus.SKIPPED))
        assertEquals(10.0, dao.getPillById(id)!!.supplyCount!!, 0.0)
        db.close()
    }

    @Test
    fun untrackedSupply_staysNull_andSupplyFloorsAtZero() = runBlocking {
        val db = memoryDb()
        val dao = db.pillDao()
        val noSupply = dao.insertPill(PillEntity(name = "A", dosage = "1", unit = "mg", color = 0))
        dao.logDoseAndAdjustSupply(DoseLogEntity(pillId = noSupply, timestamp = 1, status = DoseStatus.TAKEN))
        assertNull(dao.getPillById(noSupply)!!.supplyCount)

        val low = dao.insertPill(PillEntity(name = "B", dosage = "1", unit = "mg", color = 0, supplyCount = 1.0, tabletsPerDose = 2.0))
        dao.logDoseAndAdjustSupply(DoseLogEntity(pillId = low, timestamp = 1, status = DoseStatus.TAKEN))
        assertEquals(0.0, dao.getPillById(low)!!.supplyCount!!, 0.0)
        db.close()
    }

    @Test
    fun deletingPill_removesItsSchedulesAndDoseHistory_andLeavesOthers() = runBlocking {
        val db = memoryDb()
        val dao = db.pillDao()
        val doomed = dao.savePillWithSchedule(
            PillEntity(name = "Doomed", dosage = "5", unit = "mg", color = 0, supplyCount = 10.0),
            ScheduleEntity(pillId = 0, type = ScheduleType.FIXED_TIME, pattern = RecurrencePattern.DAILY, timeMinutes = 480)
        )
        val keeper = dao.insertPill(PillEntity(name = "Keeper", dosage = "1", unit = "mg", color = 0))
        dao.logDoseAndAdjustSupply(DoseLogEntity(pillId = doomed, timestamp = 1, status = DoseStatus.TAKEN))
        dao.logDoseAndAdjustSupply(DoseLogEntity(pillId = keeper, timestamp = 2, status = DoseStatus.TAKEN))

        dao.deletePill(dao.getPillById(doomed)!!)

        assertNull(dao.getPillById(doomed))
        assertEquals(0, dao.getSchedulesForPill(doomed).first().size)
        assertEquals(0, dao.getLogsForPill(doomed).first().size)
        assertEquals(1, dao.getLogsForPill(keeper).first().size)
        db.close()
    }
}
