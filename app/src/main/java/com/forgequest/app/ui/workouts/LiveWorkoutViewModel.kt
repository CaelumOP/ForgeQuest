package com.forgequest.app.ui.workouts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forgequest.app.AppContainer
import com.forgequest.app.R
import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.db.PlanItemEntity
import com.forgequest.app.data.db.SessionEntity
import com.forgequest.app.data.db.SetEntity
import com.forgequest.app.data.prefs.Settings
import com.forgequest.app.domain.Dates
import com.forgequest.app.domain.Equipment
import com.forgequest.app.domain.ExerciseKind
import com.forgequest.app.domain.ExerciseTiers
import com.forgequest.app.domain.LevelInfo
import com.forgequest.app.domain.Muscle
import com.forgequest.app.domain.Nudges
import com.forgequest.app.domain.PrPicker
import com.forgequest.app.domain.Progression
import com.forgequest.app.domain.Records
import com.forgequest.app.domain.SetEffort
import com.forgequest.app.domain.SetType
import com.forgequest.app.domain.WeightUnit
import com.forgequest.app.domain.WorkoutRecap
import com.forgequest.app.domain.WorkoutScore
import com.forgequest.app.domain.countsForPR
import com.forgequest.app.domain.countsForProgression
import com.forgequest.app.domain.countsForScore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

data class OpenSession(val session: SessionEntity?)

data class RestTimer(
    val totalSeconds: Int,
    val endsAt: Long,
    val nextExercise: String? = null,
    val isRestPause: Boolean = false,
)

/** Outlives the screen, so leaving a workout mid-rest and resuming it keeps the countdown. */
private val runningRest = MutableStateFlow<RestTimer?>(null)

/** A bodyweight move that has become easy: [next] is the harder version, or null to suggest a slower tempo. */
data class HarderHint(val next: ExerciseEntity?)

/** A timed hold in progress: a 3-second get-ready, then the clock runs from [startsAt] towards [target]. */
data class HoldTimer(val setId: Long, val exerciseName: String, val target: Int, val startsAt: Long) {
    /** Whole seconds held so far; negative during the get-ready. */
    fun elapsed(now: Long): Int = Math.floorDiv(now - startsAt, 1000L).toInt()
}

internal sealed interface TtsCue {
    data class RestOver(val nextExercise: String?) : TtsCue
    data class Hold(val event: HoldCueEvent) : TtsCue
}

/** Pure rest duration helper for live sets: warm-ups skip rest, rest-pause uses 15s, superset A1 gets a 10s transition. */
internal fun effectiveRestSeconds(set: SetEntity, groups: List<Group>): Int {
    if (set.setType == SetType.WARMUP.name) return 0
    if (set.setType == SetType.RESTPAUSE.name) return 15
    if (set.restSeconds <= 0) return 0
    val circuit = groups.filterIsInstance<Group.Circuit>().firstOrNull { g ->
        g.supersetGroup != null && g.allSets.any { it.id == set.id }
    }
    if (circuit != null) {
        val round = circuit.rounds.firstOrNull { r -> r.any { it.id == set.id } }
        if (round != null) {
            val otherUndone = round.any { it.id != set.id && !it.done }
            val isLastInRound = round.lastOrNull()?.id == set.id
            if (!isLastInRound && otherUndone) {
                return minOf(set.restSeconds, 10)
            }
        }
    }
    return set.restSeconds
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
    val score: WorkoutScore.Result? = null,
    /** Set when the end was pulled back to the last set: when that was (epoch ms). */
    val clippedEndAt: Long? = null,
    val sessionId: Long = 0L,
    /** The calculated debrief of this workout (domain/WorkoutDebriefMath.kt); null when it has no done sets. */
    val debrief: com.forgequest.app.domain.WorkoutDebriefMath.Result? = null,
    /** "Just log" mode: no XP, level, badges or motivational headline on the finish screen. */
    val justLog: Boolean = false,
    /** Exercise id → name, for the debrief's lines. */
    val exerciseNames: Map<String, String> = emptyMap(),
    val unit: WeightUnit = WeightUnit.KG,
    /** What the finish screen leads with, the word for the day, and the headline as shown (its phrasing already picked). */
    val dayType: com.forgequest.app.domain.DayType = com.forgequest.app.domain.DayType.ORDINARY,
    val tier: com.forgequest.app.domain.ScoreTier? = null,
    val headline: com.forgequest.app.domain.WorkoutDebriefMath.HeadlineLine? = null,
    /** Up to two comments picked once at finish; showing them started their cooldown, so they are kept here. */
    val insights: List<com.forgequest.app.domain.Insight> = emptyList(),
    /** The record that leads a PR day, with the best it beat. */
    val prHero: PrHero? = null,
    val recap: WorkoutRecap.Input? = null,
)

