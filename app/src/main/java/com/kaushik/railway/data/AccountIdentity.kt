package com.kaushik.railway.data

import java.util.Locale

/** A local storage namespace, derived from the authenticated session, never from a booking. */
object AccountIdentity {
    fun ownerId(loggedIn: Boolean, userId: String, email: String): String {
        if (!loggedIn) return ""
        val id = userId.trim().lowercase(Locale.ROOT)
        if (id.isNotEmpty()) return "user:$id"
        val address = email.trim().lowercase(Locale.ROOT)
        return if (address.isNotEmpty()) "email:$address" else ""
    }
}
