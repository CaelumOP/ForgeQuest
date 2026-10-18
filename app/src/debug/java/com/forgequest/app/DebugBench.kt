package com.forgequest.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.launch
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
import java.util.concurrent.TimeUnit

/**
 * Debug builds only: times Gemini variants with the key stored in the app, so coach latency can be measured
 * without the key ever leaving the device. `adb shell am broadcast -n com.forgequest.app/.DebugBench`
 * (optional `--es models "a,b"`), then read `adb logcat -s FQ-BENCH`.
 */
class DebugBench : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.container
        val pending = goAsync()
        app.appScope.launch {
            try {
                run(app, intent.getStringExtra("models"), intent.getStringExtra("prompt") ?: "How am I doing this week?", intent.getStringExtra("variants"), intent.getIntExtra("runs", 1))
            } catch (e: Throwable) {
                Log.e(TAG, "bench failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun run(app: AppContainer, modelsArg: String?, prompt: String, variantsArg: String?, runs: Int) {
        val key = app.settings.apiKey.value
        if (key.isBlank()) { Log.w(TAG, "no key"); return }
        val base = BuildConfig.GEMINI_BASE
        val http = OkHttpClient.Builder().readTimeout(120, TimeUnit.SECONDS).build()
        val json = Json { ignoreUnknownKeys = true }
        val list = http.newCall(Request.Builder().url("$base/models?pageSize=200").header("x-goog-api-key", key).build()).execute().use { it.body!!.string() }
        val names = json.parseToJsonElement(list).jsonObject["models"]!!.jsonArray.map { it.jsonObject["name"]!!.jsonPrimitive.content.removePrefix("models/") }
        Log.i(TAG, "models: ${names.filter { "flash" in it || "pro" in it }}")
        val system = app.coach.systemPromptForBench(prompt)
        val models = modelsArg?.split(",") ?: listOf(app.settings.current().fastModel)

        fun body(sys: String, thinking: JsonObject?) = buildJsonObject {
            put("systemInstruction", buildJsonObject { put("parts", buildJsonArray { add(buildJsonObject { put("text", sys) }) }) })
            put("contents", buildJsonArray {
                add(buildJsonObject { put("role", "user"); put("parts", buildJsonArray { add(buildJsonObject { put("text", prompt) }) }) })
            })
            if (thinking != null) put("generationConfig", buildJsonObject { put("thinkingConfig", thinking) })
        }.toString()

        val configs = mapOf(
            "budget0" to buildJsonObject { put("thinkingBudget", 0) },
            "minimal" to buildJsonObject { put("thinkingLevel", "minimal") },
            "low" to buildJsonObject { put("thinkingLevel", "low") },
            "low+thoughts" to buildJsonObject { put("thinkingLevel", "low"); put("includeThoughts", true) },
            "default+thoughts" to buildJsonObject { put("includeThoughts", true) },
            "default" to null,
        )
        val wanted = (variantsArg ?: "budget0,minimal,low,default").split(",")
        repeat(runs) { run ->
        for (model in models) {
            val variants = wanted.map { w -> "run$run stream $w" to (true to body(system, configs[w])) }
            for ((name, v) in variants) {
                val (stream, payload) = v
                val url = if (stream) "$base/models/$model:streamGenerateContent?alt=sse" else "$base/models/$model:generateContent"
                val t0 = SystemClock.elapsedRealtime()
                runCatching {
                    http.newCall(Request.Builder().url(url).header("x-goog-api-key", key).post(payload.toRequestBody("application/json".toMediaType())).build()).execute().use { r ->
                        val headers = SystemClock.elapsedRealtime() - t0
                        val source = r.body!!.source()
                        val all = StringBuilder()
                        var firstThought = -1L
                        var firstText = -1L
                        while (true) {
                            val line = source.readUtf8Line() ?: break
                            all.append(line).append('\n')
                            if (!line.startsWith("data:")) continue
                            val now = SystemClock.elapsedRealtime() - t0
                            if ("\"thought\": true" in line || "\"thought\":true" in line) { if (firstThought < 0) firstThought = now }
                            else if ("\"text\"" in line && firstText < 0) firstText = now
                        }
                        val total = SystemClock.elapsedRealtime() - t0
                        val text = all.toString()
                        val usage = Regex("\"usageMetadata\"\\s*:\\s*\\{[^}]*\\}").findAll(text).lastOrNull()?.value?.replace(Regex("\\s+"), " ")
                        Log.i(TAG, "$model | $name | HTTP ${r.code} | headers $headers | thought $firstThought | text $firstText | total $total ms | ${usage ?: text.take(300)}")
                    }
                }.onFailure { Log.w(TAG, "$model | $name | ${it.javaClass.simpleName}: ${it.message} after ${SystemClock.elapsedRealtime() - t0} ms") }
            }
        }
        }
        Log.i(TAG, "bench done")
    }

    private companion object { const val TAG = "FQ-BENCH" }
}
