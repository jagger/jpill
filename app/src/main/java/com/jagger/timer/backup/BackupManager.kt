// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.backup

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import com.jagger.timer.core.data.local.AppDatabase
import com.jagger.timer.core.data.local.DB_NAME
import com.jagger.timer.core.data.local.DB_VERSION
import com.jagger.timer.core.receiver.AlarmReceiver
import com.jagger.timer.core.util.DataEraser
import com.jagger.timer.reminderlog.ReminderLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manual backup and restore. A backup is a consistent snapshot of the database (`VACUUM INTO`), encrypted with a
 * passphrase the user chooses ([BackupCrypto]) and written wherever the user points the system file picker. Nothing
 * is uploaded by jPill. Restore checks the file thoroughly BEFORE touching live data, keeps a safety copy of the
 * current database, then swaps the file; the app must restart afterwards because the open database is replaced.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val eraser: DataEraser
) {
    private val tempSnapshot get() = File(context.cacheDir, "backup-snapshot.db")
    private val tempRestore get() = File(context.cacheDir, "backup-restore.db")
    /** The data as it was just before the last restore; one copy, replaced by the next restore. */
    val safetyCopy get() = File(context.filesDir, SAFETY_COPY_NAME)

    /** The safety copy is plain health data, so it must not linger: "Delete all data" removes it too. */
    fun deleteSafetyCopy() { safetyCopy.delete() }

    suspend fun export(out: OutputStream, passphrase: CharArray) = withContext(Dispatchers.IO) {
        try {
            snapshot(tempSnapshot)
            val encrypted = BackupCrypto.encrypt(tempSnapshot.readBytes(), passphrase)
            out.write(encrypted)
            out.flush()
            ReminderLog.log("BACKUP", "exported ${encrypted.size} bytes")
        } finally {
            tempSnapshot.delete()
        }
    }

    /** Throws [BackupException] for anything wrong with the file or passphrase; live data is untouched in that case. */
    suspend fun restore(input: InputStream, passphrase: CharArray) = withContext(Dispatchers.IO) {
        try {
            val plain = BackupCrypto.decrypt(readCapped(input), passphrase)
            tempRestore.delete()
            tempRestore.writeBytes(plain)
            validate(tempRestore)

            snapshot(safetyCopy)
            eraser.eraseAll() // cancels every alarm and posted notification belonging to the data being replaced
            database.close()
            val live = context.getDatabasePath(DB_NAME)
            listOf("", "-wal", "-shm", "-journal").forEach { File(live.path + it).delete() }
            tempRestore.copyTo(live, overwrite = true)
            ReminderLog.log("BACKUP", "restored; restart pending")
            scheduleRearm()
        } finally {
            tempRestore.delete()
        }
    }

    private fun snapshot(target: File) {
        target.delete()
        database.openHelper.writableDatabase.execSQL("VACUUM INTO '${target.absolutePath}'")
    }

    private fun readCapped(input: InputStream): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > BackupCrypto.MAX_FILE_BYTES) throw BackupException(BackupException.Reason.TOO_LARGE, "That file is too big to be a jPill backup.")
        }
        return out.toByteArray()
    }

    /** The decrypted file must be a healthy jPill database that this version of the app can open. */
    private fun validate(file: File) {
        val magic = ByteArray(16)
        file.inputStream().use { it.read(magic) }
        if (!magic.contentEquals("SQLite format 3\u0000".toByteArray(Charsets.ISO_8859_1))) {
            throw BackupException(BackupException.Reason.INVALID_DATA, "The backup doesn't contain a valid database.")
        }
        val db = try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        } catch (e: Exception) {
            throw BackupException(BackupException.Reason.INVALID_DATA, "The backup doesn't contain a valid database.")
        }
        db.use {
            if (it.version > DB_VERSION) {
                throw BackupException(BackupException.Reason.NEWER_VERSION, "This backup was made by a newer jPill. Update jPill and try again.")
            }
            val ok = it.version in 1..DB_VERSION &&
                DatabaseUtils.stringForQuery(it, "PRAGMA integrity_check", null) == "ok" &&
                DatabaseUtils.longForQuery(
                    it, "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name IN ('pills','schedules','dose_logs')", null
                ) == 3L
            if (!ok) throw BackupException(BackupException.Reason.INVALID_DATA, "The backup doesn't contain a valid jPill database.")
        }
    }

    /** This process is about to be replaced: ask a fresh one (through an alarm, which survives the restart) to re-arm every reminder. */
    private fun scheduleRearm() {
        val pi = PendingIntent.getBroadcast(
            context, REARM_REQUEST_CODE,
            Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_REARM_ALL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
            .set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 2_000L, pi)
    }

    companion object {
        private const val REARM_REQUEST_CODE = 9_900_001
        private const val SAFETY_COPY_NAME = "pre-restore.db"
        private const val SAFETY_COPY_MAX_AGE_MS = 7L * 24 * 3600 * 1000

        /** Called at app start: a rollback copy is only useful right after a restore, so drop it after a week. */
        fun purgeStaleSafetyCopy(context: Context, now: Long = System.currentTimeMillis()) {
            val f = File(context.filesDir, SAFETY_COPY_NAME)
            if (f.exists() && now - f.lastModified() > SAFETY_COPY_MAX_AGE_MS) f.delete()
        }
    }
}
