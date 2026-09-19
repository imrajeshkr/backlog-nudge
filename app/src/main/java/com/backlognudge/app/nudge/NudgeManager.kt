package com.backlognudge.app.nudge

import android.content.Context
import com.backlognudge.app.BacklogNudgeApp
import com.backlognudge.app.data.BacklogItem
import com.backlognudge.app.data.NudgeEvent
import com.backlognudge.app.detection.WatchedApps
import com.backlognudge.app.net.ClaudeApiClient
import com.backlognudge.app.prefs.SecurePrefs

/**
 * Picks which backlog item to surface and generates the nudge's copy.
 * Copy generation is grounded only in the item's own title/metadata -
 * never in raw usage logs - and falls back to a warm templated line if
 * Claude isn't reachable, so a missing API key or offline device never
 * blocks the nudge itself.
 */
class NudgeManager(private val context: Context) {

    private val db by lazy { (context.applicationContext as BacklogNudgeApp).database }
    private val securePrefs by lazy { SecurePrefs(context) }

    /** Returns true if a nudge was actually posted - false means the threshold fired but there was nothing pending to nudge about. */
    suspend fun maybeTriggerNudge(watchedPackage: String): Boolean {
        val candidates = db.backlogDao().eligibleForNudge()
        if (candidates.isEmpty()) return false

        val item = pickItem(candidates)
        val copy = generateCopy(item, watchedPackage)

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

    private suspend fun generateCopy(item: BacklogItem, watchedPackage: String): String {
        val appName = WatchedApps.friendlyName(watchedPackage)
        val apiKey = securePrefs.claudeApiKey
        if (apiKey.isNullOrBlank()) return templateCopy(item, appName)

        return try {
            val client = ClaudeApiClient(apiKey)
            val prompt = """
                Item title: "${item.title}"
                Estimated time: ${item.estimatedMinutes.label}
                Energy needed: ${item.energy.label}
                App the user has been on: $appName
            """.trimIndent()
            val response = client.complete(SYSTEM_PROMPT, prompt, maxTokens = 120)
            response.trim().removeSurrounding("\"").ifBlank { templateCopy(item, appName) }
        } catch (e: Exception) {
            templateCopy(item, appName)
        }
    }

    private fun templateCopy(item: BacklogItem, appName: String): String =
        "You've been on $appName a while — this one's only about ${item.estimatedMinutes.label} and it's been waiting."

    companion object {
        private val SYSTEM_PROMPT = """
            You write a single short, warm, conversational nudge notification body
            (max 2 short sentences, no hashtags, no emoji, no exclamation-point
            spam) that gently suggests the user switch from their current app to
            a specific backlog item. The item's title is already shown separately
            as the notification's title - don't repeat it verbatim in the body,
            write the "why now" framing instead. Ground the message only in the
            item details given - never invent details. Return ONLY the notification text.
        """.trimIndent()
    }
}
