package com.forgequest.app.data.drive

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.forgequest.app.container
import java.util.concurrent.TimeUnit

/** Daily backup to Drive. Silent; skips if Drive isn't connected. */
class DriveSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext.container
        val s = app.settings.current()
        if (s.driveEmail.isBlank()) return Result.success()
        val token = app.drive.silentToken() ?: return Result.success()
        return try {
            app.drive.backup(token)
            Result.success()
        } catch (e: DriveError.Offline) {
            Result.retry()
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.success()
        }
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DriveSyncWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("drive_sync", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
