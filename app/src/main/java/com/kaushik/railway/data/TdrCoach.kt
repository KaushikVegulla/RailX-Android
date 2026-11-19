package com.kaushik.railway.data

/**
 * Delay-refund coach: the rule most passengers never hear about.
 * Under the Railway Passengers (Cancellation of Ticket and Refund of
 * Fare) Rules, a passenger who does not travel because the train is
 * running more than three hours late at the boarding station is owed
 * a full refund of the fare, claimed through a TDR (Ticket Deposit
 * Receipt) filed before the train actually departs that station.
 */
object TdrCoach {

    /** Minutes late at (and beyond) which the full-refund rule applies. */
    const val FULL_REFUND_MINUTES = 180

    fun isFullRefundDelay(delayMin: Int): Boolean = delayMin >= FULL_REFUND_MINUTES

    val steps: List<String> = listOf(
        "Do not travel on the delayed train. The full refund applies only when you don't board.",
        "File a TDR before the train actually departs your boarding station. On the IRCTC website or app: My Transactions, then File TDR, and choose the reason 'Train late by more than three hours'.",
        "At the station instead: surrender the ticket at the reservation counter before departure and keep the TDR receipt.",
        "The refund is credited to the original payment account after IRCTC verifies the train's recorded delay."
    )

    const val NOTE =
        "Guidance from the Railway Passengers (Cancellation of Ticket and Refund of Fare) Rules. " +
            "IRCTC verifies the recorded delay and decides the final claim."
}
