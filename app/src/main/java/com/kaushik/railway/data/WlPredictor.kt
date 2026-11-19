package com.kaushik.railway.data

import java.util.Locale

/** A waitlist confirmation estimate with the reasons behind it. */
data class WlEstimate(
    /** Estimated chance the ticket ends up confirmed, 3..97. */
    val percent: Int,
    /** Very likely / Likely / Borderline / Unlikely. */
    val band: String,
    /** The main factors that moved the estimate, in plain words. */
    val factors: List<String>
)

/**
 * RailX's waitlist confirmation estimator. This is a transparent
 * heuristic, not railway data: it starts from where the ticket sits
 * in the queue and adjusts for the things experienced travellers
 * weigh — time left for cancellations, class size, quota behaviour
 * and how premium the train is. The UI always labels it an estimate.
 */
object WlPredictor {

    private val PREMIUM_MARKERS = listOf("Rajdhani", "Shatabdi", "Vande Bharat", "Duronto", "Tejas", "Humsafar")

    /** An estimate for WL/RAC statuses, or null for anything else. */
    fun estimate(
        status: String,
        classCode: String,
        quota: String,
        daysToDeparture: Int,
        trainName: String
    ): WlEstimate? {
        val s = status.uppercase(Locale.ENGLISH)
        val queueNo = Regex("\\d+").find(s)?.value?.toIntOrNull() ?: 0
        val isRac = s.contains("RAC")
        val isWl = s.contains("WL")
        if (!isRac && !isWl) return null

        val factors = mutableListOf<String>()
        var score: Int

        if (isRac) {
            score = 88
            factors += "RAC tickets already hold a seat; most convert to a full berth by charting"
        } else {
            score = when {
                queueNo <= 5 -> 85
                queueNo <= 10 -> 75
                queueNo <= 20 -> 62
                queueNo <= 30 -> 50
                queueNo <= 50 -> 38
                else -> 25
            }
            factors += when {
                queueNo <= 10 -> "Only WL $queueNo, close to the front of the queue"
                queueNo <= 30 -> "WL $queueNo, a middling place in the queue"
                else -> "WL $queueNo is deep in the queue"
            }
        }

        val days = daysToDeparture.coerceAtLeast(0)
        when {
            days >= 14 -> { score += 10; factors += "$days days to departure, plenty of time for cancellations" }
            days >= 7 -> { score += 6; factors += "$days days to departure, time for cancellations to free berths" }
            days >= 3 -> score += 2
            days == 0 -> { score -= 12; factors += "The train leaves today, little room left to move" }
            else -> { score -= 5; factors += "Only $days day(s) to departure, little time left to move" }
        }

        when (classCode.uppercase(Locale.ENGLISH)) {
            "SL" -> { score += 6; factors += "Sleeper has the largest inventory, so its waitlist churns the most" }
            "2S" -> score += 4
            "3A", "3E" -> score += 2
            "2A" -> score -= 4
            "1A", "EC" -> { score -= 8; factors += "First AC has very few berths, so its waitlist barely moves" }
        }

        if (IrctcRules.isTatkalQuota(quota)) {
            score -= 25
            factors += "Tatkal quota is tiny and its waitlist rarely clears"
        } else if (quota.uppercase(Locale.ENGLISH).startsWith("PT")) {
            score -= 10
            factors += "Premium Tatkal pricing keeps this quota's waitlist slow"
        }

        if (PREMIUM_MARKERS.any { trainName.contains(it, ignoreCase = true) }) {
            score -= 8
            factors += "Premium trains run full and see fewer cancellations"
        }

        val percent = score.coerceIn(3, 97)
        val band = when {
            percent >= 75 -> "Very likely"
            percent >= 55 -> "Likely"
            percent >= 35 -> "Borderline"
            else -> "Unlikely"
        }
        return WlEstimate(percent, band, factors)
    }
}
