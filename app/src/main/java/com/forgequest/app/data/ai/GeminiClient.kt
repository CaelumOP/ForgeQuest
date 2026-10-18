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

    /** The connection dropped after part of a streamed reply arrived; [text] is what came through. */
    class Partial(val text: String) : AiError("The connection dropped before the coach finished. Ask again to get the rest.")
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
     * [fast] turns the model's "thinking" off, which makes chat and photo scans answer in a few seconds.
     */
    suspend fun generate(
        apiKey: String,
        models: List<String>,
        system: String,
        turns: List<Pair<String, String>>,
        image: ByteArray? = null,
        jsonOutput: Boolean = false,
        fast: Boolean = false,
    ): String = attempt(apiKey, models) { model -> generateOnce(apiKey, model, system, turns, image, jsonOutput, fast, stream = null) }

    /**
     * Same as [generate] but hands text to [onDelta] as it arrives, so the screen fills in while the model
     * is still writing. Returns the complete reply. If a model fails before producing anything, the next one is tried.
     */
    suspend fun generateStreaming(
        apiKey: String,
        models: List<String>,
        system: String,
        turns: List<Pair<String, String>>,
        fast: Boolean = false,
        onDelta: suspend (String) -> Unit,
    ): String = attempt(apiKey, models) { model -> generateOnce(apiKey, model, system, turns, null, false, fast, stream = onDelta) }

    private suspend fun attempt(apiKey: String, models: List<String>, once: suspend (String) -> String): String {
        if (apiKey.isBlank()) throw AiError.MissingKey
        val candidates = (models + FALLBACK_FAST).filter { it.isNotBlank() }.distinct()
        var lastError: AiError = AiError.Blocked
        for (model in candidates) {
            var retried = false
            while (true) {
                try {
                    return once(model)
                } catch (e: AiError) {
                    if (e === AiError.MissingKey || e === AiError.InvalidKey || e === AiError.Offline || e === AiError.Blocked) throw e
                    if (e is AiError.Partial) throw e
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
        fast: Boolean,
        stream: (suspend (String) -> Unit)?,
    ): String = withContext(Dispatchers.IO) {
        try {
            request(apiKey, model, system, turns, image, jsonOutput, fast, stream)
        } catch (e: AiError.Server) {
            // Older models reject thinkingConfig with a 400; the request is still fine without it.
            if (fast && e.code == 400) request(apiKey, model, system, turns, image, jsonOutput, fast = false, stream = stream) else throw e
        }
    }

    private suspend fun request(
        apiKey: String,
        model: String,
        system: String,
        turns: List<Pair<String, String>>,
        image: ByteArray?,
        jsonOutput: Boolean,
        fast: Boolean,
        stream: (suspend (String) -> Unit)?,
    ): String {
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
            if (jsonOutput || fast) {
                put("generationConfig", buildJsonObject {
                    if (jsonOutput) put("responseMimeType", "application/json")
                    if (fast) put("thinkingConfig", buildJsonObject { put("thinkingBudget", 0) })
                })
            }
        }
        val body = payload.toString().toRequestBody("application/json".toMediaType())
        if (stream == null) {
            val request = Request.Builder().url("$base/models/$model:generateContent").header("x-goog-api-key", apiKey).post(body).build()
            return extractText(execute(request)) ?: throw AiError.Blocked
        }
        val request = Request.Builder().url("$base/models/$model:streamGenerateContent?alt=sse").header("x-goog-api-key", apiKey).post(body).build()
        return executeStreaming(request, stream)
    }

    /** Reads Gemini's server-sent events; each "data:" line is one chunk of the reply. */
    private suspend fun executeStreaming(request: Request, onDelta: suspend (String) -> Unit): String {
        val response = try {
            http.newCall(request).execute()
        } catch (e: IOException) {
            throw AiError.Offline
        }
        response.use { r ->
            if (!r.isSuccessful) throw classify(r.code, r.body?.string().orEmpty())
            val source = r.body?.source() ?: throw AiError.Blocked
            val full = StringBuilder()
            try {
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val chunk = line.removePrefix("data:").trim()
                    if (chunk.isEmpty() || chunk == "[DONE]") continue
                    val text = extractText(chunk, trim = false) ?: continue
                    full.append(text)
                    onDelta(text)
                }
            } catch (e: IOException) {
                if (full.isEmpty()) throw AiError.Offline
                throw AiError.Partial(full.toString())
            }
            return full.toString().trim().ifEmpty { throw AiError.Blocked }
        }
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
            throw classify(r.code, text)
        }
    }

    private fun classify(code: Int, text: String): AiError {
        val lower = text.lowercase()
        return when {
            "api_key_invalid" in lower || "api key not valid" in lower || "api key expired" in lower || code == 401 -> AiError.InvalidKey
            code == 429 -> AiError.RateLimited
            code == 503 -> AiError.Busy
            else -> AiError.Server(code)
        }
    }

    private fun extractText(body: String, trim: Boolean = true): String? {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val parts = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("content")?.jsonObject?.get("parts") as? JsonArray ?: return null
        val text = parts.mapNotNull { p ->
            val o = p as? JsonObject ?: return@mapNotNull null
            if (o["thought"]?.jsonPrimitive?.booleanOrNull == true) null else o["text"]?.jsonPrimitive?.contentOrNull
        }.joinToString("")
        return (if (trim) text.trim() else text).ifEmpty { null }
    }

    companion object {
        const val FALLBACK_FAST = "gemini-flash-latest"
    }
}
