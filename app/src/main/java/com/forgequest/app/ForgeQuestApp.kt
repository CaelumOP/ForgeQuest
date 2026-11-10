package com.forgequest.app

import android.app.Application
import android.content.Context
import com.forgequest.app.data.CoachRepository
import com.forgequest.app.data.CatalogRepository
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
    val models = ModelStore(settings, gemini)
    val workouts = WorkoutRepository(db, settings)
    val progress = ProgressRepository(db, settings, workouts, appScope)
    val food = FoodRepository(context, db, settings, gemini, models)
    val catalog = CatalogRepository(context)
    private val openFoodFacts = com.forgequest.app.data.OpenFoodFacts(http, "ForgeQuest/${BuildConfig.VERSION_NAME} (Android)")
    val barcodes = com.forgequest.app.data.BarcodeLookup({ db.food().foodByBarcode(it) }, catalog::byBarcode, openFoodFacts::lookup)
    val health = com.forgequest.app.data.health.HealthRepository(context, settings, db)
    val coach = CoachRepository(db, settings, gemini, models, progress, workouts, health)
    val backup = BackupRepository(context, db, settings, workouts)
    val drive = DriveSync(context, settings, workouts, coach, backup, http)
    val updates = com.forgequest.app.data.update.UpdateChecker(context, http)
    val updateNotice = com.forgequest.app.data.update.UpdateNotice(context, updates, appScope)
    val bridge = com.forgequest.app.data.bridge.GeminiBridge(context, db, coach, health)
    val meals = com.forgequest.app.data.bridge.MealInbox(context, db, drive)
    val haptics = Haptics(context)
    val notifier = Notifier(context)

    /**
     * Strings for view models and workers, in the app's language. Wrapped on every call: the application context keeps the
     * language the process started with, so after switching it in Profile, snackbars came out in the old one.
     */
    fun str(@androidx.annotation.StringRes id: Int, vararg args: Any): String = AppLocale.wrap(appContext).getString(id, *args)
    fun plural(@androidx.annotation.PluralsRes id: Int, count: Int, vararg args: Any): String = AppLocale.wrap(appContext).resources.getQuantityString(id, count, *args)
}

class ForgeQuestApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun attachBaseContext(base: Context) = super.attachBaseContext(AppLocale.wrap(base))

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        AppLocale.restoreDefault(this)
    }

    override fun onCreate() {
        super.onCreate()
        // First, so a crash anywhere after this is saved for the "Send a crash report?" card on Home.
        com.forgequest.app.data.crash.CrashReport.install(this)
        AppText.init(this)
        container = AppContainer(this)
        container.appScope.launch { container.workouts.seedLibrary() }
        container.appScope.launch { com.forgequest.app.data.suspendRunCatching { container.food.deleteOrphanPhotos() } }
        container.appScope.launch { container.settings.settings.collect { container.haptics.enabled = it.hapticsEnabled } }
        DriveSyncWorker.schedule(this)
        // Retires the periodic bridge job on phones that still have it queued.
        androidx.work.WorkManager.getInstance(this).cancelUniqueWork("gemini_bridge")
        // The fast-chat provider key is gone from the app; wipe any copy still stored.
        container.settings.clearRetiredKeys()
        // New meals from the Gemini Gem wait in the Food tab inbox by the time the person looks.
        container.appScope.launch {
            if (container.settings.current().geminiMealsEnabled) {
                container.drive.silentToken()?.let { token -> com.forgequest.app.data.suspendRunCatching { container.meals.sync(token) } }
            }
        }
        container.appScope.launch { ReminderWorker.schedule(this@ForgeQuestApp, container.settings.current().reminderHour) }
    }
}

val Context.container: AppContainer get() = (applicationContext as ForgeQuestApp).container
