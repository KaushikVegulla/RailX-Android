package com.kaushik.railway.data.repository

import com.kaushik.railway.data.WatchStatus
import com.kaushik.railway.data.db.AppDatabase
import com.kaushik.railway.data.db.WatchEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Train Watch storage and checking. A check re-runs the live train
 * search for the watched route and date (the same source the search
 * screen uses) and reads the watched class's status from it.
 */
class WatchRepository(
    private val db: AppDatabase,
    private val trains: TrainRepository = TrainRepository()
) {
    private val dao = db.watchDao()

    fun observeWatches(ownerId: String): Flow<List<WatchEntity>> = dao.getAll(ownerId)

    suspend fun add(
        ownerId: String,
        trainNumber: String,
        trainName: String,
        fromCode: String,
        toCode: String,
        date: String,
        classCode: String
    ): Long = withContext(Dispatchers.Default) {
        require(ownerId.isNotBlank()) { "Sign in before watching a train" }
        dao.insert(
            WatchEntity(
                ownerId = ownerId,
                trainNumber = trainNumber,
                trainName = trainName,
                fromCode = fromCode.uppercase(Locale.ROOT),
                toCode = toCode.uppercase(Locale.ROOT),
                date = date,
                classCode = classCode.uppercase(Locale.ROOT),
                lastStatus = "",
                lastCheckedAt = 0,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun delete(ownerId: String, id: Long) = withContext(Dispatchers.Default) {
        dao.delete(ownerId, id)
    }

    /**
     * Check one watch against live data. Returns a traveller-facing
     * note when the status improved, otherwise null. The newest seen
     * status is stored either way. Background checks skip watches
     * checked within the last five hours to protect the data quota.
     */
    suspend fun check(watch: WatchEntity, force: Boolean = false): String? {
        if (!force && watch.lastCheckedAt > 0 &&
            System.currentTimeMillis() - watch.lastCheckedAt < MIN_CHECK_INTERVAL_MS
        ) return null
        val result = trains.searchTrains(watch.fromCode, watch.toCode, toApiDate(watch.date))
        val list = result.getOrNull() ?: return null
        val train = list.firstOrNull { it.number == watch.trainNumber } ?: return null
        val status = train.classes.firstOrNull { it.code.equals(watch.classCode, ignoreCase = true) }
            ?.status?.takeIf { it.isNotBlank() } ?: return null
        val old = watch.lastStatus
        dao.updateStatus(watch.id, status, System.currentTimeMillis())
        return if (old.isNotBlank() && WatchStatus.improved(old, status)) {
            "Train ${watch.trainNumber} on ${watch.date}: ${watch.classCode} is now $status (was $old)"
        } else null
    }

    /** Check every stored watch; returns the improvement notes. */
    suspend fun checkAll(force: Boolean = false): List<String> = withContext(Dispatchers.Default) {
        dao.getAllOnce().mapNotNull { check(it, force) }
    }

    companion object {
        const val MIN_CHECK_INTERVAL_MS = 5 * 60 * 60 * 1000L

        /** "20 Nov 2026" -> "2026-11-20", the format the data source takes. */
        fun toApiDate(display: String): String = try {
            val inFmt = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
            val outFmt = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
            outFmt.format(inFmt.parse(display)!!)
        } catch (_: Exception) {
            display
        }
    }
}
