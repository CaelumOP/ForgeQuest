package com.forgequest.app.data.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.forgequest.app.data.db.AppDatabase
import com.forgequest.app.data.db.ReadinessDayEntity
import com.forgequest.app.data.prefs.SettingsRepository
import com.forgequest.app.data.suspendRunCatching
import com.forgequest.app.domain.DayCall
import com.forgequest.app.domain.Night
import com.forgequest.app.domain.Nutrition
import com.forgequest.app.domain.Readiness
import com.forgequest.app.domain.SleepPiece
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.exp
import kotlin.reflect.KClass

/** One day in the 7-day charts. */
data class DaySummary(val epochDay: Long, val recovery: Int?, val sleepMin: Int?, val sleepPerf: Int?, val strain: Double)

/** Today's Whoop-style numbers, built from Health Connect (Fitbit / Google Health). */
data class DayReadiness(
    val recovery: Int,
    val call: DayCall,
    val night: Night?,
    val sleepPerf: Int?,
    val sleepNeedLastNight: Int,
    val sleepNeedTonight: Int,
    val debtMin: Int,
    val strain: Double,
    val zoneMin: DoubleArray,
    val steps: Long,
    val kcalBurned: Int,
    val hrv: Double?,
    val hrvUsual: Double?,
    val rhr: Double?,
    val rhrUsual: Double?,
    val resp: Double?,
    val respUsual: Double?,
    /** Nights with HRV in the last 30 days; under [Readiness.MIN_BASELINE_DAYS] the score is still learning. */
    val baselineNights: Int,
    /** Oldest first, today last. */
    val week: List<DaySummary>,
    val feltGreat: Boolean,
) {
    val hasData get() = night != null || hrv != null || rhr != null
}

sealed interface HealthState {
    data object Unavailable : HealthState
    data object NeedsAccess : HealthState
    data object Loading : HealthState
    data class Ready(val day: DayReadiness) : HealthState
}

class HealthRepository(private val context: Context, private val settings: SettingsRepository, private val db: AppDatabase) {

    private val types: List<KClass<out Record>> = listOf(
        SleepSessionRecord::class, HeartRateRecord::class, RestingHeartRateRecord::class, HeartRateVariabilityRmssdRecord::class,
        RespiratoryRateRecord::class, StepsRecord::class, TotalCaloriesBurnedRecord::class,
    )
    val permissions: Set<String> = types.map { HealthPermission.getReadPermission(it) }.toSet()

