package com.forgequest.app.data.plan

import kotlinx.serialization.Serializable

/**
 * A plan before it's saved. Starter plans, coach proposals and imported docs all produce this,
 * so there is one path into the database.
 */
@Serializable
data class PlanDraft(
    val name: String,
    val description: String = "",
    val days: List<PlanDraftDay>,
    /** Fingerprint of the active plan this proposal was built on (see PlanHash); null for imports and starters. */
    val basedOnHash: String? = null,
    /** Set when the coach proposed only changes: applying re-runs them on the current plan if it changed since. */
    val patch: PlanPatch? = null,
)

@Serializable
data class PlanDraftDay(
    /** 1 = Monday .. 7 = Sunday */
    val dayOfWeek: Int,
    val title: String,
    val isRest: Boolean = false,
    val note: String = "",
    val items: List<PlanDraftItem> = emptyList(),
)

@Serializable
data class PlanDraftItem(
    /** Library id. Unknown ids become custom exercises using [name], [kind] and [muscles]. */
    val exerciseId: String,
    val name: String? = null,
    /** "REPS", "HOLD" or "WEIGHTED" */
    val kind: String? = null,
    /** Muscle enum names, e.g. "CHEST" */
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
)
