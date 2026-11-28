// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCryptoTest {
    private val pass = "correct horse".toCharArray()
    private val data = "SQLite format 3\u0000 pretend database".toByteArray()
    private val fast = 100_000 // the minimum accepted, to keep tests quick

    @Test fun roundTrip() {
        assertArrayEquals(data, BackupCrypto.decrypt(BackupCrypto.encrypt(data, pass, fast), pass))
    }

    @Test fun plaintextIsNotInTheFile() {
        val file = BackupCrypto.encrypt(data, pass, fast)
        assertFalse(String(file, Charsets.ISO_8859_1).contains("pretend database"))
    }

    @Test fun eachBackupUsesFreshSaltAndIv() {
        assertFalse(BackupCrypto.encrypt(data, pass, fast).contentEquals(BackupCrypto.encrypt(data, pass, fast)))
    }

    @Test fun wrongPassphraseFails() {
        val file = BackupCrypto.encrypt(data, pass, fast)
        val e = assertThrows(BackupException::class.java) { BackupCrypto.decrypt(file, "incorrect horse".toCharArray()) }
        assertEquals(BackupException.Reason.WRONG_PASSPHRASE_OR_DAMAGED, e.reason)
    }

    @Test fun tamperedCiphertextOrHeaderFails() {
        val file = BackupCrypto.encrypt(data, pass, fast)
        for (index in listOf(file.size - 1, 20, 40)) { // tag, salt, iv
            val bad = file.copyOf().also { it[index] = (it[index].toInt() xor 1).toByte() }
            assertThrows(BackupException::class.java) { BackupCrypto.decrypt(bad, pass) }
        }
    }

    @Test fun notABackup() {
        for (junk in listOf(ByteArray(0), "hello world, not a backup at all, nope".toByteArray(), ByteArray(200))) {
            val e = assertThrows(BackupException::class.java) { BackupCrypto.decrypt(junk, pass) }
            assertEquals(BackupException.Reason.NOT_A_BACKUP, e.reason)
        }
    }

    @Test fun absurdIterationCountIsRejectedBeforeAnyWork() {
        val file = BackupCrypto.encrypt(data, pass, fast)
        java.nio.ByteBuffer.wrap(file).putInt(8, Int.MAX_VALUE)
        assertEquals(BackupException.Reason.NOT_A_BACKUP, assertThrows(BackupException::class.java) { BackupCrypto.decrypt(file, pass) }.reason)
    }

    @Test fun shortPassphraseIsRefused() {
        assertThrows(IllegalArgumentException::class.java) { BackupCrypto.encrypt(data, "short".toCharArray(), fast) }
    }
}
