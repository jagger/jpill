// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.backup

import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class BackupException(val reason: Reason, message: String) : Exception(message) {
    enum class Reason { NOT_A_BACKUP, WRONG_PASSPHRASE_OR_DAMAGED, TOO_LARGE, NEWER_VERSION, INVALID_DATA }
}

/**
 * The backup file format. Pure JVM, so it is unit-tested.
 *
 *   "JPILLBK1" | iterations (int) | salt (16) | iv (12) | AES-256-GCM(ciphertext + 16-byte tag)
 *
 * The key comes from the passphrase with PBKDF2-HMAC-SHA256. The header is authenticated (GCM additional data), so
 * changing the iteration count or salt makes decryption fail. A wrong passphrase and a damaged file are
 * indistinguishable on purpose: both fail the GCM tag check.
 */
object BackupCrypto {
    private val MAGIC = "JPILLBK1".toByteArray(Charsets.US_ASCII)
    const val DEFAULT_ITERATIONS = 600_000
    const val MIN_PASSPHRASE = 8
    private const val MIN_ITERATIONS = 100_000
    private const val MAX_ITERATIONS = 5_000_000 // a hostile file must not make the phone grind for minutes
    private const val SALT = 16
    private const val IV = 12
    private const val HEADER = 8 + 4 + SALT + IV
    private const val TAG_BITS = 128
    const val MAX_FILE_BYTES = 64L * 1024 * 1024

    fun encrypt(plain: ByteArray, passphrase: CharArray, iterations: Int = DEFAULT_ITERATIONS, random: SecureRandom = SecureRandom()): ByteArray {
        require(passphrase.size >= MIN_PASSPHRASE) { "passphrase too short" }
        val salt = ByteArray(SALT).also(random::nextBytes)
        val iv = ByteArray(IV).also(random::nextBytes)
        val header = ByteBuffer.allocate(HEADER).put(MAGIC).putInt(iterations).put(salt).put(iv).array()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(passphrase, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(header)
        return header + cipher.doFinal(plain)
    }

    fun decrypt(file: ByteArray, passphrase: CharArray): ByteArray {
        if (file.size < HEADER + TAG_BITS / 8 || !file.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            throw BackupException(BackupException.Reason.NOT_A_BACKUP, "That isn't a jPill backup file.")
        }
        val buf = ByteBuffer.wrap(file, MAGIC.size, HEADER - MAGIC.size)
        val iterations = buf.int
        if (iterations !in MIN_ITERATIONS..MAX_ITERATIONS) {
            throw BackupException(BackupException.Reason.NOT_A_BACKUP, "That isn't a jPill backup file.")
        }
        val salt = ByteArray(SALT).also(buf::get)
        val iv = ByteArray(IV).also(buf::get)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(passphrase, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(file, 0, HEADER)
            cipher.doFinal(file, HEADER, file.size - HEADER)
        } catch (e: GeneralSecurityException) {
            throw BackupException(BackupException.Reason.WRONG_PASSPHRASE_OR_DAMAGED, "Wrong passphrase, or the file is damaged.")
        }
    }

    private fun key(passphrase: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, iterations, 256)
        try {
            return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
