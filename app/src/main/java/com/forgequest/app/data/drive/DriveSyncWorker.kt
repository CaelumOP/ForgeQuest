package com.forgequest.app.data.drive

import com.forgequest.app.R

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
import com.forgequest.app.data.plan.PlanJson
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
            if (s.driveDocsEnabled && app.settings.apiKey.value.isNotBlank()) {
                val docsToken = app.drive.silentToken() ?: return Result.success()
                com.forgequest.app.data.suspendRunCatching { app.drive.fetchPlanDoc(docsToken, s.lastDocImportModified) }.getOrNull()?.let { doc ->
                    // Staged, not applied: the person reviews it in the Coach tab and taps Apply, so edits made
                    // in the app aren't overwritten by a doc change behind their back.
                    app.settings.update { it.copy(lastDocImportModified = doc.modifiedAt) }
                    app.db.chat().insert(
                        ChatEntity(
                            role = "coach",
                            text = app.str(R.string.drive_doc_changed, doc.docName),
                            planJson = PlanJson.encode(doc.plan),
                            createdAt = System.currentTimeMillis(),
                        )
                    )
                }
            }
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
