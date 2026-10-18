package com.forgequest.app

import android.app.Application
import android.content.Context
import com.forgequest.app.data.CoachRepository
import com.forgequest.app.data.FoodRepository
import com.forgequest.app.data.ProgressRepository
import com.forgequest.app.data.WorkoutRepository
import com.forgequest.app.data.ai.GeminiClient
import com.forgequest.app.data.backup.BackupRepository
import com.forgequest.app.data.db.AppDatabase
import com.forgequest.app.data.drive.DriveSync
import com.forgequest.app.data.drive.DriveSyncWorker
import com.forgequest.app.data.notify.Notifier
import com.forgequest.app.data.notify.ReminderWorker
import com.forgequest.app.data.prefs.SettingsRepository
import com.forgequest.app.ui.Haptics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Hand-wired dependencies. Small enough that a DI framework would only add build time. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val db = AppDatabase.build(context)
    val settings = SettingsRepository(context)
    val gemini = GeminiClient()
    val workouts = WorkoutRepository(db)
    val progress = ProgressRepository(db, settings, workouts)
    val food = FoodRepository(context, db, settings, gemini)
    val coach = CoachRepository(db, settings, gemini, progress, workouts)
    val backup = BackupRepository(context, db, settings, workouts)
    val drive = DriveSync(context, settings, workouts, coach, backup)
    val haptics = Haptics(context)
    val notifier = Notifier(context)
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

class ForgeQuestApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.appScope.launch { container.workouts.seedLibrary() }
        container.appScope.launch { runCatching { container.food.deleteOrphanPhotos() } }
        container.appScope.launch { container.settings.settings.collect { container.haptics.enabled = it.hapticsEnabled } }
        DriveSyncWorker.schedule(this)
        container.appScope.launch { ReminderWorker.schedule(this@ForgeQuestApp, container.settings.current().reminderHour) }
    }
}

val Context.container: AppContainer get() = (applicationContext as ForgeQuestApp).container
