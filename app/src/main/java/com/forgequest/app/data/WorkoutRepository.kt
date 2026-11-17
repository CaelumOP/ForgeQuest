package com.forgequest.app.data

import androidx.room.withTransaction
import com.forgequest.app.AppText
import com.forgequest.app.R
import com.forgequest.app.data.db.AppDatabase
import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.db.PlanDayEntity
import com.forgequest.app.data.db.PlanEntity
import com.forgequest.app.data.db.PlanItemEntity
import com.forgequest.app.data.db.SessionEntity
import com.forgequest.app.data.db.SetEntity
import com.forgequest.app.data.plan.PlanDraft
import com.forgequest.app.data.plan.PlanDraftDay
import com.forgequest.app.data.plan.PlanDraftItem
import com.forgequest.app.data.prefs.SettingsRepository
import com.forgequest.app.data.seed.ExerciseCatalog
import com.forgequest.app.domain.Dates
import com.forgequest.app.domain.ExerciseKind
import com.forgequest.app.domain.ExerciseSwapEngine
import com.forgequest.app.domain.Muscle
import com.forgequest.app.domain.PrPicker
import com.forgequest.app.domain.Progression
import com.forgequest.app.domain.ProgressionMode
import com.forgequest.app.domain.PlanShare
import com.forgequest.app.domain.Records
import com.forgequest.app.domain.Rotation
import com.forgequest.app.domain.ScheduleMode
import com.forgequest.app.domain.SetType
import com.forgequest.app.domain.SupersetRules
import com.forgequest.app.domain.WarmupRamp
import com.forgequest.app.domain.WeightUnit
import com.forgequest.app.domain.StreakCalculator
import com.forgequest.app.domain.Trends
import com.forgequest.app.domain.WorkoutDebriefMath
import com.forgequest.app.domain.WorkoutScore
import com.forgequest.app.domain.countsForPR
import com.forgequest.app.domain.countsForScore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max

data class PlanDayWithItems(val day: PlanDayEntity, val items: List<PlanItemEntity>)
data class ActivePlan(val plan: PlanEntity, val days: List<PlanDayWithItems>) {
    fun day(dayOfWeek: Int) = days.firstOrNull { it.day.dayOfWeek == dayOfWeek }
    val trainingDays: Set<Int> get() = days.filter { !it.day.isRest && it.items.isNotEmpty() }.map { it.day.dayOfWeek }.toSet()
}

/** The active plan in the same shape as a proposal, for comparing (PlanHash) and applying coach changes. */
fun ActivePlan.toDraft(): PlanDraft = PlanDraft(
    name = plan.name,
    description = plan.description,
    days = days.sortedBy { it.day.dayOfWeek }.map { d ->
        PlanDraftDay(
            dayOfWeek = d.day.dayOfWeek,
            title = d.day.title,
            isRest = d.day.isRest,
            note = d.day.note,
            items = d.items.sortedBy { it.position }.map { i ->
                PlanDraftItem(
                    exerciseId = i.exerciseId, block = i.block, circuit = i.circuit, sets = i.sets, repsMin = i.repsMin,
                    repsMax = i.repsMax, seconds = i.seconds, weightKg = i.weightKg, restSeconds = i.restSeconds, note = i.note,
                )
            },
        )
    },
)

data class FinishResult(
    val sessionTitle: String,
    val doneSets: Int,
    val prs: List<String>,
    val xpGained: Int,
    /** When the workout counts as ended (epoch ms); shown minutes use it too. */
    val endedAt: Long = System.currentTimeMillis(),
    /** True when the end was pulled back to the last set because the app sat idle. */
    val clipped: Boolean = false,
)

