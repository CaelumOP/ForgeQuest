package com.forgequest.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.forgequest.app.data.health.DayReadiness
import com.forgequest.app.data.health.DaySummary
import com.forgequest.app.domain.Baseline
import com.forgequest.app.domain.Night
import com.forgequest.app.domain.Readiness
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
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.exp
import kotlin.math.ln

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
                // `--es send "…"` posts a coach message as if typed (adb can't type Arabic).
                intent.getStringExtra("send")?.let { app.coach.send(it); return@launch }
                // `--ez reset true` wipes data and settings like a fresh install but keeps the stored Gemini key.
                if (intent.getBooleanExtra("reset", false)) {
                    app.db.clearAllTables()
                    app.settings.update { com.forgequest.app.data.prefs.Settings() }
                    Log.i(TAG, "reset done")
                    return@launch
                }
                // `--ez readiness true` puts a stand-in day on the readiness screens, so Home, Recovery and Strain can
                // be checked on an emulator with no watch. The numbers are the owner's own morning from 2 Nov 2026.
                if (intent.getBooleanExtra("readiness", false)) { showStandInReadiness(app); return@launch }
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
        system.lines().forEach { Log.i(TAG, "SYS| $it") }
        if (variantsArg == "dump") return
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

    /**
     * A stand-in readiness day for emulator checks: the owner's own morning of 2 Nov 2026, where HRV sat at 40 ms
     * against a usual 38, resting heart rate at 74 against 72 and breathing at 16.4 against 16.1. The score and the
     * trend dots are computed by the real [Readiness] code, so what the screens draw is the live maths, not a mock-up.
     */
    private suspend fun showStandInReadiness(app: AppContainer) {
        app.settings.update { it.copy(healthEnabled = true) }
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val night = Night(
            start = today.minusDays(1).atTime(23, 10).atZone(zone).toInstant(),
            end = today.atTime(8, 30).atZone(zone).toInstant(),
            deepMin = 95, remMin = 128, lightMin = 283, awakeMin = 34, asleepMin = 506,
        )
        val hrvBase = Baseline(ln(38.0), 0.15, 20)
        val rhrBase = Baseline(72.0, 4.0, 20)
        val respBase = Baseline(16.1, 0.8, 20)
        val hrv = 40.0
        val rhr = 74.0
        val resp = 16.4
        val signal = Readiness.hrvSignal(hrv, listOf(38.0, 39.0, 37.0, 41.0, 38.0, 36.0, 40.0))!!
        val perf = Readiness.sleepPerformance(night.asleepMin, 480)
        val recovery = Readiness.recovery(signal, rhr, resp, perf, hrvBase, rhrBase, respBase)
        val strain = Readiness.strainFromLoad(1.6)
        // Last week on the same scale, so the week tab shows a real spread rather than one flat colour.
        val past = listOf(77, 60, 77, 75, 67, 62)
        val week = (6L downTo 1L).mapIndexed { i, back ->
            DaySummary(today.minusDays(back).toEpochDay(), past[i], 420 + i * 20, 85, 4.0 + i)
        } + DaySummary(today.toEpochDay(), recovery, night.asleepMin, perf, strain)
        app.health.debugShow(
            DayReadiness(
                epochDay = today.toEpochDay(),
                recovery = recovery,
                call = Readiness.call(recovery),
                night = night,
                sleepPerf = perf,
                sleepNeedLastNight = 480,
                sleepNeedTonight = 598,
                debtMin = 0,
                strain = strain,
                zoneMin = doubleArrayOf(7.0, 0.0, 0.0, 0.0, 0.0),
                steps = 463,
                kcalBurned = 935,
                hrv = hrv, hrvUsual = exp(hrvBase.mean),
                rhr = rhr, rhrUsual = rhrBase.mean,
                resp = resp, respUsual = respBase.mean,
                hrvTrend = Readiness.trendOf(Readiness.zWithSwc(signal - hrvBase.mean, hrvBase.sd)),
                rhrTrend = Readiness.trendOf(Readiness.zWithSwc(rhrBase.mean - rhr, rhrBase.sd)),
                respTrend = Readiness.trendOf(Readiness.zWithSwc(respBase.mean - resp, respBase.sd)),
                baselineNights = 20,
                week = week,
                feltGreat = false,
            )
        )
        Log.i(TAG, "readiness stand-in shown: recovery $recovery%, sleep $perf%")
    }
}
