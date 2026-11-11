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
 * The sentence under the readiness verdict on Home. Each has [variants] wordings that take turns, so the same advice does
 * not read the same every morning. The text lives in the UI layer (`ReadinessLine.bodyRes` in ui/EnumLabels.kt).
 */
enum class ReadinessLine(val variants: Int) {
    PEAK(3), READY(3), STEADY(3), EASE_OFF(3), RECOVER(3),
    LEGS_PEAK(3), LEGS_STEADY(3), LEGS_EASE(3), LEGS_RECOVER(3), UPPER_EASE(3), UPPER_RECOVER(3),
    /** A steady day after two or more short nights: says so, and asks for one set less. */
    STEADY_SHORT_NIGHTS(1),
    /** An ease-off day after a short night: names how long the person slept. */
    EASE_SHORT_SLEEP(1),
    /** A recover day after a short night: names how long the person slept and asks for rest. */
    RECOVER_SHORT_SLEEP(1);

    /** The key this line is stored under in the shown-lately history. */
    val historyKind: String get() = "call_$name"
}

/**
 * Which sentence fits today. Sleep comes first when it explains the verdict, because it is the part the person can act on
 * tonight; then the plan-aware advice for a leg or upper-body day ([Readiness.adviceFor]); then the plain one for [call].
 */
fun readinessLine(call: DayCall, focus: TodayFocus?, sleep: SleepContext?): ReadinessLine {
    if (sleep != null) {
        if (call == DayCall.STEADY && sleep.shortNightsInRow >= 2) return ReadinessLine.STEADY_SHORT_NIGHTS
        if (call == DayCall.RECOVER && (sleep.lastNightMin ?: Int.MAX_VALUE) < SleepContext.SHORT_NIGHT_MIN) return ReadinessLine.RECOVER_SHORT_SLEEP
        if (call == DayCall.EASE_OFF && (sleep.lastNightMin ?: Int.MAX_VALUE) < SleepContext.SHORT_NIGHT_MIN) return ReadinessLine.EASE_SHORT_SLEEP
    }
    return when (Readiness.adviceFor(call, focus)) {
        DayAdvice.LEGS_PEAK -> ReadinessLine.LEGS_PEAK
        DayAdvice.LEGS_STEADY -> ReadinessLine.LEGS_STEADY
        DayAdvice.LEGS_EASE -> ReadinessLine.LEGS_EASE
        DayAdvice.LEGS_RECOVER -> ReadinessLine.LEGS_RECOVER
        DayAdvice.UPPER_EASE -> ReadinessLine.UPPER_EASE
        DayAdvice.UPPER_RECOVER -> ReadinessLine.UPPER_RECOVER
        null -> when (call) {
            DayCall.PEAK -> ReadinessLine.PEAK
            DayCall.READY -> ReadinessLine.READY
            DayCall.STEADY -> ReadinessLine.STEADY
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
