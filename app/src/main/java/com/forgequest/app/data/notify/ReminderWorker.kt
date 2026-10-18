package com.forgequest.app.data.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.forgequest.app.container
import com.forgequest.app.domain.Dates
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Fires once at the chosen hour, then books itself for tomorrow. It only speaks up when today is a planned
 * training day and nothing has been logged yet; rest days and finished days stay quiet.
 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext.container
        val settings = app.settings.current()
        if (settings.reminderHour < 0) return Result.success()
        val snap = app.progress.snapshot.first()
        val today = snap.plan?.day(Dates.todayDayOfWeek())
        val planned = today != null && !today.day.isRest && today.items.isNotEmpty()
        if (planned && !snap.workoutDoneToday && app.workouts.openSessionNow() == null) {
            val count = today!!.items.map { it.exerciseId }.distinct().size
            app.notifier.showReminder("Today: ${today.day.title}", "$count exercises on the plan. Tap to start.")
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
