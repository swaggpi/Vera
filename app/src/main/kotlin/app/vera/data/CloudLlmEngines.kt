package app.vera.data

import app.vera.core.llm.LlmEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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

/**
 * Raw-HTTP cloud engines.
 *
 * We call the REST endpoints with the app's existing OkHttp client rather than pulling in the
 * vendors' server-side JVM SDKs: Vera ships as an F-Droid-style APK with a ~30 MB budget (currently
 * 24 MB), and two server SDKs would blow that for two small JSON calls.
 *
 * Keys belong to the user and are read from [SecureKeyStore]; they are never logged.
 */
private val JSON_MEDIA = "application/json".toMediaType()
private val json = Json { ignoreUnknownKeys = true }

/** Anthropic Messages API — https://api.anthropic.com/v1/messages */
class AnthropicLlmEngine(
    private val client: OkHttpClient,
    private val apiKey: String,
    private val model: String
) : LlmEngine {

    override suspend fun isReady(): Boolean = apiKey.isNotBlank()

    override suspend fun generate(prompt: String, system: String?, maxTokens: Int): String =
        withContext(Dispatchers.IO) {
            val body = buildJsonObject {
                put("model", model)
                // These calls are deliberately short (a sentence or a few bullets), so a small cap
                // is correct here and keeps latency and cost down.
                put("max_tokens", maxTokens.coerceIn(64, 4096))
                if (!system.isNullOrBlank()) put("system", system)
                put("messages", buildJsonArray {
                    add(buildJsonObject {
                        put("role", "user")
                        put("content", prompt)
                    })
                })
            }.toString()

            val req = Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .header("content-type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .post(body.toRequestBody(JSON_MEDIA))
                .build()

            runCatching {
                client.newCall(req).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) return@use ""
                    parseAnthropic(raw)
                }
            }.getOrDefault("")
        }

    override fun stream(prompt: String, system: String?, maxTokens: Int): Flow<String> =
        flow { emit(generate(prompt, system, maxTokens)) }

    /** Concatenate every `text` block of the content array. */
    internal fun parseAnthropic(raw: String): String = runCatching {
        json.parseToJsonElement(raw).jsonObject["content"]!!.jsonArray
            .mapNotNull { el ->
                val o = el.jsonObject
                if (o["type"]?.jsonPrimitive?.content == "text") o["text"]?.jsonPrimitive?.content else null
            }
            .joinToString("\n")
            .trim()
    }.getOrDefault("")
}

/** OpenAI Chat Completions — https://api.openai.com/v1/chat/completions */
class OpenAiLlmEngine(
    private val client: OkHttpClient,
    private val apiKey: String,
    private val model: String
) : LlmEngine {

    override suspend fun isReady(): Boolean = apiKey.isNotBlank()

    override suspend fun generate(prompt: String, system: String?, maxTokens: Int): String =
        withContext(Dispatchers.IO) {
            val body = buildJsonObject {
                put("model", model)
                put("max_completion_tokens", maxTokens.coerceIn(64, 4096))
                put("messages", buildJsonArray {
                    if (!system.isNullOrBlank()) add(buildJsonObject {
                        put("role", "system"); put("content", system)
                    })
                    add(buildJsonObject { put("role", "user"); put("content", prompt) })
                })
            }.toString()

            val req = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .header("content-type", "application/json")
                .header("authorization", "Bearer $apiKey")
                .post(body.toRequestBody(JSON_MEDIA))
                .build()

            runCatching {
                client.newCall(req).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) return@use ""
                    parseOpenAi(raw)
                }
            }.getOrDefault("")
        }

    override fun stream(prompt: String, system: String?, maxTokens: Int): Flow<String> =
        flow { emit(generate(prompt, system, maxTokens)) }

    internal fun parseOpenAi(raw: String): String = runCatching {
        json.parseToJsonElement(raw).jsonObject["choices"]!!.jsonArray
            .firstOrNull()?.jsonObject?.get("message")?.jsonObject
            ?.get("content")?.jsonPrimitive?.content?.trim().orEmpty()
    }.getOrDefault("")
}
