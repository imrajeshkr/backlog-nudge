package com.backlognudge.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class NudgeResponse {
    DID_IT, SNOOZED, DISMISSED, REMOVED, PENDING,
    /** User backed out of the watched app but didn't mark the item done or snoozed - a step short of DID_IT. */
    LEFT_APP
}

@Entity(tableName = "nudge_events")
data class NudgeEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val triggeredAt: Long = System.currentTimeMillis(),
    val watchedPackage: String,
    val response: NudgeResponse = NudgeResponse.PENDING
)
