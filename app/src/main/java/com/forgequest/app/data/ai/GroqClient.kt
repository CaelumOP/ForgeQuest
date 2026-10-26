package com.forgequest.app.data.ai

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
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
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Groq's free OpenAI-compatible API, used only for coach chat when the person adds a Groq key: Llama models on
 * Groq hardware answer in well under a second. Gemini keeps meal scans and plans, and stays on its native API
 * because that one gives us per-model thinking control and the model list we already rely on.
 */
class GroqClient(base: OkHttpClient = OkHttpClient()) {
    private val http: OkHttpClient = base.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        // Groq either answers in about a second or is in trouble; fall back to Gemini quickly.
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Smartest fast chat model this key can use: Llama 3.3 70B first, then Llama 4 Maverick, Llama 4 Scout, Qwen 32B, then 8B instant. */
    suspend fun pickModel(apiKey: String): String = withContext(Dispatchers.IO) {
        val ids = com.forgequest.app.data.suspendRunCatching {
            val body = execute(Request.Builder().url("$BASE/models").header("Authorization", "Bearer $apiKey").get().build())
            json.parseToJsonElement(body).jsonObject["data"]?.jsonArray.orEmpty().mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull }
        }.getOrElse { if (it is AiError.InvalidKey) throw it else emptyList() }
        pickFastest(ids).also { Log.i(TAG, "groq models: $ids; picked $it") }
    }

    internal fun pickFastest(ids: List<String>): String =
        ids.firstOrNull { "llama-3.3-70b-versatile" in it }
            ?: ids.firstOrNull { "llama-3.3-70b" in it }
            ?: ids.firstOrNull { "llama-4-maverick" in it }
            ?: ids.firstOrNull { "llama-4-scout" in it }
            ?: ids.firstOrNull { "qwen" in it && "32b" in it }
            ?: ids.firstOrNull { "llama-3.1-8b-instant" in it }
            ?: ids.firstOrNull { it.startsWith("llama") && "guard" !in it }
            ?: FALLBACK_MODEL

    /** Streams a chat reply; throws [AiError] (never [AiError.Partial] unless text already arrived). */
    suspend fun streamChat(
        apiKey: String,
        model: String,
        system: String,
        turns: List<Pair<String, String>>,
        jsonOutput: Boolean = false,
        onDelta: suspend (String) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("model", model)
            put("stream", true)
            if (jsonOutput) put("response_format", buildJsonObject { put("type", "json_object") })
            put("temperature", 0.5)
            put("max_tokens", 1500)
            put("messages", buildJsonArray {
                addJsonObject { put("role", "system"); put("content", system) }
                turns.forEach { (role, text) -> addJsonObject { put("role", if (role == "user") "user" else "assistant"); put("content", text) } }
            })
        }
        val request = Request.Builder().url("$BASE/chat/completions").header("Authorization", "Bearer $apiKey")
            .post(payload.toString().toRequestBody("application/json".toMediaType())).build()
        val started = SystemClock.elapsedRealtime()
        val response = try {
            http.newCall(request).execute()
        } catch (e: IOException) {
            throw networkError(e, "groq $model before headers")
        }
        response.use { r ->
            if (!r.isSuccessful) throw classify(r.code, r.body?.string().orEmpty(), model)
            val source = r.body?.source() ?: throw AiError.Blocked("groq: empty body")
            val full = StringBuilder()
            try {
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val chunk = line.removePrefix("data:").trim()
                    if (chunk.isEmpty() || chunk == "[DONE]") continue
                    val text = runCatching {
                        json.parseToJsonElement(chunk).jsonObject["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                            ?.get("delta")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
                    }.getOrNull()
                    if (text.isNullOrEmpty()) continue
                    if (full.isEmpty()) Log.i(TAG, "groq $model first text in ${SystemClock.elapsedRealtime() - started} ms")
                    full.append(text)
                    onDelta(text)
                }
            } catch (e: IOException) {
                val cause = networkError(e, "groq $model after ${full.length} chars")
                if (full.isEmpty()) throw cause
                throw AiError.Partial(full.toString(), cause.detail)
            }
            Log.i(TAG, "groq $model done in ${SystemClock.elapsedRealtime() - started} ms")
            full.toString().trim().ifEmpty { throw AiError.Blocked("groq $model: no text") }
        }
    }

    private fun execute(request: Request): String {
        val response = try {
            http.newCall(request).execute()
        } catch (e: IOException) {
            throw networkError(e, "groq ${request.url.encodedPath}")
        }
        response.use { r ->
            val text = r.body?.string().orEmpty()
            if (r.isSuccessful) return text
            throw classify(r.code, text, request.url.encodedPath)
        }
    }

    private fun networkError(e: IOException, where: String): AiError {
        val detail = "$where: ${e.javaClass.simpleName}: ${e.message}"
        Log.w(TAG, detail)
        return when {
            e is UnknownHostException || e is ConnectException -> AiError.Offline(detail)
            e is SocketTimeoutException && "connect" in e.message.orEmpty().lowercase() -> AiError.Offline(detail)
            e is InterruptedIOException -> AiError.Timeout(detail)
            else -> AiError.Network(detail)
        }
    }

    private fun classify(code: Int, text: String, label: String): AiError {
        val detail = "groq $label: HTTP $code: ${text.take(400)}"
        Log.w(TAG, detail)
        return when (code) {
            401, 403 -> AiError.InvalidKey(detail)
            429 -> AiError.RateLimited(detail)
            503 -> AiError.Busy(detail)
            504 -> AiError.Timeout(detail)
            else -> AiError.Server(code, detail)
        }
    }

    companion object {
        private const val TAG = "FQ-AI"
        private const val BASE = "https://api.groq.com/openai/v1"
        const val FALLBACK_MODEL = "llama-3.3-70b-versatile"
    }
}