/** [settings] gives progression the weight unit and the readiness switch; without it (tests) steps are in kg. */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutRepository(private val db: AppDatabase, private val settings: SettingsRepository? = null) {

    private val plans = db.plans()
    private val sessions = db.sessions()
    private val exercises = db.exercises()
    private val seedMutex = Mutex()
    @Volatile private var lastSeededLang: String? = null

    suspend fun seedLibrary() = seedMutex.withLock {
        val arabic = java.util.Locale.getDefault().language == "ar"
        val langTag = if (arabic) "ar" else "en"
        db.withTransaction {
            exercises.insertAll(ExerciseCatalog.all)
            val existingById = exercises.all().associateBy { it.id }
            val toUpsert = mutableListOf<ExerciseEntity>()

            for (builtIn in ExerciseCatalog.all) {
                val existing = existingById[builtIn.id] ?: continue
                if (existing.custom) continue
                val ar = com.forgequest.app.data.seed.ExerciseNamesAr.byId[builtIn.id]
                val wantedName = if (arabic && ar != null) ar else builtIn.name
                val prevAr = com.forgequest.app.data.seed.ExerciseNamesAr.previousAll[builtIn.id].orEmpty()
                val recognizedName = existing.name == ar || existing.name == builtIn.name || existing.name in prevAr
                val nameNeedsUpdate = existing.name != wantedName && recognizedName
                val videoNeedsUpdate = existing.videoUrl == null && builtIn.videoUrl != null
                val newEquipment = if (existing.equipment.isBlank() && builtIn.equipment.isNotBlank()) builtIn.equipment else existing.equipment
                val newPattern = if (existing.pattern.isBlank() && builtIn.pattern.isNotBlank()) builtIn.pattern else existing.pattern
                val newAliases = if (existing.aliases.isBlank() && builtIn.aliases.isNotBlank()) builtIn.aliases else existing.aliases
                val newMediaKey = if (existing.mediaKey.isBlank() && builtIn.mediaKey.isNotBlank()) builtIn.mediaKey else existing.mediaKey
                val metadataNeedsUpdate =
                    newEquipment != existing.equipment ||
                        newPattern != existing.pattern ||
                        newAliases != existing.aliases ||
                        newMediaKey != existing.mediaKey
                if (nameNeedsUpdate || videoNeedsUpdate || metadataNeedsUpdate) {
                    toUpsert += existing.copy(
                        name = if (nameNeedsUpdate) wantedName else existing.name,
                        videoUrl = if (videoNeedsUpdate) builtIn.videoUrl else existing.videoUrl,
                        equipment = newEquipment,
                        pattern = newPattern,
                        aliases = newAliases,
                        mediaKey = newMediaKey,
                    )
                }
            }
            if (toUpsert.isNotEmpty()) {
                exercises.upsertAll(toUpsert)
            }

            if (lastSeededLang != langTag) {
                for ((en, ar) in com.forgequest.app.data.seed.StarterPlans.arabic) {
                    val (from, to) = if (arabic) en to ar else ar to en
                    plans.renamePlans(from, to)
                    plans.redescribePlans(from, to)
                    plans.retitleDays(from, to)
                    plans.renoteDays(from, to)
                }
                lastSeededLang = langTag
            }
        }
    }

    val library: Flow<List<ExerciseEntity>> = exercises.observeAll()

    val activePlan: Flow<ActivePlan?> = plans.observeActive().flatMapLatest { plan ->
        if (plan == null) {
            flowOf(null)
        } else {
            combine(plans.observeDays(plan.id), plans.observeAllItems(plan.id)) { days, items ->
                ActivePlan(plan, days.map { d -> PlanDayWithItems(d, items.filter { it.dayId == d.id }) })
            }
        }
    }

    /** Saves [draft] as the active plan, creating custom exercises for ids the library doesn't know. */
    suspend fun applyPlan(draft: PlanDraft, source: String, scheduleMode: String = ScheduleMode.WEEKDAY.name): Long = db.withTransaction {
        for (day in draft.days) for (item in day.items) {
            val existing = exercises.byId(item.exerciseId)
            if (existing == null) {
                val kind = runCatching { ExerciseKind.valueOf(item.kind ?: "") }.getOrElse {
                    if (item.seconds != null) ExerciseKind.HOLD else if (item.weightKg != null) ExerciseKind.WEIGHTED else ExerciseKind.REPS
                }
                val muscles = item.muscles.mapNotNull { m -> runCatching { Muscle.valueOf(m) }.getOrNull() }
                exercises.upsert(
                    ExerciseEntity(
                        id = item.exerciseId,
                        name = item.name ?: item.exerciseId.replace('_', ' ').replaceFirstChar { it.uppercase() },
                        kind = kind,
                        primary = muscles.take(2).ifEmpty { listOf(Muscle.ABS) },
                        secondary = muscles.drop(2),
                        videoUrl = item.videoUrl,
                        custom = true,
                    )
                )
            } else if (!item.videoUrl.isNullOrBlank()) {
                val builtInLink = ExerciseCatalog.all.firstOrNull { it.id == existing.id }?.videoUrl
                val userEdited = existing.videoUrl != null && existing.videoUrl != builtInLink
                if (existing.videoUrl != item.videoUrl && !(userEdited && item.videoUrl == builtInLink)) {
                    exercises.upsert(existing.copy(videoUrl = item.videoUrl))
                }
            }
        }
        plans.deactivateAll()
        val planId = plans.insertPlan(
            PlanEntity(
                name = draft.name,
                description = draft.description,
                source = source,
                active = true,
                createdAt = System.currentTimeMillis(),
                scheduleMode = ScheduleMode.parse(scheduleMode).name,
                rotationCursor = 0,
            )
        )
        for (dow in 1..7) {
            val day = draft.days.firstOrNull { it.dayOfWeek == dow }
            val dayId = plans.insertDay(
                PlanDayEntity(
                    planId = planId,
                    dayOfWeek = dow,
                    title = day?.title ?: AppText.get(R.string.today_rest_day),
                    isRest = day == null || day.isRest || day.items.isEmpty(),
                    note = day?.note ?: "",
                )
            )
            if (day != null) {
                val items = day.items.mapIndexed { index, item ->
                    val ex = exercises.byId(item.exerciseId)
                    val autoWarmup = !WorkoutScore.isExtra(item.block) && WarmupRamp.isHeavyBarbellCompound(ex)
                    PlanItemEntity(
                        dayId = dayId, position = index, exerciseId = item.exerciseId, block = item.block, circuit = item.circuit,
                        sets = item.sets.coerceIn(1, 10), repsMin = item.repsMin, repsMax = item.repsMax, seconds = item.seconds,
                        weightKg = item.weightKg, restSeconds = item.restSeconds, note = item.note,
                        autoWarmup = autoWarmup,
                    )
                }
                plans.insertItems(items)
            }
        }
        planId
    }

    /** Safely imports a validated shared plan as a new active plan without overwriting any existing plans. */
    suspend fun importSharedPlan(shared: PlanShare.SharedPlan): Long = db.withTransaction {
        val existingNames = mutableListOf<String>()
        db.openHelper.writableDatabase.query("SELECT name FROM plans").use { c ->
            while (c.moveToNext()) existingNames += c.getString(0)
        }
        val safeName = PlanShare.safeImportName(shared.name, existingNames)
        for (day in shared.days) for (item in day.items) {
            val existing = exercises.byId(item.exerciseId)
            if (existing == null) {
                val kind = runCatching { ExerciseKind.valueOf(item.kind ?: "") }.getOrElse {
                    if (item.seconds != null) ExerciseKind.HOLD else if (item.weightKg != null) ExerciseKind.WEIGHTED else ExerciseKind.REPS
                }
                val muscles = item.muscles.mapNotNull { m -> runCatching { Muscle.valueOf(m) }.getOrNull() }
                exercises.upsert(
                    ExerciseEntity(
                        id = item.exerciseId,
                        name = item.name ?: item.exerciseId.replace('_', ' ').replaceFirstChar { it.uppercase() },
                        kind = kind,
                        primary = muscles.take(2).ifEmpty { listOf(Muscle.ABS) },
                        secondary = muscles.drop(2),
                        videoUrl = item.videoUrl,
                        custom = true,
                    )
                )
            }
        }
        plans.deactivateAll()
        val planId = plans.insertPlan(
            PlanEntity(
                name = safeName,
                description = shared.description,
                source = "manual",
                active = true,
                createdAt = System.currentTimeMillis(),
                scheduleMode = ScheduleMode.parse(shared.scheduleMode).name,
                rotationCursor = 0,
            )
        )
        for (dow in 1..7) {
            val day = shared.days.firstOrNull { it.dayOfWeek == dow }
            val dayId = plans.insertDay(
                PlanDayEntity(
                    planId = planId,
                    dayOfWeek = dow,
                    title = day?.title ?: AppText.get(R.string.today_rest_day),
                    isRest = day == null || day.isRest || day.items.isEmpty(),
                    note = day?.note ?: "",
                )
            )
            if (day != null && day.items.isNotEmpty()) {
                val raw = day.items.mapIndexed { index, item ->
                    PlanItemEntity(
                        dayId = dayId,
                        position = index,
                        exerciseId = item.exerciseId,
                        block = item.block,
                        circuit = item.circuit,
                        sets = item.sets.coerceIn(1, 10),
                        repsMin = item.repsMin,
                        repsMax = item.repsMax,
                        seconds = item.seconds,
                        weightKg = item.weightKg,
                        restSeconds = item.restSeconds,
                        note = item.note,
                        supersetGroup = item.supersetGroup,
                        autoWarmup = item.autoWarmup,
                        progressionMode = ProgressionMode.parse(item.progressionMode).name,
                    )
                }
                plans.insertItems(SupersetRules.normalizeDaySupersets(raw))
            }
        }
        planId
    }

    /** Switches a plan between WEEKDAY and ROTATION scheduling; resets/clamps rotationCursor safely. */
    suspend fun setPlanScheduleMode(planId: Long, mode: ScheduleMode) = db.withTransaction {
        val plan = plans.byId(planId) ?: return@withTransaction
        val queueSize = plans.days(planId).count { !it.isRest && plans.items(it.id).isNotEmpty() }
        val nextCursor = if (mode == ScheduleMode.ROTATION) {
            if (ScheduleMode.parse(plan.scheduleMode) == ScheduleMode.ROTATION) Rotation.cursorAfterPlanEdit(plan.rotationCursor, queueSize) else 0
        } else {
            plan.rotationCursor
        }
        updatePlanScheduleColumns(planId, mode.name, nextCursor, plan.active)
    }

    private suspend fun updatePlanScheduleColumns(planId: Long, scheduleMode: String, rotationCursor: Int, isActive: Boolean) {
        db.openHelper.writableDatabase.execSQL(
            "UPDATE plans SET scheduleMode = ?, rotationCursor = ? WHERE id = ?",
            arrayOf<Any>(scheduleMode, rotationCursor, planId),
        )
        if (isActive) plans.activate(planId) else db.invalidationTracker.refreshVersionsAsync()
    }

    private suspend fun clampRotationCursorForPlan(planId: Long) {
        val plan = plans.byId(planId) ?: return
        if (ScheduleMode.parse(plan.scheduleMode) != ScheduleMode.ROTATION) return
        val queueSize = plans.days(planId).count { !it.isRest && plans.items(it.id).isNotEmpty() }
        val next = Rotation.cursorAfterPlanEdit(plan.rotationCursor, queueSize)
        if (next != plan.rotationCursor) {
            updatePlanScheduleColumns(planId, plan.scheduleMode, next, plan.active)
        }
    }

    /** Every plan ever applied, newest first. Switching back is instant; nothing is lost on Apply. */
    val allPlans: Flow<List<PlanEntity>> = plans.observeAll()

    suspend fun activatePlan(planId: Long) = db.withTransaction {
        if (plans.byId(planId) == null) return@withTransaction
        plans.deactivateAll()
        plans.activate(planId)
    }

    /** Removes an inactive plan. Workouts done under it keep their sets; only the schedule goes. */
    suspend fun deletePlan(planId: Long) = db.withTransaction {
        val plan = plans.byId(planId) ?: return@withTransaction
        if (plan.active) return@withTransaction
        plans.deleteItemsOfPlan(planId)
        plans.deleteDaysOfPlan(planId)
        plans.deletePlan(planId)
    }

    /** Moves an exercise one step up (-1) or down (+1) within its day. */
    suspend fun movePlanItem(item: PlanItemEntity, delta: Int) = db.withTransaction {
        val items = plans.items(item.dayId).sortedBy { it.position }
        val index = items.indexOfFirst { it.id == item.id }
        val target = index + delta
        if (index < 0 || target !in items.indices) return@withTransaction
        val reordered = items.toMutableList().also { it.add(target, it.removeAt(index)) }
            .mapIndexed { i, it -> it.copy(position = i) }
        val normalized = SupersetRules.normalizeDaySupersets(reordered)
        normalized.forEachIndexed { i, updated ->
            val orig = items.firstOrNull { it.id == updated.id }
            if (orig != updated) plans.updateItem(updated)
        }
    }

    /** Swaps two weekdays of the active plan, e.g. to make the week start on Saturday. */
    suspend fun swapPlanDays(planId: Long, dayA: Int, dayB: Int) = db.withTransaction {
        if (dayA == dayB) return@withTransaction
        val days = plans.days(planId)
        val a = days.firstOrNull { it.dayOfWeek == dayA } ?: return@withTransaction
        val b = days.firstOrNull { it.dayOfWeek == dayB } ?: return@withTransaction
        // Park A on an unused value so the (planId, dayOfWeek) pair never collides mid-swap.
        plans.updateDay(a.copy(dayOfWeek = 0))
        plans.updateDay(b.copy(dayOfWeek = dayA))
        plans.updateDay(a.copy(dayOfWeek = dayB))
    }

    suspend fun updatePlanItem(item: PlanItemEntity) = db.withTransaction {
        plans.updateItem(item)
        normalizeDaySupersetsInDb(item.dayId)
    }

    /** Pairs or unpairs [itemId] with the next exercise on the same day when both have the same working-set count. */
    suspend fun setPairWithNext(dayId: Long, itemId: Long, pair: Boolean) = db.withTransaction {
        val items = plans.items(dayId).sortedBy { it.position }
        val index = items.indexOfFirst { it.id == itemId }
        if (index < 0) return@withTransaction
        val updated = SupersetRules.setPairWithNext(items, index, pair)
        updated.forEachIndexed { idx, next ->
            if (items[idx] != next) plans.updateItem(next)
        }
    }

    private suspend fun normalizeDaySupersetsInDb(dayId: Long) {
        val items = plans.items(dayId).sortedBy { it.position }
        val normalized = SupersetRules.normalizeDaySupersets(items)
        normalized.forEachIndexed { idx, next ->
            if (items[idx] != next) plans.updateItem(next)
        }
    }

    suspend fun deletePlanItem(id: Long) = db.withTransaction {
        var dayId: Long? = null
        db.openHelper.writableDatabase.query("SELECT dayId FROM plan_items WHERE id = ?", arrayOf<Any>(id)).use { c ->
            if (c.moveToFirst()) dayId = c.getLong(0)
        }
        plans.deleteItem(id)
        dayId?.let { did ->
            normalizeDaySupersetsInDb(did)
            plans.day(did)?.let { clampRotationCursorForPlan(it.planId) }
        }
    }

    suspend fun restorePlanItem(item: PlanItemEntity) = db.withTransaction {
        plans.restoreItem(item)
        normalizeDaySupersetsInDb(item.dayId)
        plans.day(item.dayId)?.let { clampRotationCursorForPlan(it.planId) }
    }

    suspend fun updatePlanDay(day: PlanDayEntity) = db.withTransaction {
        plans.updateDay(day)
        clampRotationCursorForPlan(day.planId)
    }

    /** Swaps an exercise on a plan day while translating its set/rep/hold/weight prescription biomechanically. */
    suspend fun swapPlanItem(item: PlanItemEntity, newExerciseId: String) = db.withTransaction {
        val newEx = exercises.byId(newExerciseId) ?: return@withTransaction
        val translated = ExerciseSwapEngine.translatePlanItem(item, newEx)
        plans.updateItem(translated)
    }

    suspend fun addPlanItem(dayId: Long, exerciseId: String) {
        val ex = exercises.byId(exerciseId) ?: return
        val existing = plans.items(dayId)
        plans.insertItem(
            PlanItemEntity(
                dayId = dayId, position = (existing.maxOfOrNull { it.position } ?: -1) + 1, exerciseId = exerciseId,
                block = "Main", circuit = false, sets = 3,
                repsMin = if (ex.kind == ExerciseKind.HOLD) null else 10,
                seconds = if (ex.kind == ExerciseKind.HOLD) 30 else null,
                weightKg = if (ex.kind == ExerciseKind.WEIGHTED) 0f else null,
                restSeconds = 90,
            )
        )
        val day = plans.day(dayId)
        if (day != null) {
            if (day.isRest) plans.updateDay(day.copy(isRest = false, title = if (day.title == "Rest day" || day.title == AppText.get(R.string.today_rest_day)) AppText.get(R.string.plan_workout) else day.title))
            clampRotationCursorForPlan(day.planId)
        }
    }

    suspend fun addCustomExercise(name: String, kind: ExerciseKind, primary: List<Muscle>, videoUrl: String?): String {
        val base = name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifEmpty { "exercise" }
        var id = "custom_$base"
        var n = 2
        while (exercises.byId(id) != null) id = "custom_${base}_${n++}"
        exercises.upsert(ExerciseEntity(id, name.trim(), kind, primary, emptyList(), videoUrl?.ifBlank { null }, custom = true))
        return id
    }

    data class ExerciseUsage(val sets: Int, val planItems: Int)

    suspend fun usage(exerciseId: String) = ExerciseUsage(exercises.setCount(exerciseId), exercises.planItemCount(exerciseId))

    suspend fun updateExercise(ex: ExerciseEntity) = exercises.upsert(ex)

    /**
     * Folds [fromId] into [toId]: every logged set and plan item moves over, the duplicate is removed and the
     * target's records are re-checked. This is how "push_ups" invented by the coach becomes the real "push_up".
     */
    suspend fun mergeExercise(fromId: String, toId: String) = db.withTransaction {
        if (fromId == toId || exercises.byId(toId) == null) return@withTransaction
        exercises.repointSets(fromId, toId)
        exercises.repointPlanItems(fromId, toId)
        exercises.delete(fromId)
        recomputeRecords(toId)
    }

    /** Deletes a custom exercise nothing refers to. Returns false if it's built in or still in use. */
    suspend fun deleteExercise(exerciseId: String): Boolean = db.withTransaction {
        val ex = exercises.byId(exerciseId) ?: return@withTransaction false
        if (!ex.custom) return@withTransaction false
        if (exercises.setCount(exerciseId) > 0 || exercises.planItemCount(exerciseId) > 0) return@withTransaction false
        exercises.delete(exerciseId)
        true
    }

    /** Saves a form-video link. Returns false (and changes nothing) when it isn't a web address. */
    suspend fun setVideoUrl(exerciseId: String, url: String?): Boolean {
        val ex = exercises.byId(exerciseId) ?: return false
        val clean = url?.trim()?.ifEmpty { null }
        if (clean != null && !isWebUrl(clean)) return false
        exercises.upsert(ex.copy(videoUrl = clean))
        return true
    }

    companion object {
        fun isWebUrl(s: String): Boolean = Regex("""^https?://[^\s/$.?#].[^\s]*$""", RegexOption.IGNORE_CASE).matches(s)
    }

    // ------------------------------------------------------------ Live session

    val openSession: Flow<SessionEntity?> = sessions.observeOpen()
    suspend fun openSessionNow(): SessionEntity? = sessions.open()

    fun observeSets(sessionId: Long): Flow<List<SetEntity>> = sessions.observeSets(sessionId)

    suspend fun startSession(day: PlanDayWithItems?): Long = db.withTransaction {
        sessions.open()?.let { return@withTransaction it.id }
        val now = System.currentTimeMillis()
        val id = sessions.insert(
            SessionEntity(planDayId = day?.day?.id, title = day?.day?.title ?: AppText.get(R.string.quick_workout_title), epochDay = Dates.today(), startedAt = now)
        )
        val sets = mutableListOf<SetEntity>()
        day?.items?.forEach { item ->
            sets += buildSetsFor(id, item.position, item, exercises.byId(item.exerciseId))
        }
        if (sets.isNotEmpty()) sessions.insertSets(sets)
        id
    }

    /** The rules' answer for one exercise: [recent] is its done sets, newest workout first. Shared by the prefill and the live card's note. */
    suspend fun progressionFor(item: PlanItemEntity?, ex: ExerciseEntity?, recent: List<SetEntity>): Progression.Result {
        val createdAt = plans.active()?.createdAt
        // A deload session had half the sets, so it would read as "missed" next time: progress from the session before it.
        val deloadIds = if (createdAt == null) emptySet() else recent.map { it.sessionId }.distinct()
            .filter { id -> sessions.byId(id)?.let { Progression.isDeloadWeek(createdAt, it.epochDay) } == true }.toSet()
        val usable = recent.filter { it.sessionId !in deloadIds }.ifEmpty { recent }
        val deload = createdAt != null && item != null && !WorkoutScore.isExtra(item.block) && Progression.isDeloadWeek(createdAt, Dates.today())
        val input = Progression.inputs(item, ex?.id, usable)
        val s = settings?.current()
        val unit = s?.weightUnitFor(ex?.unitOverride.orEmpty()) ?: WeightUnit.KG
        return Progression.next(
            kind = ex?.kind ?: ExerciseKind.REPS,
            target = input.target,
            last = input.last,
            previous = input.previous,
            unit = unit,
            warmup = input.warmup,
            deload = deload,
            mode = input.mode,
            lowerBody = Progression.isLowerBody(ex?.primary.orEmpty()),
        )
    }

    /** This week is a lighter one in the active plan's 4-week cycle. */
    suspend fun isDeloadWeekNow(): Boolean = plans.active()?.let { Progression.isDeloadWeek(it.createdAt, Dates.today()) } == true

    /** One exercise's done sets from finished workouts, newest workout first, as [progressionFor] expects them. */
    suspend fun recentDoneSets(exerciseId: String): List<SetEntity> = sessions.recentDoneSets(exerciseId)

    /** The plan item behind one position of [session], null for a quick workout or an added exercise. */
    suspend fun planItemAt(session: SessionEntity, position: Int): PlanItemEntity? =
        session.planDayId?.let { plans.items(it).firstOrNull { item -> item.position == position } }

    /**
     * Prefills today's sets from the last two finished sessions of [ex] using [Progression]: load and reps
     * only go up after clean sessions, never on a low-readiness day, and in the person's own unit.
     * When [item.autoWarmup] is enabled on a weighted move, prepends 2–3 WARMUP ramp sets before Set 1.
     */
    private suspend fun buildSetsFor(sessionId: Long, position: Int, item: PlanItemEntity?, ex: ExerciseEntity?): List<SetEntity> {
        val kind = ex?.kind ?: ExerciseKind.REPS
        val result = progressionFor(item, ex, ex?.let { sessions.recentDoneSets(it.id) }.orEmpty())
        val label = targetLabel(kind, item).let { base ->
            if (result.holdCeilingReached) listOf(base, AppText.get(R.string.rx_hold_ceiling)).filter { it.isNotBlank() }.joinToString(" · ") else base
        }
        val s = settings?.current()
        val unit = s?.weightUnitFor(ex?.unitOverride.orEmpty()) ?: WeightUnit.KG
        val barWeightKg = s?.barWeightKg ?: 20f
        val firstWorkingKg = result.sets.firstOrNull()?.weightKg ?: 0f
        val ramp = if (item != null && item.autoWarmup && !WorkoutScore.isExtra(item.block) && kind == ExerciseKind.WEIGHTED) {
            WarmupRamp.generate(firstWorkingKg, unit, barWeightKg)
        } else {
            emptyList()
        }
        val warmupEntities = ramp.mapIndexed { idx, r ->
            SetEntity(
                sessionId = sessionId,
                exerciseId = ex?.id ?: item?.exerciseId ?: "",
                itemPosition = position,
                block = item?.block ?: "Main",
                circuit = item?.circuit ?: false,
                setIndex = idx,
                targetLabel = "",
                reps = r.reps,
                seconds = null,
                weightKg = r.weightKg,
                restSeconds = 0,
                setType = SetType.WARMUP.name,
            )
        }
        val workEntities = result.sets.mapIndexed { i, p ->
            SetEntity(
                sessionId = sessionId,
                exerciseId = ex?.id ?: item?.exerciseId ?: "",
                itemPosition = position,
                block = item?.block ?: "Main",
                circuit = item?.circuit ?: false,
                setIndex = ramp.size + i,
                targetLabel = label,
                reps = p.reps,
                seconds = p.seconds,
                weightKg = p.weightKg,
                restSeconds = item?.restSeconds ?: 90,
                setType = SetType.WORKING.name,
            )
        }
        return warmupEntities + workEntities
    }

    /** The calculated score of every finished workout in [snap], from data already loaded plus plan items. With [only], just that workout is scored. */
    suspend fun scores(snap: ProgressSnapshot, only: Long? = null): Map<Long, WorkoutScore.Result> {
        val planned = plans.allItems().groupBy { it.dayId }.mapValues { (_, items) -> plannedSetsOf(items) }
        return WorkoutScore.forSessions(
            snap.sessions, snap.doneSets, snap.exercises.mapValues { it.value.kind }, planned,
            snap.dayTags, snap.plan?.trainingDays ?: emptySet(), only,
        )
    }

    /**
     * The calculated debrief of one finished workout in [snap] ([WorkoutDebriefMath]): this workout, each exercise's
     * previous time, the plan day's exercises, the weekly target, the plateau and deload report as of that day, and the
     * harder versions. Null when the workout isn't in [snap] or has no done sets. Never stored.
     */
    suspend fun debrief(snap: ProgressSnapshot, sessionId: Long): WorkoutDebriefMath.Result? {
        val session = snap.sessions.firstOrNull { it.id == sessionId } ?: return null
        val score = scores(snap, only = sessionId)[sessionId] ?: return null
        val planItems = session.planDayId?.let { day -> plans.allItems().filter { it.dayId == day } }.orEmpty()
        // As of the workout's day: only workouts and sets on or before it, so an old workout keeps its own report.
        val upTo = snap.sessions.filter { it.epochDay <= session.epochDay }
        val upToIds = upTo.mapTo(HashSet()) { it.id }
        val report = Trends.analyzeFatigueAndPlateaus(upTo, snap.doneSets.filter { it.sessionId in upToIds }, snap.exercises, session.epochDay)
        val weeklyTarget = StreakCalculator.fullTarget(snap.plan?.trainingDays ?: emptySet(), snap.settings.weeklyTargetOverride)
        return WorkoutDebriefMath.forSession(
            session = session,
            sessions = snap.sessions,
            doneSets = snap.doneSets,
            exercises = snap.exercises,
            score = score,
            planItems = planItems,
            weeklyTarget = weeklyTarget,
            report = report,
            unit = snap.settings.weightUnit,
            equipment = snap.settings.configuredEquipment,
            variations = variations(snap, report.stalledLifts),
        )
    }

    /** For each stalled lift, the variation to try instead ([VariationCycle]); lifts with none are left out. */
    private fun variations(snap: ProgressSnapshot, stalled: List<com.forgequest.app.domain.StalledLift>): Map<String, String> {
        if (stalled.isEmpty()) return emptyMap()
        val library = snap.exercises.values.toList()
        val planIds = snap.plan?.days.orEmpty().flatMap { d -> d.items.map { it.exerciseId } }.toSet()
        val cycled = com.forgequest.app.domain.VariationCycle.recentlyCycled(snap.settings.cycleHistory, Dates.today())
        return stalled.mapNotNull { lift ->
            com.forgequest.app.domain.VariationCycle.next(lift, library, snap.settings.configuredEquipment, planIds, cycled)?.let { lift.exerciseId to it.to.id }
        }.toMap()
    }

    /**
     * Swaps a stalled lift for its variation everywhere in the active plan and remembers it, so the old lift is not
     * suggested back for [com.forgequest.app.domain.VariationCycle.COOLDOWN_DAYS].
     */
    suspend fun cycleVariation(fromExerciseId: String, toExerciseId: String) {
        val items = plans.allItems().filter { it.exerciseId == fromExerciseId }
        val active = plans.active()?.let { p -> plans.days(p.id).map { it.id }.toSet() }.orEmpty()
        items.filter { it.dayId in active }.forEach { swapPlanItem(it, toExerciseId) }
        settings?.update { it.copy(cycleHistory = com.forgequest.app.domain.VariationCycle.record(it.cycleHistory, fromExerciseId, Dates.today())) }
    }

    /** A plan day's sets for the score: warm-ups left out. */
    private fun plannedSetsOf(items: List<PlanItemEntity>, deload: Boolean = false): Int =
        items.filterNot { WorkoutScore.isExtra(it.block) }.sumOf { if (deload) Progression.deloadSets(it.sets) else it.sets }

    fun targetLabel(kind: ExerciseKind, item: PlanItemEntity?): String {
        if (item == null) return ""
        val reps = when {
            item.repsMin != null && item.repsMax != null && item.repsMax != item.repsMin -> AppText.get(R.string.rx_rep_range, item.repsMin, item.repsMax)
            item.repsMin != null -> AppText.plural(R.plurals.rx_reps, item.repsMin, item.repsMin)
            else -> ""
        }
        return when (kind) {
            ExerciseKind.HOLD -> AppText.get(R.string.rx_seconds, item.seconds ?: 30)
            ExerciseKind.WEIGHTED -> reps
            ExerciseKind.REPS -> reps
        }
    }

    suspend fun updateSet(set: SetEntity) = sessions.updateSet(set)

    suspend fun addSet(sessionId: Long, itemPosition: Int) {
        val existing = sessions.sets(sessionId).filter { it.itemPosition == itemPosition }
        val last = existing.maxByOrNull { it.setIndex } ?: return
        sessions.insertSet(last.copy(id = 0, setIndex = last.setIndex + 1, done = false, isPr = false))
    }

    suspend fun removeSet(setId: Long) = sessions.deleteSet(setId)

    suspend fun addExercise(sessionId: Long, exerciseId: String) {
        val ex = exercises.byId(exerciseId) ?: return
        val position = (sessions.sets(sessionId).maxOfOrNull { it.itemPosition } ?: -1) + 1
        sessions.insertSets(buildSetsFor(sessionId, position, null, ex))
    }

    /** Replaces the exercise at [itemPosition] keeping the set count, block, and translating prescription targets. */
    suspend fun swapExercise(sessionId: Long, itemPosition: Int, newExerciseId: String) = db.withTransaction {
        val ex = exercises.byId(newExerciseId) ?: return@withTransaction
        val old = sessions.sets(sessionId).filter { it.itemPosition == itemPosition }
        if (old.isEmpty()) return@withTransaction
        sessions.deleteSetsAtPosition(sessionId, itemPosition)
        val template = old.first()
        val syntheticItem = PlanItemEntity(
            dayId = 0,
            position = itemPosition,
            exerciseId = newExerciseId,
            block = template.block,
            circuit = template.circuit,
            sets = old.size,
            repsMin = if (ex.kind == ExerciseKind.HOLD) null else (template.reps ?: 10),
            repsMax = if (ex.kind == ExerciseKind.HOLD) null else max(template.reps ?: 10, 12),
            seconds = if (ex.kind == ExerciseKind.HOLD) (template.seconds ?: 30) else null,
            weightKg = if (ex.kind == ExerciseKind.WEIGHTED) 0f else null,
            restSeconds = template.restSeconds,
        )
        val fresh = buildSetsFor(sessionId, itemPosition, syntheticItem, ex)
        sessions.insertSets(fresh)
    }

    /** Positions for [stepUp] that have no done sets yet, so completed today's work is never deleted. */
    internal fun swappableStepUpPositions(positions: List<Int>, sets: List<SetEntity>): List<Int> {
        val donePositions = sets.filter { it.done }.mapTo(HashSet()) { it.itemPosition }
        return positions.distinct().filter { it !in donePositions }
    }

    /** A harder version replaces an easy move: today's sets at unticked [positions], and the plan day the workout came from. Returns swapped position count. */
    suspend fun stepUp(session: SessionEntity, positions: List<Int>, oldId: String, newId: String): Int = db.withTransaction {
        val swappable = swappableStepUpPositions(positions, sessions.sets(session.id))
        swappable.forEach { swapExercise(session.id, it, newId) }
        session.planDayId?.let { dayId -> plans.items(dayId).filter { it.exerciseId == oldId }.forEach { swapPlanItem(it, newId) } }
        swappable.size
    }

    suspend fun discardSession(sessionId: Long) = db.withTransaction {
        sessions.deleteSetsOf(sessionId)
        sessions.delete(sessionId)
    }

    /**
     * Closes the session, optionally ticking all remaining sets first ([tickAll] = true),
     * drops unticked sets in a single query, and flags new personal records.
     */
    suspend fun finishSession(sessionId: Long, tickAll: Boolean = false): FinishResult = db.withTransaction {
        val session = sessions.byId(sessionId) ?: return@withTransaction FinishResult("", 0, emptyList(), 0)
        val wasAlreadyFinished = session.finishedAt != null
        // Sets ticked only by "tick all" are flagged autoTicked=1 so countsForPR excludes them now and forever.
        if (tickAll) {
            sessions.markAllSetsDone(sessionId, System.currentTimeMillis())
        } else {
            sessions.deleteUntickedSetsOf(sessionId)
        }
        val done = sessions.sets(sessionId).filter { it.done }

        // Only workouts dated before this one count, so a backdated log is compared against its own past.
        val earlier = sessions.finished()
            .filter { it.epochDay < session.epochDay || (it.epochDay == session.epochDay && it.startedAt < session.startedAt) }
            .map { it.id }.toSet()
        val kinds = mutableMapOf<String, ExerciseKind>()
        val previousBest = mutableMapOf<String, Float>()
        for (exerciseId in done.map { it.exerciseId }.distinct()) {
            val ex = exercises.byId(exerciseId) ?: continue
            kinds[exerciseId] = ex.kind
            sessions.recentDoneSetsAll(exerciseId)
                .filter { it.sessionId in earlier && countsForPR(it) }
                .maxOfOrNull { Records.score(ex.kind, it.reps, it.seconds, it.weightKg, ex.id) }
                ?.let { previousBest[exerciseId] = it }
        }
        val prIds = PrPicker.pick(done, { kinds[it] ?: ExerciseKind.REPS }, previousBest)
        val prNames = mutableListOf<String>()
        for (s in done.filter { it.id in prIds }) {
            sessions.updateSet(s.copy(isPr = true))
            prNames += exercises.byId(s.exerciseId)?.name ?: continue
        }
        // The plan day's sets as they are now, kept so a later plan edit can't change this workout's score.
        val plannedSets = session.planDayId?.let { plannedSetsOf(plans.items(it), deload = plans.active()?.let { p -> Progression.isDeloadWeek(p.createdAt, session.epochDay) } == true) }?.takeIf { it > 0 }
        val now = System.currentTimeMillis()
        val end = com.forgequest.app.domain.WorkoutEnd.endTime(now, sessions.lastDoneAt(sessionId))
        sessions.update(session.copy(finishedAt = end, plannedSets = plannedSets))
        // Advance ROTATION cursor atomically once when a qualifying (non-quick, previously unfinished) workout finishes.
        if (!wasAlreadyFinished && session.planDayId != null) {
            val planDay = plans.day(session.planDayId)
            val plan = planDay?.let { plans.byId(it.planId) }
            if (plan != null) {
                val queueIds = plans.days(plan.id).filter { !it.isRest && plans.items(it.id).isNotEmpty() }
                    .sortedBy { it.dayOfWeek }.map { it.id }
                val nextCursor = Rotation.cursorOnFinish(plan, queueIds, session, wasAlreadyFinished = false)
                if (nextCursor != null) {
                    updatePlanScheduleColumns(plan.id, plan.scheduleMode, nextCursor, plan.active)
                }
            }
        }
        val workDone = done.count { countsForScore(it) }
        val xp = com.forgequest.app.domain.Xp.run { total(1, setsForXp(done.size, session.epochDay), prNames.size, 0) }
        FinishResult(session.title, workDone, prNames, xp, endedAt = end, clipped = end != now)
    }

    // ------------------------------------------------------------ History

    fun observeSession(id: Long): Flow<SessionEntity?> = sessions.observeById(id)

    /** Edits a set of a finished workout and re-checks which sets of that exercise are records. */
    suspend fun updateFinishedSet(set: SetEntity) = db.withTransaction {
        sessions.updateSet(set)
        recomputeRecords(set.exerciseId)
    }

    /** Removes one set from a finished workout. A workout left with no sets is removed too. */
    suspend fun deleteFinishedSet(set: SetEntity) = db.withTransaction {
        sessions.deleteSet(set.id)
        if (sessions.sets(set.sessionId).isEmpty()) sessions.delete(set.sessionId)
        recomputeRecords(set.exerciseId)
    }

    /** Moves a finished workout to another day (logged late, or on the wrong day); times shift with it. */
    suspend fun moveSession(sessionId: Long, epochDay: Long) = db.withTransaction {
        val s = sessions.byId(sessionId) ?: return@withTransaction
        val shift = (epochDay - s.epochDay) * 86_400_000L
        sessions.update(s.copy(epochDay = epochDay, startedAt = s.startedAt + shift, finishedAt = s.finishedAt?.plus(shift)))
        // Records depend on order, so replay them for everything this workout touched.
        sessions.sets(sessionId).map { it.exerciseId }.distinct().forEach { recomputeRecords(it) }
    }

    suspend fun deleteSession(sessionId: Long) = db.withTransaction {
        val affected = sessions.sets(sessionId).map { it.exerciseId }.distinct()
        sessions.deleteSetsOf(sessionId)
        sessions.delete(sessionId)
        affected.forEach { recomputeRecords(it) }
    }

    /** Puts back a workout removed by [deleteSession], with its original ids, then re-checks the records it touched. */
    suspend fun restoreSession(session: SessionEntity, sets: List<SetEntity>) = db.withTransaction {
        sessions.insert(session)
        sessions.insertSets(sets)
        sets.map { it.exerciseId }.distinct().forEach { recomputeRecords(it) }
    }

    /** Replays the exercise's whole history in chronological order so PR tags stay truthful after an edit or delete. */
    private suspend fun recomputeRecords(exerciseId: String) {
        val ex = exercises.byId(exerciseId) ?: return
        val all = sessions.recentDoneSetsAll(exerciseId)
        val eligible = all.filter { countsForPR(it) && it.exerciseId !in ExerciseCatalog.STRETCH_IDS }
        val sessionById = sessions.finished().associateBy { it.id }
        val ordered = eligible.groupBy { it.sessionId }.entries
            .sortedWith(
                compareBy<Map.Entry<Long, List<SetEntity>>> { sessionById[it.key]?.epochDay ?: 0L }
                    .thenBy { sessionById[it.key]?.startedAt ?: 0L }
                    .thenBy { sessionById[it.key]?.finishedAt ?: 0L }
            )
            .map { it.value }
        val records = Records.personalRecords(ex.kind, ordered, { it.reps }, { it.seconds }, { it.weightKg }, ex.id)
        val toUpdate = all.mapNotNull { set ->
            val shouldBe = set in records
            if (set.isPr != shouldBe) set.copy(isPr = shouldBe) else null
        }
        if (toUpdate.isNotEmpty()) sessions.updateSets(toUpdate)
    }
}