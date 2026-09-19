package com.backlognudge.app.nudge

import android.content.Context
import com.backlognudge.app.BacklogNudgeApp
import com.backlognudge.app.data.BacklogItem
import com.backlognudge.app.data.NudgeEvent
import com.backlognudge.app.detection.WatchedApps

/**
 * Picks which backlog item to surface and generates the nudge's copy.
 * Fully local - no network call, no API key. Copy is a fixed template
 * grounded only in the item's own title/metadata, never raw usage logs.
 */
class NudgeManager(private val context: Context) {

    private val db by lazy { (context.applicationContext as BacklogNudgeApp).database }

    /** Returns true if a nudge was actually posted - false means the threshold fired but there was nothing pending to nudge about. */
    suspend fun maybeTriggerNudge(watchedPackage: String): Boolean {
        val candidates = db.backlogDao().eligibleForNudge()
        if (candidates.isEmpty()) return false

        val item = pickItem(candidates)
        val copy = templateCopy(item, WatchedApps.friendlyName(watchedPackage))

        val eventId = db.nudgeDao().insert(
            NudgeEvent(itemId = item.id, watchedPackage = watchedPackage)
        )
        db.backlogDao().recordNudged(item.id)

        NudgeNotifier.postNudge(context, item, eventId, watchedPackage, copy)
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

    private fun templateCopy(item: BacklogItem, appName: String): String =
        "You've been on $appName a while — this one's only about ${item.estimatedMinutes.label} and it's been waiting."
}
