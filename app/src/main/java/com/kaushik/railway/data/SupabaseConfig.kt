package com.kaushik.railway.data

import com.kaushik.railway.BuildConfig

/**
 * Supabase project configuration.
 *
 * The URL and the publishable key are client configuration: the publishable
 * key is designed to ship in apps and is
 * safe ONLY because every table in supabase/schema.sql has Row Level
 * Security policies scoped to auth.uid(). Never add the secret /
 * service_role key here, in local.properties, or anywhere in the app — it
 * bypasses RLS completely.
 *
 * Defaults point at the RailX project and can be overridden in
 * local.properties (supabase.url / supabase.publishable.key), which stays
 * gitignored.
 */
object SupabaseConfig {
    val url: String get() = normalizeUrl(BuildConfig.SUPABASE_URL)
    val publishableKey: String get() = BuildConfig.SUPABASE_PUBLISHABLE_KEY.trim()

    val isConfigured: Boolean
        get() = url.startsWith("https://") && publishableKey.isNotBlank() &&
            !publishableKey.contains("YOUR_") && !publishableKey.contains("XXXX")

    fun normalizeUrl(raw: String): String = raw.trim().trimEnd('/')

    fun authUrl(path: String): String = "$url/auth/v1/$path"
    fun restUrl(table: String): String = "$url/rest/v1/$table"
}
