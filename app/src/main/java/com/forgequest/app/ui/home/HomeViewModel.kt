package com.forgequest.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forgequest.app.AppContainer
import com.forgequest.app.data.PlanDayWithItems
import com.forgequest.app.data.ProgressSnapshot
import com.forgequest.app.data.db.SessionEntity
import com.forgequest.app.domain.DayTag
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(val app: AppContainer) : ViewModel() {
    val snapshot: StateFlow<ProgressSnapshot?> = app.progress.snapshot.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val openSession: StateFlow<SessionEntity?> = app.workouts.openSession.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    /** Friends for the shared-streak line in the streak card; empty unless joined (the cache, no network here). */
    val friends: StateFlow<List<com.forgequest.app.data.friends.Friend>> = app.friends.state
        .map { d -> if (d?.view?.me?.joined == true) d.view.friends else emptyList() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Home avatar photo: the Friends sign-in photo (cache, no network). The Drive account photo has no getter yet. */
    val profilePhoto: StateFlow<String?> = app.friends.state
        .map { d -> pickAvatarPhoto(d?.view?.me?.photoUrl, null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Friends username/display name for the Home avatar initials fallback when Settings name is blank. */
    val friendsName: StateFlow<String?> = app.friends.state
        .map { d -> d?.view?.me?.shownName?.takeIf { it.isNotBlank() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        // Load the food list quietly, so the first food search isn't empty while it loads.
        viewModelScope.launch { delay(1500); app.catalog.warm() }
    }

    fun logWeight(kg: Float) = viewModelScope.launch { app.progress.logWeight(kg) }
    val health = app.health.state
    fun refreshHealth(force: Boolean = false) = viewModelScope.launch { app.health.refresh(force) }

    /** Home is on screen: re-read Health Connect (steps, calories burned, weight). */
    fun resumeHealth() = viewModelScope.launch { app.health.refresh() }

    /** Catches badges a workout or meal earned but that were missed (they used to be checked against stale data). */
    fun checkBadges() = viewModelScope.launch { app.progress.evaluateBadges() }

    suspend fun startToday(day: PlanDayWithItems?) = app.workouts.startSession(day)

    fun setDayTag(epochDay: Long, tag: DayTag?) = viewModelScope.launch { app.progress.setDayTag(epochDay, tag) }

    /** Answers the one-time weekly-target question: [make] is the new target, or null to keep the current one. */
    fun answerNudge(make: Int?) = viewModelScope.launch {
        app.settings.update { it.copy(targetNudgeAnswered = true, weeklyTargetOverride = make ?: it.weeklyTargetOverride) }
    }
}
