package com.kaushik.railway.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class RazorOrder(
    val orderId: String,
    val keyId: String,
    val amountPaise: Int,
    val currency: String
)

/**
 * Payment API — Razorpay through the Supabase Edge Functions.
 *
 * The Razorpay key secret never ships in the app and is never read from
 * local.properties. The create-order and verify-payment Edge Functions hold
 * it in server-side secrets; the app only sends the signed-in user's
 * Supabase JWT and opens Razorpay Checkout with the key_id / order_id the
 * function returns.
 *
 * (Rebuilt after a stalled CI runner; no behaviour change.)
 */
object PaymentApi {
    @Volatile
    var bearerToken: String? = null

    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun createOrder(amountRupees: Int, receipt: String = "railx"): RazorOrder {
        val token = requireUserToken()
        val body = JSONObject()
            .put("amountRupees", amountRupees.coerceAtLeast(1))
            .put("receipt", receipt)
            .toString()
            .toRequestBody(jsonType)
        val root = postFunction("create-order", body, token)
        val orderId = root.optString("orderId")
        val keyId = root.optString("keyId")
        if (orderId.isBlank() || keyId.isBlank()) {
            throw IllegalStateException("Payment server did not return an order")
        }
        return RazorOrder(
            orderId = orderId,
            keyId = keyId,
            amountPaise = root.optInt("amountPaise", amountRupees.coerceAtLeast(1) * 100),
            currency = root.optString("currency", "INR")
        )
    }

    fun verifyPayment(paymentId: String, orderId: String, signature: String): Boolean {
        val token = requireUserToken()
        val body = JSONObject()
            .put("paymentId", paymentId)
            .put("orderId", orderId)
            .put("signature", signature)
            .toString()
            .toRequestBody(jsonType)
        val root = postFunction("verify-payment", body, token)
        if (!root.optBoolean("verified", false)) {
            throw IllegalStateException("Invalid payment signature")
        }
        return true
    }

    private fun postFunction(name: String, body: RequestBody, token: String): JSONObject {
        val req = Request.Builder()
            .url(SupabaseConfig.functionsUrl(name))
            .addHeader("Authorization", "Bearer $token")
            .addHeader("apikey", SupabaseConfig.publishableKey)
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()
        http.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            val root = try {
                JSONObject(raw.ifBlank { "{}" })
            } catch (_: Exception) {
                JSONObject()
            }
            if (!resp.isSuccessful) {
                val serverError = root.optString("error")
                throw IllegalStateException(
                    when {
                        resp.code == 401 -> "Your session expired. Sign in again before paying."
                        serverError.isNotBlank() -> serverError
                        else -> "Payment request failed (${resp.code})"
                    }
                )
            }
            return root
        }
    }

    private fun requireUserToken(): String =
        bearerToken?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Sign in with your Supabase account before paying.")
}

object PaymentBridge {
    var onSuccess: ((paymentId: String, orderId: String, signature: String) -> Unit)? = null
    var onError: ((message: String) -> Unit)? = null
}
