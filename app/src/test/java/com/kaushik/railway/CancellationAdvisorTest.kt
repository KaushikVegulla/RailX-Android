package com.kaushik.railway

import com.kaushik.railway.data.CancellationAdvisor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class CancellationAdvisorTest {

    private val now: LocalDateTime = LocalDateTime.of(2026, 11, 18, 21, 40)

    private fun advise(
        cls: String,
        fare: Int,
        pax: Int = 1,
        status: String = "AVAILABLE 28",
        quota: String = "GN",
        departure: LocalDateTime
    ) = CancellationAdvisor.advise(cls, fare, pax, status, quota, departure, now)

    @Test fun confirmedBeyond48HoursDropsAtThe48HourMark() {
        val departure = LocalDateTime.of(2026, 11, 25, 16, 55)
        val a = advise("1A", 4520, departure = departure)
        assertEquals(4268, a.refundNow) // flat charge 240 + GST
        assertEquals(departure.minusHours(48), a.dropsAt)
        assertEquals(3333, a.refundAfterDrop) // 25% slab + GST
    }

    @Test fun confirmedIn25PercentSlabDropsAtThe12HourMark() {
        val departure = LocalDateTime.of(2026, 11, 19, 16, 55) // 19h15m after now
        val a = advise("1A", 4520, departure = departure)
        assertEquals(3333, a.refundNow)
        assertEquals(departure.minusHours(12), a.dropsAt)
        assertEquals(2147, a.refundAfterDrop) // 50% slab: 2260 + GST = 2373 charge
    }

    @Test fun confirmedIn50PercentSlabDropsToZeroAtThe4HourMark() {
        val departure = now.plusHours(8)
        val a = advise("SL", 620, departure = departure)
        assertEquals(310, a.refundNow)
        assertEquals(departure.minusHours(4), a.dropsAt)
        assertEquals(0, a.refundAfterDrop)
    }

    @Test fun racDropsToZeroThirtyMinutesBeforeDeparture() {
        val departure = now.plusHours(10)
        val a = advise("2A", 2680, status = "RAC 4", departure = departure)
        assertEquals(2617, a.refundNow) // only clerkage 60 + GST
        assertEquals(departure.minusMinutes(30), a.dropsAt)
        assertEquals(0, a.refundAfterDrop)
    }

    @Test fun confirmedTatkalNeverPaysAndNeverDrops() {
        val a = advise("3A", 1860, quota = "TQ", departure = now.plusHours(72))
        assertEquals(0, a.refundNow)
        assertNull(a.dropsAt)
        assertNull(a.refundAfterDrop)
    }

    @Test fun confirmedInsideFourHoursHasNoRefundAndNoDrop() {
        val a = advise("SL", 620, departure = now.plusHours(2))
        assertEquals(0, a.refundNow)
        assertNull(a.dropsAt)
    }

    @Test fun multiPassengerRefundScalesWithTheQuote() {
        val departure = now.plusHours(72)
        val a = advise("3A", 1860, pax = 2, departure = departure)
        assertEquals(3720 - 378, a.refundNow) // flat 180 + GST, per passenger
        assertEquals(departure.minusHours(48), a.dropsAt)
    }
}
