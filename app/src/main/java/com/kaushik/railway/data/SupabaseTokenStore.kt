package com.kaushik.railway.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.supabaseDataStore: DataStore<Preferences> by preferencesDataStore(name = "railx_supabase")

/**
 * Persists the Supabase session (tokens + identity) on the device.
 * SessionStore keeps the app-facing session (name/email/userId) that
 * existing screens already observe; this store keeps the tokens that
 * SessionStore has no place for.
 */
class SupabaseTokenStore(private val context: Context) {
    private val keyAccess = stringPreferencesKey("access_token")
    private val keyRefresh = stringPreferencesKey("refresh_token")
    private val keyExpires = longPreferencesKey("expires_at")
    private val keyUserId = stringPreferencesKey("user_id")
    private val keyEmail = stringPreferencesKey("email")
    private val keyName = stringPreferencesKey("full_name")
    private val keyMobile = stringPreferencesKey("mobile")

    suspend fun save(session: SupabaseSession) {
        context.supabaseDataStore.edit { prefs ->
            prefs[keyAccess] = session.accessToken
            prefs[keyRefresh] = session.refreshToken
            prefs[keyExpires] = session.expiresAtEpochSec
            prefs[keyUserId] = session.userId
            prefs[keyEmail] = session.email
            prefs[keyName] = session.fullName
            prefs[keyMobile] = session.mobile
        }
    }

    suspend fun load(): SupabaseSession? {
        val prefs = context.supabaseDataStore.data.first()
        val access = prefs[keyAccess] ?: return null
        val userId = prefs[keyUserId] ?: return null
        if (access.isBlank() || userId.isBlank()) return null
        return SupabaseSession(
            accessToken = access,
            refreshToken = prefs[keyRefresh] ?: "",
            expiresAtEpochSec = prefs[keyExpires] ?: 0L,
            userId = userId,
            email = prefs[keyEmail] ?: "",
            fullName = prefs[keyName] ?: "",
            mobile = prefs[keyMobile] ?: ""
        )
    }

    suspend fun clear() {
        context.supabaseDataStore.edit { prefs -> prefs.clear() }
    }
}
