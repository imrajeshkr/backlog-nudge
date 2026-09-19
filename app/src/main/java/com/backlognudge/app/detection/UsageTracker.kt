package com.backlognudge.app.detection

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process

/**
 * Thin wrapper around UsageStatsManager. Deliberately does NOT use
 * AccessibilityService: this only asks "which package is currently in the
 * foreground", never screen content, and is the Play-Store-safe mechanism
 * for this kind of timing signal.
 */
class UsageTracker(private val context: Context) {

    /**
     * Returns the package name currently in the foreground, or null if it
     * can't be determined (e.g. screen off, or no recent events).
     * Looks back a short window and takes the most recent foreground/resume event.
     */
    fun currentForegroundPackage(lookbackMs: Long = 70_000L): String? {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null
        val end = System.currentTimeMillis()
        val start = end - lookbackMs
        val events: UsageEvents = usm.queryEvents(start, end)

        var lastForegroundPackage: String? = null
        var lastForegroundTs = 0L
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val isForegroundEvent = event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
            val isBackgroundEvent = event.eventType == UsageEvents.Event.ACTIVITY_PAUSED ||
                event.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND

            if (isForegroundEvent && event.timeStamp >= lastForegroundTs) {
                lastForegroundPackage = event.packageName
                lastForegroundTs = event.timeStamp
            } else if (isBackgroundEvent && event.packageName == lastForegroundPackage && event.timeStamp > lastForegroundTs) {
                // The last-known foreground app went to background more recently
                // than it came forward, and nothing else came forward since -> unknown.
                lastForegroundPackage = null
                lastForegroundTs = event.timeStamp
            }
        }
        return lastForegroundPackage
    }

    /** Whether the app has been granted PACKAGE_USAGE_STATS via Settings. */
    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
