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

    // Carries the last-known foreground state across calls. Android only emits
    // an ACTIVITY_RESUMED/MOVE_TO_FOREGROUND event at the moment an app comes
    // forward - not repeatedly while it stays there - so a stateless query
    // limited to a short recent window would incorrectly report "unknown" as
    // soon as that one event ages out of the window, even though the app is
    // still genuinely in front. Remembering where we left off (and folding
    // that starting point into the query range) keeps a long-running session
    // correctly detected instead of silently reverting to idle.
    private var knownForegroundPackage: String? = null
    private var knownForegroundTs: Long = 0L

    /**
     * Returns the package name currently in the foreground, or null if it
     * can't be determined (e.g. screen off, or no events ever seen).
     * Looks back a short window (or further, if needed to cover the last
     * known state) and takes the most recent foreground/resume event.
     */
    fun currentForegroundPackage(lookbackMs: Long = 70_000L): String? {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null
        val end = System.currentTimeMillis()
        val recentWindowStart = end - lookbackMs
        val start = if (knownForegroundTs > 0) minOf(knownForegroundTs, recentWindowStart) else recentWindowStart
        val events: UsageEvents = usm.queryEvents(start, end)

        var lastForegroundPackage: String? = knownForegroundPackage
        var lastForegroundTs = knownForegroundTs
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
        knownForegroundPackage = lastForegroundPackage
        knownForegroundTs = lastForegroundTs
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
