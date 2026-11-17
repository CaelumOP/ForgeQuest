package com.forgequest.app.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.reflect.KClass

/**
 * One-off look at what Health Connect holds on this phone (steps, calories burned, weight).
 */
class HealthProbe(private val context: Context) {

    val types: List<KClass<out Record>> = listOf(
        StepsRecord::class, TotalCaloriesBurnedRecord::class, WeightRecord::class,
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
