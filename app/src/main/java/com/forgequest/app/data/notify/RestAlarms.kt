package com.forgequest.app.data.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * The system alarm behind the rest timer, so "rest is over" arrives with the screen off or the app closed.
 *
 * Exact when the person allows it (SCHEDULE_EXACT_ALARM, granted in system settings on Android 14 and later); otherwise
 * an inexact alarm that still fires in Doze but can be late. The app never asks for USE_EXACT_ALARM, which Google Play
 * keeps for clock and calendar apps.
 */
class RestAlarms(private val context: Context, private val store: SharedPreferences) : RestAlarmScheduler {
    private val alarms: AlarmManager? = context.getSystemService(AlarmManager::class.java)

    /** Extras are not part of a PendingIntent's identity, so this one intent is both replaced and cancelled by request code. */
    private fun intent(nextExercise: String?): PendingIntent = PendingIntent.getBroadcast(
        context, REQUEST,
        Intent(context, RestAlarmReceiver::class.java).putExtra(RestAlarmReceiver.EXTRA_NEXT, nextExercise),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun exactAllowed(): Boolean = Build.VERSION.SDK_INT < 31 || alarms?.canScheduleExactAlarms() == true

    override fun schedule(atMillis: Long, nextExercise: String?) {
        val am = alarms ?: return
        val pi = intent(nextExercise)
        val exact = exactAllowed() && runCatching { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi) }.isSuccess
        // Refused, or the permission was taken away between the check and the call.
        if (!exact) runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi) }
    }

    override fun cancel() {
        runCatching { alarms?.cancel(intent(null)) }
    }

    /** True until answered: exact alarms are refused and the person has not been offered the settings screen yet. */
    fun shouldOfferExact(): Boolean = !exactAllowed() && !store.getBoolean(KEY_OFFERED, false)

    fun offerAnswered() = store.edit().putBoolean(KEY_OFFERED, true).apply()

    /** The system's "Alarms & reminders" switch for this app (Android 12 and later). */
    fun exactSettingsIntent(): Intent? =
        if (Build.VERSION.SDK_INT >= 31) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else null

    private companion object {
        const val REQUEST = 41
        const val KEY_OFFERED = "exact_offered"
    }
}
