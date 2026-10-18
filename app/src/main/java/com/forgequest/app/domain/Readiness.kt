package com.forgequest.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sqrt

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
) {
    val asleepMin get() = deepMin + remMin + lightMin + unstagedMin
}

/** A night after duplicates are dropped and split chunks (2:00–6:00 then 8:00–11:30) are joined. */
data class Night(val start: Instant, val end: Instant, val deepMin: Int, val remMin: Int, val lightMin: Int, val awakeMin: Int, val asleepMin: Int) {
    val inBedMin get() = ((end.epochSecond - start.epochSecond) / 60).toInt()
}

data class Baseline(val mean: Double, val sd: Double, val days: Int)

enum class DayCall { PEAK, READY, STEADY, EASE_OFF, RECOVER }

/**
 * Whoop-style recovery, sleep and strain from Fitbit data. Our own estimates, not Whoop's formulas: each signal
 * is compared with the person's own last 30 days, so "good" means good for them.
 */
object Readiness {
    /** Chunks closer than this belong to the same night. */
    private const val JOIN_GAP_SECONDS = 3 * 3600L
    const val SLEEP_NEED_MIN = 480
    const val MIN_BASELINE_DAYS = 4

    /** Keeps one copy of each stretch of sleep (Fitbit's first), joins split chunks, and files each night under the day it ended. */
    fun nights(pieces: List<SleepPiece>, zone: ZoneId): Map<LocalDate, Night> {
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
        // A day's night is the biggest sleep that ended that day; naps the same day don't replace it.
        return groups.map { g ->
            Night(g.minOf { it.start }, g.maxOf { it.end }, g.sumOf { it.deepMin }, g.sumOf { it.remMin }, g.sumOf { it.lightMin }, g.sumOf { it.awakeMin }, g.sumOf { it.asleepMin })
        }.groupBy { it.end.atZone(zone).toLocalDate() }.mapValues { (_, list) -> list.maxBy { it.asleepMin } }
    }

    /** 0–100: mostly hours against need, plus how much of the time in bed was sleep and how much was deep + REM. */
    fun sleepScore(n: Night, needMin: Int = SLEEP_NEED_MIN): Int {
        if (n.asleepMin <= 0) return 0
        val hours = minOf(1.0, n.asleepMin.toDouble() / needMin)
        val efficiency = if (n.inBedMin > 0) minOf(1.0, n.asleepMin.toDouble() / n.inBedMin) else 1.0
        val staged = n.deepMin + n.remMin + n.lightMin
        val restorative = if (staged > 0) minOf(1.0, (n.deepMin + n.remMin).toDouble() / staged / 0.40) else 0.75
        return ((0.65 * hours + 0.15 * efficiency + 0.20 * restorative) * 100).roundToInt().coerceIn(0, 100)
    }

    fun baseline(values: List<Double>, floorSd: Double): Baseline? {
        if (values.size < MIN_BASELINE_DAYS) return null
        val mean = values.average()
        val sd = sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
        return Baseline(mean, maxOf(sd, floorSd), values.size)
    }

    /**
     * 1–99 %. Higher HRV, lower resting heart rate and lower breathing rate than usual all count for you; sleep adds
     * a little. A normal day lands around 60 %. Missing signals are simply left out.
     */
    fun recovery(hrv: Double?, rhr: Double?, resp: Double?, sleepScore: Int?, hrvBase: Baseline?, rhrBase: Baseline?, respBase: Baseline?): Int {
        var sum = 0.0
        var weight = 0.0
        fun add(w: Double, z: Double) { sum += w * z.coerceIn(-3.0, 3.0); weight += w }
        if (hrv != null && hrvBase != null) add(0.50, (hrv - hrvBase.mean) / hrvBase.sd)
        if (rhr != null && rhrBase != null) add(0.25, (rhrBase.mean - rhr) / rhrBase.sd)
        if (resp != null && respBase != null) add(0.10, (respBase.mean - resp) / respBase.sd)
        if (sleepScore != null) add(0.15, (sleepScore - 70) / 15.0)
        val z = if (weight > 0) sum / weight else 0.0
        return (100 / (1 + exp(-(1.3 * z + 0.4)))).roundToInt().coerceIn(1, 99)
    }

    /** Minutes in the five heart-rate zones (50/60/70/80/90 % of max). Gaps over 30 s count as 30 s so a lost signal doesn't inflate them. */
    fun zoneMinutes(samples: List<Pair<Instant, Long>>, hrMax: Int): DoubleArray {
        val zones = DoubleArray(5)
        val sorted = samples.sortedBy { it.first }
        for (i in sorted.indices) {
            val next = sorted.getOrNull(i + 1)?.first ?: break
            val seconds = minOf(30L, next.epochSecond - sorted[i].first.epochSecond).coerceAtLeast(0)
            val pct = sorted[i].second.toDouble() / hrMax
            val zone = when { pct >= 0.9 -> 4; pct >= 0.8 -> 3; pct >= 0.7 -> 2; pct >= 0.6 -> 1; pct >= 0.5 -> 0; else -> -1 }
            if (zone >= 0) zones[zone] += seconds / 60.0
        }
        return zones
    }

    /** 0–21 like Whoop: zone minutes weighted 1–5, on a curve that gets harder to climb the higher you go. */
    fun strain(zoneMinutes: DoubleArray): Double {
        val load = zoneMinutes.withIndex().sumOf { (i, m) -> m * (i + 1) }
        return 21 * (1 - exp(-load / 250))
    }

    fun hrMax(age: Int): Int = if (age in 10..100) 220 - age else 190

    fun call(recovery: Int): DayCall = when {
        recovery >= 85 -> DayCall.PEAK
        recovery >= 67 -> DayCall.READY
        recovery >= 50 -> DayCall.STEADY
        recovery >= 34 -> DayCall.EASE_OFF
        else -> DayCall.RECOVER
    }

    fun median(values: List<Double>): Double? = values.sorted().let { if (it.isEmpty()) null else if (it.size % 2 == 1) it[it.size / 2] else (it[it.size / 2 - 1] + it[it.size / 2]) / 2 }
}
