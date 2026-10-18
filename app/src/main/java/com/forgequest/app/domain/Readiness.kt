package com.forgequest.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** One stretch of a sleep stage: 0 awake, 1 light, 2 deep, 3 REM. Kept for the hypnogram. */
data class Seg(val stage: Int, val start: Instant, val end: Instant)

/** One sleep record as Health Connect gives it; several apps may write the same night. */
data class SleepPiece(
    val start: Instant,
    val end: Instant,
    val deepMin: Int,
    val remMin: Int,
    val lightMin: Int,
    val awakeMin: Int,
    /** Minutes asleep when the record has no stages. */
    val unstagedMin: Int,
    val fromFitbit: Boolean,
    val segments: List<Seg> = emptyList(),
) {
    val asleepMin get() = deepMin + remMin + lightMin + unstagedMin
}

/** A night after duplicates are dropped and split chunks (2:00–6:00 then 8:00–11:30) are joined. */
data class Night(val start: Instant, val end: Instant, val deepMin: Int, val remMin: Int, val lightMin: Int, val awakeMin: Int, val asleepMin: Int, val segments: List<Seg> = emptyList()) {
    val inBedMin get() = ((end.epochSecond - start.epochSecond) / 60).toInt()
}

/** The day's main night plus any naps that ended the same day. */
data class SleepDay(val night: Night, val napMin: Int)

data class Baseline(val mean: Double, val sd: Double, val days: Int)

enum class DayCall { PEAK, READY, STEADY, EASE_OFF, RECOVER }
enum class StrainBand { LIGHT, MODERATE, HIGH, ALL_OUT }

/**
 * Whoop-style recovery, sleep and strain from Fitbit data. Whoop doesn't publish its formulas, so these follow its
 * documented inputs and the published methods behind them:
 * - HRV as ln RMSSD with a 7-day rolling average against the person's own baseline, ignoring changes smaller than
 *   the "smallest worthwhile change" (AIS HRV guidelines; Plews & Buchheit).
 * - Sleep performance = hours slept ÷ hours needed, need = base + strain + sleep debt − naps (Whoop).
 * - Strain = Banister TRIMP (every minute above resting, weighted exponentially by heart-rate reserve), on a 0–21
 *   curve that gets harder to climb (Whoop's scale). Walking counts a little, hard work a lot.
 */
object Readiness {
    private const val JOIN_GAP_SECONDS = 3 * 3600L
    const val BASE_NEED_MIN = 480
    const val MIN_BASELINE_DAYS = 4

    // ---------- Sleep ----------

    /** Keeps one copy of each stretch of sleep (Fitbit's first), joins split chunks, and files each night under the day it ended. */
    fun sleepDays(pieces: List<SleepPiece>, zone: ZoneId): Map<LocalDate, SleepDay> {
        val kept = mutableListOf<SleepPiece>()
        for (p in pieces.sortedWith(compareByDescending<SleepPiece> { it.fromFitbit }.thenBy { it.start })) {
            val dup = kept.any { k ->
                val overlap = minOf(k.end, p.end).epochSecond - maxOf(k.start, p.start).epochSecond
                overlap > 0.5 * (p.end.epochSecond - p.start.epochSecond)
            }
            if (!dup) kept += p
        }
        val groups = mutableListOf<MutableList<SleepPiece>>()
        for (p in kept.sortedBy { it.start }) {
            val last = groups.lastOrNull()
            if (last != null && p.start.epochSecond - last.maxOf { it.end.epochSecond } <= JOIN_GAP_SECONDS) last += p else groups += mutableListOf(p)
        }
        return groups.map { g ->
            Night(g.minOf { it.start }, g.maxOf { it.end }, g.sumOf { it.deepMin }, g.sumOf { it.remMin }, g.sumOf { it.lightMin }, g.sumOf { it.awakeMin }, g.sumOf { it.asleepMin }, g.flatMap { it.segments }.sortedBy { it.start })
        }.groupBy { it.end.atZone(zone).toLocalDate() }.mapValues { (_, list) ->
            // The biggest sleep that ended that day is the night; the rest were naps.
            val night = list.maxBy { it.asleepMin }
            SleepDay(night, list.filter { it !== night }.sumOf { it.asleepMin })
        }
    }

