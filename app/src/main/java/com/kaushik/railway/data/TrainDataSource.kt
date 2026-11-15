package com.kaushik.railway.data

/** Transport boundary for cancellable train enquiries and repository tests. */
interface TrainDataSource {
    suspend fun searchTrains(from: String, to: String, date: String): List<Train>
    suspend fun getAvailability(trainNo: String, from: String, to: String, date: String, coach: String, quota: String): AvailabilityResult
    suspend fun checkPnr(pnr: String): PnrResult
    suspend fun trackTrain(trainNo: String, date: String): List<RunningStop>
    suspend fun searchStations(query: String): List<Station>
}
