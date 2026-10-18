package com.forgequest.app.data.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.forgequest.app.MainActivity
import com.forgequest.app.R

/**
 * The app's two notifications: a silent rest countdown while a workout is open, and the optional
 * workout-day reminder. Nothing here makes a sound; the rest channel is low importance on purpose.
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

    fun restOver(exercise: String?) {
        if (!canPost()) return
        val n = NotificationCompat.Builder(context, REST_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_rest_over))
            .setContentText(exercise?.let { context.getString(R.string.notif_back_to, it) } ?: context.getString(R.string.notif_back_to_it))
            .setAutoCancel(true)
            .setSilent(true)
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

    private companion object {
        const val REST_CHANNEL = "rest"
        const val REMINDER_CHANNEL = "reminders"
        const val REST_ID = 1
        const val REMINDER_ID = 2
    }
}
