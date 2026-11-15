package com.kaushik.railway

import com.kaushik.railway.data.*
import org.junit.Assert.*
import org.junit.Test

class TrainSearchTest {
    private fun train(number: String, departure: String = "08:00", duration: String = "2h 10m", classes: List<TrainClassAvail> = emptyList()) =
        Train(number, "Train $number", "A", "B", departure, "10:10", duration, "Daily", classes = classes)
    private fun cls(code: String, fare: Int, status: String) = TrainClassAvail(code, code, fare, status, 0)

    @Test fun departureWindowsUseExclusiveUpperBoundAndRejectUnknownTime() {
        val trains = listOf(train("1", "05:59"), train("2", "06:00"), train("3", "11:59"), train("4", "12:00"), train("5", ""))
        assertEquals(listOf("2", "3"), filterTrains(trains, TrainFilters(departure = DepartureWindow.Morning)).map { it.number })
        assertEquals(5, filterTrains(trains, TrainFilters()).size)
    }
    @Test fun missingTimesAndDurationsSortLast() {
        val trains = listOf(train("unknown", "", ""), train("slow", "12:00", "26h 00m"), train("fast", "06:00", "90m"))
        assertEquals(listOf("fast", "slow", "unknown"), filterTrains(trains, TrainFilters(sort = TrainSort.Duration)).map { it.number })
        assertEquals(listOf("fast", "slow", "unknown"), filterTrains(trains, TrainFilters(sort = TrainSort.Departure)).map { it.number })
        assertNull(departureMinutes("24:00"))
        assertNull(durationMinutes("0h 0m"))
    }
    @Test fun availabilityMustMatchSelectedClass() {
        val mixed = train("mixed", classes = listOf(cls("SL", 300, "WL 10"), cls("3A", 900, "AVAILABLE-2")))
        assertTrue(filterTrains(listOf(mixed), TrainFilters(travelClass = "SL", availableOnly = true)).isEmpty())
        assertEquals(1, filterTrains(listOf(mixed), TrainFilters(travelClass = "3A", availableOnly = true)).size)
        assertTrue(filterTrains(listOf(train("unknown")), TrainFilters(availableOnly = true)).isEmpty())
    }
    @Test fun unknownFareDoesNotBecomeCheapestAndSelectedClassControlsSort() {
        val a = train("a", classes = listOf(cls("SL", 100, "AVAILABLE"), cls("3A", 900, "AVAILABLE")))
        val b = train("b", classes = listOf(cls("3A", 600, "AVAILABLE")))
        val c = train("unknown", classes = listOf(cls("3A", 0, "N/A")))
        assertEquals(listOf("b", "a", "unknown"), filterTrains(listOf(c, a, b), TrainFilters(sort = TrainSort.Fare, travelClass = "3A")).map { it.number })
    }
    @Test fun negativeAndZeroAvailabilityAreNotBookable() {
        listOf("NOT AVAILABLE", "UNAVAILABLE", "AVAILABLE-0", "WL 2", "N/A", "").forEach { assertFalse(it, isAvailableStatus(it)) }
        listOf("AVAILABLE", "AVAILABLE-0020", "AVBL 3", "CNF").forEach { assertTrue(it, isAvailableStatus(it)) }
    }
}
