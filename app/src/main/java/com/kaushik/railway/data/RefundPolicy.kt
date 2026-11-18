package com.kaushik.railway.data

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

/** What cancelling a booking costs and returns, per the IRCTC rules. */
data class RefundQuote(
    val chargePerPassenger: Int,
    val totalCharge: Int,
    val refundAmount: Int,
    /** Short label of the rule applied, e.g. "25% of fare + GST (12–48 hrs before departure)". */
    val rule: String,
    val refundable: Boolean
)

/**
 * Indian Railways cancellation charges — Railway Passengers
 * (Cancellation of Ticket and Refund of Fare) Rules, as IRCTC applies
 * them to e-tickets. Charges are per passenger on the ticket fare
 * (insurance and other add-ons are never refunded):
 *
 * Confirmed tickets:
 *  - more than 48 hrs before departure: flat charge by class
 *    (₹240 1A/EC, ₹200 2A/FC, ₹180 3A/3E/CC, ₹120 SL, ₹60 2S)
 *  - 48–12 hrs: 25% of the fare, at least the flat charge
 *  - 12–4 hrs: 50% of the fare, at least the flat charge
 *  - under 4 hrs: no refund
 *  GST at 5% is added to the charge for AC classes.
 *
 * RAC / waitlisted tickets: only a ₹60 clerkage (+ GST for AC) is
 * deducted when cancelled at least 30 minutes before departure;
 * inside 30 minutes there is no refund.
 *
 * Confirmed Tatkal tickets: no refund at any time. Waitlisted Tatkal
 * tickets follow the RAC/WL clerkage rule.
 */
object RefundPolicy {

    private val AC_CLASSES = setOf("1A", "EC", "2A", "FC", "3A", "3E", "CC")
    private const val CLERKAGE = 60

    fun flatCharge(classCode: String): Int = when (classCode.uppercase(Locale.ENGLISH)) {
        "1A", "EC" -> 240
        "2A", "FC" -> 200
        "3A", "3E", "CC" -> 180
        "SL" -> 120
        else -> 60 // 2S and anything unlisted
    }

    private fun roundHalfUp(x: Double): Int = (x + 0.5).toInt()

    /** Adds 5% GST to a charge for AC classes, as IRCTC does. */
    private fun withGst(charge: Int, classCode: String): Int =
        if (classCode.uppercase(Locale.ENGLISH) in AC_CLASSES) roundHalfUp(charge * 1.05) else charge

    /**
     * Parse the app's journey date ("19 Nov 2026") and a train's
     * departure time ("16:55") into one local date-time, or null.
     */
    fun departureOf(date: String, departTime: String): LocalDateTime? = runCatching {
        val d = LocalDate.parse(
            date.trim(),
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
        )
        val t = if (departTime.isBlank()) LocalTime.of(23, 59)
        else LocalTime.parse(departTime.trim().take(5), DateTimeFormatter.ofPattern("HH:mm"))
        LocalDateTime.of(d, t)
    }.getOrNull()

    fun quote(
        classCode: String,
        farePerPassenger: Int,
        passengers: Int,
        ticketStatus: String,
        quota: String,
        departure: LocalDateTime,
        now: LocalDateTime = LocalDateTime.now()
    ): RefundQuote {
        val pax = passengers.coerceAtLeast(1)
        val totalFare = farePerPassenger.coerceAtLeast(0) * pax
        val minutesLeft = Duration.between(now, departure).toMinutes()
        val status = ticketStatus.uppercase(Locale.ENGLISH)
        val isRacOrWl = status.contains("RAC") || status.contains("WL")
        val isTatkal = IrctcRules.isTatkalQuota(quota)

        fun result(chargePerPax: Int, rule: String): RefundQuote {
            val totalCharge = (chargePerPax * pax).coerceIn(0, totalFare)
            val refund = totalFare - totalCharge
            return RefundQuote(chargePerPax, totalCharge, refund, rule, refund > 0)
        }

        if (isRacOrWl) {
            return if (minutesLeft > 30) {
                result(
                    withGst(CLERKAGE, classCode),
                    "RAC/waitlist clerkage ₹60 per passenger" +
                        if (classCode.uppercase(Locale.ENGLISH) in AC_CLASSES) " + GST" else ""
                )
            } else {
                result(farePerPassenger, "No refund — RAC/waitlist tickets get no refund inside 30 minutes of departure")
            }
        }

        if (isTatkal) {
            return result(farePerPassenger, "No refund — confirmed Tatkal tickets are non-refundable under IRCTC rules")
        }

        val flat = flatCharge(classCode)
        return when {
            minutesLeft > 48 * 60 -> result(
                withGst(flat, classCode),
                "Flat cancellation charge (more than 48 hrs before departure)" +
                    if (classCode.uppercase(Locale.ENGLISH) in AC_CLASSES) " + GST" else ""
            )
            minutesLeft > 12 * 60 -> result(
                withGst(max(roundHalfUp(farePerPassenger * 0.25), flat), classCode),
                "25% of fare (48–12 hrs before departure), minimum the flat charge" +
                    if (classCode.uppercase(Locale.ENGLISH) in AC_CLASSES) " + GST" else ""
            )
            minutesLeft > 4 * 60 -> result(
                withGst(max(roundHalfUp(farePerPassenger * 0.50), flat), classCode),
                "50% of fare (12–4 hrs before departure), minimum the flat charge" +
                    if (classCode.uppercase(Locale.ENGLISH) in AC_CLASSES) " + GST" else ""
            )
            else -> result(
                farePerPassenger,
                "No refund — confirmed tickets get no refund inside 4 hrs of departure"
            )
        }
    }
}
