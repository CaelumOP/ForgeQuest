package com.forgequest.app.data.bridge

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.forgequest.app.container
import com.forgequest.app.data.drive.DriveError
import java.util.concurrent.TimeUnit

/** Twice a day: refresh "ForgeQuest data" and pick up a new "ForgeQuest from Gemini". Silent; never asks for consent. */
class GeminiBridgeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext.container
        if (!app.settings.current().bridgeEnabled) return Result.success()
        val token = app.drive.silentToken(withDocs = true, bridge = true) ?: return Result.success()
        return try {
            app.bridge.sync(token)
            Result.success()
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: DriveError.Offline) {
            Result.retry()
        } catch (e: Exception) {
            Result.success()
        }
    }

    companion object {
        private const val NAME = "gemini_bridge"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<GeminiBridgeWorker>(12, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(NAME)
    }
}
