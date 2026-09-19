package com.backlognudge.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NudgeDao {
    @Insert
    suspend fun insert(event: NudgeEvent): Long

    @Update
    suspend fun update(event: NudgeEvent)

    @Query("SELECT * FROM nudge_events WHERE id = :id")
    suspend fun getById(id: Long): NudgeEvent?

    @Query("SELECT * FROM nudge_events ORDER BY triggeredAt DESC LIMIT 50")
    fun observeRecent(): Flow<List<NudgeEvent>>

    @Query("SELECT MAX(triggeredAt) FROM nudge_events WHERE watchedPackage = :pkg")
    suspend fun lastNudgeTimeFor(pkg: String): Long?
}
