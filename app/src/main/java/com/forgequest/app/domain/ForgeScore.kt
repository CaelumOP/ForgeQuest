package com.forgequest.app.domain

import kotlin.math.roundToInt

/** What a friend sees on the Friends board this week. Same rules as web/src/domain/forge-score.ts (shared vectors). */
data class ForgeWeek(
    val weekStart: Long,
    val done: Int,
    val due: Int,
    val score: Int,
    val badge: String,
    val proteinDays: Int,
    val target: Int = due,
    val prCount: Int = 0,
)

@kotlinx.serialization.Serializable
data class ForgeScoreBreakdown(
    val base: Int,
    val prBonus: Int,
    val proteinBonus: Int,
    val extraBonus: Int,
    val bonus: Int,
    val score: Int,
)

/** Event timestamps are Unix seconds, and all events belong to the same Sunday week. */
data class ForgeScoreEvent(val at: Long, val workoutDay: Long? = null, val proteinDay: Long? = null, val pr: Boolean = false)

object ForgeScore {
    fun reachedAt(target: Int, events: List<ForgeScoreEvent>, weekStartedAt: Long): Long {
        val workouts = mutableSetOf<Long>()
        val protein = mutableSetOf<Long>()
        var prs = 0
        var score = 0
        var reached = weekStartedAt
        for ((at, group) in events.groupBy { it.at }.toSortedMap()) {
            for (event in group) {
                event.workoutDay?.let { workouts.add(it) }
                event.proteinDay?.let { protein.add(it) }
                if (event.pr) prs++
            }
            val next = breakdown(workouts.size, target, prs, protein.size).score
            if (next != score) reached = at
            score = next
        }
        return reached
    }

    fun breakdown(done: Int, target: Int, prCount: Int = 0, proteinDays: Int = 0): ForgeScoreBreakdown {
        require(done >= 0 && target >= 0 && prCount >= 0 && proteinDays >= 0)
        val base = if (target == 0) 0 else (100.0 * minOf(done, target) / target).roundToInt()
        val prBonus = minOf(prCount, 2) * 5
        val proteinBonus = minOf(proteinDays, 3) * 2
        val extraBonus = if (done > target) 5 else 0
        val bonus = minOf(20, prBonus + proteinBonus + extraBonus)
        return ForgeScoreBreakdown(base, prBonus, proteinBonus, extraBonus, bonus, base + bonus)
    }

    const val TRAINED = "trained"
    const val ON_TRACK = "on_track"
    const val SLACKING = "slacking"
    const val REST = "rest"

    /**
     * Forge Score for the Sunday week holding [today], with positive-only bonuses.
     * [scheduledDays]: ISO weekdays (1 = Monday .. 7 = Sunday) the active plan trains on; empty with no plan.
     * [trainedDays]: epoch days on which a workout was finished. [proteinHitDays]: epoch days at the protein goal.
     */
    fun compute(
        today: Long, scheduledDays: Set<Int>, trainedDays: Set<Long>, proteinHitDays: Set<Long> = emptySet(),
        weeklyTargetOverride: Int = 0, prCount: Int = 0,
    ): ForgeWeek {
        val weekStart = Dates.weekStart(today)
        val days = weekStart..today
        val trainedToday = today in trainedDays
        val done = days.count { it in trainedDays }
        val target = if (weeklyTargetOverride > 0) weeklyTargetOverride.coerceAtMost(7)
            else scheduledDays.count { it in 1..7 }
        val protein = days.count { it in proteinHitDays }
        val score = breakdown(done, target, prCount, protein).score
        val badge = when {
            trainedToday -> TRAINED
            Dates.dayOfWeek(today) !in scheduledDays -> REST
            score >= 60 -> ON_TRACK
            else -> SLACKING
        }
        return ForgeWeek(weekStart, done, target, score, badge, protein, target, prCount)
    }
}
