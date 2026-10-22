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
import com.forgequest.app.data.prefs.SettingsRepository
import com.forgequest.app.data.seed.ExerciseCatalog
import com.forgequest.app.domain.Dates
import com.forgequest.app.domain.DayCall
import com.forgequest.app.domain.ExerciseKind
import com.forgequest.app.domain.ExerciseSwapEngine
import com.forgequest.app.domain.Muscle
import com.forgequest.app.domain.Progression
import com.forgequest.app.domain.Readiness
import com.forgequest.app.domain.Records
import com.forgequest.app.domain.SetEffort
import com.forgequest.app.domain.WeightUnit
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

data class FinishResult(
    val sessionTitle: String,
    val doneSets: Int,
    val prs: List<String>,
    val xpGained: Int,
)

@OptIn(ExperimentalCoroutinesApi::class)
/** [settings] gives progression the weight unit and the readiness switch; without it (tests) steps are in kg. */
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
                val nameNeedsUpdate = existing.name != wantedName && (existing.name == ar || existing.name == builtIn.name)
                val videoNeedsUpdate = existing.videoUrl == null && builtIn.videoUrl != null
                if (nameNeedsUpdate || videoNeedsUpdate) {
                    toUpsert += existing.copy(
                        name = if (nameNeedsUpdate) wantedName else existing.name,
                        videoUrl = if (videoNeedsUpdate) builtIn.videoUrl else existing.videoUrl,
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
    suspend fun applyPlan(draft: PlanDraft, source: String): Long = db.withTransaction {
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
            PlanEntity(name = draft.name, description = draft.description, source = source, active = true, createdAt = System.currentTimeMillis())
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
            day?.items?.mapIndexed { index, item ->
                PlanItemEntity(
                    dayId = dayId, position = index, exerciseId = item.exerciseId, block = item.block, circuit = item.circuit,
                    sets = item.sets.coerceIn(1, 10), repsMin = item.repsMin, repsMax = item.repsMax, seconds = item.seconds,
                    weightKg = item.weightKg, restSeconds = item.restSeconds, note = item.note,
                )
            }?.let { plans.insertItems(it) }
        }
        planId
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
        reordered.forEachIndexed { i, it -> if (it.position != i) plans.updateItem(it.copy(position = i)) }
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

    suspend fun updatePlanItem(item: PlanItemEntity) = plans.updateItem(item)
    suspend fun deletePlanItem(id: Long) = plans.deleteItem(id)
    suspend fun restorePlanItem(item: PlanItemEntity) = plans.restoreItem(item)
    suspend fun updatePlanDay(day: PlanDayEntity) = plans.updateDay(day)

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
        if (day != null && day.isRest) plans.updateDay(day.copy(isRest = false, title = if (day.title == "Rest day" || day.title == AppText.get(R.string.today_rest_day)) AppText.get(R.string.plan_workout) else day.title))
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
        day?.items?.forEach { item -> sets += buildSetsFor(id, item.position, item, exercises.byId(item.exerciseId)) }
        if (sets.isNotEmpty()) sessions.insertSets(sets)
        id
    }

    /**
     * Prefills today's sets from the last two finished sessions of [ex] using [Progression]: load and reps
     * only go up after clean sessions, never on a low-readiness day, and in the person's own unit.
     */
    private suspend fun buildSetsFor(sessionId: Long, position: Int, item: PlanItemEntity?, ex: ExerciseEntity?): List<SetEntity> {
        val kind = ex?.kind ?: ExerciseKind.REPS
        val recent = ex?.let { sessions.recentDoneSets(it.id) }.orEmpty()
        val sessionIds = recent.map { it.sessionId }.distinct()
        fun past(id: Long?) = recent.filter { it.sessionId == id }.sortedBy { it.setIndex }
            .map { Progression.PastSet(it.reps, it.seconds, it.weightKg, SetEffort.of(it.effort, it.note)) }
        val isWarmup = item?.block?.equals("Warm-up", ignoreCase = true) == true || ex?.id == "jumping_jacks"
        val s = settings?.current()
        val result = Progression.next(
            kind = kind,
            target = item?.let { Progression.Target(it.sets, it.repsMin, it.repsMax, it.seconds, it.weightKg) },
            last = past(sessionIds.getOrNull(0)),
            previous = past(sessionIds.getOrNull(1)),
            unit = s?.weightUnit ?: WeightUnit.KG,
            lowReadiness = s?.healthEnabled == true && lowReadinessToday(),
            warmup = isWarmup,
        )
        val label = targetLabel(kind, item).let { base ->
            if (result.holdCeilingReached) listOf(base, AppText.get(R.string.rx_hold_ceiling)).filter { it.isNotBlank() }.joinToString(" · ") else base
        }
        return result.sets.mapIndexed { i, p ->
            SetEntity(
                sessionId = sessionId,
                exerciseId = ex?.id ?: item?.exerciseId ?: "",
                itemPosition = position,
                block = item?.block ?: "Main",
                circuit = item?.circuit ?: false,
                setIndex = i,
                targetLabel = label,
                reps = p.reps,
                seconds = p.seconds,
                weightKg = p.weightKg,
                restSeconds = item?.restSeconds ?: 90,
            )
        }
    }

    /** Today's Fitbit recovery says ease off or recover (below 50); no data means train as normal. */
    private suspend fun lowReadinessToday(): Boolean {
        val day = db.readiness().get(Dates.today()) ?: return false
        return Readiness.call(day.recovery).let { it == DayCall.EASE_OFF || it == DayCall.RECOVER }
    }

    fun targetLabel(kind: ExerciseKind, item: PlanItemEntity?): String {
        if (item == null) return ""
        val reps = when {
            item.repsMin != null && item.repsMax != null && item.repsMax != item.repsMin -> AppText.get(R.string.rx_rep_range, item.repsMin, item.repsMax)
            item.repsMin != null -> AppText.get(R.string.rx_reps_n, item.repsMin)
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
        if (tickAll) {
            sessions.markAllSetsDone(sessionId)
        } else {
            sessions.deleteUntickedSetsOf(sessionId)
        }
        val done = sessions.sets(sessionId).filter { it.done }

        val prNames = mutableListOf<String>()
        for ((exerciseId, setsOfExercise) in done.groupBy { it.exerciseId }) {
            val ex = exercises.byId(exerciseId) ?: continue
            val previousBest = sessions.recentDoneSetsAll(exerciseId)
                .maxOfOrNull { Records.score(ex.kind, it.reps, it.seconds, it.weightKg) }
                ?: continue // first time doing it: nothing to beat yet
            val best = setsOfExercise.maxByOrNull { Records.score(ex.kind, it.reps, it.seconds, it.weightKg) } ?: continue
            if (Records.score(ex.kind, best.reps, best.seconds, best.weightKg) > previousBest) {
                sessions.updateSet(best.copy(isPr = true))
                prNames += ex.name
            }
        }
        sessions.update(session.copy(finishedAt = System.currentTimeMillis()))
        val xp = com.forgequest.app.domain.Xp.total(1, done.size, prNames.size, 0)
        FinishResult(session.title, done.size, prNames, xp)
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

    /** Replays the exercise's whole history in chronological order so PR tags stay truthful after an edit or delete. */
    private suspend fun recomputeRecords(exerciseId: String) {
        val ex = exercises.byId(exerciseId) ?: return
        val all = sessions.recentDoneSetsAll(exerciseId)
        val sessionById = sessions.finished().associateBy { it.id }
        val ordered = all.groupBy { it.sessionId }.entries
            .sortedWith(
                compareBy<Map.Entry<Long, List<SetEntity>>> { sessionById[it.key]?.epochDay ?: 0L }
                    .thenBy { sessionById[it.key]?.startedAt ?: 0L }
                    .thenBy { sessionById[it.key]?.finishedAt ?: 0L }
            )
            .map { it.value }
        val records = Records.personalRecords(ex.kind, ordered, { it.reps }, { it.seconds }, { it.weightKg })
        val toUpdate = all.mapNotNull { set ->
            val shouldBe = set in records
            if (set.isPr != shouldBe) set.copy(isPr = shouldBe) else null
        }
        if (toUpdate.isNotEmpty()) sessions.updateSets(toUpdate)
    }
}