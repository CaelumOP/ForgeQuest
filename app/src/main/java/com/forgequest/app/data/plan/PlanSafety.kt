package com.forgequest.app.data.plan

import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.domain.ExerciseKind
import com.forgequest.app.domain.ExerciseSwapEngine
import com.forgequest.app.domain.Muscle
import kotlinx.serialization.Serializable

/**
 * A small edit the coach proposes instead of rewriting the week: "swap" one exercise for another, "add" one to a
 * day, "remove" one, or "edit" its sets/reps/seconds/weight/rest. Only the listed fields change.
 */
@Serializable
data class PlanChange(
    val type: String,
    val dayOfWeek: Int,
    val exerciseId: String? = null,
    val newExerciseId: String? = null,
    val sets: Int? = null,
    val repsMin: Int? = null,
    val repsMax: Int? = null,
    val seconds: Int? = null,
    val weightKg: Float? = null,
    val restSeconds: Int? = null,
    val note: String? = null,
)

@Serializable
data class PlanPatch(val changes: List<PlanChange>)

/** A 64-bit FNV-1a fingerprint of a plan's training content, so a stale proposal can be told apart. */
object PlanHash {
    fun of(plan: PlanDraft): String {
        val canonical = buildString {
            plan.days.sortedBy { it.dayOfWeek }.forEach { d ->
                append(d.dayOfWeek).append(if (d.isRest) "R" else "T").append('[')
                d.items.forEach { i ->
                    append(i.exerciseId).append(',').append(i.block).append(',').append(i.circuit).append(',').append(i.sets).append(',')
                        .append(i.repsMin).append(',').append(i.repsMax).append(',').append(i.seconds).append(',').append(i.weightKg).append(',')
                        .append(i.restSeconds).append(';')
                }
                append(']')
            }
        }
        var h = -0x340d631b7bdddcdbL // FNV offset basis 0xcbf29ce484222325
        for (b in canonical.toByteArray()) {
            h = h xor (b.toLong() and 0xff)
            h *= 0x100000001b3L
        }
        return java.lang.Long.toUnsignedString(h, 16).padStart(16, '0')
    }
}

/**
 * Checks a coach plan before anyone sees an Apply button: ids are matched to the library (near misses such as
 * "push_ups" or "Bench Press" are fixed), numbers are kept to sane limits, and anything still wrong is listed
 * so the coach can be asked once to repair it.
 */
object PlanValidator {
    data class Result(val draft: PlanDraft, val errors: List<String>, val unknownIds: Set<String>)

    private fun norm(raw: String) = raw.trim().lowercase().replace(Regex("[\\s\\-]+"), "_").replace(Regex("[^a-z0-9_]"), "")

    /** The library id [raw] most likely means, or null. Tries exact, normalised, singular/plural and name matches. */
    fun canonicalId(raw: String, name: String?, known: Map<String, ExerciseEntity>): String? {
        if (raw in known) return raw
        val n = norm(raw)
        if (n in known) return n
        val variants = listOf(n.removeSuffix("es"), n.removeSuffix("s"), n + "s", n + "es")
        variants.firstOrNull { it in known }?.let { return it }
        val byName = known.values.associateBy { norm(it.name) }
        byName[n]?.let { return it.id }
        name?.let { byName[norm(it)] }?.let { return it.id }
        return null
    }

    fun validate(draft: PlanDraft, known: Map<String, ExerciseEntity>): Result {
        val errors = mutableListOf<String>()
        val unknown = mutableSetOf<String>()
        val days = draft.days.map { day ->
            val items = day.items.map { item ->
                val id = canonicalId(item.exerciseId, item.name, known)
                // A new exercise is fine when the coach described it (name and muscles); a bare unknown id is a mistake.
                val described = !item.name.isNullOrBlank() && item.muscles.any { m -> Muscle.entries.any { it.name == m } }
                if (id == null && !described) {
                    unknown += item.exerciseId
                    errors += "Day ${day.dayOfWeek}: unknown exerciseId \"${item.exerciseId}\". Use an id from the list, or give a new exercise a name, kind and muscles."
                }
                clamp(item.copy(exerciseId = id ?: norm(item.exerciseId).ifBlank { item.exerciseId }))
            }
            items.groupBy { it.exerciseId }.filter { it.value.size > 1 && it.value.none { i -> i.circuit } }.keys.forEach { dup ->
                errors += "Day ${day.dayOfWeek}: \"$dup\" appears twice; pick a different exercise or merge the sets."
            }
            day.copy(items = items)
        }
        return Result(draft.copy(days = days), errors, unknown)
    }

    private fun clamp(i: PlanDraftItem) = i.copy(
        sets = i.sets.coerceIn(1, 10),
        repsMin = i.repsMin?.coerceIn(1, 100),
        repsMax = i.repsMax?.coerceIn(1, 100)?.let { mx -> i.repsMin?.coerceIn(1, 100)?.let { maxOf(it, mx) } ?: mx },
        seconds = i.seconds?.coerceIn(5, 3600),
        weightKg = i.weightKg?.coerceIn(0f, 500f),
        restSeconds = i.restSeconds.coerceIn(0, 600),
    )

