package com.forgequest.app.domain

/** The one-time "make N your weekly target?" offer on the Home streak card. Web: domain/target-nudge.ts. */
object TargetNudge {
    /**
     * The weekly number to offer, or null. Looks at the last two finished weeks: when in both of them the person trained at
     * least twice but fewer times than [target], the lower of the two counts is offered. Weeks before the person's first
     * week do not count.
     */
    fun offer(workoutDays: Set<Long>, target: Int, startEpochDay: Long, today: Long): Int? {
        val thisWeek = Dates.weekStart(today)
        val weeks = listOf(thisWeek - 14, thisWeek - 7)
        if (weeks[0] < Dates.weekStart(startEpochDay)) return null
        val done = weeks.map { ws -> workoutDays.count { it in ws..ws + 6 } }
        return if (done.all { it >= 2 && it < target }) done.min() else null
    }

    /** Whether the offer is on Home right now: once, and never again after any answer. */
    fun show(answered: Boolean, offer: Int?): Boolean = !answered && offer != null
}
