package com.backlognudge.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TimeEstimate(val minutes: Int, val label: String) {
    MIN_5(5, "5 min"),
    MIN_15(15, "15 min"),
    MIN_30(30, "30 min"),
    MIN_60(60, "1 hr"),
    MIN_120(120, "2 hr+")
}

enum class EnergyLevel(val label: String) {
    LOW("Low energy"),
    MEDIUM("Medium energy"),
    HIGH("High energy")
}

enum class ItemCategory(val label: String) {
    CHORE("Chore"),
    LEARNING("Learning"),
    PROJECT("Project"),
    SOCIAL("Social"),
    ADMIN("Admin"),
    OTHER("Other")
}

enum class ItemStatus {
    OPEN, DONE, DROPPED
}

@Entity(tableName = "backlog_items")
data class BacklogItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String = "",
    val estimatedMinutes: TimeEstimate = TimeEstimate.MIN_15,
    val energy: EnergyLevel = EnergyLevel.MEDIUM,
    val category: ItemCategory = ItemCategory.OTHER,
    val status: ItemStatus = ItemStatus.OPEN,
    val needsReview: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastNudgedAt: Long? = null,
    val snoozeCount: Int = 0,
    val dismissCount: Int = 0,
    val snoozedUntil: Long? = null
)
