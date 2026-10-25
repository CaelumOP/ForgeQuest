package com.forgequest.app.data.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
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

private class DayLoad(val zones: DoubleArray, val load: Double, val workoutLoad: Double, val otherLoad: Double = 0.0)

/** A workout another app (Fitbit, Google Health) recorded today: a run, a football match, a walk. */
data class OtherWorkout(val exerciseType: Int, val title: String?, val minutes: Int)

/** One day in the 7-day charts. */
data class DaySummary(val epochDay: Long, val recovery: Int?, val sleepMin: Int?, val sleepPerf: Int?, val strain: Double)

/** Today's Whoop-style numbers, built from Health Connect (Fitbit / Google Health). */
data class DayReadiness(
    /** The day these numbers are for: yesterday until Health Connect has last night's sleep. */
    val epochDay: Long,
    val recovery: Int,
    val call: DayCall,
    val night: Night?,
    val sleepPerf: Int?,
    val sleepNeedLastNight: Int,
    val sleepNeedTonight: Int,
    val debtMin: Int,
    val strain: Double,
    val zoneMin: DoubleArray,
    /** Share of today's strain load earned during ForgeQuest workouts (0–1). */
    val workoutShare: Float = 0f,
    /** Share earned in workouts other apps recorded (0–1), and what they were. */
    val otherShare: Float = 0f,
    val otherWorkouts: List<OtherWorkout> = emptyList(),
    val steps: Long,
    val kcalBurned: Int,
    /** Average calories burned a day over the last 14 full days the watch was worn; null with fewer than 10 such days. */
    val kcalAvg14: Int? = null,
    /** Steps per full day for the last 60 days (Health Connect merges sources): the calorie day base and step-drop check. */
    val dailySteps: Map<Long, Long> = emptyMap(),
    val hrv: Double?,
    val hrvUsual: Double?,
    val rhr: Double?,
    val rhrUsual: Double?,
    val resp: Double?,
    val respUsual: Double?,
    /** Nights with HRV in the last 60 days; under [Readiness.MIN_BASELINE_DAYS] the score is still learning. */
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
    /** Health Connect couldn't be read; the card offers to try again. */
    data object Failed : HealthState
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
        // Weight and other apps' workouts are extras: without them the scores still work.
        val wanted = permissions + HealthPermission.getReadPermission(WeightRecord::class) + HealthPermission.getReadPermission(ExerciseSessionRecord::class)
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
     * Returns what the read found, or null when nothing was read (reused, switched off, or no access).
     */
    suspend fun refresh(force: Boolean = false): RefreshResult? = refreshLock.withLock {
        // Still on yesterday's night: Fitbit may have just synced, so don't wait out the 5 minutes.
        val waiting = (state.value as? HealthState.Ready)?.day?.let { it.epochDay < LocalDate.now().toEpochDay() } == true
        val fresh = System.currentTimeMillis() - lastRefreshAt < 5 * 60_000 && !waiting
        if (!force && fresh && state.value is HealthState.Ready) return@withLock null
        refreshNow()
    }

    private suspend fun refreshNow(): RefreshResult? {
        if (!settings.current().healthEnabled || !available()) { _state.value = HealthState.Unavailable; return null }
        val client = HealthConnectClient.getOrCreate(context)
        val granted = suspendRunCatching { client.permissionController.getGrantedPermissions() }.getOrDefault(emptySet())
        if (!granted.containsAll(permissions)) { _state.value = HealthState.NeedsAccess; return null }
        if (HealthPermission.getReadPermission(WeightRecord::class) in granted) suspendRunCatching { syncWeight(client) }
        val exerciseOk = HealthPermission.getReadPermission(ExerciseSessionRecord::class) in granted
        val before = (_state.value as? HealthState.Ready)?.day?.epochDay
        val result = suspendRunCatching { withContext(Dispatchers.IO) { compute(client, exerciseOk) } }
            .onSuccess { _state.value = HealthState.Ready(it); lastRefreshAt = System.currentTimeMillis() }
            .onFailure {
                Log.w("ForgeQuest", "health refresh failed", it)
                // Keep the last good numbers; otherwise leave Loading so the card doesn't spin forever.
                if (_state.value !is HealthState.Ready) _state.value = HealthState.Failed
            }
        return refreshResult(before, result.getOrNull()?.epochDay, LocalDate.now().toEpochDay())
    }

    /** The "I feel great" tap, kept next to the day's scores so the formulas can be tuned to this person later. */
    suspend fun setFeltGreat(value: Boolean) {
        // The readiness day can be yesterday until tonight's sleep is recorded, so save against the day on screen.
        val day = (state.value as? HealthState.Ready)?.day?.epochDay ?: LocalDate.now().toEpochDay()
        val dao = db.readiness()
        dao.get(day)?.let { dao.upsert(it.copy(feltGreat = value)) }
        (state.value as? HealthState.Ready)?.let { _state.value = HealthState.Ready(it.day.copy(feltGreat = value)) }
    }

    private suspend fun compute(client: HealthConnectClient, exerciseOk: Boolean): DayReadiness {
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
                segments = s.stages.map { st ->
                    val code = when (st.stage) {
                        SleepSessionRecord.STAGE_TYPE_DEEP -> 2
                        SleepSessionRecord.STAGE_TYPE_REM -> 3
                        SleepSessionRecord.STAGE_TYPE_LIGHT, SleepSessionRecord.STAGE_TYPE_SLEEPING -> 1
                        else -> 0
                    }
                    com.forgequest.app.domain.Seg(code, st.startTime, st.endTime)
                },
            )
        }
        val sleepDays = Readiness.sleepDays(sleep, zone)
        val hrvRecords = read(client, HeartRateVariabilityRmssdRecord::class, from, now)
        val rhrRecords = read(client, RestingHeartRateRecord::class, from, now)
        val respRecords = read(client, RespiratoryRateRecord::class, from, now)

        fun nightOf(d: LocalDate) = sleepDays[d]?.night
        // Each day is worked out once: the week and its 60-day baselines ask for the same days hundreds of times.
        val hrvCache = HashMap<LocalDate, Double?>()
        val respCache = HashMap<LocalDate, Double?>()
        val rhrCache = HashMap<LocalDate, Double?>()
        // During the night (and up to an hour after waking, when Fitbit often files its summary); with no night
        // recorded yet, that morning's readings. Fitbit's own first, like resting heart rate.
        fun hrvFor(d: LocalDate) = hrvCache.getOrPut(d) {
            val n = nightOf(d)
            val morningEnd = d.atTime(14, 0).atZone(zone).toInstant()
            val inWindow = hrvRecords.filter { r -> if (n != null) r.time >= n.start && r.time <= n.end.plusSeconds(3600) else r.time.atZone(zone).toLocalDate() == d && r.time < morningEnd }
            val fitbit = inWindow.filter { it.metadata.dataOrigin.packageName == FITBIT }
            Readiness.median(fitbit.ifEmpty { inWindow }.map { it.heartRateVariabilityMillis })
        }
        fun respFor(d: LocalDate) = respCache.getOrPut(d) {
            val n = nightOf(d)
            Readiness.median(respRecords.filter { r -> if (n != null) r.time >= n.start && r.time <= n.end.plusSeconds(3600) else r.time.atZone(zone).toLocalDate() == d }.map { it.rate })
        }
        fun rhrFor(d: LocalDate) = rhrCache.getOrPut(d) {
            val sameDay = rhrRecords.filter { it.time.atZone(zone).toLocalDate() == d }
            val fitbit = sameDay.filter { it.metadata.dataOrigin.packageName == FITBIT }
            Readiness.median((fitbit.ifEmpty { sameDay }).map { it.beatsPerMinute.toDouble() })
        }
        // Time actually asleep (a split night's gap stays awake time), so heart rate in bed never earns strain.
        val sleepWindows = sleepDays.values.flatMap { sd ->
            val n = sd.night
            (listOf(n.start) + n.gaps.flatMap { listOf(it.first, it.second) } + n.end).chunked(2).map { it[0] to it[1] }
        }

        val s = settings.current()
        val hrMax = Readiness.hrMax(Nutrition.ageFromBirthYear(s.birthYear, today.year))
        val rest30 = Readiness.median((1L..30L).mapNotNull { rhrFor(today.minusDays(it)) }) ?: 65.0

        val workoutWindows = db.backup().sessions().mapNotNull { ss -> ss.finishedAt?.let { Instant.ofEpochMilli(ss.startedAt) to Instant.ofEpochMilli(it) } }
            .filter { it.second.isAfter(today.minusDays(9).atStartOfDay(zone).toInstant()) }
        // Workouts other apps recorded; the ones ForgeQuest itself wrote are already counted above.
        val otherSessions = if (!exerciseOk) emptyList() else suspendRunCatching {
            read(client, ExerciseSessionRecord::class, today.minusDays(9).atStartOfDay(zone).toInstant(), now)
                .filter { it.metadata.dataOrigin.packageName != context.packageName }
        }.getOrDefault(emptyList())
        val otherWindows = otherSessions.map { it.startTime to it.endTime }
        // Strain per day for the last 8 days, read a day at a time so 2-second heart rate never piles up in memory.
        val strainByDay = (0L..8L).associate { back ->
            val d = today.minusDays(back)
            val start = d.atStartOfDay(zone).toInstant()
            val end = if (back == 0L) now else d.plusDays(1).atStartOfDay(zone).toInstant()
            val female = s.bodyType == com.forgequest.app.data.prefs.BodyType.FEMALE
            val result = suspendRunCatching {
                val asleep = sleepWindows.filter { (a, b) -> b > start && a < end }
                val samples = read(client, HeartRateRecord::class, start, end).flatMap { r -> r.samples.map { it.time to it.beatsPerMinute } }
                    .filterNot { (t, _) -> asleep.any { (a, b) -> t >= a && t < b } }
                // Load during ForgeQuest workouts, for "how you earned strain".
                val inWorkout = samples.filter { (t, _) -> workoutWindows.any { (a, b) -> t >= a && t <= b } }
                val inOther = samples.filter { (t, _) -> otherWindows.any { (a, b) -> t >= a && t <= b } && workoutWindows.none { (a, b) -> t >= a && t <= b } }
                DayLoad(Readiness.zoneMinutes(samples, hrMax, rest30), Readiness.trimp(samples, hrMax, rest30, female), Readiness.trimp(inWorkout, hrMax, rest30, female), Readiness.trimp(inOther, hrMax, rest30, female))
            }.getOrDefault(DayLoad(DoubleArray(5), 0.0, 0.0))
            d to result
        }
        fun strainOf(d: LocalDate) = Readiness.strainFromLoad(strainByDay[d]?.load ?: 0.0)

        fun asleepBefore(d: LocalDate) = (3L downTo 1L).map { nightOf(d.minusDays(it))?.asleepMin ?: Readiness.BASE_NEED_MIN }
        fun needFor(d: LocalDate) = Readiness.sleepNeed(strainOf(d.minusDays(1)), asleepBefore(d), sleepDays[d.minusDays(1)]?.napMin ?: 0)
        fun perfFor(d: LocalDate) = nightOf(d)?.let { Readiness.sleepPerformance(it.asleepMin, needFor(d)) }

        // Baselines from the 60 days before the given day.
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

        // Health Connect's own totals merge Google Fit, Fitbit and the phone instead of adding them up. A day carried past
        // midnight counts from when you woke, not from yesterday's midnight.
        val dayStart = if (day < today) nightOf(day)?.end ?: day.atStartOfDay(zone).toInstant() else day.atStartOfDay(zone).toInstant()
        val totals = suspendRunCatching {
            client.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL, TotalCaloriesBurnedRecord.ENERGY_TOTAL), TimeRangeFilter.between(dayStart, now)))
        }.getOrNull()

        // Days with under 1,200 kcal mean the watch was off for part of the day, so they'd drag the average down.
        val kcalDays = suspendRunCatching {
            client.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(
                    setOf(TotalCaloriesBurnedRecord.ENERGY_TOTAL),
                    TimeRangeFilter.between(today.minusDays(14).atStartOfDay(), today.atStartOfDay()), java.time.Period.ofDays(1),
                )
            ).mapNotNull { it.result[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories?.takeIf { k -> k >= 1200 } }
        }.getOrDefault(emptyList())
        val kcalAvg14 = if (kcalDays.size >= 10) kcalDays.average().toInt() else null
        val dailySteps = suspendRunCatching {
            client.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(
                    setOf(StepsRecord.COUNT_TOTAL),
                    TimeRangeFilter.between(today.minusDays(60).atStartOfDay(), today.atStartOfDay()), java.time.Period.ofDays(1),
                )
            ).associate { it.startTime.toLocalDate().toEpochDay() to (it.result[StepsRecord.COUNT_TOTAL] ?: 0L) }
        }.getOrDefault(emptyMap())

        val night = nightOf(day)
        // The day runs from waking to the next sleep, so strain after midnight still adds to it.
        val sinceWake = (0L..java.time.temporal.ChronoUnit.DAYS.between(day, today)).mapNotNull { strainByDay[day.plusDays(it)] }
        val zonesSinceWake = DoubleArray(5) { i -> sinceWake.sumOf { it.zones[i] } }
        val loadSinceWake = sinceWake.sumOf { it.load }
        val strain = Readiness.strainFromLoad(loadSinceWake)
        val workoutShare = if (loadSinceWake > 0) (sinceWake.sumOf { it.workoutLoad } / loadSinceWake).toFloat() else 0f
        val otherShare = if (loadSinceWake > 0) (sinceWake.sumOf { it.otherLoad } / loadSinceWake).toFloat().coerceAtMost(1f - workoutShare) else 0f
        val wokeAt = nightOf(day)?.end ?: day.atStartOfDay(zone).toInstant()
        val otherToday = otherSessions.filter { it.endTime > wokeAt }.sortedBy { it.startTime }
            .map { OtherWorkout(it.exerciseType, it.title?.takeIf { t -> t.isNotBlank() }, ((it.endTime.epochSecond - it.startTime.epochSecond) / 60).toInt()) }
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
            epochDay = day.toEpochDay(),
            recovery = recovery,
            call = Readiness.call(recovery),
            night = night,
            sleepPerf = perf,
            sleepNeedLastNight = needFor(day),
            sleepNeedTonight = Readiness.sleepNeed(strain, (2L downTo 0L).map { nightOf(day.minusDays(it))?.asleepMin ?: Readiness.BASE_NEED_MIN }, sleepDays[day]?.napMin ?: 0),
            debtMin = Readiness.debtMin((2L downTo 0L).map { nightOf(day.minusDays(it))?.asleepMin ?: Readiness.BASE_NEED_MIN }),
            strain = strain,
            zoneMin = zonesSinceWake,
            workoutShare = workoutShare,
            otherShare = otherShare,
            otherWorkouts = otherToday,
            steps = totals?.get(StepsRecord.COUNT_TOTAL) ?: 0L,
            kcalBurned = totals?.get(TotalCaloriesBurnedRecord.ENERGY_TOTAL)?.inKilocalories?.toInt() ?: 0,
            kcalAvg14 = kcalAvg14,
            dailySteps = dailySteps,
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
            (if (d.baselineNights < Readiness.MIN_BASELINE_DAYS) " (still learning their normal)" else "") + "\n" + weekLine(d)
    }

    /** The last 7 days in one short line, so the coach sees a run of poor nights rather than just this morning. */
    private fun weekLine(d: DayReadiness): String {
        val rec = d.week.mapNotNull { it.recovery }
        val sleep = d.week.mapNotNull { it.sleepMin }
        if (rec.size < 2 && sleep.size < 2) return ""
        val parts = buildList {
            if (rec.isNotEmpty()) add("avg recovery ${rec.average().toInt()}%, red (under 34%) on ${rec.count { it < 34 }} days")
            if (sleep.isNotEmpty()) sleep.average().toInt().let { add("avg sleep ${it / 60}h ${it % 60}m") }
            if (d.debtMin > 0) add("sleep debt ${d.debtMin / 60}h ${d.debtMin % 60}m")
            add("avg strain ${"%.1f".format(java.util.Locale.US, d.week.map { it.strain }.average())}/21")
        }
        return "- Fitbit last 7 days: " + parts.joinToString(", ") + "\n"
    }

    private companion object {
        const val FITBIT = "com.fitbit.FitbitMobile"
    }
}
