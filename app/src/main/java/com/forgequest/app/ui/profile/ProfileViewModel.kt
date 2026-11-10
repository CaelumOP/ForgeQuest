package com.forgequest.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forgequest.app.AppContainer
import com.forgequest.app.data.ProgressSnapshot
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What Profile, Settings and Badges all read: the progress snapshot and whether the coach is set up. One instance for the
 * three screens (see `sharedProfileViewModel` in AppRoot.kt), so moving between them does not reload the snapshot. It holds
 * no passing state on purpose; dialogs, snackbars and Drive work live in [SettingsViewModel], which is made per visit.
 */
class ProfileViewModel(val app: AppContainer) : ViewModel() {
    val snapshot: StateFlow<ProgressSnapshot?> = app.progress.snapshot.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val apiKey: StateFlow<String> = app.settings.apiKey
    val aiOn: StateFlow<Boolean> = app.settings.aiOn

    fun markBadgesSeen() = viewModelScope.launch { app.progress.markBadgesSeen() }
}
