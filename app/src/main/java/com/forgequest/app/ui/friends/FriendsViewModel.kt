package com.forgequest.app.ui.friends

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forgequest.app.AppContainer
import com.forgequest.app.data.friends.FriendsUiData
import com.forgequest.app.data.friends.Me
import com.forgequest.app.data.friends.NudgeGate
import com.forgequest.app.data.prefs.Settings
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Pure state machine for per-item optimistic hide/rollback and pending tracking in Friends.
 */
data class OptimisticRowState(
    val pending: Set<String> = emptySet(),
    val hidden: Set<String> = emptySet(),
) {
    fun beginHide(id: String): OptimisticRowState? =
        if (id in pending) null else copy(pending = pending + id, hidden = hidden + id)

    fun endHide(id: String, success: Boolean, stillPresent: Boolean): OptimisticRowState =
        copy(
            pending = pending - id,
            hidden = if (!success || !stillPresent) hidden - id else hidden,
        )

    fun beginPending(id: String): OptimisticRowState? =
        if (id in pending) null else copy(pending = pending + id)

    fun endPending(id: String): OptimisticRowState =
        copy(pending = pending - id)
}

sealed interface FriendsUiEvent {
    data class ActionFailed(val overSheet: Boolean = false) : FriendsUiEvent
    data object NowFriends : FriendsUiEvent
}

class FriendsViewModel(val app: AppContainer) : ViewModel() {
    val state: StateFlow<FriendsUiData?> = app.friends.state
    val signedIn: StateFlow<Boolean> = app.friends.signedInState
    val me: StateFlow<Me?> = app.friends.me
    val error: StateFlow<String?> = app.friends.error
    val trainDayNudgeIds: StateFlow<Set<String>> = app.friends.trainDayNudgeIds
    val settings: StateFlow<Settings?> = app.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val nudgeNextAt: StateFlow<Map<String, Long>> = combine(state, app.friends.nudgeLocal) { data, local ->
        NudgeGate.effectiveMap(
            friends = data?.view?.friends.orEmpty(),
            local = local,
            nowSec = System.currentTimeMillis() / 1000L,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        NudgeGate.effectiveMap(
            friends = state.value?.view?.friends.orEmpty(),
            local = app.friends.nudgeLocal.value,
            nowSec = System.currentTimeMillis() / 1000L,
        ),
    )

    val isDemo: Boolean
        get() = app.friends.isDemo

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /** Global busy state reserved strictly for sign-in and join flows. */
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _optimistic = MutableStateFlow(OptimisticRowState())
    val pending: StateFlow<Set<String>> = _optimistic
        .map { it.pending }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val hidden: StateFlow<Set<String>> = _optimistic
        .map { it.hidden }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val _nudgeSent = MutableStateFlow<Set<String>>(emptySet())
    val nudgeSent: StateFlow<Set<String>> = _nudgeSent.asStateFlow()

    private val _events = Channel<FriendsUiEvent>(Channel.BUFFERED)
    val events: Flow<FriendsUiEvent> = _events.receiveAsFlow()

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

    fun join(username: String, sharePhoto: Boolean, sharePrs: Boolean) =
        runBusy { app.friends.join(sharePhoto, sharePrs, username) }

    /** Opening the inbox clears the badge (local first, then POST /v1/inbox/seen when needed). */
    fun markInboxSeen() {
        viewModelScope.launch { app.friends.markInboxSeen() }
    }

    fun setMuted(id: String, muted: Boolean) {
        viewModelScope.launch { app.friends.setMuted(id, muted) }
    }

    fun accept(id: String) = launchOptimisticHide(
        id = id,
        overSheet = true,
        stillPresent = { data -> data?.view?.incoming?.any { it.id == id } == true },
        onSuccessEvent = { _events.trySend(FriendsUiEvent.NowFriends) },
    ) {
        app.friends.accept(id)
    }

    fun decline(id: String) = launchOptimisticHide(
        id = id,
        overSheet = true,
        stillPresent = { data -> data?.view?.incoming?.any { it.id == id } == true },
    ) {
        app.friends.decline(id)
    }

    fun cancelInvite(id: String) = launchOptimisticHide(
        id = id,
        overSheet = false,
        stillPresent = { data -> data?.view?.outgoing?.any { it.id == id } == true },
    ) {
        app.friends.decline(id)
    }

    fun remove(id: String) = launchOptimisticHide(
        id = id,
        overSheet = false,
        stillPresent = { data -> data?.view?.friends?.any { it.id == id } == true },
    ) {
        app.friends.remove(id)
    }

    fun block(id: String) = launchOptimisticHide(
        id = id,
        overSheet = false,
        stillPresent = { data -> data?.view?.friends?.any { it.id == id } == true },
    ) {
        app.friends.block(id)
    }

    suspend fun invite(username: String): String? = app.friends.invite(username)

    fun nudge(id: String, overSheet: Boolean = false) {
        if (id in _nudgeSent.value) return
        val next = _optimistic.value.beginPending(id) ?: return
        _optimistic.value = next
        _nudgeSent.update { it + id }
        viewModelScope.launch {
            val startedMs = System.currentTimeMillis()
            val err = try {
                app.friends.nudge(id)
            } finally {
                _optimistic.update { it.endPending(id) }
            }
            if (err == null || err == "already_nudged") {
                val holdMs = if (err == null) NUDGE_SENT_HOLD_MS else 350L
                val remaining = (holdMs - (System.currentTimeMillis() - startedMs)).coerceAtLeast(0L)
                if (remaining > 0L) delay(remaining)
                _nudgeSent.update { it - id }
            } else {
                _nudgeSent.update { it - id }
                _events.trySend(FriendsUiEvent.ActionFailed(overSheet = overSheet))
            }
        }
    }

    private fun launchOptimisticHide(
        id: String,
        overSheet: Boolean,
        stillPresent: (FriendsUiData?) -> Boolean,
        onSuccessEvent: () -> Unit = {},
        action: suspend () -> Boolean,
    ) {
        val next = _optimistic.value.beginHide(id) ?: return
        _optimistic.value = next
        viewModelScope.launch {
            if (app.friends.isDemo) delay(DEMO_EXIT_ANIM_MS)
            val ok = action()
            _optimistic.update { it.endHide(id, success = ok, stillPresent = stillPresent(state.value)) }
            if (ok) {
                onSuccessEvent()
            } else {
                _events.trySend(FriendsUiEvent.ActionFailed(overSheet = overSheet))
            }
        }
    }

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

    companion object {
        private const val NUDGE_SENT_HOLD_MS = 850L
        private const val DEMO_EXIT_ANIM_MS = 240L
    }
}
