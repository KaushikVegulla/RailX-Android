package com.kaushik.railway.data

import android.content.Context
import java.util.Locale

/**
 * Offline directory of Indian Railways stations, bundled as
 * assets/stations.txt (one "CODE|Name|City" per line, ~10,700 stations).
 *
 * Station suggestions come from here first: instant, complete, and they
 * cost no RailRadar API calls — the free plan's monthly quota is small,
 * so the API is only a fallback for anything missing locally.
 */
object StationDirectory {
    @Volatile
    private var stations: List<Station>? = null

    @Volatile
    private var byCode: Map<String, Station> = emptyMap()

    /** Idempotent; safe to call from a background thread at app start. */
    fun load(context: Context) {
        if (stations != null) return
        synchronized(this) {
            if (stations != null) return
            val parsed = try {
                context.assets.open("stations.txt").bufferedReader().useLines { lines ->
                    lines.mapNotNull { line ->
                        val parts = line.split("|")
                        if (parts.size < 2) null
                        else Station(
                            code = parts[0].trim(),
                            name = parts[1].trim(),
                            city = parts.getOrNull(2)?.trim().orEmpty()
                        )
                    }.toList()
                }
            } catch (_: Exception) {
                emptyList()
            }
            byCode = parsed.associateBy { it.code }
            stations = parsed
        }
    }

    fun byCode(code: String): Station? = byCode[code.uppercase(Locale.ROOT)]

    /**
     * Ranked matches: exact code, code prefix, name prefix, city prefix,
     * then name/city contains. Returns an empty list until [load] has run.
     */
    fun search(query: String, limit: Int = 20): List<Station> {
        val all = stations ?: return emptyList()
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.length < 2) return emptyList()
        val qu = q.uppercase(Locale.ROOT)
        fun tier(s: Station): Int {
            val name = s.name.lowercase(Locale.ROOT)
            val city = s.city.lowercase(Locale.ROOT)
            return when {
                s.code == qu -> 0
                s.code.startsWith(qu) -> 1
                name.startsWith(q) -> 2
                city.startsWith(q) -> 3
                name.split(' ').any { it.startsWith(q) } -> 4
                name.contains(q) -> 5
                city.contains(q) -> 6
                else -> -1
            }
        }
        return all.mapNotNull { s -> tier(s).takeIf { it >= 0 }?.let { it to s } }
            .sortedWith(compareBy({ it.first }, { it.second.name.length }, { it.second.name }))
            .take(limit)
            .map { it.second }
    }
}
