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
import com.forgequest.app.data.health.HealthState
import com.forgequest.app.data.health.RefreshWatch
import com.forgequest.app.data.health.showsYesterday
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(val app: AppContainer) : ViewModel() {
    val snapshot: StateFlow<ProgressSnapshot?> = app.progress.snapshot.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val openSession: StateFlow<SessionEntity?> = app.workouts.openSession.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        // Load the food list quietly, so the first food search isn't empty while it loads.
        viewModelScope.launch { delay(1500); app.catalog.warm() }
    }

    fun logWeight(kg: Float) = viewModelScope.launch { app.progress.logWeight(kg) }
    val health = app.health.state
    fun refreshHealth(force: Boolean = false) = viewModelScope.launch { app.health.refresh(force) }
    val refreshWatch = RefreshWatch(viewModelScope) { app.health.refresh(force = true) }

    /** Home is on screen: re-read Health Connect, and keep looking for a while if last night is still missing. */
    fun resumeHealth() = viewModelScope.launch {
        val found = app.health.refresh()
        refreshWatch.resume(found, waiting = (app.health.state.value as? HealthState.Ready)?.day?.showsYesterday() == true)
    }
    fun setFeltGreat(v: Boolean) = viewModelScope.launch { app.health.setFeltGreat(v) }

    /** Catches badges a workout or meal earned but that were missed (they used to be checked against stale data). */
    fun checkBadges() = viewModelScope.launch { app.progress.evaluateBadges() }

    suspend fun startToday(day: PlanDayWithItems?) = app.workouts.startSession(day)

    fun setDayTag(epochDay: Long, tag: DayTag?) = viewModelScope.launch { app.progress.setDayTag(epochDay, tag) }

    /** Answers the one-time weekly-target question: [make] is the new target, or null to keep the current one. */
    fun answerNudge(make: Int?) = viewModelScope.launch {
        app.settings.update { it.copy(targetNudgeAnswered = true, weeklyTargetOverride = make ?: it.weeklyTargetOverride) }
    }
}
