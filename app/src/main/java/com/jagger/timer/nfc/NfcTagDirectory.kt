// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.nfc

import com.jagger.timer.core.data.repository.PillRepository
import com.jagger.timer.core.util.TimerManager
import javax.inject.Inject
import javax.inject.Singleton

/** Looks up which medicine or timer already owns a tag id, so one sticker is never linked to two things. */
@Singleton
class NfcTagDirectory @Inject constructor(
    private val pillRepository: PillRepository,
    private val timerManager: TimerManager
) {
    /** Name of the medicine or timer that owns [tagId], ignoring the given pill/timer ids (the one being edited). */
    suspend fun ownerName(tagId: String, exceptPillId: Long? = null, exceptTimerId: Long? = null): String? {
        pillRepository.getPillByNfcTag(tagId)?.takeIf { it.id != exceptPillId }?.let { return it.name }
        timerManager.getByNfcTag(tagId)?.takeIf { it.id != exceptTimerId }?.let { return it.label.ifBlank { "a timer" } }
        return null
    }
}
