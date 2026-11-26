package com.forgequest.app.ui.baseline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forgequest.app.AppContainer
import com.forgequest.app.data.ActivePlan
import com.forgequest.app.data.BaselineRepository
import com.forgequest.app.data.db.BaselineTestEntity
import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.prefs.BodyType
import com.forgequest.app.data.prefs.Settings
import com.forgequest.app.domain.BaselineTargets
import com.forgequest.app.domain.Equipment
import com.forgequest.app.domain.ExerciseSwapEngine
import com.forgequest.app.domain.ExerciseKind
import com.forgequest.app.domain.PlanBuilder
import com.forgequest.app.domain.WeightUnit
import com.forgequest.app.domain.WeightedResult
import com.forgequest.app.domain.WorkoutScore
import com.forgequest.app.domain.expandLegacyEquipment
import com.forgequest.app.ui.Haptics
import com.forgequest.app.ui.workouts.HoldTimer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One row in the "Find your starting point" move list. */
data class BaselineMoveItem(
    val exercise: ExerciseEntity,
    val latest: BaselineTestEntity?,
    val target: BaselineTargetDisplay?,
    val unit: WeightUnit,
    val repsMin: Int,
    val isSuggested: Boolean,
)

/** Computed starting target or guidance displayed after saving (and on each move row). */
sealed interface BaselineTargetDisplay {
    data class Reps(val reps: Int, val harderExercise: ExerciseEntity? = null) : BaselineTargetDisplay
    data class Hold(val seconds: Int) : BaselineTargetDisplay
    data class Weight(val weightKg: Float, val unit: WeightUnit) : BaselineTargetDisplay
    data class EasierVariation(val easierExercise: ExerciseEntity?) : BaselineTargetDisplay
    data object TooLight : BaselineTargetDisplay
    data object HoldTooShort : BaselineTargetDisplay
    data object WeightNoTarget : BaselineTargetDisplay
}

/** Result card payload shown immediately after saving a baseline test. */
data class BaselineSaveResult(
    val exercise: ExerciseEntity,
    val test: BaselineTestEntity,
    val target: BaselineTargetDisplay,
    val unit: WeightUnit,
)

internal fun showBaselineEntry(hasFinishedWorkout: Boolean, hasBaseline: Boolean, dismissed: Boolean): Boolean =
    !hasFinishedWorkout && !hasBaseline && !dismissed

private val BASELINE_PREFERENCES = listOf(
    listOf("bench_press", "db_bench_press", "push_up"),
    listOf("pull_up", "lat_pulldown", "barbell_row", "db_row", "australian_pull_up"),
    listOf("back_squat", "goblet_squat", "squat"),
    listOf("romanian_deadlift", "db_romanian_deadlift", "hip_bridge"),
    listOf("plank"),
)

