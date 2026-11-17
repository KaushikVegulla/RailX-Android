package com.kaushik.railway.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * PostgREST client for the RailX tables in supabase/schema.sql.
 *
 * Every request carries the publishable key as `apikey` and the signed-in
 * user's access token as the bearer token, so Postgres Row Level Security
 * (auth.uid() = user_id) decides what the app can see. There is no
 * service_role key in the app, ever.
 */
object SupabaseDb {
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    // ------------------------------------------------------------ profiles
    suspend fun fetchProfile(session: SupabaseSession): RemoteProfile? {
        val rows = select("profiles", session, mapOf("id" to "eq.${session.userId}"))
        return rows.firstOrNull()?.let { RemoteProfile.fromRow(it) }
    }

    suspend fun upsertProfile(session: SupabaseSession, profile: RemoteProfile): RemoteProfile? {
        val rows = insert("profiles", listOf(profile.toRow()), session, upsert = true)
        return rows.firstOrNull()?.let { RemoteProfile.fromRow(it) } ?: profile
    }

    // ------------------------------------------------------------ bookings
    suspend fun fetchBookings(session: SupabaseSession): List<RemoteBooking> {
        val rows = select(
            "bookings", session,
            mapOf("user_id" to "eq.${session.userId}", "order" to "created_at.desc")
        )
        return rows.map { RemoteBooking.fromRow(it) }
    }

    suspend fun upsertBooking(session: SupabaseSession, booking: Booking): RemoteBooking? {
        val remote = RemoteBooking.fromDomain(booking)
        val rows = insert("bookings", listOf(remote.toRow(session.userId)), session, upsert = true)
        return rows.firstOrNull()?.let { RemoteBooking.fromRow(it) } ?: remote
    }

    suspend fun updateBookingStatus(session: SupabaseSession, pnr: String, status: String) {
        update(
            "bookings", session,
            mapOf("user_id" to "eq.${session.userId}", "pnr" to "eq.$pnr"),
            mapOf("status" to status)
        )
    }

    // ----------------------------------------------------------- passengers
    suspend fun fetchPassengers(session: SupabaseSession): List<RemotePassenger> {
        val rows = select(
            "passengers", session,
            mapOf("user_id" to "eq.${session.userId}", "order" to "created_at.asc")
        )
        return rows.map { RemotePassenger.fromRow(it) }
    }

    suspend fun insertPassenger(session: SupabaseSession, passenger: Passenger): RemotePassenger? {
        val remote = RemotePassenger.fromDomain(passenger)
        val rows = insert("passengers", listOf(remote.toRow(session.userId)), session, upsert = false)
        return rows.firstOrNull()?.let { RemotePassenger.fromRow(it) } ?: remote
    }

    suspend fun deletePassenger(session: SupabaseSession, passengerId: String) {
        delete(
            "passengers", session,
            mapOf("user_id" to "eq.${session.userId}", "id" to "eq.$passengerId")
        )
    }

    // ------------------------------------------------------ favourite routes
    suspend fun fetchFavouriteRoutes(session: SupabaseSession): List<RemoteFavouriteRoute> {
        val rows = select(
            "favourite_routes", session,
            mapOf("user_id" to "eq.${session.userId}", "order" to "created_at.asc")
        )
        return rows.map { RemoteFavouriteRoute.fromRow(it) }
    }

    suspend fun addFavouriteRoute(
        session: SupabaseSession,
        fromCode: String,
        toCode: String
    ): RemoteFavouriteRoute? {
        val route = RemoteFavouriteRoute(fromCode = fromCode, toCode = toCode)
        val rows = insert("favourite_routes", listOf(route.toRow(session.userId)), session, upsert = true)
        return rows.firstOrNull()?.let { RemoteFavouriteRoute.fromRow(it) } ?: route
    }

    suspend fun deleteFavouriteRoute(session: SupabaseSession, routeId: String) {
        delete(
            "favourite_routes", session,
            mapOf("user_id" to "eq.${session.userId}", "id" to "eq.$routeId")
        )
    }

    // ------------------------------------------------------------- generic
    suspend fun select(
        table: String,
        session: SupabaseSession,
        filters: Map<String, String>
    ): List<Map<String, Any?>> = withContext(Dispatchers.IO) {
        val url = SupabaseConfig.restUrl(table).toHttpUrl().newBuilder().apply {
            for ((key, value) in filters) addQueryParameter(key, value)
        }.build()
        val request = baseRequest(url.toString(), session).get().build()
        executeRows(request)
    }

    suspend fun insert(
        table: String,
        rows: List<Map<String, Any?>>,
        session: SupabaseSession,
        upsert: Boolean
    ): List<Map<String, Any?>> = withContext(Dispatchers.IO) {
        val body = with(SupabaseJson) { rows.toJsonArray() }.toString().toRequestBody(jsonType)
        val prefer = if (upsert) "resolution=merge-duplicates,return=representation" else "return=representation"
        val request = baseRequest(SupabaseConfig.restUrl(table), session)
            .addHeader("Prefer", prefer)
            .post(body)
            .build()
        executeRows(request)
    }

    suspend fun update(
        table: String,
        session: SupabaseSession,
        filters: Map<String, String>,
        values: Map<String, Any?>
    ): List<Map<String, Any?>> = withContext(Dispatchers.IO) {
        val url = SupabaseConfig.restUrl(table).toHttpUrl().newBuilder().apply {
            for ((key, value) in filters) addQueryParameter(key, value)
        }.build()
        val body = with(SupabaseJson) { values.toJsonObject() }.toString().toRequestBody(jsonType)
        val request = baseRequest(url.toString(), session)
            .addHeader("Prefer", "return=representation")
            .patch(body)
            .build()
        executeRows(request)
    }

    suspend fun delete(
        table: String,
        session: SupabaseSession,
        filters: Map<String, String>
    ): List<Map<String, Any?>> = withContext(Dispatchers.IO) {
        val url = SupabaseConfig.restUrl(table).toHttpUrl().newBuilder().apply {
            for ((key, value) in filters) addQueryParameter(key, value)
        }.build()
        val request = baseRequest(url.toString(), session)
            .addHeader("Prefer", "return=representation")
            .delete()
            .build()
        executeRows(request)
    }

    private fun baseRequest(url: String, session: SupabaseSession): Request.Builder =
        Request.Builder()
            .url(url)
            .addHeader("apikey", SupabaseConfig.publishableKey)
            .addHeader("Authorization", "Bearer ${session.accessToken}")
            .addHeader("Content-Type", "application/json")

    private fun executeRows(request: Request): List<Map<String, Any?>> {
        http.newCall(request).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val error = try {
                    if (raw.isBlank()) emptyMap() else with(SupabaseJson) { JSONObject(raw).toMap() }
                } catch (_: Exception) {
                    emptyMap()
                }
                throw IllegalStateException(supabaseErrorMessage(error, resp.code))
            }
            if (raw.isBlank()) return emptyList()
            val array = try {
                JSONArray(raw)
            } catch (_: Exception) {
                return emptyList()
            }
            return with(SupabaseJson) { array.toList() }
                .filterIsInstance<Map<String, Any?>>()
        }
    }
}
