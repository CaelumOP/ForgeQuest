package com.forgequest.app.data.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.forgequest.app.data.prefs.SettingsRepository
import com.forgequest.app.data.suspendRunCatching
import com.forgequest.app.domain.Baseline
import com.forgequest.app.domain.DayCall
import com.forgequest.app.domain.Night
import com.forgequest.app.domain.Nutrition
import com.forgequest.app.domain.Readiness
import com.forgequest.app.domain.SleepPiece
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.reflect.KClass

/** Today's Whoop-style numbers, built from Health Connect (Fitbit / Google Health). */
data class DayReadiness(
    val recovery: Int,
    val call: DayCall,
    val night: Night?,
    val sleepScore: Int?,
    val strain: Double,
    val steps: Long,
    val kcalBurned: Int,
    val hrv: Double?,
    val hrvUsual: Double?,
    val rhr: Double?,
    val rhrUsual: Double?,
    val resp: Double?,
    /** Nights with HRV in the last 30 days; under [Readiness.MIN_BASELINE_DAYS] the score is still learning. */
    val baselineNights: Int,
)

sealed interface HealthState {
    data object Unavailable : HealthState
    data object NeedsAccess : HealthState
    data object Loading : HealthState
    data class Ready(val day: DayReadiness) : HealthState
}

class HealthRepository(private val context: Context, private val settings: SettingsRepository) {

    private val types: List<KClass<out Record>> = listOf(
        SleepSessionRecord::class, HeartRateRecord::class, RestingHeartRateRecord::class, HeartRateVariabilityRmssdRecord::class,
        RespiratoryRateRecord::class, StepsRecord::class, TotalCaloriesBurnedRecord::class,
    )
    val permissions: Set<String> = types.map { HealthPermission.getReadPermission(it) }.toSet()

    private val _state = MutableStateFlow<HealthState>(HealthState.Loading)
    val state: StateFlow<HealthState> = _state

    fun available(): Boolean = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    /** Re-reads Health Connect. Cheap enough to call whenever Home opens. */
    suspend fun refresh() {
        if (!available()) { _state.value = HealthState.Unavailable; return }
        val client = HealthConnectClient.getOrCreate(context)
        val granted = suspendRunCatching { client.permissionController.getGrantedPermissions() }.getOrDefault(emptySet())
        if (!granted.containsAll(permissions)) { _state.value = HealthState.NeedsAccess; return }
        suspendRunCatching { withContext(Dispatchers.IO) { compute(client) } }
            .onSuccess { _state.value = HealthState.Ready(it) }
            .onFailure { Log.w("ForgeQuest", "health refresh failed", it) }
    }

