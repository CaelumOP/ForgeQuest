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
import com.forgequest.app.data.db.ChatEntity
import java.util.concurrent.TimeUnit

/** Daily: back up to Drive and pull in an updated "Workout Plan" doc. Silent; skips if Drive isn't connected. */
class DriveSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext.container
        val s = app.settings.current()
        if (s.driveEmail.isBlank()) return Result.success()
        val token = app.drive.silentToken() ?: return Result.success()
        return try {
            app.drive.backup(token)
            if (app.settings.apiKey.value.isNotBlank()) {
                runCatching { app.drive.fetchPlanDoc(token, s.lastDocImportModified) }.getOrNull()?.let { doc ->
                    app.drive.applyDocPlan(doc)
                    app.db.chat().insert(
                        ChatEntity(
                            role = "coach",
                            text = "I imported your updated **${doc.docName}** from Google Docs. Your new week is in the Workouts tab.",
                            createdAt = System.currentTimeMillis(),
                        )
                    )
                }
            }
            Result.success()
        } catch (e: DriveError.Offline) {
            Result.retry()
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
