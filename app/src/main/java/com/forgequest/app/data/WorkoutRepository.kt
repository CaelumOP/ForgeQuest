package com.forgequest.app.data

import androidx.room.withTransaction
import com.forgequest.app.data.db.AppDatabase
import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.db.PlanDayEntity
import com.forgequest.app.data.db.PlanEntity
import com.forgequest.app.data.db.PlanItemEntity
import com.forgequest.app.data.db.SessionEntity
import com.forgequest.app.data.db.SetEntity
import com.forgequest.app.data.plan.PlanDraft
import com.forgequest.app.data.seed.ExerciseCatalog
import com.forgequest.app.domain.Dates
import com.forgequest.app.domain.ExerciseKind
import com.forgequest.app.domain.Muscle
import com.forgequest.app.domain.Records
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

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
class WorkoutRepository(private val db: AppDatabase) {

    private val plans = db.plans()
    private val sessions = db.sessions()
    private val exercises = db.exercises()

    suspend fun seedLibrary() {
        exercises.insertAll(ExerciseCatalog.all)
        // Keep built-in video links fresh without touching user edits on custom exercises.
        for (builtIn in ExerciseCatalog.all) {
            val existing = exercises.byId(builtIn.id) ?: continue
            if (!existing.custom && existing.videoUrl == null && builtIn.videoUrl != null) {
                exercises.upsert(existing.copy(videoUrl = builtIn.videoUrl))
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
            if (exercises.byId(item.exerciseId) == null) {
                val kind = runCatching { ExerciseKind.valueOf(item.kind ?: "") }.getOrElse {
                    if (item.seconds != null) ExerciseKind.HOLD else if (item.weightKg != null) ExerciseKind.WEIGHTED else ExerciseKind.REPS
                }
                val muscles = item.muscles.mapNotNull { m -> runCatching { Muscle.valueOf(m) }.getOrNull() }
                exercises.upsert(
                    ExerciseEntity(
                        id = item.exerciseId,
                        name = item.name ?: item.exerciseId.replace('_', ' ').replaceFirstChar { it.uppercase() },
                        kind = kind,
                        primary = muscles.take(2),
                        secondary = muscles.drop(2),
                        videoUrl = item.videoUrl,
                        custom = true,
                    )
                )
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
                    title = day?.title ?: "Rest day",
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

    suspend fun updatePlanItem(item: PlanItemEntity) = plans.updateItem(item)
    suspend fun deletePlanItem(id: Long) = plans.deleteItem(id)
    suspend fun updatePlanDay(day: PlanDayEntity) = plans.updateDay(day)

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
        if (day != null && day.isRest) plans.updateDay(day.copy(isRest = false, title = if (day.title == "Rest day") "Workout" else day.title))
    }

    suspend fun addCustomExercise(name: String, kind: ExerciseKind, primary: List<Muscle>, videoUrl: String?): String {
        val base = name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifEmpty { "exercise" }
        var id = "custom_$base"
        var n = 2
        while (exercises.byId(id) != null) id = "custom_${base}_${n++}"
        exercises.upsert(ExerciseEntity(id, name.trim(), kind, primary, emptyList(), videoUrl?.ifBlank { null }, custom = true))
        return id
    }

    suspend fun setVideoUrl(exerciseId: String, url: String?) {
        val ex = exercises.byId(exerciseId) ?: return
        exercises.upsert(ex.copy(videoUrl = url?.trim()?.ifEmpty { null }))
    }

    // ------------------------------------------------------------ Live session

    val openSession: Flow<SessionEntity?> = sessions.observeOpen()

    fun observeSets(sessionId: Long): Flow<List<SetEntity>> = sessions.observeSets(sessionId)

    suspend fun startSession(day: PlanDayWithItems?): Long = db.withTransaction {
        sessions.open()?.let { return@withTransaction it.id }
        val now = System.currentTimeMillis()
        val id = sessions.insert(
            SessionEntity(planDayId = day?.day?.id, title = day?.day?.title ?: "Quick workout", epochDay = Dates.today(), startedAt = now)
        )
        val sets = mutableListOf<SetEntity>()
        day?.items?.forEach { item -> sets += buildSetsFor(id, item.position, item, exercises.byId(item.exerciseId)) }
        if (sets.isNotEmpty()) sessions.insertSets(sets)
        id
    }

    private suspend fun buildSetsFor(sessionId: Long, position: Int, item: PlanItemEntity?, ex: ExerciseEntity?): List<SetEntity> {
        val kind = ex?.kind ?: ExerciseKind.REPS
        val recent = ex?.let { sessions.recentDoneSets(it.id) }.orEmpty()
        val lastSession = recent.filter { it.sessionId == recent.firstOrNull()?.sessionId }
        val count = item?.sets ?: 3
        return (0 until count).map { i ->
            val last = lastSession.getOrNull(i) ?: lastSession.lastOrNull()
            SetEntity(
                sessionId = sessionId,
                exerciseId = ex?.id ?: item?.exerciseId ?: "",
                itemPosition = position,
                block = item?.block ?: "Main",
                circuit = item?.circuit ?: false,
                setIndex = i,
                targetLabel = targetLabel(kind, item),
                reps = if (kind == ExerciseKind.HOLD) null else last?.reps ?: item?.repsMin ?: 10,
                seconds = if (kind == ExerciseKind.HOLD) last?.seconds ?: item?.seconds ?: 30 else null,
                weightKg = if (kind == ExerciseKind.WEIGHTED) last?.weightKg ?: item?.weightKg ?: 0f else null,
                restSeconds = item?.restSeconds ?: 90,
            )
        }
    }

    fun targetLabel(kind: ExerciseKind, item: PlanItemEntity?): String {
        if (item == null) return ""
        val reps = when {
            item.repsMin != null && item.repsMax != null && item.repsMax != item.repsMin -> "${item.repsMin}–${item.repsMax} reps"
            item.repsMin != null -> "${item.repsMin} reps"
            else -> ""
        }
        return when (kind) {
            ExerciseKind.HOLD -> "${item.seconds ?: 30} s"
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

    /** Replaces the exercise at [itemPosition] keeping the set count and block. */
    suspend fun swapExercise(sessionId: Long, itemPosition: Int, newExerciseId: String) = db.withTransaction {
        val ex = exercises.byId(newExerciseId) ?: return@withTransaction
        val old = sessions.sets(sessionId).filter { it.itemPosition == itemPosition }
        if (old.isEmpty()) return@withTransaction
        old.forEach { sessions.deleteSet(it.id) }
        val template = old.first()
        val fresh = buildSetsFor(sessionId, itemPosition, null, ex).take(old.size).let { built ->
            if (built.size < old.size) built + List(old.size - built.size) { i -> built.last().copy(setIndex = built.size + i) } else built
        }.map { it.copy(block = template.block, circuit = template.circuit, restSeconds = template.restSeconds) }
        sessions.insertSets(fresh)
    }

    suspend fun discardSession(sessionId: Long) = db.withTransaction {
        sessions.deleteSetsOf(sessionId)
        sessions.delete(sessionId)
    }

    /** Closes the session, drops unticked sets and flags new personal records. */
    suspend fun finishSession(sessionId: Long): FinishResult = db.withTransaction {
        val session = sessions.byId(sessionId) ?: return@withTransaction FinishResult("", 0, emptyList(), 0)
        val all = sessions.sets(sessionId)
        all.filter { !it.done }.forEach { sessions.deleteSet(it.id) }
        val done = all.filter { it.done }

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

    suspend fun deleteSession(sessionId: Long) = db.withTransaction {
        val affected = sessions.sets(sessionId).map { it.exerciseId }.distinct()
        sessions.deleteSetsOf(sessionId)
        sessions.delete(sessionId)
        affected.forEach { recomputeRecords(it) }
    }

    /** Replays the exercise's whole history so PR tags stay truthful after an edit or delete. */
    private suspend fun recomputeRecords(exerciseId: String) {
        val ex = exercises.byId(exerciseId) ?: return
        val all = sessions.recentDoneSetsAll(exerciseId)
        val finishedAt = sessions.finished().associate { it.id to (it.finishedAt ?: 0L) }
        val ordered = all.groupBy { it.sessionId }.entries.sortedBy { finishedAt[it.key] ?: 0L }.map { it.value }
        val records = Records.personalRecords(ex.kind, ordered, { it.reps }, { it.seconds }, { it.weightKg })
        for (set in all) {
            val shouldBe = set in records
            if (set.isPr != shouldBe) sessions.updateSet(set.copy(isPr = shouldBe))
        }
    }
}
