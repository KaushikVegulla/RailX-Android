package com.kaushik.railway.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "railone_session")

data class UserSession(
    val loggedIn: Boolean = false,
    val name: String = "",
    val email: String = "",
    val mobile: String = "",
    val userId: String = ""
)

/** Last search/book route for one-tap rebook */
data class LastJourney(
    val fromCode: String = "",
    val toCode: String = "",
    val classCode: String = "",
    val quota: String = "GN - General",
    val trainNumber: String = "",
    val trainName: String = ""
)

class SessionStore(private val context: Context) {
    private val KEY_LOGGED_IN = booleanPreferencesKey("logged_in")
    private val KEY_NAME = stringPreferencesKey("name")
    private val KEY_EMAIL = stringPreferencesKey("email")
    private val KEY_MOBILE = stringPreferencesKey("mobile")
    private val KEY_USER_ID = stringPreferencesKey("user_id")
    // The old unscoped keys remain on disk, but cannot be attributed to an account safely.

    val sessionFlow: Flow<UserSession> = context.dataStore.data.map { prefs ->
        if (prefs[KEY_LOGGED_IN] != true) UserSession() else UserSession(
            loggedIn = true,
            name = prefs[KEY_NAME] ?: "",
            email = prefs[KEY_EMAIL] ?: "",
            mobile = prefs[KEY_MOBILE] ?: "",
            userId = prefs[KEY_USER_ID] ?: ""
        )
    }.distinctUntilChanged()

    val lastJourneyFlow: Flow<LastJourney?> = context.dataStore.data.map { prefs ->
        val owner = activeOwner(prefs)
        if (owner.isBlank()) null else prefs[scopedKey("last_journey", owner)]
    }.distinctUntilChanged().map { raw -> raw?.let(::parseLastJourney) }.flowOn(Dispatchers.Default)

    val savedPassengersFlow: Flow<List<Passenger>> = context.dataStore.data.map { prefs ->
        val owner = activeOwner(prefs)
        if (owner.isBlank()) null else prefs[scopedKey("passengers", owner)]
    }.distinctUntilChanged().map { raw -> raw?.let(::parsePassengers) ?: emptyList() }
        .flowOn(Dispatchers.Default)

    fun observeLastJourney(ownerId: String): Flow<LastJourney?> =
        if (ownerId.isBlank()) flowOf(null) else context.dataStore.data
            .map { it[scopedKey("last_journey", ownerId)] }
            .distinctUntilChanged()
            .map { raw -> raw?.let(::parseLastJourney) }
            .flowOn(Dispatchers.Default)

    fun observeSavedPassengers(ownerId: String): Flow<List<Passenger>> =
        if (ownerId.isBlank()) flowOf(emptyList()) else context.dataStore.data
            .map { it[scopedKey("passengers", ownerId)] }
            .distinctUntilChanged()
            .map { raw -> raw?.let(::parsePassengers) ?: emptyList() }
            .flowOn(Dispatchers.Default)

    suspend fun saveSession(name: String, email: String, mobile: String = "", userId: String = "") {
        context.dataStore.edit { prefs ->
            prefs[KEY_LOGGED_IN] = true
            prefs[KEY_NAME] = name
            prefs[KEY_EMAIL] = email
            prefs[KEY_MOBILE] = mobile
            prefs[KEY_USER_ID] = userId
        }
    }

    suspend fun clearSession() {
        // Account-scoped history stays available on the next sign-in to the same account.
        context.dataStore.edit { prefs ->
            prefs[KEY_LOGGED_IN] = false
            prefs.remove(KEY_NAME)
            prefs.remove(KEY_EMAIL)
            prefs.remove(KEY_USER_ID)
            prefs.remove(KEY_MOBILE)
        }
    }

    suspend fun updateProfile(name: String, mobile: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_NAME] = name
            prefs[KEY_MOBILE] = mobile
        }
    }

    suspend fun saveLastJourney(ownerId: String, j: LastJourney) {
        require(ownerId.isNotBlank()) { "Sign in before saving a journey" }
        context.dataStore.edit { prefs ->
            prefs[scopedKey("last_journey", ownerId)] = JSONObject()
                .put("fromCode", j.fromCode)
                .put("toCode", j.toCode)
                .put("classCode", j.classCode)
                .put("quota", j.quota)
                .put("trainNumber", j.trainNumber)
                .put("trainName", j.trainName)
                .toString()
        }
    }

    suspend fun savePassengers(ownerId: String, list: List<Passenger>) {
        require(ownerId.isNotBlank()) { "Sign in before saving passengers" }
        val arr = JSONArray()
        list.filter { it.name.isNotBlank() }.forEach { p ->
            arr.put(
                JSONObject()
                    .put("name", p.name)
                    .put("age", p.age)
                    .put("gender", p.gender)
                    .put("berth", p.berth)
                    .put("concession", p.concession)
            )
        }
        context.dataStore.edit { prefs ->
            prefs[scopedKey("passengers", ownerId)] = arr.toString()
        }
    }

    private fun activeOwner(prefs: Preferences): String = AccountIdentity.ownerId(
        loggedIn = prefs[KEY_LOGGED_IN] == true,
        userId = prefs[KEY_USER_ID].orEmpty(),
        email = prefs[KEY_EMAIL].orEmpty()
    )

    private fun scopedKey(kind: String, ownerId: String): Preferences.Key<String> {
        val digest = MessageDigest.getInstance("SHA-256").digest(ownerId.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return stringPreferencesKey("${kind}_$digest")
    }

    private fun parseLastJourney(raw: String): LastJourney? = try {
        val o = JSONObject(raw)
        LastJourney(
            fromCode = o.optString("fromCode"),
            toCode = o.optString("toCode"),
            classCode = o.optString("classCode"),
            quota = o.optString("quota", "GN - General"),
            trainNumber = o.optString("trainNumber"),
            trainName = o.optString("trainName")
        )
    } catch (_: Exception) {
        null
    }

    private fun parsePassengers(raw: String): List<Passenger> = try {
        val arr = JSONArray(raw)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Passenger(
                name = o.optString("name"),
                age = o.optString("age"),
                gender = o.optString("gender", "Male"),
                berth = o.optString("berth", "No Preference"),
                concession = o.optString("concession", "None")
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}
