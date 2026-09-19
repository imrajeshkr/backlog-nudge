package com.backlognudge.app.nudge

import android.content.Context
import com.backlognudge.app.BacklogNudgeApp
import com.backlognudge.app.data.BacklogItem
import com.backlognudge.app.data.NudgeEvent

/**
 * Picks which backlog item to surface and generates the nudge's copy.
 * Fully local - no network call, no API key. Copy is a fixed template
 * grounded only in the item's own title/metadata, never raw usage logs.
 */
class NudgeManager(private val context: Context) {

    private val db by lazy { (context.applicationContext as BacklogNudgeApp).database }

    /**
     * Returns true if a nudge was actually posted - false means the threshold
     * fired but there was nothing pending to nudge about.
     *
     * [sessionElapsedMs] is how long the current continuous session in the
     * watched app has run; the copy quotes it back, so it has to be the real
     * number rather than the configured threshold.
     */
    suspend fun maybeTriggerNudge(watchedPackage: String, sessionElapsedMs: Long): Boolean {
        val candidates = db.backlogDao().eligibleForNudge()
        if (candidates.isEmpty()) return false

        val item = pickItem(candidates)
        val sessionMinutes = minutesOf(sessionElapsedMs)
        val copy = templateCopy(item, sessionMinutes)

        val eventId = db.nudgeDao().insert(
            NudgeEvent(itemId = item.id, watchedPackage = watchedPackage)
        )
        db.backlogDao().recordNudged(item.id)

        NudgeNotifier.postNudge(context, item, eventId, watchedPackage, copy, sessionMinutes)
        return true
    }

    /** Fewer snoozes, longer since last nudged, and shorter items win - a quick win beats a big ask mid-scroll. */
    private fun pickItem(items: List<BacklogItem>): BacklogItem =
        items.sortedWith(
            compareBy(
                { it.snoozeCount },
                { it.lastNudgedAt ?: 0L },
                { it.estimatedMinutes.minutes }
            )
        ).first()

    /** Whole minutes, never zero - "scrolling 0 minutes" reads like a bug. */
    private fun minutesOf(elapsedMs: Long): Int =
        Math.round(elapsedMs / 60_000.0).toInt().coerceAtLeast(1)

    private fun templateCopy(item: BacklogItem, sessionMinutes: Int): String {
        val scrolled = if (sessionMinutes == 1) "1 minute" else "$sessionMinutes minutes"
        return "You've been scrolling $scrolled. This one takes ${item.estimatedMinutes.minutes}."
    }
}
