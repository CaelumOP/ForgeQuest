package com.forgequest.app.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import kotlin.reflect.KClass

/**
 * Health Connect permission set and SDK availability check.
 */
class HealthProbe(private val context: Context) {

    val types: List<KClass<out Record>> = listOf(
        StepsRecord::class, TotalCaloriesBurnedRecord::class, WeightRecord::class,
    )
    val permissions: Set<String> = types.map { HealthPermission.getReadPermission(it) }.toSet()

    fun status(): Int = HealthConnectClient.getSdkStatus(context)
}
