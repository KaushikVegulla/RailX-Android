package com.kaushik.railway.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * A signed-in Supabase (GoTrue) session. [userId] is the auth.users UUID —
 * it doubles as the account owner for local Room/DataStore scoping
 * (AccountIdentity turns it into "user:<uuid>") and as the RLS identity
 * (auth.uid()) on the server.
 */
data class SupabaseSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochSec: Long,
    val userId: String,
    val email: String = "",
    val fullName: String = "",
    val mobile: String = ""
) {
    fun isExpired(nowEpochSec: Long, skewSec: Long = 60): Boolean =
        expiresAtEpochSec in 1 until (nowEpochSec + skewSec)

    companion object {
        /** Pure parser for a GoTrue token response; null when there is no session. */
        fun fromAuthResponse(
            response: Map<String, Any?>,
            nowEpochSec: Long = System.currentTimeMillis() / 1000
        ): SupabaseSession? {
            val accessToken = response.jsonString("access_token")
            if (accessToken.isBlank()) return null
            val user = response.jsonMap("user")
            val metadata = user.jsonMap("user_metadata")
            val expiresIn = response.jsonLong("expires_in", 3600)
            return SupabaseSession(
                accessToken = accessToken,
                refreshToken = response.jsonString("refresh_token"),
                expiresAtEpochSec = nowEpochSec + expiresIn,
                userId = user.jsonString("id"),
                email = user.jsonString("email"),
                fullName = metadata.jsonString("full_name"),
                mobile = metadata.jsonString("mobile")
            )
        }
    }
}

sealed class SupabaseCall {
    data class SignedIn(val session: SupabaseSession) : SupabaseCall()

    /** Signup accepted, but email confirmation is on — no session yet. */
    data class NeedsConfirmation(val email: String, val message: String) : SupabaseCall()

    data class Err(val message: String, val code: String = "") : SupabaseCall()
}

/** Extracts a human message from a GoTrue/PostgREST error body. */
fun supabaseErrorMessage(body: Map<String, Any?>, httpCode: Int): String {
    for (key in listOf("msg", "error_description", "message", "error")) {
        val value = body.jsonString(key)
        if (value.isNotBlank()) return value
    }
    return "Request failed ($httpCode)"
}

fun supabaseErrorCode(body: Map<String, Any?>): String =
    body.jsonString("error_code").ifBlank { body.jsonString("code") }

/**
 * Supabase Auth (GoTrue) over its REST API, using the OkHttp stack the app
 * already ships — no new dependency. Email/password only: phone OTP stays
 * off because OTP SMS to Indian numbers needs a TRAI DLT header, and Google
 * sign-in needs an OAuth client configured in the dashboard first.
 */
object SupabaseAuth {
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    // Email links (confirmation / password reset) deep-link back into the
    // app instead of Supabase's default localhost site URL.
    private const val AUTH_REDIRECT = "railx://auth-callback"
    private const val AUTH_REDIRECT_ENCODED = "railx%3A%2F%2Fauth-callback"