    fun nights(pieces: List<SleepPiece>, zone: ZoneId): Map<LocalDate, Night> = sleepDays(pieces, zone).mapValues { it.value.night }

    /** Extra sleep a hard day asks for: about 4 minutes per strain point above 10. */
    fun strainNeedMin(strain: Double): Int = (maxOf(0.0, strain - 10) * 4).roundToInt()

    /** A third of the last three nights' shortfall carries over, capped at two hours. */
    fun debtMin(previousAsleep: List<Int>): Int = (previousAsleep.takeLast(3).sumOf { maxOf(0, BASE_NEED_MIN - it) } / 3.0).roundToInt().coerceAtMost(120)

    fun sleepNeed(strainBefore: Double, previousAsleep: List<Int>, napMin: Int): Int =
        (BASE_NEED_MIN + strainNeedMin(strainBefore) + debtMin(previousAsleep) - napMin).coerceIn(360, 720)

    /** 0–100: hours slept against hours needed, like Whoop's sleep performance. */
    fun sleepPerformance(asleepMin: Int, needMin: Int): Int = if (needMin <= 0) 0 else (100.0 * asleepMin / needMin).roundToInt().coerceIn(0, 100)

    // ---------- Recovery ----------

    fun baseline(values: List<Double>, floorSd: Double): Baseline? {
        if (values.size < MIN_BASELINE_DAYS) return null
        val mean = values.average()
        val sd = sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
        return Baseline(mean, maxOf(sd, floorSd), values.size)
    }

    /** ln RMSSD of tonight blended with the 7-day rolling average, which tracks real change better than one night. */
    fun hrvSignal(tonightMs: Double?, last7Ms: List<Double>): Double? {
        val logs = last7Ms.filter { it > 0 }.map { ln(it) }
        val tonight = tonightMs?.takeIf { it > 0 }?.let { ln(it) }
        return when {
            tonight != null && logs.isNotEmpty() -> 0.6 * tonight + 0.4 * logs.average()
            tonight != null -> tonight
            logs.isNotEmpty() -> logs.average()
            else -> null
        }
    }

    /** z-score with a dead zone of half a standard deviation: wiggles inside the normal range count as zero. */
    fun zWithSwc(diff: Double, sd: Double): Double {
        val swc = 0.5 * sd
        return if (abs(diff) <= swc) 0.0 else (diff - (if (diff > 0) swc else -swc)) / sd
    }

    /** 1–99 %. HRV weighs most, then resting heart rate, then sleep, then breathing rate. A normal day lands near 60 %. */
    fun recovery(hrvLn: Double?, rhr: Double?, resp: Double?, sleepPerf: Int?, hrvBase: Baseline?, rhrBase: Baseline?, respBase: Baseline?): Int {
        var sum = 0.0
        var weight = 0.0
        fun add(w: Double, z: Double) { sum += w * z.coerceIn(-3.0, 3.0); weight += w }
        if (hrvLn != null && hrvBase != null) add(0.50, zWithSwc(hrvLn - hrvBase.mean, hrvBase.sd))
        if (rhr != null && rhrBase != null) add(0.25, zWithSwc(rhrBase.mean - rhr, rhrBase.sd))
        if (resp != null && respBase != null) add(0.10, zWithSwc(respBase.mean - resp, respBase.sd))
        if (sleepPerf != null) add(0.15, (sleepPerf - 85) / 15.0)
        val z = if (weight > 0) sum / weight else 0.0
        return (100 / (1 + exp(-(1.4 * z + 0.4)))).roundToInt().coerceIn(1, 99)
    }

    fun call(recovery: Int): DayCall = when {
        recovery >= 85 -> DayCall.PEAK
        recovery >= 67 -> DayCall.READY
        recovery >= 50 -> DayCall.STEADY
        recovery >= 34 -> DayCall.EASE_OFF
        else -> DayCall.RECOVER
    }

