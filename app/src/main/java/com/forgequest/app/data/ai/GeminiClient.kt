package com.forgequest.app.data.ai


import android.os.SystemClock
import androidx.annotation.StringRes
import com.forgequest.app.LocalizedException
import com.forgequest.app.R
import android.util.Base64
import android.util.Log
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
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Why an AI call failed, in words a user can act on. [detail] is the technical cause (HTTP status and body,
 * exception class, model) for the "Copy error details" action; it never contains the key.
 */
sealed class AiError(@StringRes res: Int, val detail: String = "", vararg args: Any) : LocalizedException(res, *args) {
    object MissingKey : AiError(R.string.ai_err_missing_key)
    class InvalidKey(detail: String = "") : AiError(R.string.ai_err_invalid_key, detail)
    class RateLimited(detail: String = "") : AiError(R.string.ai_err_rate_limited, detail)
    /** The phone couldn't reach Google at all (no DNS, no route, connect failed). */
    class Offline(detail: String = "") : AiError(R.string.ai_err_offline, detail)
    /** Connected, but the connection broke in a way that isn't a timeout (TLS, reset, protocol). */
    class Network(detail: String = "") : AiError(R.string.ai_err_network, detail)
    /** Connected, but Gemini sent nothing back in time. Never reported as offline. */
    class Timeout(detail: String = "") : AiError(R.string.ai_err_timeout, detail)
    class Blocked(detail: String = "") : AiError(R.string.ai_err_blocked, detail)
    class Busy(detail: String = "") : AiError(R.string.ai_err_busy, detail)
    class Server(val code: Int, detail: String = "") : AiError(R.string.ai_err_server, detail, code)
    class Unreadable(@StringRes what: Int) : AiError(what)

    /** The connection dropped after part of a streamed reply arrived; [text] is what came through. */
    class Partial(val text: String, detail: String = "") : AiError(R.string.ai_err_partial, detail)
}

data class GeminiModels(val fast: String, val smart: String, val lite: String)

/**
 * Direct calls to the Gemini REST API with the user's own key.
 * No mock results: every failure surfaces as an [AiError].
 */
class GeminiClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        // Quick calls (thinking off); thinking calls use [slowHttp].
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
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
                lite = pickNewest(models, "flash-lite") ?: FALLBACK_LITE,
            ).also { Log.i(TAG, "models for this key: ${models.filter { "gemini" in it }}; picked $it") }
        }.recoverCatching { e -> if (e is AiError) throw e else GeminiModels(FALLBACK_FAST, FALLBACK_FAST, FALLBACK_LITE) }
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
    ): String = attempt(apiKey, models) { model ->
        try {
            generateOnce(apiKey, model, system, turns, null, false, fast, stream = onDelta)
        } catch (e: AiError) {
            // A stalled model is better replaced by the next one than waited on again; quota and key problems
            // won't change either. Anything else (a 500, a broken stream) gets one plain call: nothing reached
            // the screen yet, so it loses nothing.
            if (e is AiError.Partial || e is AiError.InvalidKey || e is AiError.Offline || e is AiError.RateLimited || e is AiError.Timeout) throw e
            Log.w(TAG, "stream failed before any text on $model (${e.detail}); retrying without streaming")
            val text = generateOnce(apiKey, model, system, turns, null, false, fast, stream = null)
            onDelta(text)
            text
        }
    }

    /** Models whose free quota ran out, until when. Retrying them only costs a round trip per message. */
    private val coolingDown = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private suspend fun attempt(apiKey: String, models: List<String>, once: suspend (String) -> String): String {
        if (apiKey.isBlank()) throw AiError.MissingKey
        val all = (models + FALLBACK_FAST).filter { it.isNotBlank() }.distinct()
        val now = System.currentTimeMillis()
        val candidates = all.filter { (coolingDown[it] ?: 0) < now }.ifEmpty { all }
        var lastError: AiError = AiError.Blocked("no model answered")
        var timeouts = 0
        for (model in candidates) {
            var retried = false
            while (true) {
                try {
                    return once(model)
                } catch (e: AiError) {
                    if (e === AiError.MissingKey || e is AiError.InvalidKey || e is AiError.Offline || e is AiError.Blocked) throw e
                    if (e is AiError.Partial) throw e
                    lastError = e
                    if (e is AiError.RateLimited) coolingDown[model] = System.currentTimeMillis() + retryAfterMs(e.detail)
                    // Two stalled models in a row means Google (or the network) is the problem; stop making them wait.
                    if (e is AiError.Timeout && ++timeouts >= 2) throw e
                    if (e is AiError.Busy && !retried) {
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
        // Each model family takes a different switch for "no thinking": Flash wants thinkingBudget 0, Flash-Lite
        // rejects that and wants thinkingLevel "minimal". Try the likely one first, then the other, then none;
        // remember what worked so later calls go straight to it.
        val options = if (fast) {
            val remembered = offSwitch[model]
            listOfNotNull(remembered) + (if ("lite" in model) listOf(Thinking.MINIMAL, Thinking.BUDGET_0) else listOf(Thinking.BUDGET_0, Thinking.MINIMAL)) + Thinking.DEFAULT
        } else {
            listOf(Thinking.DEFAULT)
        }.distinct()
        var last: AiError? = null
        for (thinking in options) {
            try {
                return@withContext request(apiKey, model, system, turns, image, jsonOutput, thinking, stream).also {
                    if (fast) offSwitch[model] = thinking
                }
            } catch (e: AiError.Server) {
                if (e.code != 400) throw e
                last = e
            }
        }
        throw last ?: AiError.Blocked("$model: no thinking option accepted")
    }

    /** "Please retry in 12h7m14s" → milliseconds; a minute when Google doesn't say. */
    internal fun retryAfterMs(detail: String): Long {
        val m = Regex("""retry in (?:(\d+)h)?(?:(\d+)m)?(?:([\d.]+)s)?""").find(detail) ?: return 60_000
        val (h, min, s) = m.destructured
        val ms = ((h.toLongOrNull() ?: 0) * 3600 + (min.toLongOrNull() ?: 0) * 60) * 1000 + ((s.toDoubleOrNull() ?: 0.0) * 1000).toLong()
        return ms.coerceIn(10_000, 24 * 3600_000L)
    }

    private enum class Thinking { BUDGET_0, MINIMAL, DEFAULT }

    private val offSwitch = java.util.concurrent.ConcurrentHashMap<String, Thinking>()

    /** Opens the TLS connection ahead of the first message so it doesn't pay the handshake. Errors are ignored. */
    suspend fun warmUp(apiKey: String) = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext
        val started = SystemClock.elapsedRealtime()
        runCatching {
            http.newCall(Request.Builder().url("$base/models?pageSize=1").header("x-goog-api-key", apiKey).get().build()).execute().close()
            Log.i(TAG, "warm-up ${SystemClock.elapsedRealtime() - started} ms")
        }
    }

    private suspend fun request(
        apiKey: String,
        model: String,
        system: String,
        turns: List<Pair<String, String>>,
        image: ByteArray?,
        jsonOutput: Boolean,
        thinking: Thinking,
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
            val thinkingConfig = when (thinking) {
                Thinking.BUDGET_0 -> buildJsonObject { put("thinkingBudget", 0) }
                Thinking.MINIMAL -> buildJsonObject { put("thinkingLevel", "minimal") }
                Thinking.DEFAULT -> null
            }
            if (jsonOutput || thinkingConfig != null) {
                put("generationConfig", buildJsonObject {
                    if (jsonOutput) put("responseMimeType", "application/json")
                    if (thinkingConfig != null) put("thinkingConfig", thinkingConfig)
                })
            }
        }
        val body = payload.toString().toRequestBody("application/json".toMediaType())
        val label = "$model ${if (stream == null) "generate" else "stream"} ${thinking.name.lowercase()}"
        Log.i(TAG, "→ $label: system ${system.length} chars (~${system.length / 4} tokens), ${turns.size} turns${if (image != null) ", image ${image.size / 1024} KB" else ""}")
        // Google sends nothing, not even headers, until the first word. With thinking off that should take a second
        // or two, so a 20 s silence means the model is queued and the next one is a better bet. Thinking through a
        // plan can legitimately stay silent for a minute.
        val client = if (thinking == Thinking.DEFAULT) slowHttp else http
        if (stream == null) {
            val request = Request.Builder().url("$base/models/$model:generateContent").header("x-goog-api-key", apiKey).post(body).build()
            val started = SystemClock.elapsedRealtime()
            val raw = execute(request, label, client)
            logUsage(label, raw, SystemClock.elapsedRealtime() - started)
            return extractText(raw) ?: throw AiError.Blocked("$label: no text in reply: ${finishInfo(raw)}")
        }
        val request = Request.Builder().url("$base/models/$model:streamGenerateContent?alt=sse").header("x-goog-api-key", apiKey).post(body).build()
        return executeStreaming(request, label, client, stream)
    }

    private val slowHttp: OkHttpClient by lazy { http.newBuilder().readTimeout(90, TimeUnit.SECONDS).build() }

    /** Reads Gemini's server-sent events; each "data:" line is one chunk of the reply. */
    private suspend fun executeStreaming(
        request: Request,
        label: String,
        client: OkHttpClient,
        onDelta: suspend (String) -> Unit,
    ): String {
        val started = SystemClock.elapsedRealtime()
        fun ms() = SystemClock.elapsedRealtime() - started
        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw networkError(e, "$label after ${ms()} ms, before headers")
        }
        Log.i(TAG, "← $label: HTTP ${response.code} headers in ${ms()} ms (${response.protocol})")
        response.use { r ->
            if (!r.isSuccessful) throw classify(r.code, r.body?.string().orEmpty(), label)
            val source = r.body?.source() ?: throw AiError.Blocked("$label: empty body")
            val full = StringBuilder()
            var lastChunk = ""
            try {
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val chunk = line.removePrefix("data:").trim()
                    if (chunk.isEmpty() || chunk == "[DONE]") continue
                    lastChunk = chunk
                    val text = extractText(chunk, trim = false) ?: continue
                    if (full.isEmpty()) Log.i(TAG, "← $label: first text in ${ms()} ms")
                    full.append(text)
                    onDelta(text)
                }
            } catch (e: IOException) {
                val cause = networkError(e, "$label after ${ms()} ms, ${full.length} chars received")
                if (full.isEmpty()) throw cause
                throw AiError.Partial(full.toString(), cause.detail)
            }
            logUsage(label, lastChunk, ms())
            return full.toString().trim().ifEmpty { throw AiError.Blocked("$label: stream ended without text: ${finishInfo(lastChunk)}") }
        }
    }

    private fun execute(request: Request, label: String = request.url.encodedPath, client: OkHttpClient = http): String {
        val started = SystemClock.elapsedRealtime()
        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw networkError(e, "$label after ${SystemClock.elapsedRealtime() - started} ms")
        }
        response.use { r ->
            val text = try {
                r.body?.string().orEmpty()
            } catch (e: IOException) {
                throw networkError(e, "$label reading body after ${SystemClock.elapsedRealtime() - started} ms")
            }
            if (r.isSuccessful) return text
            throw classify(r.code, text, label)
        }
    }

    /** A read timeout means Google accepted the request but sent nothing back; only a failed connect is "offline". */
    private fun networkError(e: IOException, where: String): AiError {
        val detail = "$where: ${e.javaClass.simpleName}: ${e.message}"
        Log.w(TAG, detail)
        val message = e.message.orEmpty().lowercase()
        return when {
            e is UnknownHostException || e is ConnectException || e is NoRouteToHostException -> AiError.Offline(detail)
            e is SocketTimeoutException && "connect" in message -> AiError.Offline(detail)
            e is InterruptedIOException -> AiError.Timeout(detail)
            else -> AiError.Network(detail)
        }
    }

    private fun classify(code: Int, text: String, label: String): AiError {
        val detail = "$label: HTTP $code: ${text.take(600)}"
        Log.w(TAG, detail)
        val lower = text.lowercase()
        return when {
            "api_key_invalid" in lower || "api key not valid" in lower || "api key expired" in lower || code == 401 -> AiError.InvalidKey(detail)
            code == 429 -> AiError.RateLimited(detail)
            code == 503 -> AiError.Busy(detail)
            code == 504 -> AiError.Timeout(detail)
            else -> AiError.Server(code, detail)
        }
    }

    private fun logUsage(label: String, raw: String, ms: Long) {
        val usage = runCatching { json.parseToJsonElement(raw).jsonObject["usageMetadata"]?.jsonObject }.getOrNull()
        Log.i(TAG, "✓ $label done in $ms ms; usage ${usage ?: "n/a"}")
    }

    private fun finishInfo(raw: String): String = runCatching {
        val root = json.parseToJsonElement(raw).jsonObject
        val finish = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject?.get("finishReason")?.jsonPrimitive?.contentOrNull
        val block = root["promptFeedback"]?.jsonObject?.get("blockReason")?.jsonPrimitive?.contentOrNull
        "finishReason=$finish blockReason=$block"
    }.getOrDefault(raw.take(200))

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
        private const val TAG = "FQ-AI"
        const val FALLBACK_FAST = "gemini-flash-latest"
        const val FALLBACK_LITE = "gemini-flash-lite-latest"
    }
}
