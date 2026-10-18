package com.forgequest.app.data.health

import android.content.Context
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.forgequest.app.BuildConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.reflect.KClass

/**
 * One-off look at what Health Connect actually holds on this phone (from Fitbit / Google Health or anything else),
 * saved as a JSON report the owner can share. It reads only; the real import is built on what this shows.
 */
class HealthProbe(private val context: Context) {

    val types: List<KClass<out Record>> = listOf(
        StepsRecord::class, DistanceRecord::class, ActiveCaloriesBurnedRecord::class, TotalCaloriesBurnedRecord::class,
        SleepSessionRecord::class, HeartRateRecord::class, RestingHeartRateRecord::class, HeartRateVariabilityRmssdRecord::class,
        OxygenSaturationRecord::class, RespiratoryRateRecord::class, Vo2MaxRecord::class, WeightRecord::class, ExerciseSessionRecord::class,
    )
    val permissions: Set<String> = types.map { HealthPermission.getReadPermission(it) }.toSet()

    fun status(): Int = HealthConnectClient.getSdkStatus(context)

    suspend fun granted(): Set<String> = HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()

    /** A one-line verdict for the Settings button: which data types actually have readings in the last 14 days. */
    suspend fun check(): String {
        val client = HealthConnectClient.getOrCreate(context)
        val granted = client.permissionController.getGrantedPermissions()
        val end = Instant.now()
        val start = end.minus(14, ChronoUnit.DAYS)
        val withData = types.filter { HealthPermission.getReadPermission(it) in granted }.filter { type ->
            runCatching { client.readRecords(ReadRecordsRequest(type, TimeRangeFilter.between(start, end), pageSize = 1)).records.isNotEmpty() }.getOrDefault(false)
        }
        val names = withData.joinToString(", ") { t -> (t.simpleName ?: "").removeSuffix("Record").replace(Regex("(?<=[a-z])(?=[A-Z])"), " ") }
        return if (withData.isEmpty()) context.getString(com.forgequest.app.R.string.hc_check_empty)
        else context.getString(com.forgequest.app.R.string.hc_check_ok, withData.size, types.size, names)
    }

