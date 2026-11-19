package com.kaushik.railway.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One Train Watch: a train, journey date and class whose availability
 * status RailX re-checks in the background, notifying the traveller
 * when the status improves (waitlist moving toward confirmed).
 */
@Entity(tableName = "train_watches", indices = [Index(value = ["ownerId", "createdAt"])])
data class WatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ownerId: String,
    val trainNumber: String,
    val trainName: String,
    val fromCode: String,
    val toCode: String,
    /** Journey date in the app's display format, e.g. "20 Nov 2026". */
    val date: String,
    val classCode: String,
    /** Last availability status seen, e.g. "WL 14"; empty until first check. */
    val lastStatus: String,
    val lastCheckedAt: Long,
    val createdAt: Long
)
