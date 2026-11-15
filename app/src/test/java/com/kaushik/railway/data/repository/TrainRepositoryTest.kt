package com.kaushik.railway.data.repository

import com.kaushik.railway.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class TrainRepositoryTest {
    private val train = Train("12345", "Test", "AAA", "BBB", "10:00", "11:00", "1h", "Daily")
    private val available = AvailabilityResult(500, "AVAILABLE", emptyList())

    private open inner class FakeSource : TrainDataSource {
        override suspend fun searchTrains(from: String, to: String, date: String) = emptyList<Train>()
        override suspend fun getAvailability(trainNo: String, from: String, to: String, date: String, coach: String, quota: String) = available
        override suspend fun checkPnr(pnr: String): PnrResult = error("Unused")
        override suspend fun trackTrain(trainNo: String, date: String) = emptyList<RunningStop>()
        override suspend fun searchStations(query: String) = emptyList<Station>()
    }

    @Test
    fun availabilityCacheExpiresAndSeparatesJourneyKeys() = runBlocking {
        var clock = 0L
        val calls = AtomicInteger()
        val source = object : FakeSource() {
            override suspend fun getAvailability(trainNo: String, from: String, to: String, date: String, coach: String, quota: String): AvailabilityResult {
                calls.incrementAndGet()
                return available
            }
        }
        val repo = TrainRepository(source) { clock }
        suspend fun fetch(date: String = "2026-11-14", quota: String = "GN", coach: String = "SL", to: String = "BBB") =
            repo.getVacancy("12345", "AAA", to, date, coach, quota).getOrThrow()
        fetch()
        fetch()
        assertEquals(1, calls.get())
        fetch(date = "2026-11-15")
        fetch(quota = "TQ")
        fetch(coach = "3A")
        fetch(to = "CCC")
        assertEquals(5, calls.get())
        clock = 30_000
        fetch()
        assertEquals(6, calls.get())
    }

    @Test
    fun oneFailedClassDoesNotDiscardSuccessfulClasses() = runBlocking {
        val source = object : FakeSource() {
            override suspend fun getAvailability(trainNo: String, from: String, to: String, date: String, coach: String, quota: String): AvailabilityResult {
                if (coach == "1A") error("Unsupported class")
                return available
            }
        }
        val callbacks = mutableMapOf<String, Boolean>()
        val result = TrainRepository(source).loadClassAvailability(train, "2026-11-14", listOf("1A", "SL"), "GN") { cls, row ->
            callbacks[cls] = row.isSuccess
        }
        assertEquals(listOf("SL"), result.getOrThrow().map { it.code })
        assertEquals(mapOf("1A" to false, "SL" to true), callbacks)
    }

    @Test
    fun availabilityConcurrencyIsBoundedAndResultsArriveBeforeAllComplete() = runBlocking {
        withTimeout(5_000) {
            val active = AtomicInteger()
            val peak = AtomicInteger()
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val slowClass = CompletableDeferred<Unit>()
            val firstResult = CompletableDeferred<Unit>()
            val source = object : FakeSource() {
                override suspend fun getAvailability(trainNo: String, from: String, to: String, date: String, coach: String, quota: String): AvailabilityResult {
                    val count = active.incrementAndGet()
                    peak.updateAndGet { maxOf(it, count) }
                    if (count == 3) started.complete(Unit)
                    try {
                        release.await()
                        if (coach == "SL") slowClass.await()
                        return available
                    } finally { active.decrementAndGet() }
                }
            }
            val pending = async {
                TrainRepository(source).loadClassAvailability(train, "2026-11-14", listOf("SL", "1A", "2A", "3A", "CC", "EC"), "GN") { _, _ ->
                    firstResult.complete(Unit)
                }
            }
            started.await()
            assertEquals(3, active.get())
            release.complete(Unit)
            firstResult.await()
            assertFalse(pending.isCompleted)
            slowClass.complete(Unit)
            assertEquals(6, pending.await().getOrThrow().size)
            assertTrue(peak.get() <= 3)
        }
    }

    @Test
    fun cancellationIsNotTurnedIntoEmptyStationResults() = runBlocking {
        val source = object : FakeSource() {
            override suspend fun searchStations(query: String): List<Station> = throw CancellationException("Cancelled")
        }
        try {
            TrainRepository(source).searchStations("Delhi")
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
    }

    @Test
    fun stationCacheNormalizesQueries() = runBlocking {
        val calls = AtomicInteger()
        val source = object : FakeSource() {
            override suspend fun searchStations(query: String): List<Station> {
                calls.incrementAndGet()
                return listOf(Station("NDLS", "New Delhi", "Delhi"))
            }
        }
        val repo = TrainRepository(source)
        repo.searchStations(" Delhi ")
        repo.searchStations("delhi")
        assertEquals(1, calls.get())
    }

    @Test
    fun cacheEvictsLeastRecentlyUsedEntry() {
        val cache = ExpiringCache<String, Int>(100, 2) { 0 }
        cache.put("a", 1)
        cache.put("b", 2)
        assertEquals(1, cache["a"])
        cache.put("c", 3)
        assertNull(cache["b"])
        assertEquals(1, cache["a"])
        assertEquals(3, cache["c"])
    }
}
