package com.forgequest.app.domain

import java.time.LocalDate

/**
 * What the last nights looked like, for a readiness sentence that can say why ("two short nights in a row") and not
 * only what to do. Built from [Readiness.nights].
 */
data class SleepContext(
    /** Minutes asleep last night; null when the watch has no night for that day. */
    val lastNightMin: Int?,
    /** Nights in a row, ending with last night, under [SHORT_NIGHT_MIN]. */
    val shortNightsInRow: Int,
    /** Deep sleep as a share of last night's sleep (0 to 1); null without a night. */
    val deepShare: Float?,
) {
    companion object {
        /** Under six hours is a short night for nearly everyone. */
        const val SHORT_NIGHT_MIN = 360

        /** [day] is the morning the night ended on, the key [Readiness.nights] uses. */
        fun from(nights: Map<LocalDate, Night>, day: LocalDate): SleepContext {
            val last = nights[day]
            var short = 0
            var d = day
            while (true) {
                val n = nights[d] ?: break
                if (n.asleepMin >= SHORT_NIGHT_MIN) break
                short++
                d = d.minusDays(1)
            }
            return SleepContext(
                lastNightMin = last?.asleepMin,
                shortNightsInRow = short,
                deepShare = last?.takeIf { it.asleepMin > 0 }?.let { it.deepMin.toFloat() / it.asleepMin },
            )
        }
    }
}

/**
 * What the heart numbers say about today, for a readiness sentence that can give its reason with the person's own
 * numbers ("HRV 24 ms, under your usual 31 ms"). A flag is only set when the trend the score read and the two whole
 * numbers the sentence would show agree: HRV in the score is blended with the week, so last night's number alone can
 * sit on the other side of the usual, and the sentence must never say something the numbers beside it contradict.
 */
data class HeartContext(val hrvLow: Boolean, val hrvHigh: Boolean, val rhrHigh: Boolean) {
    companion object {
        fun from(hrv: Double?, hrvUsual: Double?, hrvTrend: Trend?, rhr: Double?, rhrUsual: Double?, rhrTrend: Trend?): HeartContext {
            val h = hrv?.toInt(); val hu = hrvUsual?.toInt(); val r = rhr?.toInt(); val ru = rhrUsual?.toInt()
            return HeartContext(
                hrvLow = hrvTrend == Trend.WORSE && h != null && hu != null && h < hu,
                hrvHigh = hrvTrend == Trend.BETTER && h != null && hu != null && h > hu,
                rhrHigh = rhrTrend == Trend.WORSE && r != null && ru != null && r > ru,
            )
        }
    }
}

/**
 * The sentence under the readiness verdict on Home. Each has [variants] wordings that take turns, so the same advice does
 * not read the same every morning. The text lives in the UI layer (`ReadinessLine.bodyRes` in ui/EnumLabels.kt).
 */
enum class ReadinessLine(val variants: Int, val arg: LineArg = LineArg.NONE) {
    PEAK(3), READY(3), STEADY(3), EASE_OFF(3), RECOVER(3),
    LEGS_PEAK(3), LEGS_READY(3), LEGS_STEADY(3), LEGS_EASE(3), LEGS_RECOVER(3),
    UPPER_PEAK(3), UPPER_READY(3), UPPER_STEADY(3), UPPER_EASE(3), UPPER_RECOVER(3),
    /** A steady day after two or more short nights: says so, and asks for one set less. */
    STEADY_SHORT_NIGHTS(2),
    /** A steady, ease-off or recover day after a short night: names how long the person slept. */
    STEADY_SHORT_SLEEP(2, LineArg.SLEPT), EASE_SHORT_SLEEP(2, LineArg.SLEPT), RECOVER_SHORT_SLEEP(2, LineArg.SLEPT),
    /** A low day after three or more short nights: the run is the story, not last night alone. */
    EASE_SHORT_RUN(1, LineArg.SHORT_RUN), RECOVER_SHORT_RUN(1, LineArg.SHORT_RUN),
    /** The verdict with its reason: HRV (the heaviest signal) or resting heart rate against the usual. */
    EASE_HRV(2, LineArg.HRV), RECOVER_HRV(2, LineArg.HRV), EASE_RHR(2, LineArg.RHR), RECOVER_RHR(2, LineArg.RHR),
    STEADY_HRV(2, LineArg.HRV), STEADY_RHR(2, LineArg.RHR), READY_HRV(2, LineArg.HRV), PEAK_HRV(2, LineArg.HRV),
    /** A good day after a long night: names the sleep that earned it. */
    PEAK_SLEPT_WELL(2, LineArg.SLEPT), READY_SLEPT_WELL(2, LineArg.SLEPT),
    /** A rest day: never "train as planned". The low ones give their reason when there is one. */
    REST_CHARGED(3), REST_STEADY(2), REST_LOW(3), REST_LOW_SLEEP(2, LineArg.SLEPT), REST_LOW_HRV(2, LineArg.HRV),
    /** The workout is already done: the advice turns to tonight's sleep, with the hours the body needs. */
    DONE_GOOD(3, LineArg.SLEEP_NEED), DONE_LOW(2, LineArg.SLEEP_NEED);

    /** True for the lines that show HRV and its usual; [showsRhr] for resting heart rate. */
    val showsHrv: Boolean get() = arg == LineArg.HRV
    val showsRhr: Boolean get() = arg == LineArg.RHR

    /** The key this line is stored under in the shown-lately history. */
    val historyKind: String get() = "call_$name"
}

/** The number a sentence carries, so the card can fill it in without knowing each line. */
enum class LineArg { NONE, SLEPT, HRV, RHR, SLEEP_NEED, SHORT_RUN }

