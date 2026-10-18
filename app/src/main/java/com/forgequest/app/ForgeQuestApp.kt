package com.forgequest.app

import android.app.Application
import android.content.Context
import com.forgequest.app.data.CoachRepository
import com.forgequest.app.data.FoodRepository
import com.forgequest.app.data.ProgressRepository
import com.forgequest.app.data.WorkoutRepository
import com.forgequest.app.data.ai.GeminiClient
import com.forgequest.app.data.ai.ModelStore
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
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    /** One connection pool and thread pool for every API; each client derives its own timeouts from it. */
    private val http = okhttp3.OkHttpClient()
    val gemini = GeminiClient(http)
    val groq = com.forgequest.app.data.ai.GroqClient(http)
    val models = ModelStore(settings, gemini)
    val workouts = WorkoutRepository(db)
    val progress = ProgressRepository(db, settings, workouts, appScope)
    val food = FoodRepository(context, db, settings, gemini, models)
    val coach = CoachRepository(db, settings, gemini, models, progress, workouts, groq)
    val backup = BackupRepository(context, db, settings, workouts)
    val drive = DriveSync(context, settings, workouts, coach, backup, http)
    val haptics = Haptics(context)
    val notifier = Notifier(context)

    /** Strings for view models and workers, in the app's language. */
    fun str(@androidx.annotation.StringRes id: Int, vararg args: Any): String = appContext.getString(id, *args)
    fun plural(@androidx.annotation.PluralsRes id: Int, count: Int, vararg args: Any): String = appContext.resources.getQuantityString(id, count, *args)
}

class ForgeQuestApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun attachBaseContext(base: Context) = super.attachBaseContext(AppLocale.wrap(base))

    override fun onCreate() {
        super.onCreate()
        AppText.init(this)
        container = AppContainer(this)
        container.appScope.launch { container.workouts.seedLibrary() }
        container.appScope.launch { com.forgequest.app.data.suspendRunCatching { container.food.deleteOrphanPhotos() } }
        container.appScope.launch { container.settings.settings.collect { container.haptics.enabled = it.hapticsEnabled } }
        DriveSyncWorker.schedule(this)
        container.appScope.launch { ReminderWorker.schedule(this@ForgeQuestApp, container.settings.current().reminderHour) }
    }
}

val Context.container: AppContainer get() = (applicationContext as ForgeQuestApp).container