    /** What the Connect button asks for: the required set plus, where the phone supports it, full history. */
    fun requestPermissions(): Set<String> {
        val history = runCatching {
            HealthConnectClient.getOrCreate(context).features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY) ==
                HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
        }.getOrDefault(false)
        val wanted = permissions + HealthPermission.getReadPermission(WeightRecord::class)
        return if (history) wanted + HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY else wanted
    }

    private val _state = MutableStateFlow<HealthState>(HealthState.Loading)
    val state: StateFlow<HealthState> = _state

    fun available(): Boolean = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    private val refreshLock = kotlinx.coroutines.sync.Mutex()
    private var lastRefreshAt = 0L

    /**
     * Re-reads Health Connect. Home calls it on every open, so a fresh result is reused for a few minutes and two
     * opens at once share one read; [force] skips that (after granting access or changing the setting).
     */
    suspend fun refresh(force: Boolean = false) = refreshLock.withLock {
        val fresh = System.currentTimeMillis() - lastRefreshAt < 5 * 60_000
        if (!force && fresh && state.value is HealthState.Ready) return@withLock
        refreshNow()
    }

    private suspend fun refreshNow() {
        if (!settings.current().healthEnabled || !available()) { _state.value = HealthState.Unavailable; return }
        val client = HealthConnectClient.getOrCreate(context)
        val granted = suspendRunCatching { client.permissionController.getGrantedPermissions() }.getOrDefault(emptySet())
        if (!granted.containsAll(permissions)) { _state.value = HealthState.NeedsAccess; return }
        if (HealthPermission.getReadPermission(WeightRecord::class) in granted) suspendRunCatching { syncWeight(client) }
        suspendRunCatching { withContext(Dispatchers.IO) { compute(client) } }
            .onSuccess { _state.value = HealthState.Ready(it); lastRefreshAt = System.currentTimeMillis() }
            .onFailure { Log.w("ForgeQuest", "health refresh failed", it) }
    }

    /** The "I feel great" tap, kept next to the day's scores so the formulas can be tuned to this person later. */
    suspend fun setFeltGreat(value: Boolean) {
        // The readiness day can be yesterday until tonight's sleep is recorded, so save against the day on screen.
        val day = (state.value as? HealthState.Ready)?.day?.week?.lastOrNull()?.epochDay ?: LocalDate.now().toEpochDay()
        val dao = db.readiness()
        dao.get(day)?.let { dao.upsert(it.copy(feltGreat = value)) }
        (state.value as? HealthState.Ready)?.let { _state.value = HealthState.Ready(it.day.copy(feltGreat = value)) }
    }

    private suspend fun compute(client: HealthConnectClient): DayReadiness {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val from = today.minusDays(70).atStartOfDay(zone).toInstant()
        val now = Instant.now()

        val sleep = read(client, SleepSessionRecord::class, from, now).map { s ->
            fun mins(vararg stage: Int) = s.stages.filter { it.stage in stage }.sumOf { ((it.endTime.epochSecond - it.startTime.epochSecond) / 60).toInt() }
            SleepPiece(
                s.startTime, s.endTime,
                deepMin = mins(SleepSessionRecord.STAGE_TYPE_DEEP),
                remMin = mins(SleepSessionRecord.STAGE_TYPE_REM),
                lightMin = mins(SleepSessionRecord.STAGE_TYPE_LIGHT),
                awakeMin = mins(SleepSessionRecord.STAGE_TYPE_AWAKE, SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED, SleepSessionRecord.STAGE_TYPE_OUT_OF_BED),
                unstagedMin = if (s.stages.isNotEmpty()) mins(SleepSessionRecord.STAGE_TYPE_SLEEPING) else ((s.endTime.epochSecond - s.startTime.epochSecond) / 60).toInt(),
                fromFitbit = s.metadata.dataOrigin.packageName == FITBIT,
            )
        }
        val sleepDays = Readiness.sleepDays(sleep, zone)
        val hrvRecords = read(client, HeartRateVariabilityRmssdRecord::class, from, now)
        val rhrRecords = read(client, RestingHeartRateRecord::class, from, now)
        val respRecords = read(client, RespiratoryRateRecord::class, from, now)

        fun nightOf(d: LocalDate) = sleepDays[d]?.night
        fun hrvFor(d: LocalDate) = nightOf(d)?.let { n -> Readiness.median(hrvRecords.filter { it.time >= n.start && it.time <= n.end }.map { it.heartRateVariabilityMillis }) }
        fun respFor(d: LocalDate): Double? {
            val n = nightOf(d)
            return Readiness.median(respRecords.filter { r -> if (n != null) r.time >= n.start && r.time <= n.end.plusSeconds(3600) else r.time.atZone(zone).toLocalDate() == d }.map { it.rate })
        }
        fun rhrFor(d: LocalDate): Double? {
            val sameDay = rhrRecords.filter { it.time.atZone(zone).toLocalDate() == d }
            val fitbit = sameDay.filter { it.metadata.dataOrigin.packageName == FITBIT }
            return Readiness.median((fitbit.ifEmpty { sameDay }).map { it.beatsPerMinute.toDouble() })
        }

        val s = settings.current()
        val hrMax = Readiness.hrMax(Nutrition.ageFromBirthYear(s.birthYear, today.year))
        val rest30 = Readiness.median((1L..30L).mapNotNull { rhrFor(today.minusDays(it)) }) ?: 65.0

        // Strain per day for the last 8 days, read a day at a time so 2-second heart rate never piles up in memory.
        val strainByDay = (0L..8L).associate { back ->
            val d = today.minusDays(back)
            val start = d.atStartOfDay(zone).toInstant()
            val end = if (back == 0L) now else d.plusDays(1).atStartOfDay(zone).toInstant()
            val female = s.bodyType == com.forgequest.app.data.prefs.BodyType.FEMALE
            val result = suspendRunCatching {
                val samples = read(client, HeartRateRecord::class, start, end).flatMap { r -> r.samples.map { it.time to it.beatsPerMinute } }
                Readiness.zoneMinutes(samples, hrMax, rest30) to Readiness.trimp(samples, hrMax, rest30, female)
            }.getOrDefault(DoubleArray(5) to 0.0)
            d to result
        }
        fun strainOf(d: LocalDate) = Readiness.strainFromLoad(strainByDay[d]?.second ?: 0.0)

        fun asleepBefore(d: LocalDate) = (3L downTo 1L).map { nightOf(d.minusDays(it))?.asleepMin ?: Readiness.BASE_NEED_MIN }
        fun needFor(d: LocalDate) = Readiness.sleepNeed(strainOf(d.minusDays(1)), asleepBefore(d), sleepDays[d.minusDays(1)]?.napMin ?: 0)
        fun perfFor(d: LocalDate) = nightOf(d)?.let { Readiness.sleepPerformance(it.asleepMin, needFor(d)) }

        // Baselines from the 30 days before the given day.
        fun recoveryFor(d: LocalDate): Triple<Int, Pair<Double?, Double?>, Int> {
            val past = (1L..60L).map { d.minusDays(it) }
            val hrvHistory = past.mapNotNull(::hrvFor)
            val hrvBase = Readiness.baseline(hrvHistory.filter { it > 0 }.map { kotlin.math.ln(it) }, floorSd = 0.05)
            val rhrBase = Readiness.baseline(past.mapNotNull(::rhrFor), floorSd = 2.0)
            val respBase = Readiness.baseline(past.mapNotNull(::respFor), floorSd = 0.5)
            val last7 = (0L..6L).mapNotNull { hrvFor(d.minusDays(it)) }
            val signal = Readiness.hrvSignal(hrvFor(d), last7)
            val rec = Readiness.recovery(signal, rhrFor(d), respFor(d), perfFor(d), hrvBase, rhrBase, respBase)
            return Triple(rec, Pair(hrvBase?.mean?.let { exp(it) }, rhrBase?.mean), hrvHistory.size)
        }

        // Like Whoop, a day starts when you wake up: after midnight but before tonight's sleep is recorded, keep showing
        // the night you last woke from instead of an empty card.
        val day = if (nightOf(today) == null && nightOf(today.minusDays(1)) != null) today.minusDays(1) else today
        val (recovery, usual, baselineNights) = recoveryFor(day)
        val respUsual = Readiness.median((1L..30L).mapNotNull { respFor(day.minusDays(it)) })
        val week = (6L downTo 0L).map { back ->
            val d = day.minusDays(back)
            val hasSignal = nightOf(d) != null || rhrFor(d) != null
            DaySummary(d.toEpochDay(), if (hasSignal) (if (back == 0L) recovery else recoveryFor(d).first) else null, nightOf(d)?.asleepMin, perfFor(d), strainOf(d))
        }

        // Health Connect's own totals merge Google Fit, Fitbit and the phone instead of adding them up.
        val totals = suspendRunCatching {
            client.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL, TotalCaloriesBurnedRecord.ENERGY_TOTAL), TimeRangeFilter.between(day.atStartOfDay(zone).toInstant(), now)))
        }.getOrNull()

        val night = nightOf(day)
        // The day runs from waking to the next sleep, so strain after midnight still adds to it.
        val sinceWake = (0L..java.time.temporal.ChronoUnit.DAYS.between(day, today)).mapNotNull { strainByDay[day.plusDays(it)] }
        val zonesSinceWake = DoubleArray(5) { i -> sinceWake.sumOf { it.first[i] } }
        val strain = Readiness.strainFromLoad(sinceWake.sumOf { it.second })
        val perf = perfFor(day)
        val dao = db.readiness()
        val felt = dao.get(day.toEpochDay())?.feltGreat ?: false
        if (night != null || rhrFor(day) != null) {
            dao.upsert(
                ReadinessDayEntity(
                    epochDay = day.toEpochDay(), recovery = recovery, sleepPerf = perf ?: 0, sleepMin = night?.asleepMin ?: 0,
                    strain = strain.toFloat(), hrv = (hrvFor(day) ?: 0.0).toFloat(), rhr = (rhrFor(day) ?: 0.0).toFloat(),
                    feltGreat = felt, updatedAt = System.currentTimeMillis(),
                )
            )
        }

        return DayReadiness(
            recovery = recovery,
            call = Readiness.call(recovery),
            night = night,
            sleepPerf = perf,
            sleepNeedLastNight = needFor(day),
            sleepNeedTonight = Readiness.sleepNeed(strain, (2L downTo 0L).map { nightOf(day.minusDays(it))?.asleepMin ?: Readiness.BASE_NEED_MIN }, sleepDays[day]?.napMin ?: 0),
            debtMin = Readiness.debtMin((2L downTo 0L).map { nightOf(day.minusDays(it))?.asleepMin ?: Readiness.BASE_NEED_MIN }),
            strain = strain,
            zoneMin = zonesSinceWake,
            steps = totals?.get(StepsRecord.COUNT_TOTAL) ?: 0L,
            kcalBurned = totals?.get(TotalCaloriesBurnedRecord.ENERGY_TOTAL)?.inKilocalories?.toInt() ?: 0,
            hrv = hrvFor(day), hrvUsual = usual.first,
            rhr = rhrFor(day), rhrUsual = usual.second,
            resp = respFor(day), respUsual = respUsual,
            baselineNights = baselineNights,
            week = week,
            feltGreat = felt,
        )
    }

    /**
     * Weigh-ins from Google Health / Fitbit fill days the app has no weight for; a weight typed in the app always wins.
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
                dao.upsert(com.forgequest.app.data.db.WeightEntity(epochDay = day, kg = latest.weight.inKilograms.toFloat(), loggedAt = latest.time.toEpochMilli()))
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

    /** One line for the coach's context. */
    fun coachLine(): String? {
        val d = (state.value as? HealthState.Ready)?.day?.takeIf { it.hasData } ?: return null
        val sleep = d.night?.let { "slept ${it.asleepMin / 60}h ${it.asleepMin % 60}m of ${d.sleepNeedLastNight / 60}h ${d.sleepNeedLastNight % 60}m needed (deep ${it.deepMin}m, REM ${it.remMin}m)" } ?: "no sleep recorded last night"
        val hrv = d.hrv?.let { "HRV ${it.toInt()} ms" + (d.hrvUsual?.let { u -> " vs usual ${u.toInt()}" } ?: "") } ?: "no HRV"
        val rhr = d.rhr?.let { "resting HR ${it.toInt()}" + (d.rhrUsual?.let { u -> " vs usual ${u.toInt()}" } ?: "") } ?: ""
        val target = Readiness.strainTarget(d.recovery)
        return "- Fitbit today: recovery ${d.recovery}% (${d.call.name.lowercase().replace('_', ' ')}), $sleep, $hrv, $rhr, strain so far ${"%.1f".format(java.util.Locale.US, d.strain)}/21 (target ${target.start.toInt()}–${target.endInclusive.toInt()}), ${d.steps} steps" +
            (if (d.baselineNights < Readiness.MIN_BASELINE_DAYS) " (still learning their normal)" else "") + "\n"
    }

    private companion object {
        const val FITBIT = "com.fitbit.FitbitMobile"
    }
}
