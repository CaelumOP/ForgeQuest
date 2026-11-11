package com.forgequest.app

import android.app.Application
import android.content.Context
import com.forgequest.app.data.drive.DriveSyncWorker
import com.forgequest.app.data.notify.ReminderWorker
import kotlinx.coroutines.launch

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