    /** Whoop's recovery colours: green 67+, yellow 34–66, red below. */
    fun recoveryZone(recovery: Int): Int = when { recovery >= 67 -> 2; recovery >= 34 -> 1; else -> 0 }

    // ---------- Strain ----------

    fun hrMax(age: Int): Int = if (age in 10..100) 220 - age else 190

    /**
     * Minutes in five heart-rate-reserve zones (20/40/60/75/90 % of the gap between resting and max): light movement,
     * moderate, hard, very hard, max. Gaps over 30 s count as 30 s so a lost signal doesn't inflate them.
     */
    fun zoneMinutes(samples: List<Pair<Instant, Long>>, hrMax: Int, hrRest: Double): DoubleArray {
        val zones = DoubleArray(5)
        val reserve = (hrMax - hrRest).coerceAtLeast(40.0)
        val sorted = samples.sortedBy { it.first }
        for (i in sorted.indices) {
            val next = sorted.getOrNull(i + 1)?.first ?: break
            val seconds = minOf(30L, next.epochSecond - sorted[i].first.epochSecond).coerceAtLeast(0)
            val pct = (sorted[i].second - hrRest) / reserve
            // Same floor as the strain load (20 % of reserve), so the zones explain the number above them.
            val zone = when { pct >= 0.9 -> 4; pct >= 0.75 -> 3; pct >= 0.6 -> 2; pct >= 0.4 -> 1; pct >= 0.2 -> 0; else -> -1 }
            if (zone >= 0) zones[zone] += seconds / 60.0
        }
        return zones
    }

    /**
     * Banister TRIMP: minutes × heart-rate reserve × 0.64·e^(1.92·reserve) (0.86·e^(1.67·reserve) for women). A quiet
     * day with some walking lands near 90, an hour of hard training adds about 140.
     */
    fun trimp(samples: List<Pair<Instant, Long>>, hrMax: Int, hrRest: Double, female: Boolean = false): Double {
        val reserve = (hrMax - hrRest).coerceAtLeast(40.0)
        val (a, b) = if (female) 0.86 to 1.67 else 0.64 to 1.92
        val sorted = samples.sortedBy { it.first }
        var load = 0.0
        for (i in sorted.indices) {
            val next = sorted.getOrNull(i + 1)?.first ?: break
            val minutes = minOf(30L, next.epochSecond - sorted[i].first.epochSecond).coerceAtLeast(0) / 60.0
            val r = ((sorted[i].second - hrRest) / reserve).coerceIn(0.0, 1.0)
            // Sitting a little above resting isn't effort; below 20 % of reserve nothing counts.
            if (r < 0.2) continue
            load += minutes * r * a * exp(b * r)
        }
        return load
    }

    /** 0–21 on a curve that gets harder to climb: a day with an hour of walking about 5–6, a hard training day 15–17. */
    fun strainFromLoad(load: Double): Double = 21 * (1 - exp(-load / 110))

    fun band(strain: Double): StrainBand = when {
        strain >= 18 -> StrainBand.ALL_OUT
        strain >= 14 -> StrainBand.HIGH
        strain >= 10 -> StrainBand.MODERATE
        else -> StrainBand.LIGHT
    }

    /** Today's strain target, matching the call so "go lighter" never comes with a hard target. */
    fun strainTarget(recovery: Int): ClosedFloatingPointRange<Double> = when (call(recovery)) {
        DayCall.PEAK -> 15.0..18.0
        DayCall.READY -> 13.0..16.0
        DayCall.STEADY -> 10.0..13.0
        DayCall.EASE_OFF -> 7.0..10.0
        DayCall.RECOVER -> 4.0..7.0
    }

    fun median(values: List<Double>): Double? = values.sorted().let { if (it.isEmpty()) null else if (it.size % 2 == 1) it[it.size / 2] else (it[it.size / 2 - 1] + it[it.size / 2]) / 2 }
}
