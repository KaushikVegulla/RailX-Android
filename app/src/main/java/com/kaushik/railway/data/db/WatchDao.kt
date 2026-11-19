package com.kaushik.railway.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchDao {
    @Query("SELECT * FROM train_watches WHERE ownerId = :ownerId AND ownerId != '' ORDER BY createdAt DESC")
    fun getAll(ownerId: String): Flow<List<WatchEntity>>

    @Query("SELECT * FROM train_watches ORDER BY createdAt DESC")
    suspend fun getAllOnce(): List<WatchEntity>

    @Insert
    suspend fun insert(watch: WatchEntity): Long

    @Query("DELETE FROM train_watches WHERE ownerId = :ownerId AND ownerId != '' AND id = :id")
    suspend fun delete(ownerId: String, id: Long)

    @Query("UPDATE train_watches SET lastStatus = :status, lastCheckedAt = :at WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, at: Long)
}
