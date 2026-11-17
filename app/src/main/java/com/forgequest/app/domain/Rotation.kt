package com.forgequest.app.domain

import com.forgequest.app.data.ActivePlan
import com.forgequest.app.data.PlanDayWithItems
import com.forgequest.app.data.db.PlanEntity
import com.forgequest.app.data.db.SessionEntity

enum class ScheduleMode {
    WEEKDAY,
    ROTATION;

    companion object {
        fun parse(raw: String?): ScheduleMode =
            if (raw.equals(ROTATION.name, ignoreCase = true)) ROTATION else WEEKDAY
    }
}

/**
 * Pure scheduling rules for WEEKDAY (fixed day-of-week) and ROTATION (Push -> Pull -> Legs queue) plans.
 *
 * Cursor lifecycle:
 * - Advances atomically inside finishSession exactly once when a qualifying (non-quick, previously unfinished,
 *   not discarded) session belonging to a ROTATION plan finishes.
 * - Discard, quick workouts (planDayId == null), repeated finish calls, and backup/undo restores do not advance it.
 * - Plan edits clamp/wrap the cursor into the updated queue length.
 * - "Do missed workout today" is strictly a user-triggered suggestion in WEEKDAY mode; never mutates the plan.
 */
object Rotation {

    fun queue(plan: ActivePlan): List<PlanDayWithItems> =
        plan.days
            .filter { !it.day.isRest && it.items.isNotEmpty() }
            .sortedBy { it.day.dayOfWeek }

    fun normalizeCursor(cursor: Int, queueSize: Int): Int {
        if (queueSize <= 0) return 0
        val mod = cursor % queueSize
        return if (mod < 0) mod + queueSize else mod
    }

    /** Today's due workout day: day-of-week lookup in WEEKDAY mode, or queue[cursor] in ROTATION mode. */
    fun dueDay(plan: ActivePlan?, todayDayOfWeek: Int): PlanDayWithItems? {
        if (plan == null) return null
        if (ScheduleMode.parse(plan.plan.scheduleMode) == ScheduleMode.WEEKDAY) {
            return plan.day(todayDayOfWeek)?.takeIf { !it.day.isRest && it.items.isNotEmpty() }
        }
        val q = queue(plan)
        if (q.isEmpty()) return null
        return q[normalizeCursor(plan.plan.rotationCursor, q.size)]
    }

    /**
     * Next rotation cursor when [session] finishes.
     * Returns null when the plan's cursor must NOT change (WEEKDAY plan, quick workout, repeated finish,
     * session belongs to another plan, or the session's day was removed/turned into rest).
     */
    fun cursorOnFinish(
        plan: PlanEntity,
        queueDayIds: List<Long>,
        session: SessionEntity,
        wasAlreadyFinished: Boolean,
    ): Int? {
        if (wasAlreadyFinished) return null
        if (ScheduleMode.parse(plan.scheduleMode) != ScheduleMode.ROTATION) return null
        val planDayId = session.planDayId ?: return null
        if (queueDayIds.isEmpty()) return null
        val idx = queueDayIds.indexOf(planDayId)
        if (idx < 0) return null
        return (idx + 1) % queueDayIds.size
    }

    /** Re-normalizes the cursor after a plan edit changes the number of training days. */
    fun cursorAfterPlanEdit(currentCursor: Int, newQueueSize: Int): Int =
        normalizeCursor(currentCursor, newQueueSize)

    /**
     * Yesterday's missed planned workout in WEEKDAY mode, offered as a one-tap user action ("Do missed workout today").
     * Returns null when in ROTATION mode, today already has a finished workout, today is excused, yesterday was not
     * a training day, or yesterday already had a finished workout or excuse tag.
     */
    fun missedYesterday(
        plan: ActivePlan?,
        sessions: List<SessionEntity>,
        dayTags: Map<Long, DayTag>,
        todayEpochDay: Long,
    ): PlanDayWithItems? {
        if (plan == null || ScheduleMode.parse(plan.plan.scheduleMode) != ScheduleMode.WEEKDAY) return null
        if (todayEpochDay in dayTags) return null
        val finishedDays = sessions.filter { it.finishedAt != null }.map { it.epochDay }.toSet()
        if (todayEpochDay in finishedDays) return null
        val yesterday = todayEpochDay - 1
        if (yesterday in finishedDays || yesterday in dayTags) return null
        val yesterdayDow = Dates.dayOfWeek(yesterday)
        return plan.day(yesterdayDow)?.takeIf { !it.day.isRest && it.items.isNotEmpty() }
    }
}
