package com.jagger.timer.core.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jagger.timer.core.data.local.entity.DoseLogEntity
import com.jagger.timer.core.data.local.entity.DoseStatus
import com.jagger.timer.core.data.local.entity.PillEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryDbTest {

    private fun memoryDb() = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(), AppDatabase::class.java
    ).allowMainThreadQueries().build()

    @Test
    fun history_listsAllMedicationsNewestFirst_includingArchived() = runBlocking {
        val db = memoryDb(); val dao = db.pillDao()
        val a = dao.insertPill(PillEntity(name = "Alpha", dosage = "10", unit = "mg", color = 0))
        val b = dao.insertPill(PillEntity(name = "Beta", dosage = "5", unit = "mg", color = 0))
        dao.logDoseAndAdjustSupply(DoseLogEntity(pillId = a, timestamp = 100, status = DoseStatus.TAKEN, amount = 10.0))
        dao.logDoseAndAdjustSupply(DoseLogEntity(pillId = b, timestamp = 300, status = DoseStatus.SKIPPED, amount = 5.0))
        dao.logDoseAndAdjustSupply(DoseLogEntity(pillId = a, timestamp = 200, status = DoseStatus.MISSED, amount = 10.0))
        dao.setArchived(b, true, 400L) // archived medications must still appear

        val rows = dao.getHistory(-1, "", 100).first()
        assertEquals(listOf(300L, 200L, 100L), rows.map { it.timestamp })          // newest first
        assertEquals(listOf("Beta", "Alpha", "Alpha"), rows.map { it.pillName })
        assertEquals(listOf(true, false, false), rows.map { it.isArchived })
        assertEquals("mg", rows[0].pillUnit)
        assertEquals(DoseStatus.SKIPPED, rows[0].status)
        db.close()
    }

    @Test
    fun history_filtersByMedicationAndStatus() = runBlocking {
        val db = memoryDb(); val dao = db.pillDao()
        val a = dao.insertPill(PillEntity(name = "Alpha", dosage = "10", unit = "mg", color = 0))
        val b = dao.insertPill(PillEntity(name = "Beta", dosage = "5", unit = "mg", color = 0))
        dao.logDoseAndAdjustSupply(DoseLogEntity(pillId = a, timestamp = 1, status = DoseStatus.TAKEN))
        dao.logDoseAndAdjustSupply(DoseLogEntity(pillId = a, timestamp = 2, status = DoseStatus.MISSED))
        dao.logDoseAndAdjustSupply(DoseLogEntity(pillId = b, timestamp = 3, status = DoseStatus.TAKEN))

        assertEquals(2, dao.getHistory(a, "", 100).first().size)
        assertEquals(1, dao.getHistory(-1, "MISSED", 100).first().size)
        assertEquals(listOf(3L, 1L), dao.getHistory(-1, "TAKEN", 100).first().map { it.timestamp })
        assertEquals(0, dao.getHistory(b, "MISSED", 100).first().size)
        db.close()
    }

    @Test
    fun history_limitAndDeletedMedicationsLogsAreGone() = runBlocking {
        val db = memoryDb(); val dao = db.pillDao()
        val a = dao.insertPill(PillEntity(name = "Alpha", dosage = "10", unit = "mg", color = 0))
        val b = dao.insertPill(PillEntity(name = "Beta", dosage = "5", unit = "mg", color = 0))
        (1..5).forEach { dao.logDoseAndAdjustSupply(DoseLogEntity(pillId = a, timestamp = it.toLong(), status = DoseStatus.TAKEN)) }
        dao.logDoseAndAdjustSupply(DoseLogEntity(pillId = b, timestamp = 10, status = DoseStatus.TAKEN))
        assertEquals(3, dao.getHistory(-1, "", 3).first().size)

        dao.deletePill(dao.getPillById(b)!!)
        assertTrue(dao.getHistory(-1, "", 100).first().none { it.pillName == "Beta" })
        assertEquals(listOf("Alpha"), dao.getAllPillsIncludingArchived().first().map { it.name })
        db.close()
    }

    @Test
    fun missedLog_doesNotChangeSupply() = runBlocking {
        val db = memoryDb(); val dao = db.pillDao()
        val a = dao.insertPill(PillEntity(name = "Alpha", dosage = "10", unit = "mg", color = 0, supplyCount = 10.0))
        dao.logDoseAndAdjustSupply(DoseLogEntity(pillId = a, timestamp = 1, status = DoseStatus.MISSED))
        assertEquals(10.0, dao.getPillById(a)!!.supplyCount!!, 0.0)
        db.close()
    }
}
