package com.forgequest.app.data.notify

/**
 * Tells the person the rest between sets is over. [alert] gets the next exercise's name and whether the workout screen
 * is showing: on screen the app buzzes and plays a short tone itself, otherwise it posts the loud notification.
 */
class RestAlert(private val alert: (nextExercise: String?, onScreen: Boolean) -> Unit) {
    /** Set by the workout screen while it is in front (resumed), cleared when it is paused or left. */
    @Volatile var screenVisible: Boolean = false

    fun fire(nextExercise: String?) = alert(nextExercise, screenVisible)
}
