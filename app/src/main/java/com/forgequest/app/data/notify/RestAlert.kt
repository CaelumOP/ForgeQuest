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

    /** A rest started, or its end moved ("+30 s"): remember the end and set (or move) the alarm. */
    fun start(endsAtMillis: Long, nextExercise: String?) {
        synchronized(this) { store.edit().putLong(KEY_ENDS_AT, endsAtMillis).apply() }
        scheduler.schedule(endsAtMillis, nextExercise)
    }

    /** The rest was skipped, or the workout finished or was discarded: nothing must alert later. */
    fun cancel() {
        synchronized(this) { store.edit().remove(KEY_ENDS_AT).apply() }
        scheduler.cancel()
    }

    /** The rest ended. Returns true when this call was the one that alerted. */
    fun fire(nextExercise: String?): Boolean {
        synchronized(this) {
            if (store.getLong(KEY_ENDS_AT, 0L) == 0L) return false
            store.edit().remove(KEY_ENDS_AT).apply()
        }
        scheduler.cancel()
        alert(nextExercise, screenVisible)
        return true
    }

    companion object {
        const val PREFS = "rest_alert"
        private const val KEY_ENDS_AT = "ends_at"
    }
}
