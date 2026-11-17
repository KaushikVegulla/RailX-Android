package com.kaushik.railway

import com.kaushik.railway.data.Passenger
import com.kaushik.railway.data.RemoteBooking
import com.kaushik.railway.data.RemoteFavouriteRoute
import com.kaushik.railway.data.RemotePassenger
import com.kaushik.railway.data.RemoteProfile
import com.kaushik.railway.data.SupabaseConfig
import com.kaushik.railway.data.SupabaseSession
import com.kaushik.railway.data.supabaseErrorMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseModelsTest {

    @Test
    fun bookingRowRoundTripsWithPassengers() {
        val booking = RemoteBooking(
            pnr = "1234567890",
            trainNumber = "20833",
            trainName = "Vande Bharat",
            fromCode = "BBS",
            toCode = "VSKP",
            fromName = "Bhubaneswar",
            toName = "Visakhapatnam",
            travelClassCode = "CC",
            travelClassName = "Chair Car",
            fare = 845,
            journeyDate = "2026-12-01",
            quota = "GN - General",
            passengers = listOf(
                Passenger(name = "Kaushik", age = "20", gender = "Male", berth = "Window", concession = "None")
            ),
            contact = "9999999999",
            status = "CONFIRMED",
            amount = 845,
            paymentId = "pay_1",
            orderId = "order_1"
        )
        val row = booking.toRow("user-uuid")
        assertEquals("user-uuid", row["user_id"])
        val parsed = RemoteBooking.fromRow(row)
        assertEquals(booking, parsed)
    }

    @Test
    fun bookingRowAcceptsPostgrestNumberTypes() {
        val row = mapOf<String, Any?>(
            "pnr" to "999",
            "fare" to 500L,
            "amount" to 500.0,
            "passengers" to emptyList<Map<String, Any?>>()
        )
        val parsed = RemoteBooking.fromRow(row)
        assertEquals(500, parsed.fare)
        assertEquals(500, parsed.amount)
        assertEquals("CONFIRMED", parsed.status)
        assertTrue(parsed.passengers.isEmpty())
    }

    @Test
    fun profileAndPassengerAndRouteRowsRoundTrip() {
        val profile = RemoteProfile(id = "u1", fullName = "K", email = "k@example.com", mobile = "9000000000")
        assertEquals(profile, RemoteProfile.fromRow(profile.toRow()))

        val passenger = RemotePassenger(
            id = "p1", fullName = "K", age = 20, gender = "Male",
            berthPreference = "Lower", concession = "None"
        )
        assertEquals(passenger, RemotePassenger.fromRow(passenger.toRow("u1")))

        val route = RemoteFavouriteRoute(id = "r1", fromCode = "BBS", toCode = "VSKP")
        assertEquals(route, RemoteFavouriteRoute.fromRow(route.toRow("u1")))

        val fromDomain = RemotePassenger.fromDomain(
            Passenger(name = "K", age = "20", gender = "Male", berth = "Lower", concession = "None")
        )
        assertEquals(20, fromDomain.age)
        assertNull(RemotePassenger.fromDomain(Passenger(name = "K", age = "")).age)
    }

    @Test
    fun sessionParsesGoTrueTokenResponse() {
        val response = mapOf<String, Any?>(
            "access_token" to "access-123",
            "refresh_token" to "refresh-456",
            "expires_in" to 3600L,
            "user" to mapOf<String, Any?>(
                "id" to "user-uuid",
                "email" to "k@example.com",
                "user_metadata" to mapOf<String, Any?>(
                    "full_name" to "Kaushik",
                    "mobile" to "9000000000"
                )
            )
        )
        val session = SupabaseSession.fromAuthResponse(response, nowEpochSec = 1_000)
        assertEquals("access-123", session?.accessToken)
        assertEquals("user-uuid", session?.userId)
        assertEquals("Kaushik", session?.fullName)
        assertEquals(4_600L, session?.expiresAtEpochSec)
        assertTrue(session!!.isExpired(nowEpochSec = 4_550))
        assertFalse(session.isExpired(nowEpochSec = 2_000))

        assertNull(SupabaseSession.fromAuthResponse(mapOf("user" to response["user"])))
        assertNull(SupabaseSession.fromAuthResponse(emptyMap()))
    }

    @Test
    fun configNormalizesUrlAndErrorsAreExtracted() {
        assertEquals("https://x.supabase.co", SupabaseConfig.normalizeUrl(" https://x.supabase.co/ "))
        assertEquals(
            "Invalid login credentials",
            supabaseErrorMessage(mapOf("msg" to "Invalid login credentials"), 400)
        )
        assertEquals(
            "Email not confirmed",
            supabaseErrorMessage(mapOf("error_description" to "Email not confirmed"), 400)
        )
        assertEquals("Request failed (500)", supabaseErrorMessage(emptyMap(), 500))
    }
}