/** One gear-compatible staple per movement, followed by compatible tested moves. */
internal fun buildBaselineMoves(
    library: List<ExerciseEntity>,
    settings: Settings,
    activePlan: ActivePlan?,
    latestById: Map<String, BaselineTestEntity>,
    callerEquipment: Set<Equipment>? = null,
): List<BaselineMoveItem> {
    if (library.isEmpty()) return emptyList()
    val byId = library.associateBy { it.id }
    val equipment = expandLegacyEquipment(callerEquipment ?: settings.configuredEquipment) + Equipment.BODYWEIGHT
    fun fits(ex: ExerciseEntity) = ExerciseSwapEngine.profileFor(ex).fits(equipment)
    val planRepsMin = mutableMapOf<String, Int>()
    val planWeightedMainIds = mutableListOf<String>()

    if (activePlan != null) {
        val orderedItems = activePlan.days
            .sortedBy { it.day.dayOfWeek }
            .flatMap { day -> day.items.sortedBy { it.position } }
        for (item in orderedItems) {
            if (item.exerciseId !in planRepsMin && item.repsMin != null) {
                planRepsMin[item.exerciseId] = item.repsMin
            }
            if (!WorkoutScore.isExtra(item.block) &&
                !item.block.equals(PlanBuilder.FINISHER_BLOCK, ignoreCase = true)
            ) {
                val ex = byId[item.exerciseId]
                if (ex != null &&
                    ex.kind == ExerciseKind.WEIGHTED && equipment.any { it != Equipment.BODYWEIGHT } && fits(ex) &&
                    PlanBuilder.isCompoundExercise(ex) && ex.id != "around_the_worlds" &&
                    item.exerciseId !in planWeightedMainIds
                ) {
                    planWeightedMainIds += item.exerciseId
                }
            }
        }
    }

    val suggestedIds = BASELINE_PREFERENCES.mapNotNull { ids ->
        val candidates = ids.mapNotNull(byId::get)
        val patterns = candidates.map { ExerciseSwapEngine.profileFor(it).pattern }.toSet()
        // Around-the-worlds is catalogued as a push but is a single-joint isolation.
        val planId = planWeightedMainIds.firstOrNull { id ->
            ExerciseSwapEngine.profileFor(byId.getValue(id)).pattern in patterns
        }
        planId ?: candidates.firstOrNull { fits(it) }?.id
    }

    val suggestedSet = suggestedIds.toSet()
    val extraTestedIds = latestById.values
        .filter { it.exerciseId !in suggestedSet && it.exerciseId in byId }
        .filter { fits(byId.getValue(it.exerciseId)) }
        .sortedByDescending { it.createdAt }
        .map { it.exerciseId }

    fun toItem(id: String, isSuggested: Boolean): BaselineMoveItem? {
        val ex = byId[id] ?: return null
        val unit = settings.weightUnitFor(ex.unitOverride)
        val repsMin = planRepsMin[id] ?: DEFAULT_WEIGHTED_REPS_MIN
        val latest = latestById[id]?.takeIf { it.kind == ex.kind.name }
        val target = latest?.let {
            computeTargetDisplay(
                exercise = ex,
                test = it,
                library = library,
                equipment = equipment,
                unit = unit,
                repsMin = repsMin,
            )
        }
        return BaselineMoveItem(
            exercise = ex,
            latest = latest,
            target = target,
            unit = unit,
            repsMin = repsMin,
            isSuggested = isSuggested,
        )
    }

    return suggestedIds.mapNotNull { toItem(it, isSuggested = true) } +
        extraTestedIds.mapNotNull { toItem(it, isSuggested = false) }
}

/** Pure target calculation for a stored baseline test row. */
internal fun computeTargetDisplay(
    exercise: ExerciseEntity,
    test: BaselineTestEntity,
    library: List<ExerciseEntity>,
    equipment: Set<Equipment>,
    unit: WeightUnit,
    repsMin: Int = DEFAULT_WEIGHTED_REPS_MIN,
): BaselineTargetDisplay = when (exercise.kind) {
    ExerciseKind.REPS -> {
        val reps = test.reps ?: 0
        val target = BaselineTargets.repsTarget(reps)
        if (target != null) {
            val harder = if (BaselineTargets.isOutgrown(exercise.kind, reps)) {
                BaselineTargets.harderVariation(exercise, library, equipment)
            } else {
                null
            }
            BaselineTargetDisplay.Reps(target, harder)
        } else {
            BaselineTargetDisplay.EasierVariation(
                BaselineTargets.easierVariation(exercise, library, equipment),
            )
        }
    }
    ExerciseKind.HOLD -> {
        val seconds = test.seconds ?: 0
        val target = BaselineTargets.holdTarget(seconds)
        if (target != null) BaselineTargetDisplay.Hold(target) else BaselineTargetDisplay.HoldTooShort
    }
    ExerciseKind.WEIGHTED -> {
        val wKg = test.weightKg ?: 0f
        val r = test.reps ?: 0
        when (
            val res = BaselineTargets.weightedTarget(
                weightKg = unit.fromKg(wKg).toDouble(),
                reps = r,
                repsMin = repsMin,
                loadStep = unit.loadStep.toDouble(),
            )
        ) {
            is WeightedResult.Target -> BaselineTargetDisplay.Weight(unit.toKg(res.weightKg.toFloat()), unit)
            WeightedResult.TooLight -> BaselineTargetDisplay.TooLight
            WeightedResult.NoTarget -> BaselineTargetDisplay.WeightNoTarget
        }
    }
}

private const val PLANK_ID = "plank"
internal const val DEFAULT_WEIGHTED_REPS_MIN = 8
internal const val BASELINE_HOLD_BENCHMARK_SECONDS = 60
internal const val BASELINE_REST_SECONDS = 90

