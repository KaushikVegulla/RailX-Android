package com.kaushik.railway.data.repository

import android.content.Context
import com.kaushik.railway.data.SessionStore
import com.kaushik.railway.data.SupabaseAuth
import com.kaushik.railway.data.SupabaseCall
import com.kaushik.railway.data.SupabaseSession
import com.kaushik.railway.data.SupabaseTokenStore

/**
 * Supabase-backed auth repository.
 *
 * On a successful sign-in it persists the tokens in SupabaseTokenStore and
 * mirrors the identity into SessionStore, so the existing account-scoped
 * storage keeps working unchanged: the Supabase user UUID becomes the
 * SessionStore userId and therefore the Room/DataStore owner.
 *
 * AppViewModel drives sign-in and registration through this repository:
 * Supabase is the cloud auth, and the local demo flow remains the offline
 * fallback.
 */
class SupabaseAuthRepository(context: Context) {
    private val tokenStore = SupabaseTokenStore(context.applicationContext)
    private val sessionStore = SessionStore(context.applicationContext)

    suspend fun signUp(email: String, password: String, fullName: String, mobile: String): SupabaseCall {
        val call = try {
            SupabaseAuth.signUp(email, password, fullName, mobile)
        } catch (e: Exception) {
            return SupabaseCall.Err("Could not reach Supabase (${e.message ?: "network error"})")
        }
        if (call is SupabaseCall.SignedIn) persist(call.session, fullName, mobile)
        return call
    }

    suspend fun signIn(email: String, password: String): SupabaseCall {
        val call = try {
            SupabaseAuth.signIn(email, password)
        } catch (e: Exception) {
            return SupabaseCall.Err("Could not reach Supabase (${e.message ?: "network error"})")
        }
        if (call is SupabaseCall.SignedIn) persist(call.session)
        return call
    }

    /** Stored session, refreshed first when its access token has expired. */
    suspend fun currentSession(): SupabaseSession? {
        val stored = tokenStore.load() ?: return null
        val now = System.currentTimeMillis() / 1000
        if (!stored.isExpired(now)) return stored
        if (stored.refreshToken.isBlank()) {
            signOut()
            return null
        }
        val refreshed = try {
            SupabaseAuth.refresh(stored.refreshToken)
        } catch (e: Exception) {
            signOut()
            return null
        }
        return when (val call = refreshed) {
            is SupabaseCall.SignedIn -> {
                // Refresh responses may omit metadata kept from sign-in.
                val merged = call.session.copy(
                    fullName = call.session.fullName.ifBlank { stored.fullName },
                    mobile = call.session.mobile.ifBlank { stored.mobile },
                    email = call.session.email.ifBlank { stored.email }
                )
                persist(merged)
                merged
            }
            else -> {
                signOut()
                null
            }
        }
    }

    suspend fun signOut() {
        val stored = tokenStore.load()
        if (stored != null) runCatching { SupabaseAuth.signOut(stored.accessToken) }
        tokenStore.clear()
        sessionStore.clearSession()
    }

    suspend fun resendConfirmation(email: String): SupabaseCall = try {
        SupabaseAuth.resendConfirmation(email)
    } catch (e: Exception) {
        SupabaseCall.Err("Could not reach Supabase (${e.message ?: "network error"})")
    }

    suspend fun sendPasswordReset(email: String): SupabaseCall = try {
        SupabaseAuth.sendPasswordReset(email)
    } catch (e: Exception) {
        SupabaseCall.Err("Could not reach Supabase (${e.message ?: "network error"})")
    }

    private suspend fun persist(
        session: SupabaseSession,
        fullNameOverride: String = "",
        mobileOverride: String = ""
    ) {
        val named = session.copy(
            fullName = session.fullName.ifBlank { fullNameOverride },
            mobile = session.mobile.ifBlank { mobileOverride }
        )
        tokenStore.save(named)
        sessionStore.saveSession(
            name = named.fullName,
            email = named.email,
            mobile = named.mobile,
            userId = named.userId
        )
    }
}
