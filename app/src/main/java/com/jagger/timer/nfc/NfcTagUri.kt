// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.nfc

import java.security.SecureRandom
import java.util.Base64

/**
 * What goes on a medicine's NFC sticker: one NDEF URI record, `jpill://tag/<random id>`. The id is 128 random bits
 * (URL-safe base64, 22 characters) and says nothing about the medicine, so a lost sticker reveals nothing. The app
 * keeps the mapping id -> medicine in its own database.
 */
object NfcTagUri {
    const val SCHEME = "jpill"
    const val HOST = "tag"
    private val idPattern = Regex("^[A-Za-z0-9_-]{16,64}$")
    private val uriPattern = Regex("^$SCHEME://$HOST/([A-Za-z0-9_-]{16,64})$")

    fun newId(random: SecureRandom = SecureRandom()): String {
        val bytes = ByteArray(16).also { random.nextBytes(it) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun isValidId(id: String?): Boolean = id != null && idPattern.matches(id)

    fun toUri(id: String): String {
        require(isValidId(id)) { "invalid tag id" }
        return "$SCHEME://$HOST/$id"
    }

    /** The tag id in a `jpill://tag/<id>` URI, or null for anything else (including other apps' tags). */
    fun parse(uri: String?): String? = uri?.trim()?.let { uriPattern.matchEntire(it)?.groupValues?.get(1) }
}
