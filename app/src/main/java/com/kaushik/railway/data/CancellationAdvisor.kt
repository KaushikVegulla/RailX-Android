package com.kaushik.railway.data

import java.time.LocalDateTime
import java.util.Locale

/**
 * What the Cancellation Advisor shows for one booking: the refund for
 * cancelling right now, and the moment that refund next gets smaller.
 */
data class CancellationAdvice(
    /** Refund (total, all passengers) for cancelling at the given moment. */
    val refundNow: Int,
    /**
     * The deadline the current refund holds until: cancelling before
     * this moment keeps [refundNow]. Null when the refund never drops
     * again (already zero, or flat until departure).
     */
    val dropsAt: LocalDateTime?,
    /** Refund once [dropsAt] has passed. Null when it never drops. */
    val refundAfterDrop: Int?
)

/**
 * Turns the IRCTC slab rules in [RefundPolicy] into timing advice.
 * Confirmed tickets step down 48, 12 and 4 hours before departure;
 * RAC / waitlist tickets lose their refund 30 minutes before. The
 * advisor evaluates the same quote at each boundary and reports the
 * first one that pays less than cancelling now, so the numbers can
 * never drift from the charges the cancel flow actually applies.
 */
object CancellationAdvisor {

    private val CONFIRMED_BOUNDARY_MINUTES = listOf(48 * 60L, 12 * 60L, 4 * 60L)
    private const val RAC_WL_BOUNDARY_MINUTES = 30L

    fun advise(
        classCode: String,
        farePerPassenger: Int,
        passengers: Int,
        ticketStatus: String,
        quota: String,
        departure: LocalDateTime,
        now: LocalDateTime = LocalDateTime.now()
    ): CancellationAdvice {
        fun refundAt(moment: LocalDateTime): Int = RefundPolicy.quote(
            classCode, farePerPassenger, passengers, ticketStatus, quota, departure, moment
        ).refundAmount

        val refundNow = refundAt(now)
        val status = ticketStatus.uppercase(Locale.ENGLISH)
        val isRacOrWl = status.contains("RAC") || status.contains("WL")
        val boundaries =
            if (isRacOrWl) listOf(RAC_WL_BOUNDARY_MINUTES) else CONFIRMED_BOUNDARY_MINUTES

        for (minutesBefore in boundaries) {
            val boundary = departure.minusMinutes(minutesBefore)
            if (boundary.isAfter(now)) {
                val after = refundAt(boundary)
                if (after < refundNow) {
                    return CancellationAdvice(refundNow, boundary, after)
                }
            }
        }
        return CancellationAdvice(refundNow, null, null)
    }
}