/** What today is in the plan: a workout still to do, a rest (or excused) day, or a workout already done. */
enum class TodayState { TRAIN, REST, DONE }

/** Seven and a half hours or more is a long night for the "you slept well" sentences. */
const val LONG_NIGHT_MIN = 450

/**
 * Which sentence fits today. A finished workout and a rest day come first ([state]), so the advice never asks for
 * training that is done or not planned. On a training day sleep comes first when it explains the verdict, because it is
 * the part the person can act on tonight; then the heart number behind the verdict ([HeartContext], HRV before resting
 * heart rate, the order of their weights in the score); then a long night behind a good day; then the plan-aware advice
 * for a leg or upper-body day; then the plain one for [call]. A peak or ready leg day keeps its leg sentence.
 */
fun readinessLine(call: DayCall, focus: TodayFocus?, sleep: SleepContext?, heart: HeartContext? = null, state: TodayState = TodayState.TRAIN): ReadinessLine {
    val low = call == DayCall.EASE_OFF || call == DayCall.RECOVER
    val lastNight = sleep?.lastNightMin
    val shortNight = lastNight != null && lastNight < SleepContext.SHORT_NIGHT_MIN
    if (state == TodayState.DONE) return if (low) ReadinessLine.DONE_LOW else ReadinessLine.DONE_GOOD
    if (state == TodayState.REST) return when {
        low && shortNight -> ReadinessLine.REST_LOW_SLEEP
        low && heart?.hrvLow == true -> ReadinessLine.REST_LOW_HRV
        low -> ReadinessLine.REST_LOW
        call == DayCall.STEADY -> ReadinessLine.REST_STEADY
        else -> ReadinessLine.REST_CHARGED
    }
    if (sleep != null) {
        if (low && sleep.shortNightsInRow >= 3) return if (call == DayCall.RECOVER) ReadinessLine.RECOVER_SHORT_RUN else ReadinessLine.EASE_SHORT_RUN
        if (call == DayCall.STEADY && sleep.shortNightsInRow >= 2) return ReadinessLine.STEADY_SHORT_NIGHTS
        if (shortNight) when (call) {
            DayCall.RECOVER -> return ReadinessLine.RECOVER_SHORT_SLEEP
            DayCall.EASE_OFF -> return ReadinessLine.EASE_SHORT_SLEEP
            DayCall.STEADY -> return ReadinessLine.STEADY_SHORT_SLEEP
            else -> Unit
        }
    }
    if (heart != null) {
        when (call) {
            DayCall.EASE_OFF -> if (heart.hrvLow) return ReadinessLine.EASE_HRV else if (heart.rhrHigh) return ReadinessLine.EASE_RHR
            DayCall.RECOVER -> if (heart.hrvLow) return ReadinessLine.RECOVER_HRV else if (heart.rhrHigh) return ReadinessLine.RECOVER_RHR
            DayCall.STEADY -> if (heart.hrvLow) return ReadinessLine.STEADY_HRV else if (heart.rhrHigh) return ReadinessLine.STEADY_RHR
            DayCall.READY -> if (heart.hrvHigh && focus != TodayFocus.LEGS) return ReadinessLine.READY_HRV
            DayCall.PEAK -> if (heart.hrvHigh && focus != TodayFocus.LEGS) return ReadinessLine.PEAK_HRV
        }
    }
    if (focus == null && lastNight != null && lastNight >= LONG_NIGHT_MIN) {
        if (call == DayCall.PEAK) return ReadinessLine.PEAK_SLEPT_WELL
        if (call == DayCall.READY) return ReadinessLine.READY_SLEPT_WELL
    }
    return when (Readiness.adviceFor(call, focus)) {
        DayAdvice.LEGS_PEAK -> ReadinessLine.LEGS_PEAK
        DayAdvice.LEGS_STEADY -> ReadinessLine.LEGS_STEADY
        DayAdvice.LEGS_EASE -> ReadinessLine.LEGS_EASE
        DayAdvice.LEGS_RECOVER -> ReadinessLine.LEGS_RECOVER
        DayAdvice.UPPER_EASE -> ReadinessLine.UPPER_EASE
        DayAdvice.UPPER_RECOVER -> ReadinessLine.UPPER_RECOVER
        null -> when (call) {
            DayCall.PEAK -> if (focus == TodayFocus.UPPER) ReadinessLine.UPPER_PEAK else ReadinessLine.PEAK
            DayCall.READY -> when (focus) { TodayFocus.LEGS -> ReadinessLine.LEGS_READY; TodayFocus.UPPER -> ReadinessLine.UPPER_READY; null -> ReadinessLine.READY }
            DayCall.STEADY -> if (focus == TodayFocus.UPPER) ReadinessLine.UPPER_STEADY else ReadinessLine.STEADY
            DayCall.EASE_OFF -> ReadinessLine.EASE_OFF
            DayCall.RECOVER -> ReadinessLine.RECOVER
        }
    }
}

/**
 * The wording of [kind] for [today]: the one already shown today if there is one (the sentence must not change every
 * time Home is opened), else the one shown longest ago ([leastRecentVariant]). The second value is true when it is new
 * today and the caller should store it with [appendHistory].
 */
fun dailyVariant(kind: String, variants: Int, history: String, today: Long): Pair<Int, Boolean> {
    val entries = parseHistory(history)
    entries.lastOrNull { it.kind == kind && it.day == today && it.variant in 0 until variants }?.let { return it.variant to false }
    return leastRecentVariant(kind, variants, entries) to true
}
