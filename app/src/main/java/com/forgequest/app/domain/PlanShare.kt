package com.forgequest.app.domain

import com.forgequest.app.data.ActivePlan
import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.db.PlanItemEntity
import com.forgequest.app.data.plan.PlanDraft
import com.forgequest.app.data.plan.PlanDraftDay
import com.forgequest.app.data.plan.PlanDraftItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Validated portable plan sharing (copy/paste + share sheet), weekly muscle volume preview,
 * and 2-exercise superset invariant helpers.
 */
object PlanShare {

    const val FORMAT = "forgequest-plan-v1"

    @Serializable
    data class SharedPlan(
        val format: String = FORMAT,
        val name: String,
        val description: String = "",
        val scheduleMode: String = ScheduleMode.WEEKDAY.name,
        val days: List<SharedDay>,
    )

    @Serializable
    data class SharedDay(
        val dayOfWeek: Int,
        val title: String,
        val isRest: Boolean = false,
        val note: String = "",
        val items: List<SharedItem> = emptyList(),
    )

    @Serializable
    data class SharedItem(
        val exerciseId: String,
        val name: String? = null,
        val kind: String? = null,
        val muscles: List<String> = emptyList(),
        val block: String = "Main",
        val circuit: Boolean = false,
        val sets: Int = 3,
        val repsMin: Int? = null,
        val repsMax: Int? = null,
        val seconds: Int? = null,
        val weightKg: Float? = null,
        val restSeconds: Int = 90,
        val note: String = "",
        val videoUrl: String? = null,
        val supersetGroup: Int? = null,
        val autoWarmup: Boolean = false,
        val progressionMode: String = ProgressionMode.DOUBLE.name,
    )

    data class MuscleSets(val muscle: Muscle, val sets: Int)

    sealed interface ParseResult {
        data class Valid(
            val plan: SharedPlan,
            val customExercises: List<String>,
            val volume: List<MuscleSets>,
        ) : ParseResult

        data class Invalid(val errors: List<String>) : ParseResult
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val fenced = Regex("""```(?:json)?\s*(\{[\s\S]*?\})\s*```""")

    fun exportPlan(plan: ActivePlan, exercises: Map<String, ExerciseEntity>): String {
        val shared = SharedPlan(
            format = FORMAT,
            name = plan.plan.name,
            description = plan.plan.description,
            scheduleMode = ScheduleMode.parse(plan.plan.scheduleMode).name,
            days = plan.days.sortedBy { it.day.dayOfWeek }.map { d ->
                SharedDay(
                    dayOfWeek = d.day.dayOfWeek,
                    title = d.day.title,
                    isRest = d.day.isRest,
                    note = d.day.note,
                    items = d.items.sortedBy { it.position }.map { item ->
                        val ex = exercises[item.exerciseId]
                        SharedItem(
                            exerciseId = item.exerciseId,
                            name = ex?.name,
                            kind = ex?.kind?.name,
                            muscles = ex?.primary?.map { it.name }.orEmpty(),
                            block = item.block,
                            circuit = item.circuit,
                            sets = item.sets,
                            repsMin = item.repsMin,
                            repsMax = item.repsMax,
                            seconds = item.seconds,
                            weightKg = item.weightKg,
                            restSeconds = item.restSeconds,
                            note = item.note,
                            videoUrl = ex?.videoUrl,
                            supersetGroup = item.supersetGroup,
                            autoWarmup = item.autoWarmup,
                            progressionMode = ProgressionMode.parse(item.progressionMode).name,
                        )
                    },
                )
            },
        )
        return json.encodeToString(SharedPlan.serializer(), shared)
    }

    fun toDraft(shared: SharedPlan): PlanDraft = PlanDraft(
        name = shared.name,
        description = shared.description,
        days = shared.days.map { d ->
            PlanDraftDay(
                dayOfWeek = d.dayOfWeek,
                title = d.title,
                isRest = d.isRest,
                note = d.note,
                items = d.items.map { i ->
                    PlanDraftItem(
                        exerciseId = i.exerciseId,
                        name = i.name,
                        kind = i.kind,
                        muscles = i.muscles,
                        block = i.block,
                        circuit = i.circuit,
                        sets = i.sets,
                        repsMin = i.repsMin,
                        repsMax = i.repsMax,
                        seconds = i.seconds,
                        weightKg = i.weightKg,
                        restSeconds = i.restSeconds,
                        note = i.note,
                        videoUrl = i.videoUrl,
                    )
                },
            )
        },
    )

