package com.kaushik.railway.data.repository

import com.kaushik.railway.data.AvailabilityResult
import com.kaushik.railway.data.PnrResult
import com.kaushik.railway.data.RailKitClient
import com.kaushik.railway.data.RunningStop
import com.kaushik.railway.data.Station
import com.kaushik.railway.data.Train
import com.kaushik.railway.data.TrainClassAvail
import com.kaushik.railway.data.TrainDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.Locale

class TrainRepository(
    private val source: TrainDataSource = RailKitClient,
    nowMillis: () -> Long = { System.nanoTime() / 1_000_000 }
) {
    private data class AvailabilityKey(
        val train: String, val from: String, val to: String,
        val date: String, val coach: String, val quota: String
    )
    private val availability = ExpiringCache<AvailabilityKey, AvailabilityResult>(30_000, 128, nowMillis)
    private val stations = ExpiringCache<String, List<Station>>(86_400_000, 64, nowMillis)
    private val trainSearches = ExpiringCache<String, List<Train>>(600_000, 32, nowMillis)
    private val availabilitySlots = Semaphore(3)

    suspend fun searchTrains(from: String, to: String, date: String): Result<List<Train>> {
        // Repeat searches for the same route and date are served from cache:
        // the RailRadar free plan allows only 100 requests a month.
        val key = "${from.uppercase(Locale.ROOT)}|${to.uppercase(Locale.ROOT)}|$date"
        trainSearches[key]?.let { return Result.success(it) }
        val result = capture { source.searchTrains(from, to, date) }
        result.getOrNull()?.takeIf { it.isNotEmpty() }?.let { trainSearches.put(key, it) }
        return result
    }

    /** Each class can succeed independently; callbacks execute in the caller's context. */
    suspend fun loadClassAvailability(
        train: Train,
        date: String,
        classes: List<String>,
        quota: String,
        onClass: (String, Result<TrainClassAvail>) -> Unit = { _, _ -> }
    ): Result<List<TrainClassAvail>> = coroutineScope {
        val results = classes.distinct().map { cls ->
            async {
                val result = getVacancy(train.number, train.fromCode, train.toCode, date, cls, quota)
                    .map { av -> TrainClassAvail(cls, className(cls), av.fare, av.status, 0) }
                onClass(cls, result)
                result
            }
        }.awaitAll()
        val rows = results.mapNotNull { it.getOrNull() }
        if (rows.isNotEmpty() || results.isEmpty()) Result.success(rows)
        else Result.failure(results.first().exceptionOrNull() ?: IllegalStateException("Availability unavailable"))
    }

    suspend fun getVacancy(
        trainNo: String, from: String, to: String, date: String, coach: String, quota: String
    ): Result<AvailabilityResult> {
        val key = AvailabilityKey(trainNo, from.uppercase(Locale.ROOT), to.uppercase(Locale.ROOT),
            date, coach.uppercase(Locale.ROOT), quota.uppercase(Locale.ROOT))
        availability[key]?.let { return Result.success(it) }
        return capture {
            availabilitySlots.withPermit {
                availability[key] ?: source.getAvailability(trainNo, from, to, date, coach, quota)
                    .also { availability.put(key, it) }
            }
        }
    }

    suspend fun checkPnr(pnr: String): Result<PnrResult> = capture { source.checkPnr(pnr) }

    suspend fun trackTrain(trainNo: String, date: String): Result<List<RunningStop>> =
        capture { source.trackTrain(trainNo, date) }

    suspend fun searchStations(query: String): List<Station> {
        val normalized = query.trim().lowercase(Locale.ROOT)
        if (normalized.length < 2) return emptyList()
        // Offline directory first: complete and free. The API is only a
        // fallback for a station missing from the bundled list.
        val local = com.kaushik.railway.data.StationDirectory.search(normalized)
        if (local.isNotEmpty()) return local
        stations[normalized]?.let { return it }
        return capture { source.searchStations(normalized) }
            .getOrDefault(emptyList()).also { if (it.isNotEmpty()) stations.put(normalized, it) }
    }

    private suspend fun <T> capture(block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun className(code: String) = when (code) {
        "SL" -> "Sleeper"
        "3A" -> "AC 3 Tier"
        "2A" -> "AC 2 Tier"
        "1A" -> "AC First"
        "3E" -> "AC 3 Economy"
        "CC" -> "Chair Car"
        "2S" -> "Second Sitting"
        "EC" -> "Executive"
        else -> code
    }
}
