package com.forgequest.app.domain

/** When a workout counts as ended. Web: domain/workout-end.ts. */
object WorkoutEnd {
    const val IDLE_MS = 20 * 60_000L
    const val GRACE_MS = 2 * 60_000L

    /** [now], unless the last set was ticked more than 20 minutes ago: then two minutes after that set. */
    fun endTime(now: Long, lastDoneAt: Long?): Long =
        if (lastDoneAt != null && now - lastDoneAt > IDLE_MS) lastDoneAt + GRACE_MS else now
}
