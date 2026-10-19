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
}