    private suspend fun compute(client: HealthConnectClient): DayReadiness {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val from = today.minusDays(31).atStartOfDay(zone).toInstant()
        val now = Instant.now()
        val dayStart = today.atStartOfDay(zone).toInstant()

        val sleep = read(client, SleepSessionRecord::class, from, now).map { s ->
            fun mins(vararg stage: Int) = s.stages.filter { it.stage in stage }.sumOf { ((it.endTime.epochSecond - it.startTime.epochSecond) / 60).toInt() }
            val staged = s.stages.isNotEmpty()
            SleepPiece(
                s.startTime, s.endTime,
                deepMin = mins(SleepSessionRecord.STAGE_TYPE_DEEP),
                remMin = mins(SleepSessionRecord.STAGE_TYPE_REM),
                lightMin = mins(SleepSessionRecord.STAGE_TYPE_LIGHT),
                awakeMin = mins(SleepSessionRecord.STAGE_TYPE_AWAKE, SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED, SleepSessionRecord.STAGE_TYPE_OUT_OF_BED),
                unstagedMin = if (staged) mins(SleepSessionRecord.STAGE_TYPE_SLEEPING) else ((s.endTime.epochSecond - s.startTime.epochSecond) / 60).toInt(),
                fromFitbit = s.metadata.dataOrigin.packageName == FITBIT,
            )
        }
        val nights = Readiness.nights(sleep, zone)
        val hrvRecords = read(client, HeartRateVariabilityRmssdRecord::class, from, now)
        val rhrRecords = read(client, RestingHeartRateRecord::class, from, now)
        val respRecords = read(client, RespiratoryRateRecord::class, from, now)

        // Per-day signals: HRV and breathing during that day's night, resting heart rate on that day (Fitbit's first).
        fun hrvFor(n: Night) = Readiness.median(hrvRecords.filter { it.time >= n.start && it.time <= n.end }.map { it.heartRateVariabilityMillis })
        fun respFor(day: LocalDate, n: Night?) = respRecords.filter { r ->
            if (n != null) r.time >= n.start && r.time <= n.end.plusSeconds(3600) else r.time.atZone(zone).toLocalDate() == day
        }.map { it.rate }.let(Readiness::median)
        fun rhrFor(day: LocalDate): Double? {
            val sameDay = rhrRecords.filter { it.time.atZone(zone).toLocalDate() == day }
            val fitbit = sameDay.filter { it.metadata.dataOrigin.packageName == FITBIT }
            return Readiness.median((fitbit.ifEmpty { sameDay }).map { it.beatsPerMinute.toDouble() })
        }

        val pastDays = (1L..30L).map { today.minusDays(it) }
        val hrvHistory = pastDays.mapNotNull { d -> nights[d]?.let(::hrvFor) }
        val rhrHistory = pastDays.mapNotNull(::rhrFor)
        val respHistory = pastDays.mapNotNull { d -> nights[d]?.let { respFor(d, it) } }
        val hrvBase: Baseline? = Readiness.baseline(hrvHistory, floorSd = 3.0)
        val rhrBase = Readiness.baseline(rhrHistory, floorSd = 2.0)
        val respBase = Readiness.baseline(respHistory, floorSd = 0.5)

        val night = nights[today]
        val sleepScore = night?.let { Readiness.sleepScore(it) }
        val hrv = night?.let(::hrvFor)
        val rhr = rhrFor(today)
        val resp = respFor(today, night)
        val recovery = Readiness.recovery(hrv, rhr, resp, sleepScore, hrvBase, rhrBase, respBase)

        val s = settings.current()
        val age = Nutrition.ageFromBirthYear(s.birthYear, today.year)
        val heart = read(client, HeartRateRecord::class, dayStart, now).flatMap { r -> r.samples.map { it.time to it.beatsPerMinute } }
        val strain = Readiness.strain(Readiness.zoneMinutes(heart, Readiness.hrMax(age)))

        // Health Connect's own totals merge Google Fit, Fitbit and the phone instead of adding them up.
        val totals = suspendRunCatching {
            client.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL, TotalCaloriesBurnedRecord.ENERGY_TOTAL), TimeRangeFilter.between(dayStart, now)))
        }.getOrNull()

        return DayReadiness(
            recovery = recovery,
            call = Readiness.call(recovery),
            night = night,
            sleepScore = sleepScore,
            strain = strain,
            steps = totals?.get(StepsRecord.COUNT_TOTAL) ?: 0L,
            kcalBurned = totals?.get(TotalCaloriesBurnedRecord.ENERGY_TOTAL)?.inKilocalories?.toInt() ?: 0,
            hrv = hrv, hrvUsual = hrvBase?.mean,
            rhr = rhr, rhrUsual = rhrBase?.mean,
            resp = resp,
            baselineNights = hrvHistory.size,
        )
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

    /** One line for the coach's context. */
    fun coachLine(): String? {
        val d = (state.value as? HealthState.Ready)?.day ?: return null
        val sleep = d.night?.let { "slept ${it.asleepMin / 60}h ${it.asleepMin % 60}m (deep ${it.deepMin}m, REM ${it.remMin}m), sleep score ${d.sleepScore}" } ?: "no sleep recorded last night"
        val hrv = d.hrv?.let { "HRV ${it.toInt()} ms" + (d.hrvUsual?.let { u -> " vs usual ${u.toInt()}" } ?: "") } ?: "no HRV"
        val rhr = d.rhr?.let { "resting HR ${it.toInt()}" + (d.rhrUsual?.let { u -> " vs usual ${u.toInt()}" } ?: "") } ?: ""
        return "- Fitbit today: recovery ${d.recovery}% (${d.call.name.lowercase().replace('_', ' ')}), $sleep, $hrv, $rhr, strain so far ${"%.1f".format(java.util.Locale.US, d.strain)}/21, ${d.steps} steps" +
            (if (d.baselineNights < Readiness.MIN_BASELINE_DAYS) " (still learning their normal)" else "") + "\n"
    }

    private companion object {
        const val FITBIT = "com.fitbit.FitbitMobile"
    }
}
