package com.forgequest.app.ui.baseline

import com.forgequest.app.data.ActivePlan
import com.forgequest.app.data.PlanDayWithItems
import com.forgequest.app.data.db.BaselineTestEntity
import com.forgequest.app.data.db.PlanEntity
import com.forgequest.app.data.db.PlanDayEntity
import com.forgequest.app.data.db.PlanItemEntity
import com.forgequest.app.data.prefs.Settings
import com.forgequest.app.data.seed.ExerciseCatalog
import com.forgequest.app.domain.Equipment
import com.forgequest.app.domain.ExerciseKind
import org.junit.Assert.*
import org.junit.Test

class BaselineMovesTest {
    private val noGear = listOf("push_up", "squat", "hip_bridge", "plank")
    private val gym = listOf("bench_press", "pull_up", "back_squat", "romanian_deadlift", "plank")
    private val library = ExerciseCatalog.all
    private fun plan(ids: List<String>, block: String = "Main") = ActivePlan(
        PlanEntity(id = 1, name = "Test", source = "builder", active = true, createdAt = 1),
        listOf(PlanDayWithItems(
            PlanDayEntity(id = 1, planId = 1, dayOfWeek = 1, title = "Day", isRest = false),
            ids.mapIndexed { index, id -> PlanItemEntity(dayId = 1, position = index,
                exerciseId = id, block = block, circuit = false, sets = 3, repsMin = 6) },
        )),
    )

    @Test fun gearCasesMatchWebAndCallerOverridesSavedGym() {
        val cases = listOf(
            emptySet<Equipment>() to noGear,
            setOf(Equipment.BODYWEIGHT) to noGear,
            setOf(Equipment.DUMBBELLS, Equipment.FLAT_BENCH) to listOf("db_bench_press", "db_row", "goblet_squat", "db_romanian_deadlift", "plank"),
            setOf(Equipment.DUMBBELLS) to listOf("push_up", "db_row", "goblet_squat", "db_romanian_deadlift", "plank"),
            Equipment.allActive to gym,
            (Equipment.allActive - Equipment.PULL_UP_BAR) to listOf("bench_press", "lat_pulldown", "back_squat", "romanian_deadlift", "plank"),
        )
        for ((gear, expected) in cases) {
            val moves = buildBaselineMoves(library, Settings(equipment = "all"), null, emptyMap(), gear)
            assertEquals(expected, moves.map { it.exercise.id })
            assertTrue(moves.all { it.isSuggested })
            if (gear.isEmpty()) assertTrue(moves.none { it.exercise.kind == ExerciseKind.WEIGHTED })
        }
        assertEquals(noGear, buildBaselineMoves(library, Settings(equipment = "BODYWEIGHT"), null, emptyMap()).map { it.exercise.id })
    }

    @Test fun planReplacesOnlyCompatibleCompoundPattern() {
        val rejected = listOf("around_the_worlds", "dumbbell_lying_rear_lateral_raise", "arnold_press", "leg_extension", "cable_fly")
        assertTrue(rejected.all { id -> library.any { it.id == id } })
        val settings = Settings(equipment = "all")
        assertEquals(gym, buildBaselineMoves(library, settings, plan(rejected), emptyMap()).map { it.exercise.id })
        val moves = buildBaselineMoves(library, settings, plan(rejected + listOf("front_squat", "db_row")), emptyMap())
        assertEquals(listOf("bench_press", "db_row", "front_squat", "romanian_deadlift", "plank"), moves.map { it.exercise.id })
        assertEquals(6, moves[2].repsMin)
        assertEquals(gym, buildBaselineMoves(library, settings, plan(listOf("front_squat"), "Finisher"), emptyMap()).map { it.exercise.id })
        assertEquals(noGear, buildBaselineMoves(library, settings, plan(listOf("front_squat", "weighted_push_up")), emptyMap(), emptySet()).map { it.exercise.id })
    }

    @Test fun testedMovesAlsoRequireActiveGear() {
        val latest = listOf("pull_up", "db_row", "knee_push_up").mapIndexed { i, id ->
            id to BaselineTestEntity(exerciseId = id, kind = if (id == "db_row") "WEIGHTED" else "REPS", reps = 8, createdAt = i.toLong())
        }.toMap()
        val moves = buildBaselineMoves(library, Settings(equipment = "all"), null, latest, emptySet())
        assertEquals(noGear + "knee_push_up", moves.map { it.exercise.id })
        assertFalse(moves.last().isSuggested)
    }
}