class BaselineViewModel(
    private val baselines: BaselineRepository,
    private val libraryFlow: Flow<List<ExerciseEntity>>,
    activePlanFlow: Flow<ActivePlan?>,
    private val settingsFlow: Flow<Settings>,
    private val haptics: Haptics? = null,
    val initialExerciseId: String? = null,
    private val clockMillis: () -> Long = { System.currentTimeMillis() },
    private val callerEquipment: Set<Equipment>? = null,
) : ViewModel() {

    constructor(app: AppContainer, initialExerciseId: String? = null, callerEquipment: Set<Equipment>? = null) : this(
        baselines = app.baselines,
        libraryFlow = app.workouts.library,
        activePlanFlow = app.workouts.activePlan,
        settingsFlow = app.settings.settings,
        haptics = app.haptics,
        initialExerciseId = initialExerciseId,
        callerEquipment = callerEquipment,
    )

    val library: StateFlow<List<ExerciseEntity>> = libraryFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val settings: StateFlow<Settings> = settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    val userEquipment: StateFlow<Set<Equipment>> = settings.map { expandLegacyEquipment(callerEquipment ?: it.configuredEquipment) + Equipment.BODYWEIGHT }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val bodyType: StateFlow<BodyType> = settings.map { it.bodyType }
        .stateIn(viewModelScope, SharingStarted.Eagerly, BodyType.MALE)

    private val latestById: StateFlow<Map<String, BaselineTestEntity>> = baselines.observeLatest()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val activePlan: StateFlow<ActivePlan?> = activePlanFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val moves: StateFlow<List<BaselineMoveItem>> = combine(
        library, settings, activePlan, latestById,
    ) { lib, s, plan, latest ->
        buildBaselineMoves(lib, s, plan, latest, callerEquipment)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val activeExerciseId = MutableStateFlow(initialExerciseId?.takeIf { it.isNotBlank() })
    val lastResult = MutableStateFlow<BaselineSaveResult?>(null)
    val hold = MutableStateFlow<HoldTimer?>(null)
    val restEndsAt = MutableStateFlow<Long?>(null)

    val saving = MutableStateFlow(false)
    val saveFailed = MutableStateFlow(false)

    private var lastHoldCueSecond = Int.MIN_VALUE

    fun openMove(exerciseId: String) {
        activeExerciseId.value = exerciseId
        lastResult.value = null
        restEndsAt.value = null
        hold.value = null
    }

    fun closeMove() {
        activeExerciseId.value = null
        lastResult.value = null
        hold.value = null
    }

    fun retest() {
        lastResult.value = null
        restEndsAt.value = null
    }

    fun startHold(exercise: ExerciseEntity) {
        restEndsAt.value = null
        lastHoldCueSecond = Int.MIN_VALUE
        hold.value = HoldTimer(
            setId = 0L,
            exerciseName = exercise.name,
            target = BASELINE_HOLD_BENCHMARK_SECONDS,
            startsAt = clockMillis() + 3000L,
        )
    }

    fun onHoldTick(nowMillis: Long) {
        val h = hold.value ?: return
        val s = h.elapsed(nowMillis)
        if (s == lastHoldCueSecond) return
        lastHoldCueSecond = s
        val left = h.target - s
        when {
            s == h.target -> haptics?.celebrate()
            s < 0 || left in 1..3 || s == h.target / 2 -> haptics?.tick()
        }
    }

    fun cancelHold() {
        hold.value = null
    }

    fun stopHold(exerciseId: String, nowMillis: Long = clockMillis()) {
        val h = hold.value ?: return
        hold.value = null
        val held = h.elapsed(nowMillis).coerceIn(0, 3600)
        if (held <= 0) {
            viewModelScope.launch {
                val ex = library.value.firstOrNull { it.id == exerciseId }
                    ?: libraryFlow.first().firstOrNull { it.id == exerciseId }
                    ?: return@launch
                lastResult.value = BaselineSaveResult(
                    exercise = ex,
                    test = BaselineTestEntity(
                        exerciseId = exerciseId,
                        kind = ExerciseKind.HOLD.name,
                        seconds = 0,
                        createdAt = clockMillis(),
                    ),
                    target = BaselineTargetDisplay.HoldTooShort,
                    unit = settings.value.weightUnitFor(ex.unitOverride),
                )
            }
            return
        }
        saveHold(exerciseId, held)
    }

    fun skipRest() {
        restEndsAt.value = null
        haptics?.tick()
    }

    fun onRestExpired() {
        if (restEndsAt.value != null) {
            restEndsAt.value = null
            haptics?.check()
        }
    }

    private fun save(block: suspend () -> Unit) {
        if (saving.value) return
        saving.value = true
        saveFailed.value = false
        viewModelScope.launch {
            try {
                com.forgequest.app.data.suspendRunCatching { block() }
                    .onFailure { saveFailed.value = true }
            } finally {
                saving.value = false
            }
        }
    }

    fun saveReps(exerciseId: String, reps: Int) = save { recordReps(exerciseId, reps) }

    fun saveHold(exerciseId: String, seconds: Int) = save { recordHold(exerciseId, seconds) }

    fun saveWeighted(exerciseId: String, displayWeight: Float, reps: Int) = save { recordWeighted(exerciseId, displayWeight, reps) }

    internal suspend fun recordReps(exerciseId: String, reps: Int): BaselineSaveResult? {
        val cleanReps = reps.coerceIn(0, 999)
        return persistAndCompute(
            exerciseId = exerciseId,
            kind = ExerciseKind.REPS,
            reps = cleanReps,
            seconds = null,
            weightKg = null,
        )
    }

    internal suspend fun recordHold(exerciseId: String, seconds: Int): BaselineSaveResult? {
        val cleanSeconds = seconds.coerceIn(0, 3600)
        return persistAndCompute(
            exerciseId = exerciseId,
            kind = ExerciseKind.HOLD,
            reps = null,
            seconds = cleanSeconds,
            weightKg = null,
        )
    }

    internal suspend fun recordWeighted(
        exerciseId: String,
        displayWeight: Float,
        reps: Int,
    ): BaselineSaveResult? {
        val lib = library.value.ifEmpty { libraryFlow.first() }
        val ex = lib.firstOrNull { it.id == exerciseId } ?: return null
        val curSettings = settingsFlow.first()
        val unit = curSettings.weightUnitFor(ex.unitOverride)
        val weightKg = unit.toKg(displayWeight).coerceIn(0f, 999f)
        val cleanReps = reps.coerceIn(1, 999)
        return persistAndCompute(
            exerciseId = exerciseId,
            kind = ExerciseKind.WEIGHTED,
            reps = cleanReps,
            seconds = null,
            weightKg = weightKg,
        )
    }

    private suspend fun persistAndCompute(
        exerciseId: String,
        kind: ExerciseKind,
        reps: Int?,
        seconds: Int?,
        weightKg: Float?,
    ): BaselineSaveResult? {
        val lib = library.value.ifEmpty { libraryFlow.first() }
        val ex = lib.firstOrNull { it.id == exerciseId } ?: return null
        if (ex.kind != kind) return null
        val curSettings = settingsFlow.first()
        val equipment = expandLegacyEquipment(callerEquipment ?: curSettings.configuredEquipment) + Equipment.BODYWEIGHT
        val unit = curSettings.weightUnitFor(ex.unitOverride)
        val plan = activePlan.value
        val repsMin = plan?.days
            ?.sortedBy { it.day.dayOfWeek }
            ?.flatMap { it.items.sortedBy { item -> item.position } }
            ?.firstOrNull { it.exerciseId == exerciseId && it.repsMin != null }
            ?.repsMin
            ?: DEFAULT_WEIGHTED_REPS_MIN

        val now = clockMillis()
        val entity = BaselineTestEntity(
            exerciseId = exerciseId,
            kind = kind.name,
            reps = reps,
            seconds = seconds,
            weightKg = weightKg,
            createdAt = now,
        )
        baselines.save(entity)
        haptics?.setDone()

        val display = computeTargetDisplay(
            exercise = ex,
            test = entity,
            library = lib,
            equipment = equipment,
            unit = unit,
            repsMin = repsMin,
        )
        val result = BaselineSaveResult(
            exercise = ex,
            test = entity,
            target = display,
            unit = unit,
        )
        lastResult.value = result
        restEndsAt.value = now + BASELINE_REST_SECONDS * 1000L
        return result
    }
}
