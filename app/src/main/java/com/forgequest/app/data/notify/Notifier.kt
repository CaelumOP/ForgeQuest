package com.forgequest.app.data.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.forgequest.app.MainActivity
import com.forgequest.app.R
import com.forgequest.app.ui.Haptics

/**
 * The app's notifications: a silent rest countdown while a workout is open, the "rest is over" alert, and the optional
 * workout-day reminder. Only the rest-over alert is loud: it has its own high-importance channel that plays on the alarm
 * stream and vibrates, so it is noticed with the phone on a bench. The countdown channel stays low importance on purpose.
 */
class Notifier(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    init {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(REST_CHANNEL, context.getString(R.string.notif_rest_channel), NotificationManager.IMPORTANCE_LOW).apply {
                    description = context.getString(R.string.notif_rest_channel_desc)
                    setShowBadge(false)
                }
            )
            // A new id, not a change to "rest": Android keeps a channel's sound and importance from the day it was first made.
            nm.createNotificationChannel(
                NotificationChannel(REST_DONE_CHANNEL, context.getString(R.string.notif_rest_done_channel), NotificationManager.IMPORTANCE_HIGH).apply {
                    description = context.getString(R.string.notif_rest_done_channel_desc)
                    setShowBadge(false)
                    setSound(alertSound(), ALARM_AUDIO)
                    enableVibration(true)
                    vibrationPattern = Haptics.REST_OVER_PATTERN
                }
            )
            nm.createNotificationChannel(
                NotificationChannel(REMINDER_CHANNEL, context.getString(R.string.notif_reminder_channel), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = context.getString(R.string.notif_reminder_channel_desc)
                }
            )
        }
    }

    fun canPost(): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    // ------------------------------------------------------------ Rest timer

    /** Live countdown in the shade; the system renders the chronometer so no updates are needed. */
    fun showRest(endsAtMillis: Long, exercise: String?) {
        if (!canPost()) return
        val n = NotificationCompat.Builder(context, REST_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.live_rest))
            .setContentText(exercise?.let { context.getString(R.string.notif_next, it) } ?: context.getString(R.string.notif_next_set))
            .setWhen(endsAtMillis)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openApp())
            .build()
        runCatching { manager.notify(REST_ID, n) }
    }

    /** Counts the rest-over notifications of this run of the app, so their three wordings take turns. */
    private var restOverCount = 0

    fun restOver(exercise: String?) {
        if (!canPost()) return
        val wording = REST_OVER_WORDINGS[restOverCount++ % REST_OVER_WORDINGS.size]
        val n = NotificationCompat.Builder(context, REST_DONE_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_rest_over))
            .setContentText(exercise?.let { context.getString(wording, it) } ?: context.getString(R.string.notif_back_to_it))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setTimeoutAfter(20_000)
            .setContentIntent(openApp())
            .build()
        runCatching { manager.notify(REST_ID, n) }
    }

    fun cancelRest() = runCatching { manager.cancel(REST_ID) }

    // ------------------------------------------------------------ Reminder

    fun showReminder(title: String, body: String) {
        if (!canPost()) return
        val n = NotificationCompat.Builder(context, REMINDER_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .build()
        runCatching { manager.notify(REMINDER_ID, n) }
    }

    companion object {
        /** Alarm usage: heard at the alarm volume, also when the ringer is on vibrate. */
        val ALARM_AUDIO: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        /**
         * The sound for "rest is over": the phone's short notification tone, played as an alarm. The alarm ringtone is the
         * fallback only, because most alarm tones run for half a minute and a rest ending is not a wake-up call.
         */
        fun alertSound(): Uri? =
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

        /** Each takes the next exercise's name. */
        private val REST_OVER_WORDINGS = intArrayOf(R.string.notif_back_to, R.string.notif_back_to_2, R.string.notif_back_to_3)

        private const val REST_CHANNEL = "rest"
        private const val REST_DONE_CHANNEL = "rest_done_v2"
        private const val REMINDER_CHANNEL = "reminders"
        private const val REST_ID = 1
        private const val REMINDER_ID = 2
    }
}
