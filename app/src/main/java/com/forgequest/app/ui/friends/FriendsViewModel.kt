package com.forgequest.app.ui.friends

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forgequest.app.AppContainer
import com.forgequest.app.data.friends.FriendsUiData
import com.forgequest.app.data.friends.Me
import com.forgequest.app.data.prefs.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FriendsViewModel(val app: AppContainer) : ViewModel() {
    val state: StateFlow<FriendsUiData?> = app.friends.state
    val signedIn: StateFlow<Boolean> = app.friends.signedInState
    val me: StateFlow<Me?> = app.friends.me
    val error: StateFlow<String?> = app.friends.error
    val nudgedToday: StateFlow<Set<String>> = app.friends.nudgedToday
    val settings: StateFlow<Settings?> = app.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isDemo: Boolean
        get() = app.friends.isDemo

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun refresh() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            try {
                app.friends.refresh()
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun signIn(activity: Activity) = runBusy { app.friends.signIn(activity) }

    fun join(name: String, username: String, sharePhoto: Boolean, sharePrs: Boolean) =
        runBusy { app.friends.join(name, sharePhoto, sharePrs, username) }

    fun accept(id: String) = runBusy { app.friends.accept(id) }

    fun decline(id: String) = runBusy { app.friends.decline(id) }

    fun remove(id: String) = runBusy { app.friends.remove(id) }

    fun block(id: String) = runBusy { app.friends.block(id) }

    suspend fun invite(username: String): String? = withBusy { app.friends.invite(username) }

    suspend fun nudge(id: String): String? = withBusy { app.friends.nudge(id) }

    private fun runBusy(block: suspend () -> Unit) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                block()
            } finally {
                _busy.value = false
            }
        }
    }

    private suspend fun <T> withBusy(block: suspend () -> T): T {
        _busy.value = true
        return try {
            block()
        } finally {
            _busy.value = false
        }
    }
}
