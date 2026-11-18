package com.kaushik.railway

import com.kaushik.railway.data.RefundPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class RefundPolicyTest {

    private val now: LocalDateTime = LocalDateTime.of(2026, 11, 18, 21, 40)

    private fun quote(
        cls: String,
        fare: Int,
        pax: Int = 1,
        status: String = "AVAILABLE 28",
        quota: String = "GN",
        departure: LocalDateTime
    ) = RefundPolicy.quote(cls, fare, pax, status, quota, departure, now)

    @Test fun parsesJourneyDateAndDepartureTime() {
        assertEquals(
            LocalDateTime.of(2026, 11, 19, 16, 55),
            RefundPolicy.departureOf("19 Nov 2026", "16:55")
        )
    }

    @Test fun confirmedAcFirstFlatChargeBeyond48Hours() {
        val q = quote("1A", 4520, departure = LocalDateTime.of(2026, 11, 25, 16, 55))
        assertEquals(252, q.totalCharge) // 240 + 5% GST
        assertEquals(4268, q.refundAmount)
        assertTrue(q.refundable)
    }

    @Test fun confirmedAcFirstTwentyFivePercentSlab() {
        // 19h15m before departure: 25% of 4520 = 1130, +5% GST = 1187.
        val q = quote("1A", 4520, departure = LocalDateTime.of(2026, 11, 19, 16, 55))
        assertEquals(1187, q.totalCharge)
        assertEquals(3333, q.refundAmount)
    }

    @Test fun confirmedAcThirdPercentSlabRoundsHalfUp() {
        val q = quote("3A", 1860, departure = now.plusHours(24))
        assertEquals(488, q.totalCharge) // 465 + 5% GST = 488.25 -> 488
        assertEquals(1372, q.refundAmount)
    }

    @Test fun confirmedSleeperFiftyPercentSlabHasNoGst() {
        val q = quote("SL", 620, departure = now.plusHours(8))
        assertEquals(310, q.totalCharge)
        assertEquals(310, q.refundAmount)
    }

    @Test fun confirmedInsideFourHoursGetsNoRefund() {
        val q = quote("SL", 620, departure = now.plusHours(2))
        assertEquals(620, q.totalCharge)
        assertEquals(0, q.refundAmount)
        assertFalse(q.refundable)
    }

    @Test fun racPaysOnlyClerkageUntilThirtyMinutesBefore() {
        val q = quote("2A", 2680, status = "RAC 4", departure = now.plusHours(10))
        assertEquals(63, q.totalCharge) // 60 + 5% GST
        assertEquals(2617, q.refundAmount)
    }

    @Test fun waitlistInsideThirtyMinutesGetsNoRefund() {
        val q = quote("3A", 1860, status = "WL 14", departure = now.plusMinutes(20))
        assertEquals(0, q.refundAmount)
        assertFalse(q.refundable)
    }

    @Test fun confirmedTatkalIsNonRefundable() {
        val q = quote("3A", 1860, quota = "TQ", departure = now.plusHours(72))
        assertEquals(0, q.refundAmount)
        assertFalse(q.refundable)
    }

    @Test fun secondSittingFlatChargeBeyond48Hours() {
        val q = quote("2S", 250, departure = now.plusHours(72))
        assertEquals(60, q.totalCharge)
        assertEquals(190, q.refundAmount)
    }

    @Test fun chargesScalePerPassenger() {
        val q = quote("3A", 1860, pax = 2, departure = now.plusHours(72))
        assertEquals(189, q.chargePerPassenger) // 180 + 5% GST
        assertEquals(378, q.totalCharge)
        assertEquals(3720 - 378, q.refundAmount)
    }
}