    fun parseAndValidate(raw: String, exercises: Map<String, ExerciseEntity>): ParseResult {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ParseResult.Invalid(listOf("Empty plan text."))
        val body = fenced.findAll(trimmed).map { it.groupValues[1] }.lastOrNull { "\"days\"" in it }
            ?: trimmed.takeIf { it.startsWith("{") && it.endsWith("}") }
            ?: return ParseResult.Invalid(listOf("No valid JSON plan found."))

        val decoded = runCatching { json.decodeFromString(SharedPlan.serializer(), body) }.getOrNull()
            ?: return ParseResult.Invalid(listOf("Could not parse plan JSON."))
        if (decoded.format != FORMAT) return ParseResult.Invalid(listOf("Unsupported plan format."))

        val validDays = decoded.days.filter { it.dayOfWeek in 1..7 }.distinctBy { it.dayOfWeek }
        if (validDays.isEmpty()) return ParseResult.Invalid(listOf("Plan has no valid days (1..7)."))

        val errors = mutableListOf<String>()
        val custom = mutableListOf<String>()
        val cleanDays = validDays.sortedBy { it.dayOfWeek }.map { day ->
            val rawItems = day.items.map { item ->
                val resolvedId = canonicalId(item.exerciseId, item.name, exercises)
                if (resolvedId == null) {
                    val label = item.name?.trim().orEmpty()
                    if (label.isEmpty()) {
                        errors += "Day ${day.dayOfWeek}: unknown exercise \"${item.exerciseId}\"."
                    } else {
                        custom += label
                    }
                }
                val id = resolvedId ?: normId(item.exerciseId).ifBlank { "custom_exercise" }
                val sets = item.sets.coerceIn(1, 10)
                val repsMin = item.repsMin?.coerceIn(1, 100)
                val repsMax = item.repsMax?.coerceIn(1, 100)?.let { mx -> repsMin?.let { maxOf(it, mx) } ?: mx }
                item.copy(
                    exerciseId = id,
                    block = item.block.trim().ifEmpty { "Main" }.take(30),
                    sets = sets,
                    repsMin = repsMin,
                    repsMax = repsMax,
                    seconds = item.seconds?.coerceIn(5, 3600),
                    weightKg = item.weightKg?.coerceIn(0f, 500f),
                    restSeconds = item.restSeconds.coerceIn(0, 600),
                    note = item.note.take(200),
                    progressionMode = ProgressionMode.parse(item.progressionMode).name,
                )
            }
            val cleanItems = normalizeSharedSupersets(rawItems)
            day.copy(
                title = day.title.trim().ifEmpty { if (day.isRest || cleanItems.isEmpty()) "Rest day" else "Workout" }.take(40),
                isRest = day.isRest || cleanItems.isEmpty(),
                note = day.note.take(300),
                items = cleanItems,
            )
        }
        if (errors.isNotEmpty()) return ParseResult.Invalid(errors)

        val cleanPlan = SharedPlan(
            format = FORMAT,
            name = decoded.name.trim().ifEmpty { "Imported plan" }.take(80),
            description = decoded.description.trim().take(400),
            scheduleMode = ScheduleMode.parse(decoded.scheduleMode).name,
            days = cleanDays,
        )
        val vol = weeklyMuscleVolume(cleanPlan, exercises)
        return ParseResult.Valid(cleanPlan, custom.distinct(), vol)
    }

    /** Generates a non-colliding plan name so importing never overwrites or confuses existing plans. */
    fun safeImportName(requested: String, existingNames: Collection<String>): String {
        val base = requested.trim().ifEmpty { "Imported plan" }.take(80)
        val taken = existingNames.mapTo(HashSet()) { it.trim().lowercase() }
        if (base.lowercase() !in taken) return base
        var n = 2
        while ("$base ($n)".lowercase() in taken) n++
        return "$base ($n)".take(80)
    }

    /** Weekly primary working sets per muscle across a PlanDraft, excluding Warm-up/Cool-down blocks. */
    fun weeklyMuscleVolume(draft: PlanDraft, exercises: Map<String, ExerciseEntity>): List<MuscleSets> =
        weeklyMuscleVolumeFromPairs(
            draft.days.filter { !it.isRest }.flatMap { d ->
                d.items.filter { !WorkoutScore.isExtra(it.block) }.map { item ->
                    val muscles = exercises[item.exerciseId]?.primary
                        ?: item.muscles.mapNotNull { runCatching { Muscle.valueOf(it) }.getOrNull() }
                    muscles to item.sets
                }
            },
        )

    fun weeklyMuscleVolume(plan: SharedPlan, exercises: Map<String, ExerciseEntity>): List<MuscleSets> =
        weeklyMuscleVolumeFromPairs(
            plan.days.filter { !it.isRest }.flatMap { d ->
                d.items.filter { !WorkoutScore.isExtra(it.block) }.map { item ->
                    val muscles = exercises[item.exerciseId]?.primary
                        ?: item.muscles.mapNotNull { runCatching { Muscle.valueOf(it) }.getOrNull() }
                    muscles to item.sets
                }
            },
        )

    private fun weeklyMuscleVolumeFromPairs(items: List<Pair<List<Muscle>, Int>>): List<MuscleSets> {
        val totals = LinkedHashMap<Muscle, Int>()
        for ((muscles, sets) in items) {
            for (m in muscles) {
                totals[m] = (totals[m] ?: 0) + sets
            }
        }
        return totals.entries
            .map { MuscleSets(it.key, it.value) }
            .sortedWith(compareByDescending<MuscleSets> { it.sets }.thenBy { it.muscle.ordinal })
    }

