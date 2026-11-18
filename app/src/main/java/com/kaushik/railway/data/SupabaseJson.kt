package com.kaushik.railway.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Minimal org.json <-> Map bridges, matching the style already used by
 * PaymentApi / RailKitClient. The Map side is pure Kotlin so row/session
 * mapping can be unit-tested on the JVM without Android.
 */
object SupabaseJson {

    fun JSONObject.toMap(): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        val keys = keys()
        while (keys.hasNext()) {
            val key = keys.next()
            out[key] = unwrap(opt(key))
        }
        return out
    }

    fun JSONArray.toList(): List<Any?> {
        val out = ArrayList<Any?>(length())
        for (i in 0 until length()) out.add(unwrap(opt(i)))
        return out
    }

    fun Map<String, Any?>.toJsonObject(): JSONObject {
        val obj = JSONObject()
        for ((key, value) in this) obj.put(key, wrap(value))
        return obj
    }

    fun List<Map<String, Any?>>.toJsonArray(): JSONArray {
        val arr = JSONArray()
        for (row in this) arr.put(row.toJsonObject())
        return arr
    }

    private fun unwrap(value: Any?): Any? = when (value) {
        null, JSONObject.NULL -> null
        is JSONObject -> value.toMap()
        is JSONArray -> value.toList()
        else -> value
    }

    private fun wrap(value: Any?): Any = when (value) {
        null -> JSONObject.NULL
        is Map<*, *> -> {
            val obj = JSONObject()
            for ((k, v) in value) obj.put(k.toString(), wrap(v))
            obj
        }
        is List<*> -> {
            val arr = JSONArray()
            for (item in value) arr.put(wrap(item))
            arr
        }
        else -> value
    }
}

/** String value from a decoded JSON row; numbers/booleans are stringified. */
fun Map<String, Any?>.jsonString(key: String): String =
    when (val v = this[key]) {
        null -> ""
        is String -> v
        else -> v.toString()
    }

/** Int value from a decoded JSON row; accepts Number or numeric String. */
fun Map<String, Any?>.jsonInt(key: String, fallback: Int = 0): Int =
    when (val v = this[key]) {
        is Number -> v.toInt()
        is String -> v.toIntOrNull() ?: fallback
        else -> fallback
    }

/** Long value from a decoded JSON row; accepts Number or numeric String. */
fun Map<String, Any?>.jsonLong(key: String, fallback: Long = 0L): Long =
    when (val v = this[key]) {
        is Number -> v.toLong()
        is String -> v.toLongOrNull() ?: fallback
        else -> fallback
    }

/** Boolean value from a decoded JSON row; accepts Boolean or "true"/"false". */
fun Map<String, Any?>.jsonBool(key: String, fallback: Boolean = false): Boolean =
    when (val v = this[key]) {
        is Boolean -> v
        is String -> v.toBooleanStrictOrNull() ?: fallback
        else -> fallback
    }

@Suppress("UNCHECKED_CAST")
fun Map<String, Any?>.jsonMap(key: String): Map<String, Any?> =
    this[key] as? Map<String, Any?> ?: emptyMap()

@Suppress("UNCHECKED_CAST")
fun Map<String, Any?>.jsonMapList(key: String): List<Map<String, Any?>> {
    val raw = this[key] ?: return emptyList()
    if (raw is List<*>) {
        return raw.mapNotNull { it as? Map<String, Any?> }
    }
    // PostgREST can return jsonb as text when it was stored as a string.
    if (raw is String && raw.isNotBlank()) {
        return try {
            val parsed = with(SupabaseJson) { JSONArray(raw).toList() }
            parsed.mapNotNull { it as? Map<String, Any?> }
        } catch (_: Exception) {
            emptyList()
        }
    }
    return emptyList()
}
