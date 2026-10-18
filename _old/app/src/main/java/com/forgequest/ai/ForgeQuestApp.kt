package com.forgequest.ai

import android.app.Application
import com.forgequest.ai.data.repository.ForgeQuestRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ForgeQuestApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val repository: ForgeQuestRepository by lazy {
        ForgeQuestRepository.getInstance(this)
    }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            runCatching {
                repository.initializeAndEvaluateStreak()
                // Attempt Google Drive 'Workout Plan' Doc auto-sync on app launch
                repository.syncImportWorkoutPlanDoc(forceImport = false)
            }
        }
    }
}
