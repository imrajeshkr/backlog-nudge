package com.backlognudge.app.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Minimal client for the Claude Messages API. Used for two short, cheap
 * calls: (1) parsing a voice transcript into structured backlog items, and
 * (2) generating a short conversational nudge line. No SDK dependency to
 * keep the app lightweight - just a direct HTTPS POST.
 */
class ClaudeApiClient(private val apiKey: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /** Returns the raw text content of Claude's reply, or throws on any failure. */
    suspend fun complete(systemPrompt: String, userMessage: String, maxTokens: Int = 512): String =
        withContext(Dispatchers.IO) {
            val bodyJson = buildJsonRequest(systemPrompt, userMessage, maxTokens)
            val request = Request.Builder()
                .url(API_URL)
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("content-type", "application/json")
                .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException("Claude API error ${response.code}: ${raw.take(300)}")
                }
                extractText(raw)
            }
        }

    private fun buildJsonRequest(systemPrompt: String, userMessage: String, maxTokens: Int): String {
        val body = buildJsonObject {
            put("model", MODEL)
            put("max_tokens", maxTokens)
            put("system", systemPrompt)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", userMessage)
                })
            })
        }
        return json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), body)
    }

    private fun extractText(raw: String): String {
        val obj = json.parseToJsonElement(raw).jsonObject
        val content = obj["content"]?.jsonArray ?: JsonArray(emptyList())
        val builder = StringBuilder()
        for (block in content) {
            val blockObj = block.jsonObject
            val type = blockObj["type"]?.jsonPrimitive?.content
            if (type == "text") {
                builder.append(blockObj["text"]?.jsonPrimitive?.content.orEmpty())
            }
        }
        return builder.toString()
    }

    companion object {
        private const val API_URL = "https://api.anthropic.com/v1/messages"
        private const val MODEL = "claude-3-5-haiku-20241022"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
