package com.kaushik.railway

import com.kaushik.railway.data.WatchStatus
import com.kaushik.railway.data.WlPredictor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchStatusTest {

    @Test fun waitlistImprovesAsTheNumberFalls() {
        assertTrue(WatchStatus.improved("WL 14", "WL 6"))
        assertFalse(WatchStatus.improved("WL 6", "WL 14"))
    }

    @Test fun racBeatsWaitlistAndAvailableBeatsRac() {
        assertTrue(WatchStatus.improved("WL 2", "RAC 9"))
        assertTrue(WatchStatus.improved("RAC 1", "AVAILABLE 12"))
        assertFalse(WatchStatus.improved("AVAILABLE 12", "RAC 1"))
    }

    @Test fun sameStatusIsNotAnImprovement() {
        assertFalse(WatchStatus.improved("WL 6", "WL 6"))
    }
}

class WlPredictorTest {

    @Test fun confirmedStatusGetsNoEstimate() {
        assertNull(WlPredictor.estimate("AVAILABLE 28", "3A", "GN", 10, "Some Express"))
    }

    @Test fun lowWaitlistFarOutScoresHigh() {
        val e = WlPredictor.estimate("WL 4", "SL", "GN", 20, "Some Express")!!
        assertTrue(e.percent >= 90)
        assertEquals("Very likely", e.band)
    }

    @Test fun tatkalWaitlistScoresLow() {
        val e = WlPredictor.estimate("WL 18", "3A", "TQ", 2, "Some Express")!!
        assertTrue(e.percent < 35)
        assertEquals("Unlikely", e.band)
    }

    @Test fun racScoresAboveDeepWaitlist() {
        val rac = WlPredictor.estimate("RAC 6", "2A", "GN", 5, "Some Express")!!
        val wl = WlPredictor.estimate("WL 40", "2A", "GN", 5, "Some Express")!!
        assertTrue(rac.percent > wl.percent)
    }

    @Test fun premiumTrainPullsTheEstimateDown() {
        val plain = WlPredictor.estimate("WL 10", "3A", "GN", 10, "Some Express")!!
        val premium = WlPredictor.estimate("WL 10", "3A", "GN", 10, "Mumbai Rajdhani Express")!!
        assertTrue(premium.percent < plain.percent)
    }
}
