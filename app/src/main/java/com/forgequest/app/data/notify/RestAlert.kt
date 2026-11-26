package com.forgequest.app.data.notify

import android.content.SharedPreferences

/** Wakes the app at the end of a rest. An interface so the rules in [RestAlert] can be tested without AlarmManager. */
interface RestAlarmScheduler {
    fun schedule(atMillis: Long, nextExercise: String?)
    fun cancel()
}

/**
 * Tells the person the rest between sets is over, exactly once per rest.
 *
 * Two things can notice the end: the workout screen's own clock (while the app is open) and the system alarm set by
 * [scheduler] (screen off, app in the background, or the process gone). Both call [fire]; the first one takes the saved
 * end time and alerts, the second finds nothing and does nothing. The end time is kept in [store], not in memory, so the
 * alarm still alerts after Android has closed the app mid-rest.
 *
 * [alert] gets the next exercise's name and whether the workout screen is showing: on screen the app buzzes and plays a
 * short tone itself, otherwise it posts the loud notification.
 */
class RestAlert(
    private val store: SharedPreferences,
    private val scheduler: RestAlarmScheduler,
    private val alert: (nextExercise: String?, onScreen: Boolean) -> Unit,
) {
    /** Set by the workout screen while it is in front (resumed), cleared when it is paused or left. */
    @Volatile var screenVisible: Boolean = false

    /** Snapshot of an active saved rest whose end time is still in the future, for cold-process rebuild. */
    data class SavedRest(
        val endsAtMillis: Long,
        val totalSeconds: Int,
        val nextExercise: String?,
        val isRestPause: Boolean,
        val setId: Long?,
        val sessionId: Long?,
    )

    /** A rest started, or its end moved ("+30 s"): remember the end and set (or move) the alarm. */
    fun start(
        endsAtMillis: Long,
        nextExercise: String?,
        totalSeconds: Int = 0,
        isRestPause: Boolean = false,
        setId: Long? = null,
        sessionId: Long? = null,
    ) {
        synchronized(this) {
            store.edit().apply {
                putLong(KEY_ENDS_AT, endsAtMillis)
                if (totalSeconds > 0) putInt(KEY_TOTAL_SECONDS, totalSeconds) else remove(KEY_TOTAL_SECONDS)
                if (nextExercise != null) putString(KEY_NEXT_EXERCISE, nextExercise) else remove(KEY_NEXT_EXERCISE)
                putBoolean(KEY_IS_REST_PAUSE, isRestPause)
                if (setId != null) putLong(KEY_SET_ID, setId) else remove(KEY_SET_ID)
                if (sessionId != null) putLong(KEY_SESSION_ID, sessionId) else remove(KEY_SESSION_ID)
            }.apply()
        }
        scheduler.schedule(endsAtMillis, nextExercise)
    }

    /** Returns the saved rest if its alarm time is still in the future; otherwise null. */
    fun pending(nowMillis: Long = System.currentTimeMillis()): SavedRest? = synchronized(this) {
        val endsAt = store.getLong(KEY_ENDS_AT, 0L)
        if (endsAt <= nowMillis) return null
        val remainingSeconds = ((endsAt - nowMillis + 999L) / 1000L).toInt().coerceAtLeast(1)
        val savedTotal = store.getInt(KEY_TOTAL_SECONDS, 0)
        val totalSeconds = maxOf(savedTotal, remainingSeconds)
        val nextExercise = store.getString(KEY_NEXT_EXERCISE, null)
        val isRestPause = store.getBoolean(KEY_IS_REST_PAUSE, false)
        val setId = if (store.contains(KEY_SET_ID)) store.getLong(KEY_SET_ID, 0L) else null
        val sessionId = if (store.contains(KEY_SESSION_ID)) store.getLong(KEY_SESSION_ID, 0L) else null
        SavedRest(
            endsAtMillis = endsAt,
            totalSeconds = totalSeconds,
            nextExercise = nextExercise,
            isRestPause = isRestPause,
            setId = setId,
            sessionId = sessionId,
        )
    }

    /** The rest was skipped, or the workout finished or was discarded: nothing must alert later. */
    fun cancel() {
        synchronized(this) { clearSavedLocked() }
        scheduler.cancel()
    }

    /** The rest ended. Returns true when this call was the one that alerted. */
    fun fire(nextExercise: String?): Boolean {
        synchronized(this) {
            if (store.getLong(KEY_ENDS_AT, 0L) == 0L) return false
            clearSavedLocked()
        }
        scheduler.cancel()
        alert(nextExercise, screenVisible)
        return true
    }

    private fun clearSavedLocked() {
        store.edit()
            .remove(KEY_ENDS_AT)
            .remove(KEY_TOTAL_SECONDS)
            .remove(KEY_NEXT_EXERCISE)
            .remove(KEY_IS_REST_PAUSE)
            .remove(KEY_SET_ID)
            .remove(KEY_SESSION_ID)
            .apply()
    }

    companion object {
        const val PREFS = "rest_alert"
        private const val KEY_ENDS_AT = "ends_at"
        private const val KEY_TOTAL_SECONDS = "total_seconds"
        private const val KEY_NEXT_EXERCISE = "next_exercise"
        private const val KEY_IS_REST_PAUSE = "is_rest_pause"
        private const val KEY_SET_ID = "set_id"
        private const val KEY_SESSION_ID = "session_id"
    }
}
