package com.forgequest.app.ui.workouts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forgequest.app.AppContainer
import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.db.SessionEntity
import com.forgequest.app.data.db.SetEntity
import com.forgequest.app.data.plan.WorkoutDebrief
import com.forgequest.app.domain.Dates
import com.forgequest.app.domain.Nudges
import com.forgequest.app.domain.SetEffort
import com.forgequest.app.domain.Equipment
import com.forgequest.app.domain.ExerciseTiers
import com.forgequest.app.domain.Progression
import com.forgequest.app.domain.ExerciseKind
import com.forgequest.app.domain.LevelInfo
import com.forgequest.app.domain.Muscle
import com.forgequest.app.domain.Records
import com.forgequest.app.domain.WeightUnit
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

data class RestTimer(val totalSeconds: Int, val endsAt: Long, val nextExercise: String? = null)

/** Outlives the screen, so leaving a workout mid-rest and resuming it keeps the countdown. */
private val runningRest = MutableStateFlow<RestTimer?>(null)

/** A timed hold in progress: a 3-second get-ready, then the clock runs from [startsAt] towards [target]. */
/** A bodyweight move that has become easy: [next] is the harder version, or null to suggest a slower tempo. */
data class HarderHint(val next: ExerciseEntity?)

data class HoldTimer(val setId: Long, val exerciseName: String, val target: Int, val startsAt: Long) {
    /** Whole seconds held so far; negative during the get-ready. */
    fun elapsed(now: Long): Int = Math.floorDiv(now - startsAt, 1000L).toInt()
}

