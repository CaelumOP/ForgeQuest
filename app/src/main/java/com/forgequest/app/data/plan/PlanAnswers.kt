package com.forgequest.app.data.plan

import com.forgequest.app.domain.Cardio
import com.forgequest.app.domain.Equipment
import com.forgequest.app.domain.EquipmentPreset
import com.forgequest.app.domain.Experience
import com.forgequest.app.domain.Injury
import com.forgequest.app.domain.Muscle
import com.forgequest.app.domain.PlanBuilder
import com.forgequest.app.domain.PlanInput
import com.forgequest.app.domain.RepPref
import com.forgequest.app.domain.RestPref
import com.forgequest.app.domain.TrainGoal
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * What the person told the plan builder, saved (as JSON under the `plan_builder_answers` setting) so a rebuild starts
 * from the same answers. Mirrors [PlanInput] without the exercise library. Not part of backups.
 */
@Serializable
data class PlanAnswers(
    val goal: TrainGoal = TrainGoal.GENERAL,
    val days: Set<Int> = PlanBuilder.spreadDays(3),
    val minutes: Int = 45,
    val equipment: Set<Equipment> = EquipmentPreset.CALISTHENICS_PARK.equipment,
    val experience: Experience = Experience.SOME,
    val injuries: Set<Injury> = emptySet(),
    val avoidIds: Set<String> = emptySet(),
    val pushUpMax: Int? = null,
    val pullUpMax: Int? = null,
    val focus: Set<Muscle> = emptySet(),
    val minutesByDay: Map<Int, Int> = emptyMap(),
    val cardio: Cardio = Cardio.NONE,
    val warmUp: Boolean = true,
    val coolDown: Boolean = false,
    val holdsOk: Boolean = true,
    val reps: RepPref = RepPref.AUTO,
    val rest: RestPref = RestPref.AUTO,
) {
    fun toInput() = PlanInput(
        goal = goal, days = days, minutes = minutes, equipment = equipment + Equipment.BODYWEIGHT, experience = experience,
        injuries = injuries, avoidIds = avoidIds, pushUpMax = pushUpMax, pullUpMax = pullUpMax, focus = focus,
        minutesByDay = minutesByDay.filterKeys { it in days }, cardio = cardio, warmUp = warmUp, coolDown = coolDown,
        holdsOk = holdsOk, reps = reps, rest = rest,
    )

    fun encode(): String = json.encodeToString(this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; encodeDefaults = true }

        /** Null for nothing saved or text that no longer parses (a changed enum, a damaged file): the screen then starts fresh. */
        fun decode(raw: String?): PlanAnswers? =
            raw?.takeIf { it.isNotBlank() }?.let { runCatching { json.decodeFromString<PlanAnswers>(it) }.getOrNull() }
    }
}
