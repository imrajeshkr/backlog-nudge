package com.backlognudge.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.backlognudge.app.data.AppDatabase

class BacklogNudgeApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.build(this) }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)

        val watcherChannel = NotificationChannel(
            CHANNEL_WATCHER,
            "Background monitoring",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Low-priority status notification while Backlog Nudge is watching for scroll sessions."
            setShowBadge(false)
        }

        val nudgeChannel = NotificationChannel(
            CHANNEL_NUDGE,
            "Backlog nudges",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Gentle nudges toward a backlog item when you've been scrolling for a while."
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setAllowBubbles(true)
            }
        }

        nm.createNotificationChannel(watcherChannel)
        nm.createNotificationChannel(nudgeChannel)
    }

    companion object {
        const val CHANNEL_WATCHER = "watcher_status"
        const val CHANNEL_NUDGE = "nudge"
    }
}