    suspend fun signUp(
        email: String,
        password: String,
        fullName: String = "",
        mobile: String = "",
        username: String = ""
    ): SupabaseCall = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) {
            return@withContext SupabaseCall.Err("Supabase is not configured in local.properties")
        }
        val body = mapOf<String, Any?>(
            "email" to email.trim(),
            "password" to password,
            "email_redirect_to" to AUTH_REDIRECT,
            "data" to mapOf(
                "full_name" to fullName.trim(),
                "mobile" to mobile.trim(),
                "username" to username.trim()
            )
        )
        val (code, response) = post("signup?redirect_to=$AUTH_REDIRECT_ENCODED", body, accessToken = null)
        val session = SupabaseSession.fromAuthResponse(response)
        when {
            session != null -> SupabaseCall.SignedIn(session)
            // GoTrue answers an already-registered email with HTTP 200 and a
            // user whose identities list is empty (obfuscated, no session).
            // Treating that as "check your email" made the app pretend to
            // create the account a second time.
            code in 200..299 && hasEmptyIdentities(response) ->
                SupabaseCall.Err("This email already has a Supabase account", "user_already_exists")
            // With email confirmation on, GoTrue returns the user but no tokens.
            code in 200..299 && response.jsonMap("user").isNotEmpty() ->
                SupabaseCall.NeedsConfirmation(
                    email.trim(),
                    "Check your email to confirm your account, then sign in."
                )
            code in 200..299 && response.jsonString("id").isNotBlank() ->
                SupabaseCall.NeedsConfirmation(
                    email.trim(),
                    "Check your email to confirm your account, then sign in."
                )
            else -> SupabaseCall.Err(supabaseErrorMessage(response, code), supabaseErrorCode(response))
        }
    }

    /**
     * Resolve a sign-in user ID (username) to the account email via the
     * email_for_username RPC, or null when no account uses that ID.
     * Profiles RLS is owner-only, so the pre-login lookup goes through
     * the security-definer function instead of the table.
     */
    suspend fun lookupEmailForUsername(username: String): String? = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) return@withContext null
        runCatching {
            val body = JSONObject().put("p_username", username.trim()).toString().toRequestBody(jsonType)
            val request = Request.Builder()
                .url(SupabaseConfig.restUrl("rpc/email_for_username"))
                .addHeader("apikey", SupabaseConfig.publishableKey)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()
            http.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val raw = resp.body?.string().orEmpty().trim()
                raw.removeSurrounding("\"").takeIf { it.contains("@") }
            }
        }.getOrNull()
    }

    suspend fun signIn(email: String, password: String): SupabaseCall =
        withContext(Dispatchers.IO) {
            if (!SupabaseConfig.isConfigured) {
                return@withContext SupabaseCall.Err("Supabase is not configured in local.properties")
            }
            val body = mapOf<String, Any?>("email" to email.trim(), "password" to password)
            val (code, response) = post("token?grant_type=password", body, accessToken = null)
            val session = SupabaseSession.fromAuthResponse(response)
            when {
                session != null -> SupabaseCall.SignedIn(session)
                code in 200..299 -> SupabaseCall.Err("Sign-in returned no session")
                else -> SupabaseCall.Err(supabaseErrorMessage(response, code), supabaseErrorCode(response))
            }
        }

    suspend fun refresh(refreshToken: String): SupabaseCall = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured || refreshToken.isBlank()) {
            return@withContext SupabaseCall.Err("No refresh token")
        }
        val body = mapOf<String, Any?>("refresh_token" to refreshToken)
        val (code, response) = post("token?grant_type=refresh_token", body, accessToken = null)
        val session = SupabaseSession.fromAuthResponse(response)
        when {
            session != null -> SupabaseCall.SignedIn(session)
            else -> SupabaseCall.Err(supabaseErrorMessage(response, code), supabaseErrorCode(response))
        }
    }

    /** Best-effort: local state is cleared by the caller regardless. */
    suspend fun signOut(accessToken: String) = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured || accessToken.isBlank()) return@withContext
        runCatching { post("logout", emptyMap(), accessToken) }
        Unit
    }

    suspend fun resendConfirmation(email: String): SupabaseCall = withContext(Dispatchers.IO) {
        val body = mapOf<String, Any?>("type" to "signup", "email" to email.trim())
        val (code, response) = post("resend?redirect_to=$AUTH_REDIRECT_ENCODED", body, accessToken = null)
        if (code in 200..299) {
            SupabaseCall.NeedsConfirmation(email.trim(), "Confirmation email sent again.")
        } else {
            SupabaseCall.Err(supabaseErrorMessage(response, code), supabaseErrorCode(response))
        }
    }

    suspend fun sendPasswordReset(email: String): SupabaseCall = withContext(Dispatchers.IO) {
        val body = mapOf<String, Any?>("email" to email.trim())
        val (code, response) = post("recover?redirect_to=$AUTH_REDIRECT_ENCODED", body, accessToken = null)
        if (code in 200..299) {
            SupabaseCall.NeedsConfirmation(email.trim(), "Password reset email sent.")
        } else {
            SupabaseCall.Err(supabaseErrorMessage(response, code), supabaseErrorCode(response))
        }
    }

    private fun hasEmptyIdentities(response: Map<String, Any?>): Boolean {
        val user = response.jsonMap("user").ifEmpty { response }
        if (!user.containsKey("identities")) return false
        return user.jsonMapList("identities").isEmpty()
    }

    /** Set a new password using a recovery access token from a reset link. */
    suspend fun updatePassword(accessToken: String, newPassword: String): SupabaseCall =
        withContext(Dispatchers.IO) {
            if (!SupabaseConfig.isConfigured) {
                return@withContext SupabaseCall.Err("Supabase is not configured in local.properties")
            }
            val (code, response) = put("user", mapOf("password" to newPassword), accessToken)
            if (code in 200..299) {
                // No session is returned by this endpoint; NeedsConfirmation
                // doubles as the non-error result here.
                SupabaseCall.NeedsConfirmation("", "Password updated.")
            } else {
                SupabaseCall.Err(supabaseErrorMessage(response, code), supabaseErrorCode(response))
            }
        }

    private fun put(
        path: String,
        body: Map<String, Any?>,
        accessToken: String?
    ): Pair<Int, Map<String, Any?>> {
        val requestBody = with(SupabaseJson) { body.toJsonObject() }
            .toString()
            .toRequestBody(jsonType)
        val builder = Request.Builder()
            .url(SupabaseConfig.authUrl(path))
            .addHeader("apikey", SupabaseConfig.publishableKey)
            .addHeader("Content-Type", "application/json")
            .put(requestBody)
        if (!accessToken.isNullOrBlank()) {
            builder.addHeader("Authorization", "Bearer $accessToken")
        }
        http.newCall(builder.build()).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            val parsed = try {
                if (raw.isBlank()) emptyMap() else with(SupabaseJson) { JSONObject(raw).toMap() }
            } catch (_: Exception) {
                emptyMap()
            }
            return resp.code to parsed
        }
    }

    private fun post(
        path: String,
        body: Map<String, Any?>,
        accessToken: String?
    ): Pair<Int, Map<String, Any?>> {
        val requestBody = with(SupabaseJson) { body.toJsonObject() }
            .toString()
            .toRequestBody(jsonType)
        val builder = Request.Builder()
            .url(SupabaseConfig.authUrl(path))
            .addHeader("apikey", SupabaseConfig.publishableKey)
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
        if (!accessToken.isNullOrBlank()) {
            builder.addHeader("Authorization", "Bearer $accessToken")
        }
        http.newCall(builder.build()).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            val parsed = try {
                if (raw.isBlank()) emptyMap() else with(SupabaseJson) { JSONObject(raw).toMap() }
            } catch (_: Exception) {
                emptyMap()
            }
            return resp.code to parsed
        }
    }
}
