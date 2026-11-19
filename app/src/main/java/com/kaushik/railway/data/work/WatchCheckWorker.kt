package com.kaushik.railway.data.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kaushik.railway.MainActivity
import com.kaushik.railway.data.db.AppDatabase
import com.kaushik.railway.data.repository.WatchRepository
import java.util.concurrent.TimeUnit

/**
 * Background Train Watch check: about every six hours, re-checks all
 * watches against live data and posts a notification for each status
 * that improved. The repository's per-watch five-hour floor keeps the
 * data quota safe if the schedule ever fires early.
 */
class WatchCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = WatchRepository(AppDatabase.getInstance(applicationContext))
        val notes = runCatching { repo.checkAll() }.getOrDefault(emptyList())
        notes.forEachIndexed { index, note -> notify(applicationContext, FIRST_ID + index, note) }
        return Result.success()
    }

    companion object {
        private const val CHANNEL_ID = "train_watch"
        private const val WORK_NAME = "railx-train-watch"
        private const val FIRST_ID = 4100

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<WatchCheckWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun notify(context: Context, id: Int, text: String) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) return
            ensureChannel(context)
            val intent = Intent(context, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pending = PendingIntent.getActivity(
                context, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Train Watch: good news")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()
            runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(NotificationManager::class.java)
                if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                    manager.createNotificationChannel(
                        NotificationChannel(
                            CHANNEL_ID,
                            "Train Watch alerts",
                            NotificationManager.IMPORTANCE_DEFAULT
                        ).apply { description = "Alerts when a watched train's status improves" }
                    )
                }
            }
        }
    }
}
