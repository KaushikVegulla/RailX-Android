package com.kaushik.railway.util

import android.content.Context
import java.io.File

/**
 * Saves the most recent uncaught exception to app storage so the next
 * launch can show what actually went wrong instead of dying silently.
 */
object CrashLog {
    private const val FILE_NAME = "last_crash.txt"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                File(appContext.filesDir, FILE_NAME)
                    .writeText(error.stackTraceToString().take(6000))
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(context: Context): String? = runCatching {
        val file = File(context.filesDir, FILE_NAME)
        if (file.exists()) file.readText().ifBlank { null } else null
    }.getOrNull()

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE_NAME).delete() }
    }
}
