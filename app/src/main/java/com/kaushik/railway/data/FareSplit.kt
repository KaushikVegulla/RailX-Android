package com.kaushik.railway.data

/** One person's share of a group booking. */
data class FareShare(val name: String, val amount: Int)

/**
 * Group fare splitter: divides what a booking actually cost (fare plus
 * any add-ons, exactly as paid) equally across its passengers, and
 * renders the WhatsApp-ready summary friends settle up from.
 */
object FareSplit {

    /**
     * Equal split of [totalAmount]. The rupee remainder is spread one
     * rupee at a time over the first passengers, so the shares always
     * add back up to the amount that was paid.
     */
    fun split(totalAmount: Int, passengerNames: List<String>): List<FareShare> {
        val names = passengerNames.ifEmpty { listOf("") }
        val n = names.size
        val base = totalAmount / n
        val remainder = totalAmount % n
        return names.mapIndexed { index, name ->
            FareShare(
                name = name.ifBlank { "Passenger ${index + 1}" },
                amount = base + if (index < remainder) 1 else 0
            )
        }
    }

    /** Plain-text split summary, ready to paste into a group chat. */
    fun summaryText(
        trainNumber: String,
        trainName: String,
        fromName: String,
        toName: String,
        date: String,
        pnr: String,
        totalAmount: Int,
        shares: List<FareShare>
    ): String = buildString {
        appendLine("RailX fare split")
        appendLine("$trainNumber $trainName")
        appendLine("$fromName to $toName · $date")
        appendLine("PNR $pnr · Total ₹$totalAmount")
        appendLine()
        shares.forEach { appendLine("${it.name}: ₹${it.amount}") }
        appendLine()
        appendLine("Split with RailX")
    }
}
