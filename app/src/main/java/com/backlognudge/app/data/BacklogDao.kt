package com.backlognudge.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BacklogDao {
    @Query("SELECT * FROM backlog_items WHERE status = 'OPEN' ORDER BY createdAt DESC")
    fun observeOpen(): Flow<List<BacklogItem>>

    @Query("SELECT * FROM backlog_items ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<BacklogItem>>

    @Query("SELECT * FROM backlog_items WHERE status = 'OPEN' AND (snoozedUntil IS NULL OR snoozedUntil < :now)")
    suspend fun eligibleForNudge(now: Long = System.currentTimeMillis()): List<BacklogItem>

    @Query("SELECT * FROM backlog_items WHERE id = :id")
    suspend fun getById(id: Long): BacklogItem?

    @Insert
    suspend fun insert(item: BacklogItem): Long

    @Insert
    suspend fun insertAll(items: List<BacklogItem>): List<Long>

    @Update
    suspend fun update(item: BacklogItem)

    @Query("UPDATE backlog_items SET status = 'DONE' WHERE id = :id")
    suspend fun markDone(id: Long)

    @Query("UPDATE backlog_items SET status = 'DROPPED' WHERE id = :id")
    suspend fun markDropped(id: Long)

    @Query("UPDATE backlog_items SET snoozeCount = snoozeCount + 1, snoozedUntil = :until, lastNudgedAt = :now WHERE id = :id")
    suspend fun snooze(id: Long, until: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE backlog_items SET dismissCount = dismissCount + 1, lastNudgedAt = :now WHERE id = :id")
    suspend fun recordDismiss(id: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE backlog_items SET lastNudgedAt = :now WHERE id = :id")
    suspend fun recordNudged(id: Long, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM backlog_items WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM backlog_items WHERE status = 'OPEN'")
    fun observeOpenCount(): Flow<Int>
}
