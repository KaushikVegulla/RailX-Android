package com.kaushik.railway.data.repository

import com.kaushik.railway.data.Booking
import com.kaushik.railway.data.Passenger
import com.kaushik.railway.data.RemoteBooking
import com.kaushik.railway.data.RemoteFavouriteRoute
import com.kaushik.railway.data.RemotePassenger
import com.kaushik.railway.data.RemoteProfile
import com.kaushik.railway.data.SupabaseDb

/**
 * Sync between the app and the Supabase tables.
 *
 * Room stays the on-device cache and keeps working offline. These methods
 * are the write-through / pull side: call push after a local save, and
 * pull on sign-in to hydrate other devices. Every call needs a live
 * Supabase session from SupabaseAuthRepository and fails soft (null /
 * empty / false) when signed out or offline, so sync can never break the
 * local booking flow.
 */
class SupabaseSyncRepository(private val authRepository: SupabaseAuthRepository) {

    suspend fun pushBooking(booking: Booking): Boolean {
        val session = authRepository.currentSession() ?: return false
        return runCatching { SupabaseDb.upsertBooking(session, booking) }.isSuccess
    }

    suspend fun pullBookings(): List<Booking> {
        val session = authRepository.currentSession() ?: return emptyList()
        return runCatching {
            SupabaseDb.fetchBookings(session).map { it.toDomain() }
        }.getOrDefault(emptyList())
    }

    suspend fun pushBookingStatus(pnr: String, status: String): Boolean {
        val session = authRepository.currentSession() ?: return false
        return runCatching { SupabaseDb.updateBookingStatus(session, pnr, status) }.isSuccess
    }

    suspend fun pushPassenger(passenger: Passenger): RemotePassenger? {
        val session = authRepository.currentSession() ?: return null
        return runCatching { SupabaseDb.insertPassenger(session, passenger) }.getOrNull()
    }

    suspend fun pullPassengers(): List<RemotePassenger> {
        val session = authRepository.currentSession() ?: return emptyList()
        return runCatching { SupabaseDb.fetchPassengers(session) }.getOrDefault(emptyList())
    }

    suspend fun deletePassenger(passengerId: String): Boolean {
        val session = authRepository.currentSession() ?: return false
        return runCatching { SupabaseDb.deletePassenger(session, passengerId) }.isSuccess
    }

    suspend fun addFavouriteRoute(fromCode: String, toCode: String): RemoteFavouriteRoute? {
        val session = authRepository.currentSession() ?: return null
        return runCatching {
            SupabaseDb.addFavouriteRoute(session, fromCode, toCode)
        }.getOrNull()
    }

    suspend fun pullFavouriteRoutes(): List<RemoteFavouriteRoute> {
        val session = authRepository.currentSession() ?: return emptyList()
        return runCatching { SupabaseDb.fetchFavouriteRoutes(session) }.getOrDefault(emptyList())
    }

    suspend fun deleteFavouriteRoute(routeId: String): Boolean {
        val session = authRepository.currentSession() ?: return false
        return runCatching { SupabaseDb.deleteFavouriteRoute(session, routeId) }.isSuccess
    }

    suspend fun pullProfile(): RemoteProfile? {
        val session = authRepository.currentSession() ?: return null
        return runCatching { SupabaseDb.fetchProfile(session) }.getOrNull()
    }

    suspend fun pushProfile(fullName: String, mobile: String): RemoteProfile? {
        val session = authRepository.currentSession() ?: return null
        val profile = RemoteProfile(
            id = session.userId,
            fullName = fullName,
            email = session.email,
            mobile = mobile
        )
        return runCatching { SupabaseDb.upsertProfile(session, profile) }.getOrNull()
    }
}
