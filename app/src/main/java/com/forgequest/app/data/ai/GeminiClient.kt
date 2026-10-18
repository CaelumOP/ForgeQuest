package com.forgequest.app.data.ai

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
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

/** Why an AI call failed, in words a user can act on. */
sealed class AiError(val userMessage: String) : Exception(userMessage) {
    object MissingKey : AiError("Add your free Gemini key in Profile to use this.")
    object InvalidKey : AiError("Your Gemini key was rejected. Check it in Profile → AI key.")
    object RateLimited : AiError("Gemini's free limit is used up for now. Try again in a minute.")
    object Offline : AiError("No internet connection. Check your connection and try again.")
    object Blocked : AiError("Gemini couldn't answer that one. Try rephrasing or another photo.")
    object Busy : AiError("Gemini is very busy right now. Try again in a moment.")
    class Server(val code: Int) : AiError("Gemini is having trouble right now (error $code). Try again shortly.")
    class Unreadable(what: String) : AiError(what)
}

data class GeminiModels(val fast: String, val smart: String)

/**
 * Direct calls to the Gemini REST API with the user's own key.
 * No mock results: every failure surfaces as an [AiError].
 */
class GeminiClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build(),
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val base = com.forgequest.app.BuildConfig.GEMINI_BASE

    /**
     * Asks Google which models this key can use and picks the newest Flash (fast) and Pro (smart).
     * Falls back to the "latest" alias if the list can't be read. Free keys often have no Pro quota;
     * [generate] then falls through to Flash on its own.
     */
    suspend fun discoverModels(apiKey: String): Result<GeminiModels> = withContext(Dispatchers.IO) {
        runCatching {
            val body = execute(Request.Builder().url("$base/models?pageSize=200").header("x-goog-api-key", apiKey).get().build())
            val models = json.parseToJsonElement(body).jsonObject["models"]?.jsonArray.orEmpty().mapNotNull { m ->
                val o = m.jsonObject
                val name = o["name"]?.jsonPrimitive?.contentOrNull?.removePrefix("models/") ?: return@mapNotNull null
                val methods = o["supportedGenerationMethods"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
                if ("generateContent" in methods) name else null
            }
            GeminiModels(
                fast = pickNewest(models, "flash") ?: FALLBACK_FAST,
                smart = pickNewest(models, "pro") ?: pickNewest(models, "flash") ?: FALLBACK_FAST,
            )
        }.recoverCatching { e -> if (e is AiError) throw e else GeminiModels(FALLBACK_FAST, FALLBACK_FAST) }
    }

    /** Newest "gemini-X.Y-[family]" model; a stable release only wins over a preview of the same version. */
    internal fun pickNewest(models: List<String>, family: String): String? {
        val pattern = Regex("""^gemini-(\d+(?:\.\d+)?)-$family(-preview(?:-[\w-]+)?)?$""")
        return models.mapNotNull { name ->
            val m = pattern.matchEntire(name) ?: return@mapNotNull null
            Triple(name, m.groupValues[1].toDoubleOrNull() ?: 0.0, m.groupValues[2].isEmpty())
        }.sortedWith(compareByDescending<Triple<String, Double, Boolean>> { it.second }.thenByDescending { it.third })
            .firstOrNull()?.first
    }

    /**
     * Tries [models] in order. A busy model gets one retry; quota limits, retired models and server errors
     * move on to the next model. A bad key or no connection stops right away.
     */
    suspend fun generate(
        apiKey: String,
        models: List<String>,
        system: String,
        turns: List<Pair<String, String>>,
        image: ByteArray? = null,
        jsonOutput: Boolean = false,
    ): String {
        if (apiKey.isBlank()) throw AiError.MissingKey
        val candidates = (models + FALLBACK_FAST).filter { it.isNotBlank() }.distinct()
        var lastError: AiError = AiError.Blocked
        for (model in candidates) {
            var retried = false
            while (true) {
                try {
                    return generateOnce(apiKey, model, system, turns, image, jsonOutput)
                } catch (e: AiError) {
                    if (e === AiError.MissingKey || e === AiError.InvalidKey || e === AiError.Offline || e === AiError.Blocked) throw e
                    lastError = e
                    if (e === AiError.Busy && !retried) {
                        retried = true
                        delay(1500)
                        continue
                    }
                    break
                }
            }
        }
        throw lastError
    }

    private suspend fun generateOnce(
        apiKey: String,
        model: String,
        system: String,
        turns: List<Pair<String, String>>,
        image: ByteArray?,
        jsonOutput: Boolean,
    ): String = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("systemInstruction", buildJsonObject {
                put("parts", buildJsonArray { add(buildJsonObject { put("text", system) }) })
            })
            put("contents", buildJsonArray {
                turns.forEachIndexed { index, (role, text) ->
                    add(buildJsonObject {
                        put("role", if (role == "user") "user" else "model")
                        put("parts", buildJsonArray {
                            add(buildJsonObject { put("text", text) })
                            if (image != null && index == turns.lastIndex) {
                                add(buildJsonObject {
                                    put("inlineData", buildJsonObject {
                                        put("mimeType", "image/jpeg")
                                        put("data", Base64.encodeToString(image, Base64.NO_WRAP))
                                    })
                                })
                            }
                        })
                    })
                }
            })
            if (jsonOutput) put("generationConfig", buildJsonObject { put("responseMimeType", "application/json") })
        }
        val request = Request.Builder()
            .url("$base/models/$model:generateContent")
            .header("x-goog-api-key", apiKey)
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val body = execute(request)
        extractText(body) ?: throw AiError.Blocked
    }

    private fun execute(request: Request): String {
        val response = try {
            http.newCall(request).execute()
        } catch (e: IOException) {
            throw AiError.Offline
        }
        response.use { r ->
            val text = r.body?.string().orEmpty()
            if (r.isSuccessful) return text
            val lower = text.lowercase()
            throw when {
                "api_key_invalid" in lower || "api key not valid" in lower || "api key expired" in lower || r.code == 401 -> AiError.InvalidKey
                r.code == 429 -> AiError.RateLimited
                r.code == 503 -> AiError.Busy
                else -> AiError.Server(r.code)
            }
        }
    }

    private fun extractText(body: String): String? {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val parts = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("content")?.jsonObject?.get("parts") as? JsonArray ?: return null
        return parts.mapNotNull { p ->
            val o = p as? JsonObject ?: return@mapNotNull null
            if (o["thought"]?.jsonPrimitive?.booleanOrNull == true) null else o["text"]?.jsonPrimitive?.contentOrNull
        }.joinToString("").trim().ifEmpty { null }
    }

    companion object {
        const val FALLBACK_FAST = "gemini-flash-latest"
    }
}
