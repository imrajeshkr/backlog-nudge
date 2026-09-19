package com.backlognudge.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class NudgeResponse {
    DID_IT, SNOOZED, DISMISSED, REMOVED, PENDING
}

@Entity(tableName = "nudge_events")
data class NudgeEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val triggeredAt: Long = System.currentTimeMillis(),
    val watchedPackage: String,
    val response: NudgeResponse = NudgeResponse.PENDING
)
