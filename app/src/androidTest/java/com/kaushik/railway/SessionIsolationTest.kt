package com.kaushik.railway

import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.kaushik.railway.data.AccountProvider
import com.kaushik.railway.data.AccountStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
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
    @Test fun demoRegistrationSurvivesReloadAndSignsInWithoutCloudCredentials() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = AccountStore(context)
        val id = "demo-${UUID.randomUUID()}"
        val password = "Demo-Test-482!"
        val sessions = SessionStore(context)
        sessions.clearSession()
        val holder = ViewModelStore()
        lateinit var vm: AppViewModel
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        try {
            instrumentation.runOnMainSync {
                vm = AppViewModel()
                holder.put("auth", vm)
            }
            val registration = CompletableDeferred<String?>()
            instrumentation.runOnMainSync {
                vm.startRegistration(false)
                vm.regUserId = id
                vm.regFullName = "Demo"
                vm.regEmail = "$id@example.test"
                vm.regPassword = password
                vm.issueOtps()
                vm.submitRegistrationCodes(vm.regEmailOtp, vm.regMobileOtp) { registration.complete(it) }
            }
            assertNull(withTimeout(20_000) { registration.await() })
            val reloaded = AccountStore(context).findProfile(id)!!
            assertEquals(AccountProvider.LOCAL_DEMO, reloaded.authProvider)
            assertEquals(id, store.authenticate(reloaded.email, password)?.userId)
            suspend fun login(pass: String): String {
                val result = CompletableDeferred<String>()
                instrumentation.runOnMainSync {
                    vm.signIn(id, pass, { result.complete("success") },
                        { result.complete("unexpected verification") }, { result.complete(it) })
                }
                return withTimeout(20_000) { result.await() }
            }
            assertEquals("Invalid user ID or password", login("wrong-password"))
            assertFalse(sessions.sessionFlow.first().loggedIn)
            assertEquals("success", login(password))
            assertEquals(id, sessions.sessionFlow.first().userId)
            assertTrue(sessions.sessionFlow.first().loggedIn)
        } finally {
            instrumentation.runOnMainSync { holder.clear() }
            sessions.clearSession()
        }
    }

    @Test fun cloudIdentityIsPersistedAndLegacyAccountsAreNotAssumedToBeDemo() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = AccountStore(context)
        val id = "cloud-${UUID.randomUUID()}"
        store.create(id, "Cloud", "$id@example.test", "", "Password!482", "English",
            authProvider = AccountProvider.SUPABASE, cloudUsername = "canonical-$id")
        val reloaded = AccountStore(context).findProfile(id)!!
        assertEquals(AccountProvider.SUPABASE, reloaded.authProvider)
        assertEquals("canonical-$id", reloaded.cloudUsername)
        val legacyId = "legacy-${UUID.randomUUID()}"
        store.create(legacyId, "Legacy", "$legacyId@example.test", "", "Password!482", "English")
        assertEquals(AccountProvider.LEGACY, store.findProfile(legacyId)?.authProvider)
    }

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
