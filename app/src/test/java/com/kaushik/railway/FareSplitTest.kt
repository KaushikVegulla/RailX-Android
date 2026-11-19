package com.kaushik.railway

import com.kaushik.railway.data.FareSplit
import com.kaushik.railway.data.TdrCoach
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FareSplitTest {

    @Test fun splitsEvenlyWhenDivisible() {
        val shares = FareSplit.split(4000, listOf("Asha", "Bikram", "Charu", "Dev"))
        assertEquals(listOf(1000, 1000, 1000, 1000), shares.map { it.amount })
    }

    @Test fun remainderRupeesGoToTheFirstPassengers() {
        val shares = FareSplit.split(1000, listOf("Asha", "Bikram", "Charu"))
        assertEquals(listOf(334, 333, 333), shares.map { it.amount })
        assertEquals(1000, shares.sumOf { it.amount })
    }

    @Test fun blankNamesFallBackToNumberedPassengers() {
        val shares = FareSplit.split(500, listOf("", ""))
        assertEquals("Passenger 1", shares[0].name)
        assertEquals("Passenger 2", shares[1].name)
    }

    @Test fun summaryCarriesRouteTotalAndEveryShare() {
        val shares = FareSplit.split(945, listOf("Kaushik"))
        val text = FareSplit.summaryText(
            trainNumber = "20817",
            trainName = "Vande Bharat Express",
            fromName = "Bhubaneswar",
            toName = "Visakhapatnam",
            date = "20 Nov 2026",
            pnr = "3917536209",
            totalAmount = 945,
            shares = shares
        )
        assertTrue(text.contains("20817"))
        assertTrue(text.contains("PNR 3917536209"))
        assertTrue(text.contains("Kaushik: ₹945"))
    }
}

class TdrCoachTest {

    @Test fun fullRefundStartsAtThreeHoursLate() {
        assertFalse(TdrCoach.isFullRefundDelay(179))
        assertTrue(TdrCoach.isFullRefundDelay(180))
        assertTrue(TdrCoach.isFullRefundDelay(342))
    }
}
