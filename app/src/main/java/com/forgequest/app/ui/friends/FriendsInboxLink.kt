package com.forgequest.app.ui.friends

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * A tapped Friends push asks to open the Friends tab with the Inbox sheet. Set by MainActivity (cold start and
 * onNewIntent); AppRoot switches to the Friends tab and FriendsScreen opens the Inbox and clears it. Survives the
 * app lock screen because it lives outside the composition.
 */
object FriendsInboxLink {
    var pending by mutableStateOf(false)
}
