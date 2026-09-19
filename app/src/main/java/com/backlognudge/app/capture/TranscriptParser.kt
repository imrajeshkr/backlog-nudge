package com.backlognudge.app.capture

import com.backlognudge.app.data.BacklogItem
import com.backlognudge.app.data.EnergyLevel
import com.backlognudge.app.data.ItemCategory
import com.backlognudge.app.data.TimeEstimate
import com.backlognudge.app.net.ClaudeApiClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

sealed class ParseResult {
    data class Parsed(val items: List<BacklogItem>) : ParseResult()
    data class Fallback(val item: BacklogItem, val reason: String) : ParseResult()
}

/**
 * Turns a raw speech transcript into one or more structured BacklogItems via
 * a single well-crafted Claude prompt. On any failure (no network, bad key,
 * unparseable response) it NEVER loses the capture: it falls back to saving
 * the raw transcript as an unparsed item the user can edit later.
 */
class TranscriptParser(private val apiKeyProvider: () -> String?) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun parse(transcript: String): ParseResult {
        val trimmed = transcript.trim()
        if (trimmed.isBlank()) {
            return ParseResult.Fallback(
                rawItem("(empty transcript)"),
                "Didn't catch anything - edit this item to add a title."
            )
        }

        val apiKey = apiKeyProvider()
        if (apiKey.isNullOrBlank()) {
            return ParseResult.Fallback(
                rawItem(trimmed),
                "No Claude API key set yet - saved your words as-is. Add a key in Settings to get automatic parsing."
            )
        }

        return try {
            val client = ClaudeApiClient(apiKey)
            val response = client.complete(SYSTEM_PROMPT, trimmed, maxTokens = 700)
            val items = parseItemsFromResponse(response, trimmed)
            if (items.isEmpty()) {
                ParseResult.Fallback(rawItem(trimmed), "Couldn't structure that - saved your words as-is.")
            } else {
                ParseResult.Parsed(items)
            }
        } catch (e: Exception) {
            ParseResult.Fallback(
                rawItem(trimmed),
                "Couldn't reach Claude (${e.message?.take(60) ?: "unknown error"}) - saved your words as-is."
            )
        }
    }

    private fun rawItem(text: String) = BacklogItem(
        title = text.take(140),
        needsReview = true
    )

    private fun parseItemsFromResponse(response: String, fallbackText: String): List<BacklogItem> {
        val jsonText = extractJsonArray(response) ?: return emptyList()
        val array: JsonArray = json.parseToJsonElement(jsonText).jsonArray
        return array.mapNotNull { element ->
            runCatching {
                val obj = element.jsonObject
                val title = obj["title"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                    ?: return@runCatching null
                val minutes = obj["estimated_minutes"]?.jsonPrimitive?.content?.toIntOrNull() ?: 15
                val energyStr = obj["energy"]?.jsonPrimitive?.content?.uppercase() ?: "MEDIUM"
                val categoryStr = obj["category"]?.jsonPrimitive?.content?.uppercase() ?: "OTHER"
                BacklogItem(
                    title = title,
                    estimatedMinutes = nearestEstimate(minutes),
                    energy = runCatching { EnergyLevel.valueOf(energyStr) }.getOrDefault(EnergyLevel.MEDIUM),
                    category = runCatching { ItemCategory.valueOf(categoryStr) }.getOrDefault(ItemCategory.OTHER)
                )
            }.getOrNull()
        }
    }

    private fun nearestEstimate(minutes: Int): TimeEstimate =
        TimeEstimate.values().minByOrNull { kotlin.math.abs(it.minutes - minutes) } ?: TimeEstimate.MIN_15

    /** Claude may wrap the JSON in prose or a code fence; pull out the array. */
    private fun extractJsonArray(text: String): String? {
        val start = text.indexOf('[')
        val end = text.lastIndexOf(']')
        if (start == -1 || end == -1 || end < start) return null
        return text.substring(start, end + 1)
    }

    companion object {
        private val SYSTEM_PROMPT = """
            You turn a short spoken voice-note into a JSON array of backlog items.
            The user is dictating one or more things they want to do later.

            Return ONLY a JSON array, no prose, no code fences. Each element:
            {
              "title": short imperative title, <= 60 chars,
              "estimated_minutes": one of 5, 15, 30, 60, 120,
              "energy": one of "LOW", "MEDIUM", "HIGH",
              "category": one of "CHORE", "LEARNING", "PROJECT", "SOCIAL", "ADMIN", "OTHER"
            }

            If the transcript clearly names multiple distinct tasks, return multiple
            items. If time/energy is ambiguous, make a reasonable guess rather than
            asking a question - the user can edit it later. If the transcript is
            nonsense or empty, return [].
        """.trimIndent()
    }
}
