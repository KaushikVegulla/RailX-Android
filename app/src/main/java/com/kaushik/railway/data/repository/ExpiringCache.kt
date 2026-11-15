package com.kaushik.railway.data.repository

/** Small bounded in-memory cache. A monotonic clock makes expiry independent of wall-clock changes. */
internal class ExpiringCache<K, V>(
    private val ttlMillis: Long,
    private val capacity: Int,
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 }
) {
    private data class Entry<V>(val value: V, val savedAt: Long)
    private val entries = LinkedHashMap<K, Entry<V>>(16, 0.75f, true)

    @Synchronized
    operator fun get(key: K): V? {
        val entry = entries[key] ?: return null
        if (nowMillis() - entry.savedAt >= ttlMillis) {
            entries.remove(key)
            return null
        }
        return entry.value
    }

    @Synchronized
    fun put(key: K, value: V) {
        entries[key] = Entry(value, nowMillis())
        while (entries.size > capacity) entries.remove(entries.keys.first())
    }
}
