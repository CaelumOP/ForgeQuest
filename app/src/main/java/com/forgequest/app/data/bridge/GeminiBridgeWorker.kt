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

/**
 * Twice a day: refresh "ForgeQuest data" and pick up a new "ForgeQuest from Gemini", and read new meals from the
 * Gem's meals Doc into the Food tab inbox. Each part runs only when turned on. Silent; never asks for consent.
 */
class GeminiBridgeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext.container
        val s = app.settings.current()
        if (!s.bridgeEnabled && !s.geminiMealsEnabled) return Result.success()
        val token = app.drive.silentToken() ?: return Result.success()
        return try {
            if (s.geminiMealsEnabled) app.meals.sync(token)
            if (s.bridgeEnabled) app.bridge.sync(token)
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