    private fun normId(raw: String): String =
        raw.trim().lowercase().replace(Regex("[\\s\\-]+"), "_").replace(Regex("[^a-z0-9_]"), "")

    private fun canonicalId(raw: String, name: String?, known: Map<String, ExerciseEntity>): String? {
        if (raw in known) return raw
        val n = normId(raw)
        if (n in known) return n
        listOf(n.removeSuffix("es"), n.removeSuffix("s"), "${n}s", "${n}es")
            .firstOrNull { it in known }?.let { return it }
        val byName = known.values.associateBy { normId(it.name) }
        byName[n]?.let { return it.id }
        name?.let { byName[normId(it)] }?.let { return it.id }
        return null
    }

    private fun normalizeSharedSupersets(items: List<SharedItem>): List<SharedItem> {
        val groups = SupersetRules.validPairIndices(
            items.map { SupersetCandidate(it.supersetGroup, it.sets, WorkoutScore.isExtra(it.block)) },
        )
        return items.mapIndexed { idx, item -> item.copy(supersetGroup = groups[idx]) }
    }
}

internal data class SupersetCandidate(val supersetGroup: Int?, val sets: Int, val extra: Boolean)

/**
 * Pure 2-exercise superset rules:
 * - Only pairs two adjacent non-extra items on the same day with identical working-set counts.
 * - Any single-item group, non-adjacent pair, or set-count mismatch is cleared to null.
 */
object SupersetRules {

    internal fun validPairIndices(items: List<SupersetCandidate>): List<Int?> {
        val result = MutableList<Int?>(items.size) { null }
        var nextGroup = 1
        var i = 0
        while (i + 1 < items.size) {
            val a = items[i]
            val b = items[i + 1]
            if (a.supersetGroup != null && a.supersetGroup == b.supersetGroup &&
                !a.extra && !b.extra && a.sets == b.sets
            ) {
                result[i] = nextGroup
                result[i + 1] = nextGroup
                nextGroup++
                i += 2
            } else {
                i++
            }
        }
        return result
    }

    fun normalizeDaySupersets(items: List<PlanItemEntity>): List<PlanItemEntity> {
        val ordered = items.sortedBy { it.position }
        val valid = validPairIndices(ordered.map { SupersetCandidate(it.supersetGroup, it.sets, WorkoutScore.isExtra(it.block)) })
        return ordered.mapIndexed { idx, item ->
            val target = valid[idx]
            if (item.supersetGroup == target) item else item.copy(supersetGroup = target)
        }
    }

    /** True when [items] (sorted by position) allows [index] to pair with [index + 1]. */
    fun canPairWithNext(items: List<PlanItemEntity>, index: Int): Boolean {
        if (index !in 0 until items.lastIndex) return false
        val current = items[index]
        val next = items[index + 1]
        if (WorkoutScore.isExtra(current.block) || WorkoutScore.isExtra(next.block)) return false
        if (current.sets != next.sets) return false
        val pairedWithPrev = index > 0 && current.supersetGroup != null && items[index - 1].supersetGroup == current.supersetGroup
        return !pairedWithPrev
    }

    fun isPairedWithNext(items: List<PlanItemEntity>, index: Int): Boolean {
        if (index !in 0 until items.lastIndex) return false
        val g = items[index].supersetGroup ?: return false
        return items[index + 1].supersetGroup == g
    }

    fun isPairedWithPrev(items: List<PlanItemEntity>, index: Int): Boolean {
        if (index !in 1..items.lastIndex) return false
        val g = items[index].supersetGroup ?: return false
        return items[index - 1].supersetGroup == g
    }

    /** Toggles pairing between [index] and [index + 1], then normalizes all groups on the day. */
    fun setPairWithNext(items: List<PlanItemEntity>, index: Int, pair: Boolean): List<PlanItemEntity> {
        val ordered = items.sortedBy { it.position }.toMutableList()
        if (index !in ordered.indices) return ordered
        if (!pair) {
            val g = ordered[index].supersetGroup
            if (g != null && index + 1 < ordered.size && ordered[index + 1].supersetGroup == g) {
                ordered[index] = ordered[index].copy(supersetGroup = null)
                ordered[index + 1] = ordered[index + 1].copy(supersetGroup = null)
            }
            return normalizeDaySupersets(ordered)
        }
        if (!canPairWithNext(ordered, index)) return ordered
        // Clear any old pairing on next (e.g. if next was paired with index + 2)
        val nextOldGroup = ordered[index + 1].supersetGroup
        if (nextOldGroup != null && index + 2 < ordered.size && ordered[index + 2].supersetGroup == nextOldGroup) {
            ordered[index + 2] = ordered[index + 2].copy(supersetGroup = null)
        }
        val newGroup = (ordered.mapNotNull { it.supersetGroup }.maxOrNull() ?: 0) + 1
        ordered[index] = ordered[index].copy(supersetGroup = newGroup)
        ordered[index + 1] = ordered[index + 1].copy(supersetGroup = newGroup)
        return normalizeDaySupersets(ordered)
    }
}
