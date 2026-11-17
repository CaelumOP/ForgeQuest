package com.forgequest.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.forgequest.app.ui.AppRoot
import com.forgequest.app.ui.theme.ForgeQuestTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: android.content.Context) = super.attachBaseContext(AppLocale.wrap(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) takeFriendsInboxExtra(intent)
        // Built-in exercise names follow the language, which can change between activity restarts.
        container.appScope.launch(kotlinx.coroutines.Dispatchers.IO) { container.workouts.seedLibrary() }
        // Strings resolved outside Compose (errors, notifications) follow the in-app language too.
        AppText.init(AppLocale.wrap(applicationContext))
        setContent {
            val settings by container.settings.settings.collectAsState(initial = null)
            val dark = when (settings?.theme) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                )
                onDispose {}
            }
            ForgeQuestTheme(dark) { AppRoot() }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        takeFriendsInboxExtra(intent)
    }

    private fun takeFriendsInboxExtra(intent: android.content.Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_FRIENDS_INBOX, false) == true) {
            com.forgequest.app.ui.friends.FriendsInboxLink.pending = true
            intent.removeExtra(EXTRA_OPEN_FRIENDS_INBOX)
        }
    }

    companion object {
        /** Set on Friends push notifications: open the Friends tab with the Inbox sheet. */
        const val EXTRA_OPEN_FRIENDS_INBOX = "com.forgequest.app.OPEN_FRIENDS_INBOX"
    }
}
