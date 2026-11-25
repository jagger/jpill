// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.nfc

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Everything a jPill NFC sticker can say, as one NDEF URI record:
 *
 * - medicine: `jpill://tag/<id>?name=Amlodipine`: a random id (the app maps it to a medicine) and, unless the user
 *   turned it off for that sticker, the medicine's name. The name is plain text anyone with a phone can read; it
 *   lets another phone say what the tag is for. Never a dose or any other detail.
 * - timer: `jpill://timer/<id>?label=Washer&secs=2700`: a random id, the timer's name and its length, so scanning
 *   recreates the timer on a fresh install.
 */
sealed interface NfcPayload {
    data class Medicine(val tagId: String, val name: String? = null) : NfcPayload
    data class Timer(val tagId: String, val label: String, val seconds: Long) : NfcPayload

    companion object {
        const val SCHEME = "jpill"
        const val HOST_MEDICINE = "tag"
        const val HOST_TIMER = "timer"
        const val MAX_LABEL = 40
        const val MAX_SECONDS = 99L * 3600 + 59 * 60 + 59

        private val medicine = Regex("^$SCHEME://$HOST_MEDICINE/([A-Za-z0-9_-]{16,64})(?:\\?(.*))?$")
        private val timer = Regex("^$SCHEME://$HOST_TIMER/([A-Za-z0-9_-]{16,64})\\?(.*)$")

        fun medicineUri(tagId: String, name: String? = null): String {
            require(NfcTagUri.isValidId(tagId)) { "invalid tag id" }
            val n = name?.trim()?.take(MAX_LABEL)?.takeIf { it.isNotEmpty() } ?: return "$SCHEME://$HOST_MEDICINE/$tagId"
            return "$SCHEME://$HOST_MEDICINE/$tagId?name=${URLEncoder.encode(n, "UTF-8")}"
        }

        fun timerUri(tagId: String, label: String, seconds: Long): String {
            require(NfcTagUri.isValidId(tagId)) { "invalid tag id" }
            require(seconds in 1..MAX_SECONDS) { "invalid duration" }
            val l = URLEncoder.encode(label.trim().take(MAX_LABEL), "UTF-8")
            return "$SCHEME://$HOST_TIMER/$tagId?label=$l&secs=$seconds"
        }

        private fun queryParams(query: String): Map<String, String> = query.split('&').mapNotNull { p ->
            p.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] }
        }.toMap()

        /** Reads a `jpill://` URI, or null for anything else (other apps' tags, malformed or out-of-range values). */
        fun parse(uri: String?): NfcPayload? {
            val s = uri?.trim() ?: return null
            medicine.matchEntire(s)?.let { m ->
                val name = queryParams(m.groupValues[2])["name"]
                    ?.let { v -> runCatching { URLDecoder.decode(v, "UTF-8") }.getOrNull() }
                    ?.trim()?.take(MAX_LABEL)?.takeIf { it.isNotEmpty() }
                return Medicine(m.groupValues[1], name)
            }
            val m = timer.matchEntire(s) ?: return null
            val params = queryParams(m.groupValues[2])
            val secs = params["secs"]?.toLongOrNull()?.takeIf { it in 1..MAX_SECONDS } ?: return null
            val label = runCatching { URLDecoder.decode(params["label"].orEmpty(), "UTF-8") }.getOrNull() ?: return null
            return Timer(m.groupValues[1], label.trim().take(MAX_LABEL), secs)
        }
    }
}
