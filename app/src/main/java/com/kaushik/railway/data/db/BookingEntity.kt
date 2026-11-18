package com.kaushik.railway.data.db

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "bookings", indices = [Index(value = ["ownerId", "createdAt"])])
data class BookingEntity(
    @PrimaryKey val pnr: String,
    val trainNumber: String,
    val trainName: String,
    val fromCode: String,
    val toCode: String,
    val fromName: String,
    val toName: String,
    val travelClassCode: String,
    val travelClassName: String,
    val fare: Int,
    val date: String,
    val quota: String,
    val passengersJson: String,   // simple JSON array of passengers
    val contact: String,
    val status: String,
    val amount: Int,
    val paymentId: String = "",
    val orderId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    // Empty identifies pre-migration records whose owner cannot be safely inferred.
    @ColumnInfo(defaultValue = "''") val ownerId: String = "",
    @ColumnInfo(defaultValue = "''") val departTime: String = "",
    @ColumnInfo(defaultValue = "''") val classStatus: String = "",
    @ColumnInfo(defaultValue = "0") val cancelCharge: Int = 0,
    @ColumnInfo(defaultValue = "0") val refundAmount: Int = 0
)
