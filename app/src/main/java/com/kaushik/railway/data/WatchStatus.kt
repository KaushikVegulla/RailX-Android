package com.kaushik.railway.data

import java.util.Locale

/**
 * Orders availability statuses so Train Watch can tell whether a
 * watched journey got better: confirmed/available beats RAC, RAC
 * beats waitlist, and inside RAC/WL a lower number is a better place
 * in the queue.
 */
object WatchStatus {

    fun rank(status: String): Int {
        val s = status.uppercase(Locale.ENGLISH).trim()
        if (s.isBlank()) return Int.MIN_VALUE
        val num = Regex("\\d+").find(s)?.value?.toIntOrNull() ?: 0
        return when {
            s.startsWith("AVAILABLE") || s.startsWith("CNF") || s.contains("CONFIRMED") -> 3000 + num
            s.startsWith("RAC") -> 2000 - num
            s.contains("WL") -> 1000 - num
            s.startsWith("REGRET") || s.contains("NOT AVAILABLE") || s.contains("DEPARTED") -> -1
            else -> 0
        }
    }

    /** True when [new] is a strictly better status than [old]. */
    fun improved(old: String, new: String): Boolean = rank(new) > rank(old)
}