data class WorkoutSummary(
    val title: String,
    val doneSets: Int,
    val minutes: Int,
    val prs: List<String>,
    val newBadges: List<com.forgequest.app.domain.BadgeDef>,
    val xpGained: Int,
    val before: LevelInfo,
    val after: LevelInfo,
    /** Next planned training day after today: day of week (1 = Monday) and title. */
    val nextUp: Pair<Int, String>? = null,
    /** No reminder set yet and still one of the first workouts: offer one while consistency is fragile. */
    val offerReminder: Boolean = false,
    /** The calculated workout score and its reasons (domain/WorkoutScore.kt). */
    val score: com.forgequest.app.domain.WorkoutScore.Result? = null,
    /** Set when the end was pulled back to the last set: when that was (epoch ms). */
    val clippedEndAt: Long? = null,
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

    val unit: StateFlow<WeightUnit> = app.settings.settings.map { it.weightUnit }
        .stateIn(viewModelScope, SharingStarted.Eagerly, WeightUnit.KG)

    val userEquipment: StateFlow<Set<Equipment>> = app.settings.settings.map { it.configuredEquipment }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /**
     * What each exercise looked like last time, set by set, shown beside today's sets the way Strong and Hevy
     * show a "Previous" column. Loaded once per exercise as exercises appear in the session.
     */
    val previous = MutableStateFlow<Map<String, List<SetEntity>>>(emptyMap())
    /** Exercise id → "ready for a harder version", from its last two finished sessions. */
    val harder = MutableStateFlow<Map<String, HarderHint>>(emptyMap())

    val rest = runningRest
    val hold = MutableStateFlow<HoldTimer?>(null)
    val now = MutableStateFlow(System.currentTimeMillis())
    val summary = MutableStateFlow<WorkoutSummary?>(null)
    /** The coach's debrief under the summary; loads in the background. When it can't be written, [debriefFailed] offers Try again. */
    val debrief = MutableStateFlow<WorkoutDebrief?>(null)
    val debriefLoading = MutableStateFlow(false)
    val debriefFailed = MutableStateFlow(false)
    private var debriefSessionId = 0L
    val finishing = MutableStateFlow(false)

    /**
     * First workout back after a sick or injured day, or after the reminders backed off: offer to ease in.
     * Declared before init: viewModelScope starts coroutines immediately, so checkWelcomeBack() can write it
     * while the constructor is still running.
     */
    val welcomeBack = MutableStateFlow(false)

    init {
        viewModelScope.launch { checkWelcomeBack() }
        viewModelScope.launch {
            sets.collect { list ->
                val missing = list.map { it.exerciseId }.distinct().filter { it !in previous.value }
                if (missing.isEmpty()) return@collect
                val recentById = missing.associateWith { id -> app.db.sessions().recentDoneSets(id).filter { it.sessionId != currentSession?.id } }
                val loaded = recentById.mapValues { (_, recent) ->
                    recent.filter { it.sessionId == recent.firstOrNull()?.sessionId }.sortedBy { it.setIndex }
                }
                previous.value = previous.value + loaded
                harder.value = harder.value + harderHints(recentById, list)
            }
        }
        viewModelScope.launch {
            var lastSecond = -1L
            while (isActive) {
                val currentMillis = System.currentTimeMillis()
                val currentSecond = currentMillis / 1000L
                if (currentSecond != lastSecond) {
                    lastSecond = currentSecond
                    now.value = currentMillis
                }
                rest.value?.let { r ->
                    if (currentMillis >= r.endsAt) {
                        rest.value = null
                        now.value = currentMillis
                        app.haptics.setDone()
                        app.notifier.restOver(r.nextExercise)
                    }
                }
                hold.value?.let { h ->
                    val elapsedSec = h.elapsed(currentMillis)
                    if (elapsedSec != h.elapsed(now.value)) {
                        now.value = currentMillis
                    }
                    holdCue(h, elapsedSec)
                }
                delay(250)
            }
        }
    }

    /** The set just ticked: only it shows the Easy · OK · Hard · Pain chips, so they fade once you move on. */
    val effortSetId = MutableStateFlow<Long?>(null)

    /** Sets whose numbers were typed or nudged this session; the rest still hold the plan's defaults. */
    private val edited = mutableSetOf<Long>()

    fun toggle(set: SetEntity) {
        val done = !set.done
        effortSetId.value = if (done) set.id else null
        if (done) app.haptics.setDone() else app.haptics.tick()
        viewModelScope.launch {
            repo.updateSet(set.copy(done = done, doneAt = if (done) System.currentTimeMillis() else null))
            if (done) carryForward(set)
        }
        if (done && set.restSeconds > 0) startRest(set.restSeconds, nextExerciseAfter(set)) else if (!done) skipRest()
    }

    /** The warm-up's single tick: marks all of one exercise's sets done (or all undone) and starts no rest timer. */
    fun toggleAll(group: List<SetEntity>) {
        val done = !group.all { it.done }
        effortSetId.value = null
        if (done) app.haptics.setDone() else app.haptics.tick()
        viewModelScope.launch { group.filter { it.done != done }.forEach { repo.updateSet(it.copy(done = done, doneAt = if (done) System.currentTimeMillis() else null)) } }
    }

    /** What you just did becomes the starting point for the exercise's next sets you haven't touched yet. */
    private suspend fun carryForward(set: SetEntity) {
        sets.value.filter { it.itemPosition == set.itemPosition && it.exerciseId == set.exerciseId && it.setIndex > set.setIndex && !it.done && it.id !in edited }
            .forEach { next ->
                val carried = next.copy(
                    reps = if (set.reps != null && next.reps != null) set.reps else next.reps,
                    weightKg = set.weightKg?.takeIf { it > 0f } ?: next.weightKg,
                )
                if (carried != next) repo.updateSet(carried)
            }
    }

    /** Name of the exercise the next unticked set belongs to, for the notification text. */
    private fun nextExerciseAfter(set: SetEntity): String? {
        val ordered = sets.value.sortedWith(compareBy({ it.itemPosition }, { it.setIndex }))
        val next = ordered.firstOrNull { !it.done && it.id != set.id } ?: return null
        return exercises.value[next.exerciseId]?.name
    }

    fun startRest(seconds: Int, nextExercise: String? = null) {
        val endsAt = System.currentTimeMillis() + seconds * 1000L
        rest.value = RestTimer(seconds, endsAt, nextExercise)
        app.notifier.showRest(endsAt, nextExercise)
    }

    fun addRest(seconds: Int) {
        rest.value = rest.value?.let { it.copy(totalSeconds = (it.totalSeconds + seconds).coerceAtLeast(15), endsAt = it.endsAt + seconds * 1000L) }
        rest.value?.let { app.notifier.showRest(it.endsAt, it.nextExercise) }
        app.haptics.tick()
    }

    fun skipRest() {
        rest.value = null
        app.notifier.cancelRest()
    }

    // ------------------------------------------------------------ Hold timer

    private var lastCue = Int.MIN_VALUE

    fun startHold(set: SetEntity) {
        skipRest()
        val target = set.seconds?.takeIf { it > 0 } ?: 30
        lastCue = Int.MIN_VALUE
        hold.value = HoldTimer(set.id, exercises.value[set.exerciseId]?.name.orEmpty(), target, System.currentTimeMillis() + 3000)
    }

    /** One buzz per second of the get-ready, at halfway, on the last three seconds, and a big one on the target. */
    private fun holdCue(h: HoldTimer, s: Int) {
        if (s == lastCue) return
        lastCue = s
        val left = h.target - s
        when {
            s == h.target -> app.haptics.celebrate()
            s < 0 || left in 1..3 || (h.target >= 10 && s == h.target / 2) -> app.haptics.tick()
        }
    }

    fun cancelHold() { hold.value = null }

    /** Saves the seconds actually held (short of the target or past it), ticks the set and starts the rest. */
    fun stopHold() {
        val h = hold.value ?: return
        hold.value = null
        val held = h.elapsed(System.currentTimeMillis()).coerceIn(0, 3600)
        val set = sets.value.firstOrNull { it.id == h.setId } ?: return
        if (held == 0) return
        val updated = set.copy(seconds = held, done = true, doneAt = System.currentTimeMillis())
        app.haptics.setDone()
        viewModelScope.launch { repo.updateSet(updated) }
        checkBest(set, updated)
        if (set.restSeconds > 0) startRest(set.restSeconds, nextExerciseAfter(set))
    }

    fun adjust(set: SetEntity, kind: ExerciseKind, field: Field, direction: Int) {
        val current = when (field) {
            Field.REPS -> (set.reps ?: 0).toFloat()
            Field.SECONDS -> (set.seconds ?: 0).toFloat()
            Field.WEIGHT -> set.weightKg ?: 0f
        }
        val step = when (field) {
            Field.REPS -> 1f
            Field.SECONDS -> 5f
            Field.WEIGHT -> 2.5f
        }
        setField(set, field, current + step * direction)
    }

    /** Best score ever per exercise (finished sessions only), for the gold flash when a typed value beats it. */
    private val bestScores: StateFlow<Map<String, Float>> = app.progress.snapshot.map { snap ->
        snap.doneSets.groupBy { it.exerciseId }.mapValues { (id, sets) ->
            val kind = snap.exercises[id]?.kind ?: ExerciseKind.REPS
            sets.maxOf { Records.score(kind, it.reps, it.seconds, it.weightKg, it.exerciseId) }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Id of the set whose value just beat the person's best; cleared after the flash. */
    val newBest = MutableStateFlow<Long?>(null)
    private val celebrated = mutableSetOf<Long>()

    /** Writes a typed or nudged value, clamped to a sane range. */
    fun setField(set: SetEntity, field: Field, value: Float) {
        app.haptics.tick()
        edited += set.id
        val updated = when (field) {
            Field.REPS -> set.copy(reps = value.roundToInt().coerceIn(0, 999))
            Field.SECONDS -> set.copy(seconds = value.roundToInt().coerceIn(0, 3600))
            Field.WEIGHT -> set.copy(weightKg = ((value * 10).roundToInt() / 10f).coerceIn(0f, 999f))
        }
        viewModelScope.launch { repo.updateSet(updated) }
        checkBest(set, updated)
    }

    private fun checkBest(set: SetEntity, updated: SetEntity) {
        val kind = exercises.value[set.exerciseId]?.kind ?: ExerciseKind.REPS
        val best = bestScores.value[set.exerciseId] ?: return
        if (Records.score(kind, updated.reps, updated.seconds, updated.weightKg, updated.exerciseId) > best && celebrated.add(set.id)) {
            app.haptics.celebrate()
            newBest.value = set.id
            viewModelScope.launch { delay(1400); if (newBest.value == set.id) newBest.value = null }
        }
    }

    /** Tapping the chosen chip again clears it back to "not tapped" (counts as OK). */
    fun setEffort(set: SetEntity, effort: SetEffort) = viewModelScope.launch {
        app.haptics.tick()
        repo.updateSet(set.copy(effort = if (set.effort == effort.name) "" else effort.name))
    }

    fun setNote(set: SetEntity, note: String) = viewModelScope.launch { repo.updateSet(set.copy(note = note.trim().take(120))) }

    fun addSet(itemPosition: Int) = currentSession?.let { s -> viewModelScope.launch { repo.addSet(s.id, itemPosition) } }

    /** Adds one more round to every exercise in a circuit block. */
    fun addRound(positions: List<Int>) = currentSession?.let { s ->
        viewModelScope.launch { positions.forEach { repo.addSet(s.id, it) } }
    }

    private suspend fun checkWelcomeBack() {
        val snap = app.progress.snapshot.first()
        val days = snap.sessions.map { it.epochDay }.toSet()
        val today = Dates.today()
        welcomeBack.value = Nudges.welcomeBack(today, days, snap.dayTags, snap.plan?.trainingDays ?: emptySet(), days.minOrNull() ?: today)
    }

    /** One set fewer for every exercise that still has more than 2 and hasn't started; nothing else changes. */
    fun easeIn() {
        welcomeBack.value = false
        val byExercise = sets.value.groupBy { it.itemPosition }
        viewModelScope.launch {
            byExercise.values.filter { group -> group.size > 2 && group.none { it.done } }
                .forEach { group -> repo.removeSet(group.maxBy { it.setIndex }.id) }
        }
    }

    fun dismissWelcomeBack() { welcomeBack.value = false }

    fun removeLastSet(itemPosition: Int) {
        val last = sets.value.filter { it.itemPosition == itemPosition }.maxByOrNull { it.setIndex } ?: return
        if (sets.value.count { it.itemPosition == itemPosition } <= 1) return
        viewModelScope.launch { repo.removeSet(last.id) }
    }

    private suspend fun harderHints(recentById: Map<String, List<SetEntity>>, sets: List<SetEntity>): Map<String, HarderHint> {
        val library = repo.library.first()
        val byId = library.associateBy { it.id }
        val equipment = app.settings.current().configuredEquipment
        return recentById.mapNotNull { (id, recent) ->
            val ex = byId[id]?.takeIf { it.kind == ExerciseKind.REPS && it.id != "jumping_jacks" } ?: return@mapNotNull null
            if (sets.any { it.exerciseId == id && it.block.equals("Warm-up", ignoreCase = true) }) return@mapNotNull null
            val sessionIds = recent.map { it.sessionId }.distinct()
            fun past(sid: Long?) = recent.filter { it.sessionId == sid }.map { Progression.PastSet(it.reps, it.seconds, it.weightKg, SetEffort.of(it.effort, it.note)) }
            if (!ExerciseTiers.readyForHarder(past(sessionIds.getOrNull(0)), past(sessionIds.getOrNull(1)))) return@mapNotNull null
            id to HarderHint(ExerciseTiers.harderVersion(ex, library, equipment))
        }.toMap()
    }

    /** Switches an easy move for its harder version, today and on the plan day this workout came from. */
    fun stepUp(oldId: String, newId: String) = currentSession?.let { s ->
        val positions = sets.value.filter { it.exerciseId == oldId }.map { it.itemPosition }.distinct()
        harder.value = harder.value - oldId
        viewModelScope.launch { repo.stepUp(s, positions, oldId, newId) }
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

    /** The summary's one-tap reminder: planned days at 18:00, only when nothing is logged yet. */
    fun remindMe() = viewModelScope.launch {
        app.settings.update { it.copy(reminderHour = REMINDER_HOUR) }
        com.forgequest.app.data.notify.ReminderWorker.schedule(app.appContext, REMINDER_HOUR)
    }

    fun discard(onDone: () -> Unit) {
        val s = currentSession ?: return onDone()
        rest.value = null
        app.notifier.cancelRest()
        viewModelScope.launch {
            repo.discardSession(s.id)
            onDone()
        }
    }

    /** For when you did the work but didn't tick along (Strong offers the same on finish). */
    fun tickAllAndFinish() = finish(tickAll = true)

    fun finish(tickAll: Boolean = false) {
        val s = currentSession ?: return
        if (finishing.value) return
        finishing.value = true
        rest.value = null
        app.notifier.cancelRest()
        viewModelScope.launch {
            try {
                val before = app.progress.freshSnapshot().level
                val result = repo.finishSession(s.id, tickAll = tickAll)
                val badges = app.progress.evaluateBadges()
                // The summary below shows them, so Home shouldn't announce them a second time.
                if (badges.isNotEmpty()) app.progress.markBadgesSeen()
                // Straight from the database, with this workout and its badges in, or the summary shows "+0 XP".
                val snap = app.progress.freshSnapshot()
                val after = snap.level
                val today = com.forgequest.app.domain.Dates.todayDayOfWeek()
                val nextUp = snap.plan?.days.orEmpty()
                    .filter { !it.day.isRest && it.items.isNotEmpty() }
                    .minByOrNull { (it.day.dayOfWeek - today + 7 - 1) % 7 }
                    ?.let { it.day.dayOfWeek to it.day.title }
                app.haptics.celebrate()
                summary.value = WorkoutSummary(
                    title = result.sessionTitle,
                    doneSets = result.doneSets,
                    minutes = ((result.endedAt - s.startedAt) / 60_000).toInt().coerceAtLeast(1),
                    prs = result.prs,
                    newBadges = badges,
                    xpGained = after.totalXp - before.totalXp,
                    before = before,
                    after = after,
                    nextUp = nextUp,
                    offerReminder = snap.settings.reminderHour < 0 && snap.sessions.count { it.finishedAt != null } <= 3,
                    score = repo.scores(snap)[s.id],
                    clippedEndAt = result.endedAt.takeIf { result.clipped },
                )
                if (snap.settings.debriefEnabled && app.coach.canDebrief()) {
                    debriefSessionId = s.id
                    retryDebrief()
                }
            } finally {
                finishing.value = false
            }
        }
    }

    /**
     * Asks the coach for the debrief of the workout just finished. In its own job so it never holds up the summary.
     * App scope, not viewModelScope: leaving this screen must not cancel it, since it saves to the journal entry.
     */
    fun retryDebrief() {
        if (debriefSessionId == 0L || debriefLoading.value) return
        debriefLoading.value = true
        debriefFailed.value = false
        app.appScope.launch {
            val d = app.coach.generateDebrief(debriefSessionId)
            debrief.value = d
            debriefFailed.value = d == null
            if (d == null) app.haptics.error()
            debriefLoading.value = false
        }
    }

    enum class Field { REPS, SECONDS, WEIGHT }

    private companion object {
        const val REMINDER_HOUR = 18
    }
}
