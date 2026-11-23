package com.jagger.timer.core.util

import com.jagger.timer.core.data.local.entity.DoseLogEntity
import com.jagger.timer.core.data.local.entity.PillEntity
import com.jagger.timer.core.data.local.entity.doseAmount

enum class CooldownStatus { READY, WAITING, AT_DAILY_MAX }

/** A single taken dose: when, and how much (in the pill's unit). */
data class TakenDose(val timestampMillis: Long, val amount: Double)

data class CooldownState(
    val status: CooldownStatus,
    /** Epoch millis when the next dose is OK, or null when [status] is READY. */
    val nextDoseAtMillis: Long?,
    val dosesIn24h: Int,
    val amountIn24h: Double,
    val lastDoseMillis: Long?
) {
    fun millisUntilNext(nowMillis: Long): Long =
        ((nextDoseAtMillis ?: nowMillis) - nowMillis).coerceAtLeast(0L)
}

/** Pure rolling-window cooldown logic for as-needed medicines. */
object CooldownCalculator {

    const val WINDOW_MILLIS = 24L * 60 * 60 * 1000

    /** Cooldown for an as-needed [pill] from its TAKEN [logs] (older logs without an amount use the pill's dose). */
    fun forPill(pill: PillEntity, logs: List<DoseLogEntity>, nowMillis: Long): CooldownState {
        val amount = pill.doseAmount() ?: 1.0
        return calculate(
            doses = logs.map { TakenDose(it.timestamp, it.amount ?: amount) },
            nowMillis = nowMillis,
            minIntervalMinutes = pill.minIntervalMinutes,
            maxPer24h = pill.maxPer24h,
            maxIsDoses = pill.maxIsDoses,
            nextAmount = amount
        )
    }

    /**
     * @param doses        TAKEN doses in any order
     * @param minIntervalMinutes minimum wait since the last dose (null = no wait)
     * @param maxPer24h    rolling 24h cap (null = no cap)
     * @param maxIsDoses   cap counts doses (true) or sums amounts (false)
     * @param nextAmount   amount of the dose about to be taken (used for the amount cap)
     */
    fun calculate(
        doses: List<TakenDose>,
        nowMillis: Long,
        minIntervalMinutes: Int?,
        maxPer24h: Int?,
        maxIsDoses: Boolean,
        nextAmount: Double
    ): CooldownState {
        val windowStart = nowMillis - WINDOW_MILLIS
        val inWindow = doses
            .filter { it.timestampMillis > windowStart && it.timestampMillis <= nowMillis }
            .sortedBy { it.timestampMillis }
        val last = doses.filter { it.timestampMillis <= nowMillis }.maxOfOrNull { it.timestampMillis }
        val total = inWindow.sumOf { it.amount }

        // Time the minimum interval ends.
        val intervalEnd = if (last != null && minIntervalMinutes != null)
            last + minIntervalMinutes * 60_000L else null

        // Time the daily cap lets another dose in: drop oldest doses until the next one fits.
        var capEnd: Long? = null
        if (maxPer24h != null) {
            var count = inWindow.size
            var sum = total
            var idx = 0
            fun fits() = if (maxIsDoses) count + 1 <= maxPer24h else sum + nextAmount <= maxPer24h
            while (!fits() && idx < inWindow.size) {
                val d = inWindow[idx++]
                count -= 1
                sum -= d.amount
                capEnd = d.timestampMillis + WINDOW_MILLIS
            }
        }

        val next = listOfNotNull(intervalEnd, capEnd).maxOrNull()
        val status = when {
            next == null || next <= nowMillis -> CooldownStatus.READY
            capEnd != null && capEnd > nowMillis && capEnd >= (intervalEnd ?: Long.MIN_VALUE) ->
                CooldownStatus.AT_DAILY_MAX
            else -> CooldownStatus.WAITING
        }
        return CooldownState(
            status = status,
            nextDoseAtMillis = if (status == CooldownStatus.READY) null else next,
            dosesIn24h = inWindow.size,
            amountIn24h = total,
            lastDoseMillis = last
        )
    }
}
