package com.kaushik.railway.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookingDao {
    @Query("SELECT * FROM bookings WHERE ownerId = :ownerId AND ownerId != '' ORDER BY createdAt DESC")
    fun getAllBookings(ownerId: String): Flow<List<BookingEntity>>

    @Query("SELECT * FROM bookings WHERE ownerId = :ownerId AND ownerId != '' AND pnr = :pnr LIMIT 1")
    suspend fun getByPnr(ownerId: String, pnr: String): BookingEntity?

    // A duplicate PNR must never replace another account's record.
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(booking: BookingEntity)

    @Query("UPDATE bookings SET status = :status WHERE ownerId = :ownerId AND ownerId != '' AND pnr = :pnr")
    suspend fun updateStatus(ownerId: String, pnr: String, status: String)

    @Query("DELETE FROM bookings WHERE ownerId = :ownerId AND ownerId != '' AND pnr = :pnr")
    suspend fun delete(ownerId: String, pnr: String)
}
