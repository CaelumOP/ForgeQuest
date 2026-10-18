package com.forgequest.ai.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.forgequest.ai.data.local.ForgeQuestDatabase
import com.forgequest.ai.data.security.EncryptedSettingsDataStore
import java.util.concurrent.TimeUnit

class DriveSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return runCatching {
            val database = ForgeQuestDatabase.getInstance(applicationContext)
            val settingsStore = EncryptedSettingsDataStore.getInstance(applicationContext)
            val syncManager = GoogleDriveSyncManager(
                context = applicationContext,
                dao = database.forgeQuestDao(),
                settingsDataStore = settingsStore
            )

            val runImport = inputData.getBoolean(KEY_RUN_IMPORT, true)
            val runExport = inputData.getBoolean(KEY_RUN_EXPORT, true)

            if (runImport) {
                syncManager.syncImportWorkoutPlanDoc(forceImport = false)
            }
            if (runExport) {
                syncManager.syncExportBackupAndSummary()
            }
            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }

    companion object {
        const val KEY_RUN_IMPORT = "run_import"
        const val KEY_RUN_EXPORT = "run_export"
        private const val UNIQUE_ONE_TIME_WORK_NAME = "forgequest_drive_one_time_sync"
        private const val UNIQUE_PERIODIC_WORK_NAME = "forgequest_drive_periodic_sync"

        fun enqueueOneTimeSync(
            context: Context,
            runImport: Boolean = false,
            runExport: Boolean = true
        ) {
            runCatching {
                val request = OneTimeWorkRequestBuilder<DriveSyncWorker>()
                    .setInputData(
                        workDataOf(
                            KEY_RUN_IMPORT to runImport,
                            KEY_RUN_EXPORT to runExport
                        )
                    )
                    .build()
                WorkManager.getInstance(context.applicationContext)
                    .enqueueUniqueWork(
                        UNIQUE_ONE_TIME_WORK_NAME,
                        ExistingWorkPolicy.REPLACE,
                        request
                    )
            }
        }

        fun schedulePeriodicSync(context: Context) {
            runCatching {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
                val periodicRequest = PeriodicWorkRequestBuilder<DriveSyncWorker>(
                    6,
                    TimeUnit.HOURS
                )
                    .setConstraints(constraints)
                    .setInputData(
                        workDataOf(
                            KEY_RUN_IMPORT to true,
                            KEY_RUN_EXPORT to true
                        )
                    )
                    .build()
                WorkManager.getInstance(context.applicationContext)
                    .enqueueUniquePeriodicWork(
                        UNIQUE_PERIODIC_WORK_NAME,
                        ExistingPeriodicWorkPolicy.KEEP,
                        periodicRequest
                    )
            }
        }
    }
}
