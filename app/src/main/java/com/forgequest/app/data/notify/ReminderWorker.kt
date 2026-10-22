package com.forgequest.app.data.notify

import com.forgequest.app.R

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.forgequest.app.container
import com.forgequest.app.domain.Dates
import com.forgequest.app.domain.Nudges
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Fires once at the chosen hour, then books itself for tomorrow. It only speaks up when today is a planned
 * training day and nothing has been logged yet; rest days, finished days and excused days stay quiet, and so
 * does every day after three missed planned days in a row, until a workout or a day tag is logged.
 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext.container
        val settings = app.settings.current()
        if (settings.reminderHour < 0) return Result.success()
        val snap = app.progress.snapshot.first()
        val today = snap.plan?.day(Dates.todayDayOfWeek())
        val planned = today != null && !today.day.isRest && today.items.isNotEmpty()
        val workoutDays = snap.sessions.map { it.epochDay }.toSet()
        // Quiet on an excused day, and after 3 missed planned days in a row: no nagging someone who stepped away.
        val missed = Nudges.missedInARow(
            Dates.today(), snap.plan?.trainingDays ?: emptySet(), workoutDays, snap.dayTags.keys,
            workoutDays.minOrNull() ?: Dates.today(),
        )
        val remind = Nudges.shouldRemind(planned, snap.workoutDoneToday, snap.excusedToday != null, missed)
        if (remind && app.workouts.openSessionNow() == null) {
            val count = today!!.items.map { it.exerciseId }.distinct().size
            app.notifier.showReminder(app.str(R.string.notif_reminder_title, today.day.title), app.plural(R.plurals.notif_reminder_body, count, count))
        }
        schedule(applicationContext, settings.reminderHour)
        return Result.success()
    }

    companion object {
        private const val NAME = "workout_reminder"

        /** Books the next firing at [hour]:00 local time, or cancels when [hour] is negative. */
        fun schedule(context: Context, hour: Int) {
            val wm = WorkManager.getInstance(context)
            if (hour !in 0..23) {
                wm.cancelUniqueWork(NAME)
                return
            }
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(LocalTime.of(hour, 0))
            if (!next.isAfter(now)) next = next.plusDays(1)
            val delay = Duration.between(now, next).toMillis()
            val request = OneTimeWorkRequestBuilder<ReminderWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).build()
            wm.enqueueUniqueWork(NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
