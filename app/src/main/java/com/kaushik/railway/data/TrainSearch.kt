package com.kaushik.railway.data

private val departurePattern = Regex("^(\\d{1,2}):(\\d{2})(?::\\d{2})?$")
private val durationPattern = Regex("^(?:(\\d+)h)?\\s*(?:(\\d+)m)?$", RegexOption.IGNORE_CASE)
private val durationClockPattern = Regex("^(\\d+):(\\d{2})$")
private val availabilityPattern = Regex("^(?:AVAILABLE|AVBL)(?:\\s*[-:/]?\\s*(\\d+))?$", RegexOption.IGNORE_CASE)

/** Filters operate only on values actually supplied with a search result. */
enum class TrainSort(val label: String) {
    Recommended("Recommended"), Departure("Earliest departure"), Duration("Shortest journey"), Fare("Lowest fare")
}

enum class DepartureWindow(val label: String, val startMinute: Int, val endMinute: Int) {
    Any("Any time", 0, 1440), Night("00:00–06:00", 0, 360), Morning("06:00–12:00", 360, 720),
    Afternoon("12:00–18:00", 720, 1080), Evening("18:00–24:00", 1080, 1440)
}

data class TrainFilters(
    val sort: TrainSort = TrainSort.Recommended,
    val departure: DepartureWindow = DepartureWindow.Any,
    val travelClass: String = "",
    val availableOnly: Boolean = false
)

fun filterTrains(trains: List<Train>, filters: TrainFilters): List<Train> {
    val filtered = trains.filter { train ->
        val departureMatches = filters.departure == DepartureWindow.Any ||
            departureMinutes(train.depart)?.let { it >= filters.departure.startMinute && it < filters.departure.endMinute } == true
        val eligibleClasses = train.classes.filter { filters.travelClass.isBlank() || it.code == filters.travelClass }
        departureMatches && (filters.travelClass.isBlank() || eligibleClasses.isNotEmpty()) &&
            (!filters.availableOnly || eligibleClasses.any { isAvailableStatus(it.status) })
    }
    return when (filters.sort) {
        TrainSort.Recommended -> filtered
        TrainSort.Departure -> filtered.sortedBy { departureMinutes(it.depart) ?: Int.MAX_VALUE }
        TrainSort.Duration -> filtered.sortedBy { durationMinutes(it.duration) ?: Int.MAX_VALUE }
        TrainSort.Fare -> filtered.sortedBy { train ->
            train.classes.filter { (filters.travelClass.isBlank() || it.code == filters.travelClass) &&
                (!filters.availableOnly || isAvailableStatus(it.status)) }
                .map { it.fare }.filter { it > 0 }.minOrNull() ?: Int.MAX_VALUE
        }
    }
}

fun departureMinutes(value: String): Int? {
    val match = departurePattern.matchEntire(value.trim()) ?: return null
    val hours = match.groupValues[1].toInt()
    val minutes = match.groupValues[2].toInt()
    return if (hours in 0..23 && minutes in 0..59) hours * 60 + minutes else null
}

fun durationMinutes(value: String): Int? {
    val match = durationPattern.matchEntire(value.trim())
    if (match != null && match.groupValues.drop(1).any { it.isNotEmpty() }) {
        val hours = match.groupValues[1].let { if (it.isEmpty()) 0 else it.toIntOrNull() ?: return null }
        val remainder = match.groupValues[2].let { if (it.isEmpty()) 0 else it.toIntOrNull() ?: return null }
        val minutes = hours.toLong() * 60 + remainder
        return minutes.takeIf { it in 1..Int.MAX_VALUE.toLong() }?.toInt()
    }
    val parts = durationClockPattern.matchEntire(value.trim()) ?: return null
    val hours = parts.groupValues[1].toIntOrNull() ?: return null
    val minutes = parts.groupValues[2].toInt()
    val total = hours.toLong() * 60 + minutes
    return total.takeIf { minutes in 0..59 && it in 1..Int.MAX_VALUE.toLong() }?.toInt()
}

fun isAvailableStatus(status: String): Boolean {
    val match = availabilityPattern.matchEntire(status.trim()) ?: return status.trim().equals("CNF", ignoreCase = true)
    return match.groupValues[1].isBlank() || (match.groupValues[1].toIntOrNull() ?: 0) > 0
}
