package com.backlognudge.app.capture

import com.backlognudge.app.data.BacklogItem

/**
 * Turns a raw speech transcript into a BacklogItem. Fully local, no network
 * call: the transcript becomes the title as-is, with default time/energy/
 * category the user can adjust later - no auto-tagging.
 */
class TranscriptParser {

    fun parse(transcript: String): BacklogItem {
        val trimmed = transcript.trim()
        return BacklogItem(
            title = trimmed.ifBlank { "(empty transcript)" }.take(140),
            needsReview = true
        )
    }
}