/** A new record ([win]) and the previous best set of that exercise (null when there was none). */
data class PrHero(val win: com.forgequest.app.domain.WorkoutDebriefMath.Win.Pr, val oldKg: Float?, val oldReps: Int?, val oldSeconds: Int?)

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

    val settings: StateFlow<Settings> = app.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    val unit: StateFlow<WeightUnit> = settings.map { it.weightUnit }
        .stateIn(viewModelScope, SharingStarted.Eagerly, WeightUnit.KG)

    val userEquipment: StateFlow<Set<Equipment>> = settings.map { it.configuredEquipment }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** Plan item position -> supersetGroup for the open session's plan day (used for A1/A2 grouping). */
    val supersetGroups: StateFlow<Map<Int, Int?>> = combine(repo.openSession, repo.activePlan) { session, plan ->
        val dayId = session?.planDayId ?: return@combine emptyMap()
        val day = plan?.days?.firstOrNull { it.day.id == dayId } ?: return@combine emptyMap()
        day.items.associate { it.position to it.supersetGroup }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    fun effectiveUnit(exerciseId: String): WeightUnit =
        settings.value.weightUnitFor(exercises.value[exerciseId]?.unitOverride.orEmpty())

    /**
     * What each exercise looked like last time, set by set, shown beside today's sets the way Strong and Hevy
     * show a "Previous" column. Loaded once per exercise as exercises appear in the session.
     */
    val previous = MutableStateFlow<Map<String, List<SetEntity>>>(emptyMap())
    /** Exercise id -> last 3 finished sessions for Quick Peek. */
    val recentHistory = MutableStateFlow<Map<String, List<PeekSessionHistory>>>(emptyMap())
    /** Exercise id → "ready for a harder version", from its last two finished sessions. */
    val harder = MutableStateFlow<Map<String, HarderHint>>(emptyMap())
    /** Exercise id → why today's numbers are what they are, from the same rules as the prefill. */
    val reasons = MutableStateFlow<Map<String, Progression.Reason>>(emptyMap())

    val rest = runningRest
    val hold = MutableStateFlow<HoldTimer?>(null)
    val now = MutableStateFlow(System.currentTimeMillis())
    val summary = MutableStateFlow<WorkoutSummary?>(null)
    val finishing = MutableStateFlow(false)
    internal val ttsEvents = MutableSharedFlow<TtsCue>(extraBufferCapacity = 8)

    /**
     * First workout back after a sick or injured day, or after the reminders backed off: offer to ease in.
     * Declared before init: viewModelScope starts coroutines immediately, so checkWelcomeBack() can write it
     * while the constructor is still running.
     */
    val welcomeBack = MutableStateFlow(false)

    /** This is the lighter week of the plan's 4-week cycle. */
    val deloadWeek = MutableStateFlow(false)

    init {
        viewModelScope.launch { checkWelcomeBack() }
        viewModelScope.launch { deloadWeek.value = repo.isDeloadWeekNow() }
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
                val session = currentSession
                val found = recentById.mapNotNull { (id, recent) ->
                    val first = list.firstOrNull { it.exerciseId == id } ?: return@mapNotNull null
                    val item = session?.let { repo.planItemAt(it, first.itemPosition) }?.takeIf { it.exerciseId == id }
                    val ex = repo.library.first().firstOrNull { it.id == id }
                    repo.progressionFor(item, ex, recent).reason?.let { id to it }
                }.toMap()
                reasons.value = reasons.value + found
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
                        effortSetId.value = null
                        now.value = currentMillis
                        app.restAlert.fire(r.nextExercise)
                        if (settings.value.ttsCues) ttsEvents.tryEmit(TtsCue.RestOver(r.nextExercise))
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

    /** Loads the last 3 finished sessions for [exerciseId] when Quick Peek opens. */
    fun loadQuickPeekHistory(exerciseId: String) {
        viewModelScope.launch {
            val allSets = app.db.sessions().recentDoneSetsAll(exerciseId)
                .filter { it.sessionId != currentSession?.id }
            if (allSets.isEmpty()) {
                recentHistory.value = recentHistory.value + (exerciseId to emptyList())
                return@launch
            }
            val finishedById = app.db.sessions().finished().associateBy { it.id }
            val grouped = allSets.groupBy { it.sessionId }
                .mapNotNull { (sid, list) ->
                    finishedById[sid]?.let { PeekSessionHistory(it, list.sortedBy { s -> s.setIndex }) }
                }
                .sortedWith(compareByDescending<PeekSessionHistory> { it.session.epochDay }.thenByDescending { it.session.startedAt })
                .take(3)
            recentHistory.value = recentHistory.value + (exerciseId to grouped)
        }
    }

    /** The set just ticked: only it shows the Easy · OK · Hard · Pain chips, so they fade once you move on. */
    val effortSetId = MutableStateFlow<Long?>(null)

    /** Sets whose numbers were typed or nudged this session; the rest still hold the plan's defaults. */
    private val edited = mutableSetOf<Long>()

    /** Exercises whose record buzz already fired this workout: one per exercise. */
    private val prBuzzed = mutableSetOf<String>()

    /** True when ticking [set] makes it the exercise's record set and the buzz hasn't fired for that exercise yet. */
    private fun isNewPr(set: SetEntity): Boolean {
        if (!countsForPR(set)) return false
        val withTick = sets.value.map { if (it.id == set.id) it.copy(done = true) else it }
            .filter { countsForPR(it) }
        val picked = PrPicker.pick(withTick, { exercises.value[it]?.kind ?: ExerciseKind.REPS }, bestScores.value)
        val fresh = set.id in picked && prBuzzed.add(set.exerciseId)
        if (fresh) announcePr(set)
        return fresh
    }

    /** One line for the snackbar when a set becomes the exercise's record; at most once per exercise per workout. */
    val prMessage = MutableStateFlow<String?>(null)

    private fun announcePr(set: SetEntity) {
        val ex = exercises.value[set.exerciseId] ?: return
        val u = effectiveUnit(set.exerciseId)
        val what = when (ex.kind) {
            ExerciseKind.WEIGHTED -> "${set.reps ?: 0} × ${u.format(set.weightKg ?: 0f)}"
            ExerciseKind.HOLD -> (set.seconds ?: 0).let { app.plural(R.plurals.debrief_n_seconds, it, it) }
            ExerciseKind.REPS -> (set.reps ?: 0).let { app.plural(R.plurals.debrief_n_reps, it, it) }
        }
        prMessage.value = app.str(R.string.live_pr_new, ex.name, what)
    }

    fun toggle(set: SetEntity) {
        val done = !set.done
        effortSetId.value = if (done) set.id else null
        when {
            !done -> app.haptics.tick()
            isNewPr(set) -> app.haptics.pr()
            else -> app.haptics.check()
        }
        val currentGroups = group(sets.value) { pos -> supersetGroups.value[pos] }
        val restSec = if (done) effectiveRestSeconds(set, currentGroups) else 0
        viewModelScope.launch {
            repo.updateSet(set.copy(done = done, doneAt = if (done) System.currentTimeMillis() else null))
            if (done) carryForward(set)
        }
        if (done && restSec > 0) {
            startRest(restSec, nextExerciseAfter(set), isRestPause = set.setType == SetType.RESTPAUSE.name)
        } else if (!done) {
            skipRest()
        }
    }

    /** The warm-up's single tick: marks all of one exercise's sets done (or all undone) and starts no rest timer. */
    fun toggleAll(group: List<SetEntity>) {
        val done = !group.all { it.done }
        effortSetId.value = null
        if (done) app.haptics.check() else app.haptics.tick()
        viewModelScope.launch { group.filter { it.done != done }.forEach { repo.updateSet(it.copy(done = done, doneAt = if (done) System.currentTimeMillis() else null)) } }
    }

    /** What you just did becomes the starting point for the exercise's next working sets you haven't touched yet. */
    private suspend fun carryForward(set: SetEntity) {
        if (!countsForProgression(set)) return
        sets.value.filter {
            it.itemPosition == set.itemPosition &&
                it.exerciseId == set.exerciseId &&
                it.setIndex > set.setIndex &&
                !it.done &&
                it.id !in edited &&
                countsForProgression(it)
        }.forEach { next ->
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

    fun startRest(seconds: Int, nextExercise: String? = null, isRestPause: Boolean = rest.value?.isRestPause ?: false) {
        val endsAt = System.currentTimeMillis() + seconds * 1000L
        rest.value = RestTimer(seconds, endsAt, nextExercise, isRestPause)
        app.notifier.showRest(endsAt, nextExercise)
        app.restAlert.start(endsAt, nextExercise)
        if (app.restAlarms.shouldOfferExact()) offerExactAlarm.value = true
    }

    fun setRestPreset(seconds: Int) {
        val current = rest.value ?: return
        app.haptics.tick()
        startRest(seconds, current.nextExercise, current.isRestPause)
    }

    /** Asked once: the system refuses exact alarms until the person allows them, and without them the alert can be late. */
    val offerExactAlarm = MutableStateFlow(false)

    fun answerExactAlarm() {
        offerExactAlarm.value = false
        app.restAlarms.offerAnswered()
    }

    fun addRest(seconds: Int) {
        rest.value = rest.value?.let { it.copy(totalSeconds = (it.totalSeconds + seconds).coerceAtLeast(15), endsAt = it.endsAt + seconds * 1000L) }
        rest.value?.let { app.notifier.showRest(it.endsAt, it.nextExercise); app.restAlert.start(it.endsAt, it.nextExercise) }
        app.haptics.tick()
    }

    fun skipRest() {
        rest.value = null
        effortSetId.value = null
        app.notifier.cancelRest()
        app.restAlert.cancel()
    }

    /** Source set for "Copy last set": latest done set of [target]'s exercise in this session, else last session's matching set. */
    fun copySourceFor(target: SetEntity?): SetEntity? {
        if (target == null) return null
        val inSession = sets.value
            .filter { it.exerciseId == target.exerciseId && it.done && it.id != target.id }
            .maxByOrNull { it.doneAt ?: it.setIndex.toLong() }
        if (inSession != null) return inSession
        val prevList = previous.value[target.exerciseId].orEmpty()
        return prevList.getOrNull(target.setIndex) ?: prevList.lastOrNull()
    }

    fun copyLastSetTo(target: SetEntity) {
        val src = copySourceFor(target) ?: return
        edited += target.id
        app.haptics.tick()
        val updated = target.copy(
            reps = src.reps ?: target.reps,
            seconds = src.seconds ?: target.seconds,
            weightKg = src.weightKg ?: target.weightKg,
        )
        viewModelScope.launch { repo.updateSet(updated) }
    }

    /** Applies last session's numbers onto all unticked sets of [exerciseId]. */
    fun useLastSessionFor(exerciseId: String): Boolean {
        val prevList = previous.value[exerciseId].orEmpty()
        if (prevList.isEmpty()) return false
        val targets = sets.value.filter { it.exerciseId == exerciseId && !it.done }.sortedBy { it.setIndex }
        if (targets.isEmpty()) return false
        app.haptics.tick()
        viewModelScope.launch {
            targets.forEachIndexed { idx, s ->
                val src = prevList.getOrNull(idx) ?: prevList.lastOrNull() ?: return@forEachIndexed
                edited += s.id
                repo.updateSet(
                    s.copy(
                        reps = src.reps ?: s.reps,
                        seconds = src.seconds ?: s.seconds,
                        weightKg = src.weightKg ?: s.weightKg,
                    ),
                )
            }
        }
        return true
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
        if (settings.value.ttsCues) {
            holdCueEvent(s, h.target)?.let { ttsEvents.tryEmit(TtsCue.Hold(it)) }
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
        val currentGroups = group(sets.value) { pos -> supersetGroups.value[pos] }
        val restSec = effectiveRestSeconds(updated, currentGroups)
        viewModelScope.launch { repo.updateSet(updated) }
        checkBest(set, updated)
        if (restSec > 0) startRest(restSec, nextExerciseAfter(set), isRestPause = set.setType == SetType.RESTPAUSE.name)
    }

    /** Best score ever per exercise (finished sessions only), for the gold flash when a typed value beats it. */
    private val bestScores: StateFlow<Map<String, Float>> = app.progress.snapshot.map { snap ->
        snap.doneSets.filter { countsForPR(it) }.groupBy { it.exerciseId }.mapValues { (id, sets) ->
            val kind = snap.exercises[id]?.kind ?: ExerciseKind.REPS
            sets.maxOf { Records.score(kind, it.reps, it.seconds, it.weightKg, it.exerciseId) }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /**
     * Sets whose numbers right now beat the person's best (same test as the gold flash). Presentation only: the live
     * tick turns gold for them; the saved PR flag is still worked out when the workout finishes.
     */
    val prSetIds: StateFlow<Set<Long>> = combine(sets, bestScores, exercises) { list, best, exs ->
        PrPicker.pick(list.filter { countsForPR(it) }, { exs[it]?.kind ?: ExerciseKind.REPS }, best)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** Id of the set whose value just beat the person's best; cleared after the flash. */
    val newBest = MutableStateFlow<Long?>(null)
    private val celebrated = mutableSetOf<Long>()

    /** Writes a typed or nudged value, clamped to a sane range. */
    fun setField(set: SetEntity, field: Field, value: Float) {
        // The stepper gives its own light tick per step.
        edited += set.id
        val updated = when (field) {
            Field.REPS -> set.copy(reps = value.roundToInt().coerceIn(0, 999))
            Field.SECONDS -> set.copy(seconds = value.roundToInt().coerceIn(0, 3600))
            Field.WEIGHT -> set.copy(weightKg = ((value * 100).roundToInt() / 100f).coerceIn(0f, 999f))
        }
        viewModelScope.launch { repo.updateSet(updated) }
        checkBest(set, updated)
    }

    private fun checkBest(set: SetEntity, updated: SetEntity) {
        if (!countsForPR(updated)) return
        // Same rule as ticking and finishing (PrPicker): extras, warm-ups, dropsets and stretches never flash.
        val withEdit = sets.value.map { if (it.id == set.id) updated.copy(done = true) else it }
            .filter { countsForPR(it) }
        val picked = PrPicker.pick(withEdit, { exercises.value[it]?.kind ?: ExerciseKind.REPS }, bestScores.value)
        if (set.id in picked && celebrated.add(set.id)) {
            if (prBuzzed.add(set.exerciseId)) { app.haptics.celebrate(); announcePr(updated) }
            newBest.value = set.id
            viewModelScope.launch { delay(1400); if (newBest.value == set.id) newBest.value = null }
        }
    }

    fun setSetType(set: SetEntity, type: SetType) = viewModelScope.launch {
        app.haptics.tick()
        repo.updateSet(set.copy(setType = type.name))
    }

    fun updateSetupNote(exerciseId: String, note: String) = viewModelScope.launch {
        val ex = exercises.value[exerciseId] ?: return@launch
        app.haptics.tick()
        repo.updateExercise(ex.copy(setupNote = note.trim().take(120)))
    }

    fun setExerciseUnitOverride(exerciseId: String, unitOverride: String) = viewModelScope.launch {
        val ex = exercises.value[exerciseId] ?: return@launch
        app.haptics.tick()
        repo.updateExercise(ex.copy(unitOverride = unitOverride))
    }

    fun toggleTtsCues() = viewModelScope.launch {
        app.haptics.tick()
        app.settings.update { it.copy(ttsCues = !it.ttsCues) }
    }

    /** Tapping the chosen chip again clears it back to "not tapped" (counts as OK). */
    fun setEffort(set: SetEntity, effort: SetEffort) = viewModelScope.launch {
        app.haptics.tick()
        effortSetId.value = null
        val (e, type) = SetEffort.pick(set.effort, set.setType, effort)
        repo.updateSet(set.copy(effort = e, setType = type))
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
            fun past(sid: Long?) = recent.filter { it.sessionId == sid && countsForProgression(it) }
                .map { Progression.PastSet(it.reps, it.seconds, it.weightKg, SetEffort.of(it.effort, it.note)) }
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
        app.restAlert.cancel()
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
        app.restAlert.cancel()
        viewModelScope.launch {
            try {
                val beforeSnap = app.progress.freshSnapshot()
                val before = beforeSnap.level
                val result = repo.finishSession(s.id, tickAll = tickAll)
                val badges = app.progress.evaluateBadges()
                val justLog = app.settings.current().justLog
                // The summary below shows them, so Home shouldn't announce them a second time. In "Just log" mode they
                // aren't shown, so they stay new for when the mode is turned off.
                if (badges.isNotEmpty() && !justLog) app.progress.markBadgesSeen()
                // Straight from the database, with this workout and its badges in, or the summary shows "+0 XP".
                val snap = app.progress.freshSnapshot()
                val after = snap.level
                val today = Dates.todayDayOfWeek()
                val nextUp = snap.plan?.days.orEmpty()
                    .filter { !it.day.isRest && it.items.isNotEmpty() }
                    .minByOrNull { (it.day.dayOfWeek - today + 7 - 1) % 7 }
                    ?.let { it.day.dayOfWeek to it.day.title }
                val score = repo.scores(snap, only = s.id)[s.id]
                val debrief = repo.debrief(snap, s.id)
                val day = Dates.today()
                val history = snap.settings.insightHistory
                // Picked once, here: picking starts the cooldown, so recomposing or reopening the screen never picks again.
                var insights = if (justLog || score == null || debrief == null) emptyList() else com.forgequest.app.domain.pick(
                    com.forgequest.app.domain.InsightInput(
                        sessions = snap.sessions.filter { it.finishedAt != null },
                        sets = snap.doneSets,
                        exercises = snap.exercises,
                        streak = snap.streak,
                        muscleWeekTargets = com.forgequest.app.domain.muscleWeekTargets(snap.plan?.days.orEmpty().flatMap { it.items }, snap.exercises),
                        focusSessionId = s.id,
                    ),
                    history, day,
                )
                val dayType = if (score != null && debrief != null) com.forgequest.app.domain.dayType(score, debrief, insights) else com.forgequest.app.domain.DayType.ORDINARY
                // A rough day stays quiet: nothing extra, and nothing counted as shown.
                if (dayType == com.forgequest.app.domain.DayType.ROUGH) insights = emptyList()
                val headline = debrief?.headline?.let { h -> com.forgequest.app.domain.pickHeadlineVariant(h.type, history)?.let { h.copy(variant = it) } ?: h }
                val bigDay = dayType == com.forgequest.app.domain.DayType.PR || dayType == com.forgequest.app.domain.DayType.MILESTONE
                // Only what the screen shows goes into the history; History never writes to it.
                if (!justLog) {
                    var updated = com.forgequest.app.domain.appendHistory(history, insights, day)
                    if (headline != null && dayType != com.forgequest.app.domain.DayType.ROUGH && com.forgequest.app.domain.pickHeadlineVariant(headline.type, "") != null) {
                        updated = com.forgequest.app.domain.appendHistory(updated, com.forgequest.app.domain.headlineKey(headline.type), headline.variant, day)
                    }
                    if (updated != history) app.settings.setInsightHistory(updated)
                }
                // The strong haptic is for a record or a milestone only.
                if (bigDay) app.haptics.celebrate()
                app.appScope.launch { app.friends.syncQuietly() }

                val minutes = ((result.endedAt - s.startedAt) / 60_000).toInt().coerceAtLeast(1)
                val sessionSets = snap.doneSets.filter { it.sessionId == s.id }
                    .sortedWith(compareBy({ it.itemPosition }, { it.setIndex }))
                val arabic = Locale.getDefault().language == "ar"
                val recapExercises = sessionSets.groupBy { it.exerciseId }.map { (exId, list) ->
                    val ex = snap.exercises[exId]
                    WorkoutRecap.RecapExercise(
                        exerciseId = exId,
                        name = ex?.name ?: exId,
                        kind = ex?.kind ?: ExerciseKind.REPS,
                        unit = snap.settings.weightUnitFor(ex?.unitOverride.orEmpty()),
                        sets = list.map { set ->
                            WorkoutRecap.RecapSet(
                                reps = set.reps,
                                seconds = set.seconds,
                                weightKg = set.weightKg,
                                isPr = set.isPr,
                                countsForPr = countsForPR(set),
                            )
                        },
                    )
                }
                val recap = WorkoutRecap.Input(
                    title = com.forgequest.app.data.seed.StarterPlans.displayTitle(result.sessionTitle),
                    epochDay = s.epochDay,
                    minutes = minutes,
                    doneSetsCount = result.doneSets,
                    defaultUnit = snap.settings.weightUnit,
                    exercises = recapExercises,
                    prs = result.prs,
                    arabic = arabic,
                )

                summary.value = WorkoutSummary(
                    title = result.sessionTitle,
                    doneSets = result.doneSets,
                    minutes = minutes,
                    prs = result.prs,
                    newBadges = badges,
                    xpGained = after.totalXp - before.totalXp,
                    before = before,
                    after = after,
                    nextUp = nextUp,
                    offerReminder = !justLog && snap.settings.reminderHour < 0 && snap.sessions.count { it.finishedAt != null } <= 3,
                    score = score,
                    clippedEndAt = result.endedAt.takeIf { result.clipped },
                    sessionId = s.id,
                    debrief = debrief,
                    exerciseNames = snap.exercises.mapValues { it.value.name },
                    unit = snap.settings.weightUnit,
                    justLog = justLog,
                    dayType = dayType,
                    tier = score?.let { com.forgequest.app.domain.scoreTier(it) },
                    headline = headline,
                    insights = insights,
                    prHero = debrief?.wins?.filterIsInstance<com.forgequest.app.domain.WorkoutDebriefMath.Win.Pr>()?.firstOrNull()?.let { prHeroOf(it, snap.doneSets, snap.exercises, s.id) },
                    recap = recap,
                )
            } finally {
                finishing.value = false
            }
        }
    }

    /** The best set of this exercise before this workout, by the same score the records use. */
    private fun prHeroOf(win: com.forgequest.app.domain.WorkoutDebriefMath.Win.Pr, sets: List<SetEntity>, exercises: Map<String, ExerciseEntity>, sessionId: Long): PrHero {
        val kind = exercises[win.exerciseId]?.kind ?: ExerciseKind.REPS
        val old = sets.filter { it.exerciseId == win.exerciseId && it.sessionId != sessionId && countsForPR(it) }
            .maxByOrNull { Records.score(kind, it.reps, it.seconds, it.weightKg, win.exerciseId) }
        return PrHero(win, old?.weightKg?.takeIf { it > 0f }, old?.reps, old?.seconds)
    }

    enum class Field { REPS, SECONDS, WEIGHT }

    private companion object {
        const val REMINDER_HOUR = 18
    }
}
