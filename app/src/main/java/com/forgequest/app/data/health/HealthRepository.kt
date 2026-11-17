package com.forgequest.app.data.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.forgequest.app.data.db.AppDatabase
import com.forgequest.app.data.db.WeightEntity
import com.forgequest.app.data.prefs.SettingsRepository
import com.forgequest.app.data.suspendRunCatching
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import kotlin.reflect.KClass

/** Daily steps and calorie-burn averages read from Health Connect for the calorie budget and Settings. */
data class DayHealth(
    /** Average calories burned a day over the last 14 full days; null with fewer than 10 days >= 1,200 kcal. */
    val kcalAvg14: Int? = null,
    /** Steps per full day for the last 60 days: the calorie day base and step-drop check. */
    val dailySteps: Map<Long, Long> = emptyMap(),
)

sealed interface HealthState {
    data object Unavailable : HealthState
    data object NeedsAccess : HealthState
    data object Loading : HealthState
    data object Failed : HealthState
    data class Ready(val day: DayHealth) : HealthState
}

class HealthRepository(private val context: Context, private val settings: SettingsRepository, private val db: AppDatabase) {

    private val requiredTypes: List<KClass<out Record>> = listOf(
        StepsRecord::class,
        TotalCaloriesBurnedRecord::class,
    )
    val permissions: Set<String> = requiredTypes.map { HealthPermission.getReadPermission(it) }.toSet()

    /** Permissions requested when connecting Health Connect: steps, calories burned, and weight import. */
    fun requestPermissions(): Set<String> = permissions + HealthPermission.getReadPermission(WeightRecord::class)

    private val _state = MutableStateFlow<HealthState>(HealthState.Loading)
    val state: StateFlow<HealthState> = _state

    fun available(): Boolean = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    private val refreshLock = kotlinx.coroutines.sync.Mutex()
    private var lastRefreshAt = 0L

    /** Re-reads Health Connect; reuses a fresh result for 5 minutes unless [force] is true. */
    suspend fun refresh(force: Boolean = false) = refreshLock.withLock {
        val fresh = System.currentTimeMillis() - lastRefreshAt < 5 * 60_000
        if (!force && fresh && state.value is HealthState.Ready) return@withLock
        refreshNow()
    }

    private suspend fun refreshNow() {
        if (!settings.current().healthEnabled || !available()) {
            _state.value = HealthState.Unavailable
            return
        }
        val client = HealthConnectClient.getOrCreate(context)
        val granted = suspendRunCatching { client.permissionController.getGrantedPermissions() }.getOrDefault(emptySet())
        if (!granted.containsAll(permissions)) {
            _state.value = if (settings.current().healthConnected) HealthState.NeedsAccess else HealthState.Unavailable
            return
        }
        if (HealthPermission.getReadPermission(WeightRecord::class) in granted) suspendRunCatching { syncWeight(client) }
        suspendRunCatching { withContext(Dispatchers.IO) { compute(client) } }
            .onSuccess {
                _state.value = HealthState.Ready(it)
                lastRefreshAt = System.currentTimeMillis()
                if (!settings.current().healthConnected) settings.update { s -> s.copy(healthConnected = true) }
            }
            .onFailure {
                Log.w("ForgeQuest", "health refresh failed", it)
                if (_state.value !is HealthState.Ready) _state.value = HealthState.Failed
            }
    }

    private suspend fun compute(client: HealthConnectClient): DayHealth {
        val today = LocalDate.now(ZoneId.systemDefault())
        // Days with under 1,200 kcal mean the watch was off for part of the day, so they'd drag the average down.
        val kcalDays = suspendRunCatching {
            client.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(
                    setOf(TotalCaloriesBurnedRecord.ENERGY_TOTAL),
                    TimeRangeFilter.between(today.minusDays(14).atStartOfDay(), today.atStartOfDay()),
                    Period.ofDays(1),
                )
            ).mapNotNull { it.result[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories?.takeIf { k -> k >= 1200 } }
        }.getOrDefault(emptyList())
        val kcalAvg14 = if (kcalDays.size >= 10) kcalDays.average().toInt() else null
        val dailySteps = suspendRunCatching {
            client.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(
                    setOf(StepsRecord.COUNT_TOTAL),
                    TimeRangeFilter.between(today.minusDays(60).atStartOfDay(), today.atStartOfDay()),
                    Period.ofDays(1),
                )
            ).associate { it.startTime.toLocalDate().toEpochDay() to (it.result[StepsRecord.COUNT_TOTAL] ?: 0L) }
        }.getOrDefault(emptyMap())
        return DayHealth(kcalAvg14 = kcalAvg14, dailySteps = dailySteps)
    }

    /**
     * Weigh-ins from Health Connect fill days the app has no weight for; a weight typed in the app always wins.
     * The latest reading of each day counts.
     */
    private suspend fun syncWeight(client: HealthConnectClient) = withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        val since = LocalDate.now(zone).minusDays(70).atStartOfDay(zone).toInstant()
        val records = read(client, WeightRecord::class, since, Instant.now()).filter { it.metadata.dataOrigin.packageName != context.packageName }
        if (records.isEmpty()) return@withContext
        val dao = db.weights()
        val have = dao.all().map { it.epochDay }.toSet()
        records.groupBy { it.time.atZone(zone).toLocalDate().toEpochDay() }
            .filterKeys { it !in have }
            .forEach { (day, list) ->
                val latest = list.maxBy { it.time }
                dao.upsert(WeightEntity(epochDay = day, kg = latest.weight.inKilograms.toFloat(), loggedAt = latest.time.toEpochMilli()))
            }
    }

    private suspend fun <T : Record> read(client: HealthConnectClient, type: KClass<T>, start: Instant, end: Instant): List<T> {
        val out = mutableListOf<T>()
        var token: String? = null
        do {
            val page = client.readRecords(ReadRecordsRequest(type, TimeRangeFilter.between(start, end), pageSize = 5000, pageToken = token))
            out += page.records
            token = page.pageToken
        } while (token != null && out.size < 100_000)
        return out
    }
}