    /**
     * Last resort after the repair round: each unknown id becomes the closest library exercise (same movement and
     * muscles, available equipment). Returns the plan and the swaps made, as (old id, new exercise name).
     */
    fun replaceUnknown(
        draft: PlanDraft,
        unknownIds: Set<String>,
        library: List<ExerciseEntity>,
        equipment: Set<com.forgequest.app.domain.Equipment>,
    ): Pair<PlanDraft, List<Pair<String, String>>> {
        val swaps = mutableListOf<Pair<String, String>>()
        val days = draft.days.map { day ->
            val used = day.items.map { it.exerciseId }.toMutableSet()
            day.copy(items = day.items.mapNotNull { item ->
                if (item.exerciseId !in unknownIds && PlanValidator.norm(item.exerciseId) !in unknownIds.map(::norm)) return@mapNotNull item
                val stand = ExerciseEntity(
                    id = item.exerciseId,
                    name = item.name ?: item.exerciseId.replace('_', ' '),
                    kind = runCatching { ExerciseKind.valueOf(item.kind ?: "") }.getOrDefault(
                        if (item.seconds != null) ExerciseKind.HOLD else if (item.weightKg != null) ExerciseKind.WEIGHTED else ExerciseKind.REPS,
                    ),
                    primary = item.muscles.mapNotNull { m -> runCatching { Muscle.valueOf(m) }.getOrNull() },
                    secondary = emptyList(),
                )
                val pick = ExerciseSwapEngine.rankSubstitutes(stand, library, equipment)
                    .firstOrNull { it.matchesUserEquipment && it.exercise.id !in used && it.score >= 20 }?.exercise
                    ?: return@mapNotNull null.also { swaps += item.exerciseId to "" }
                used += pick.id
                swaps += item.exerciseId to pick.name
                ExerciseSwapEngine.translateDraftItem(item, pick)
            })
        }
        return draft.copy(days = days) to swaps
    }
}

/** Applies a coach [PlanPatch] to a plan; every change must find its day and exercise, or nothing is applied. */
object PlanPatches {
    data class Outcome(val draft: PlanDraft?, val errors: List<String>)

    fun apply(base: PlanDraft, patch: PlanPatch, known: Map<String, ExerciseEntity>): Outcome {
        if (patch.changes.isEmpty()) return Outcome(null, listOf("The changes list is empty."))
        val errors = mutableListOf<String>()
        val days = base.days.associateBy { it.dayOfWeek }.toMutableMap()
        for (c in patch.changes) {
            val day = days[c.dayOfWeek] ?: PlanDraftDay(c.dayOfWeek, "Day ${c.dayOfWeek}", isRest = true).takeIf { c.type == "add" }
            if (day == null || c.dayOfWeek !in 1..7) { errors += "No day ${c.dayOfWeek} in the plan."; continue }
            fun find(): Int {
                val id = c.exerciseId?.let { PlanValidator.canonicalId(it, null, known) ?: it }
                return day.items.indexOfFirst { it.exerciseId == id }
            }
            fun withFields(i: PlanDraftItem) = i.copy(
                sets = c.sets ?: i.sets,
                repsMin = c.repsMin ?: i.repsMin,
                repsMax = c.repsMax ?: i.repsMax,
                seconds = c.seconds ?: i.seconds,
                weightKg = c.weightKg ?: i.weightKg,
                restSeconds = c.restSeconds ?: i.restSeconds,
                note = c.note ?: i.note,
            )
            val newId = c.newExerciseId?.let { PlanValidator.canonicalId(it, null, known) }
            when (c.type) {
                "swap" -> {
                    val at = find()
                    when {
                        at < 0 -> errors += "Day ${c.dayOfWeek}: \"${c.exerciseId}\" is not in that day."
                        newId == null -> errors += "Day ${c.dayOfWeek}: unknown newExerciseId \"${c.newExerciseId}\"."
                        day.items.any { it.exerciseId == newId } -> errors += "Day ${c.dayOfWeek}: \"$newId\" is already in that day."
                        else -> {
                            val swapped = ExerciseSwapEngine.translateDraftItem(day.items[at], known.getValue(newId))
                            days[c.dayOfWeek] = day.copy(items = day.items.toMutableList().also { it[at] = withFields(swapped) })
                        }
                    }
                }
                "add" -> when {
                    newId == null -> errors += "Day ${c.dayOfWeek}: unknown newExerciseId \"${c.newExerciseId}\"."
                    day.items.any { it.exerciseId == newId } -> errors += "Day ${c.dayOfWeek}: \"$newId\" is already in that day."
                    else -> {
                        val ex = known.getValue(newId)
                        val fresh = PlanDraftItem(
                            exerciseId = newId,
                            sets = c.sets ?: 3,
                            repsMin = if (ex.kind == ExerciseKind.HOLD) null else c.repsMin ?: 8,
                            repsMax = if (ex.kind == ExerciseKind.HOLD) null else c.repsMax ?: 12,
                            seconds = if (ex.kind == ExerciseKind.HOLD) c.seconds ?: 30 else null,
                            weightKg = c.weightKg,
                            restSeconds = c.restSeconds ?: 90,
                            note = c.note ?: "",
                        )
                        days[c.dayOfWeek] = day.copy(isRest = false, items = day.items + fresh)
                    }
                }
                "remove" -> {
                    val at = find()
                    if (at < 0) errors += "Day ${c.dayOfWeek}: \"${c.exerciseId}\" is not in that day."
                    else days[c.dayOfWeek] = day.copy(items = day.items.filterIndexed { i, _ -> i != at })
                }
                "edit" -> {
                    val at = find()
                    if (at < 0) errors += "Day ${c.dayOfWeek}: \"${c.exerciseId}\" is not in that day."
                    else days[c.dayOfWeek] = day.copy(items = day.items.toMutableList().also { it[at] = withFields(it[at]) })
                }
                else -> errors += "Unknown change type \"${c.type}\"; use swap, add, remove or edit."
            }
        }
        if (errors.isNotEmpty()) return Outcome(null, errors)
        return Outcome(base.copy(days = days.values.sortedBy { it.dayOfWeek }, basedOnHash = PlanHash.of(base), patch = patch), emptyList())
    }
}
