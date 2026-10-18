package com.forgequest.app.ui.workouts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forgequest.app.AppContainer
import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.db.SessionEntity
import com.forgequest.app.data.db.SetEntity
import com.forgequest.app.domain.ExerciseKind
import com.forgequest.app.domain.LevelInfo
import com.forgequest.app.domain.Muscle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class OpenSession(val session: SessionEntity?)

data class RestTimer(val totalSeconds: Int, val endsAt: Long)

data class WorkoutSummary(
    val title: String,
    val doneSets: Int,
    val minutes: Int,
    val prs: List<String>,
    val newBadges: List<String>,
    val xpGained: Int,
    val before: LevelInfo,
    val after: LevelInfo,
)

@OptIn(ExperimentalCoroutinesApi::class)
class LiveWorkoutViewModel(val app: AppContainer) : ViewModel() {
    private val repo = app.workouts

    /** Null while loading; otherwise wraps the open session (which itself is null when none is open). */
    val sessionState: StateFlow<OpenSession?> = repo.openSession.map { OpenSession(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val currentSession: SessionEntity? get() = sessionState.value?.session

    val sets: StateFlow<List<SetEntity>> = repo.openSession
        .flatMapLatest { s -> if (s == null) flowOf(emptyList()) else repo.observeSets(s.id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val library: StateFlow<List<ExerciseEntity>> = repo.library.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val exercises: StateFlow<Map<String, ExerciseEntity>> = repo.library.map { list -> list.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val rest = MutableStateFlow<RestTimer?>(null)
    val now = MutableStateFlow(System.currentTimeMillis())
    val summary = MutableStateFlow<WorkoutSummary?>(null)
    val finishing = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            while (isActive) {
                now.value = System.currentTimeMillis()
                rest.value?.let { r ->
                    if (now.value >= r.endsAt) {
                        rest.value = null
                        app.haptics.setDone()
                    }
                }
                delay(250)
            }
        }
    }

    fun toggle(set: SetEntity) {
        val done = !set.done
        if (done) app.haptics.setDone() else app.haptics.tick()
        viewModelScope.launch { repo.updateSet(set.copy(done = done)) }
        if (done && set.restSeconds > 0) startRest(set.restSeconds) else if (!done) rest.value = null
    }

    fun startRest(seconds: Int) {
        rest.value = RestTimer(seconds, System.currentTimeMillis() + seconds * 1000L)
    }

    fun addRest(seconds: Int) {
        rest.value = rest.value?.let { it.copy(totalSeconds = it.totalSeconds + seconds, endsAt = it.endsAt + seconds * 1000L) }
        app.haptics.tick()
    }

    fun skipRest() {
        rest.value = null
    }

    fun adjust(set: SetEntity, kind: ExerciseKind, field: Field, direction: Int) {
        app.haptics.tick()
        val updated = when (field) {
            Field.REPS -> set.copy(reps = ((set.reps ?: 0) + direction).coerceIn(0, 999))
            Field.SECONDS -> set.copy(seconds = ((set.seconds ?: 0) + 5 * direction).coerceIn(0, 3600))
            Field.WEIGHT -> set.copy(weightKg = (((set.weightKg ?: 0f) + 2.5f * direction) * 10).roundToInt() / 10f)
        }
        viewModelScope.launch { repo.updateSet(updated) }
    }

    fun addSet(itemPosition: Int) = currentSession?.let { s -> viewModelScope.launch { repo.addSet(s.id, itemPosition) } }

    /** Adds one more round to every exercise in a circuit block. */
    fun addRound(positions: List<Int>) = currentSession?.let { s ->
        viewModelScope.launch { positions.forEach { repo.addSet(s.id, it) } }
    }

    fun removeLastSet(itemPosition: Int) {
        val last = sets.value.filter { it.itemPosition == itemPosition }.maxByOrNull { it.setIndex } ?: return
        if (sets.value.count { it.itemPosition == itemPosition } <= 1) return
        viewModelScope.launch { repo.removeSet(last.id) }
    }

    fun swap(itemPosition: Int, exerciseId: String) = currentSession?.let { s ->
        viewModelScope.launch { repo.swapExercise(s.id, itemPosition, exerciseId) }
    }

    fun addExercise(exerciseId: String) = currentSession?.let { s ->
        viewModelScope.launch { repo.addExercise(s.id, exerciseId) }
    }

    fun createExercise(name: String, kind: ExerciseKind, muscle: Muscle, then: (String) -> Unit) {
        viewModelScope.launch { then(repo.addCustomExercise(name, kind, listOf(muscle), null)) }
    }

    fun discard(onDone: () -> Unit) {
        val s = currentSession ?: return onDone()
        viewModelScope.launch {
            repo.discardSession(s.id)
            onDone()
        }
    }

    fun finish() {
        val s = currentSession ?: return
        if (finishing.value) return
        finishing.value = true
        rest.value = null
        viewModelScope.launch {
            val before = app.progress.snapshot.first().level
            val result = repo.finishSession(s.id)
            val badges = app.progress.evaluateBadges()
            val after = app.progress.snapshot.first().level
            app.haptics.celebrate()
            summary.value = WorkoutSummary(
                title = result.sessionTitle,
                doneSets = result.doneSets,
                minutes = ((System.currentTimeMillis() - s.startedAt) / 60_000).toInt().coerceAtLeast(1),
                prs = result.prs,
                newBadges = badges,
                xpGained = after.totalXp - before.totalXp,
                before = before,
                after = after,
            )
            finishing.value = false
        }
    }

    enum class Field { REPS, SECONDS, WEIGHT }
}
