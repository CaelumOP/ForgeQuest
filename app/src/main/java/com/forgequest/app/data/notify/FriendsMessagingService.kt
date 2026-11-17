package com.forgequest.app.data.notify

import com.forgequest.app.AppLocale
import com.forgequest.app.R
import com.forgequest.app.container
import com.forgequest.app.data.friends.Usernames
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.launch

/**
 * Receives Friends pushes. The Worker sends DATA-ONLY messages `{type: "ping"|"activity", fromId, name}` (name = username),
 * so this runs in the foreground and background alike and the text follows the in-app language, not the server's.
 */
class FriendsMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        val app = applicationContext.container
        app.appScope.launch { app.friends.syncPushToken(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val type = data["type"] ?: return
        val fromId = data["fromId"].orEmpty()
        val name = data["name"]?.takeIf { it.isNotBlank() } ?: "ForgeQuest"
        val ctx = AppLocale.wrap(applicationContext)
        val ping = when (type) {
            "ping" -> true
            "activity" -> false
            else -> return
        }
        val text = ctx.getString(if (ping) R.string.push_friend_ping else R.string.push_friend_activity, Usernames.ltr(name))
        val app = applicationContext.container
        app.notifier.showFriendPush(ping, fromId, text)
        // Keep the bell badge and inbox fresh if the app is open.
        app.appScope.launch { app.friends.syncQuietly() }
    }
}
