package com.forgequest.app.domain

import kotlin.math.max
import kotlin.math.roundToInt

/** What a friend sees on the Friends board this week. Same rules as web/src/domain/forge-score.ts (shared vectors). */
data class ForgeWeek(
    val weekStart: Long,
    val done: Int,
    val due: Int,
    val score: Int,
    val badge: String,
    val proteinDays: Int,
)

object ForgeScore {
    const val TRAINED = "trained"
    const val ON_TRACK = "on_track"
    const val SLACKING = "slacking"
    const val REST = "rest"

    /**
     * Forge Score for the Sunday week holding [today], from workouts only (food never subtracts).
     * [scheduledDays]: ISO weekdays (1 = Monday .. 7 = Sunday) the active plan trains on; empty with no plan.
     * [trainedDays]: epoch days on which a plan workout was finished. [proteinHitDays]: epoch days at the protein goal.
     */
    fun compute(today: Long, scheduledDays: Set<Int>, trainedDays: Set<Long>, proteinHitDays: Set<Long> = emptySet()): ForgeWeek {
        val weekStart = Dates.weekStart(today)
        val days = weekStart..today
        val trainedToday = today in trainedDays
        val done = days.count { it in trainedDays }
        val dueBeforeToday = (weekStart until today).count { Dates.dayOfWeek(it) in scheduledDays }
        val due = max(dueBeforeToday + if (trainedToday) 1 else 0, done)
        val score = if (due == 0) 100 else (100.0 * done / due).roundToInt()
        val badge = when {
            trainedToday -> TRAINED
            Dates.dayOfWeek(today) !in scheduledDays -> REST
            score >= 60 -> ON_TRACK
            else -> SLACKING
        }
        return ForgeWeek(weekStart, done, due, score, badge, days.count { it in proteinHitDays })
    }
}
