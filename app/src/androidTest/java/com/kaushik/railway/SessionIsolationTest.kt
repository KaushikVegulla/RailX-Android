package com.kaushik.railway

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kaushik.railway.data.AccountIdentity
import com.kaushik.railway.data.LastJourney
import com.kaushik.railway.data.Passenger
import com.kaushik.railway.data.SessionStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SessionIsolationTest {
    @Test fun savedPassengersAndJourneyFollowTheirAccountAndLogoutClearsContact() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = SessionStore(context)
        val alice = "alice-${UUID.randomUUID()}"
        val bob = "bob-${UUID.randomUUID()}"
        val owner = AccountIdentity.ownerId(true, alice, "alice@example.com")
        try {
            store.saveSession("Alice", "alice@example.com", "9000000000", alice)
            store.saveLastJourney(owner, LastJourney("NDLS", "MMCT"))
            store.savePassengers(owner, listOf(Passenger(name = "Alice")))
            store.saveSession("Bob", "bob@example.com", userId = bob)
            assertNull(store.lastJourneyFlow.first())
            assertTrue(store.savedPassengersFlow.first().isEmpty())
            assertEquals("", store.sessionFlow.first().mobile)
            assertEquals("Alice", store.observeSavedPassengers(owner).first().single().name)
            store.saveSession("Alice", "alice@example.com", userId = alice)
            assertEquals("NDLS", store.lastJourneyFlow.first()?.fromCode)
            assertEquals("Alice", store.savedPassengersFlow.first().single().name)
            store.clearSession()
            assertFalse(store.sessionFlow.first().loggedIn)
            assertEquals("", store.sessionFlow.first().mobile)
            assertNull(store.lastJourneyFlow.first())
            assertTrue(store.savedPassengersFlow.first().isEmpty())
        } finally {
            store.clearSession()
        }
    }
}