    suspend fun report(days: Long = 14): String {
        val client = HealthConnectClient.getOrCreate(context)
        val granted = client.permissionController.getGrantedPermissions()
        val end = Instant.now()
        val start = end.minus(days, ChronoUnit.DAYS)
        val zone = ZoneId.systemDefault()
        val root = buildJsonObject {
            put("generatedAt", end.toString())
            put("days", days)
            put("app", BuildConfig.VERSION_NAME)
            put("device", "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})")
            put("timeZone", zone.id)
            put("types", buildJsonObject {
                for (type in types) {
                    val name = type.simpleName ?: continue
                    if (HealthPermission.getReadPermission(type) !in granted) { put(name, JsonPrimitive("not allowed")); continue }
                    val records = try { readAll(client, type, start, end) } catch (e: Exception) {
                        if (e is kotlin.coroutines.cancellation.CancellationException) throw e
                        put(name, JsonPrimitive("error: ${e.javaClass.simpleName}: ${e.message}")); continue
                    }
                    put(name, summarize(records, zone))
                }
            })
        }
        return Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), root)
    }

    private suspend fun <T : Record> readAll(client: HealthConnectClient, type: KClass<T>, start: Instant, end: Instant): List<T> {
        val out = mutableListOf<T>()
        var token: String? = null
        do {
            val page = client.readRecords(ReadRecordsRequest(type, TimeRangeFilter.between(start, end), pageSize = 1000, pageToken = token))
            out += page.records
            token = page.pageToken
        } while (token != null && out.size < 20_000)
        return out
    }

    private fun summarize(records: List<Record>, zone: ZoneId): JsonObject = buildJsonObject {
        put("count", records.size)
        put("sources", JsonArray(records.map { it.metadata.dataOrigin.packageName }.distinct().map(::JsonPrimitive)))
        put("devices", JsonArray(records.mapNotNull { r -> r.metadata.device?.let { "${it.manufacturer ?: ""} ${it.model ?: ""}".trim() } }.distinct().map(::JsonPrimitive)))
        if (records.isEmpty()) return@buildJsonObject
        fun day(i: Instant) = i.atZone(zone).toLocalDate().toString()
        fun time(i: Instant) = i.atZone(zone).toLocalDateTime().truncatedTo(ChronoUnit.MINUTES).toString()
        when (val first = records.first()) {
            is StepsRecord -> put("perDay", perDay(records.filterIsInstance<StepsRecord>().map { day(it.startTime) to it.count.toDouble() }))
            is DistanceRecord -> put("metersPerDay", perDay(records.filterIsInstance<DistanceRecord>().map { day(it.startTime) to it.distance.inMeters }))
            is ActiveCaloriesBurnedRecord -> put("kcalPerDay", perDay(records.filterIsInstance<ActiveCaloriesBurnedRecord>().map { day(it.startTime) to it.energy.inKilocalories }))
            is TotalCaloriesBurnedRecord -> put("kcalPerDay", perDay(records.filterIsInstance<TotalCaloriesBurnedRecord>().map { day(it.startTime) to it.energy.inKilocalories }))
            is SleepSessionRecord -> put("sessions", buildJsonArray {
                records.filterIsInstance<SleepSessionRecord>().takeLast(14).forEach { s ->
                    add(buildJsonObject {
                        put("start", time(s.startTime)); put("end", time(s.endTime))
                        put("minutes", ChronoUnit.MINUTES.between(s.startTime, s.endTime))
                        put("stageMinutes", buildJsonObject {
                            s.stages.groupBy { stageName(it.stage) }.forEach { (k, v) -> put(k, v.sumOf { ChronoUnit.MINUTES.between(it.startTime, it.endTime) }) }
                        })
                    })
                }
            })
            is HeartRateRecord -> {
                val bpm = records.filterIsInstance<HeartRateRecord>().flatMap { it.samples }.map { it.beatsPerMinute }
                put("samples", bpm.size); put("min", bpm.minOrNull() ?: 0); put("max", bpm.maxOrNull() ?: 0); put("avg", if (bpm.isEmpty()) 0 else bpm.average().toInt())
                val all = records.filterIsInstance<HeartRateRecord>().flatMap { it.samples }
                if (all.size > 1) put("avgSecondsBetweenSamples", ChronoUnit.SECONDS.between(all.first().time, all.last().time) / (all.size - 1))
            }
            is RestingHeartRateRecord -> put("values", series(records.filterIsInstance<RestingHeartRateRecord>().map { time(it.time) to it.beatsPerMinute.toDouble() }))
            is HeartRateVariabilityRmssdRecord -> put("values", series(records.filterIsInstance<HeartRateVariabilityRmssdRecord>().map { time(it.time) to it.heartRateVariabilityMillis }))
            is OxygenSaturationRecord -> put("values", series(records.filterIsInstance<OxygenSaturationRecord>().map { time(it.time) to it.percentage.value }))
            is RespiratoryRateRecord -> put("values", series(records.filterIsInstance<RespiratoryRateRecord>().map { time(it.time) to it.rate }))
            is Vo2MaxRecord -> put("values", series(records.filterIsInstance<Vo2MaxRecord>().map { time(it.time) to it.vo2MillilitersPerMinuteKilogram }))
            is WeightRecord -> put("values", series(records.filterIsInstance<WeightRecord>().map { time(it.time) to it.weight.inKilograms }))
            is ExerciseSessionRecord -> put("sessions", buildJsonArray {
                records.filterIsInstance<ExerciseSessionRecord>().takeLast(20).forEach { e ->
                    add(buildJsonObject {
                        put("start", time(e.startTime)); put("minutes", ChronoUnit.MINUTES.between(e.startTime, e.endTime))
                        put("type", e.exerciseType); put("title", e.title ?: "")
                    })
                }
            })
            else -> put("note", first.javaClass.simpleName)
        }
    }

    private fun perDay(pairs: List<Pair<String, Double>>) = buildJsonObject {
        pairs.groupBy({ it.first }, { it.second }).toSortedMap().forEach { (d, v) -> put(d, v.sum().toInt()) }
    }

    private fun series(pairs: List<Pair<String, Double>>) = buildJsonArray {
        pairs.takeLast(30).forEach { (t, v) -> add(buildJsonObject { put("at", t); put("value", "%.1f".format(java.util.Locale.US, v).toDouble()) }) }
    }

    private fun stageName(stage: Int) = when (stage) {
        SleepSessionRecord.STAGE_TYPE_AWAKE, SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED -> "awake"
        SleepSessionRecord.STAGE_TYPE_LIGHT -> "light"
        SleepSessionRecord.STAGE_TYPE_DEEP -> "deep"
        SleepSessionRecord.STAGE_TYPE_REM -> "rem"
        SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> "outOfBed"
        SleepSessionRecord.STAGE_TYPE_SLEEPING -> "asleep"
        else -> "unknown"
    }
}
