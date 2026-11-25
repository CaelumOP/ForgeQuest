package com.forgequest.app.ui.workouts

import com.forgequest.app.data.db.PlanItemEntity
import com.forgequest.app.data.db.SetEntity
import com.forgequest.app.domain.ExerciseKind
import com.forgequest.app.domain.SetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveWorkoutIntraSessionTest {
    private var id = 0L

    private fun set(
        pos: Int,
        idx: Int,
        exerciseId: String = "e$pos",
        restSeconds: Int = 90,
        setType: SetType = SetType.WORKING,
        done: Boolean = false,
        block: String = "Main",
        circuit: Boolean = false,
    ) = SetEntity(
        id = ++id,
        sessionId = 1L,
        exerciseId = exerciseId,
        itemPosition = pos,
        block = block,
        circuit = circuit,
        setIndex = idx,
        targetLabel = "",
        restSeconds = restSeconds,
        setType = setType.name,
        done = done,
        doneAt = if (done) 100L else null,
    )

    @Test
    fun effectiveRestFlagsSupersetAndCircuitTransitionOnlyWhenUndonePartnerRemains() {
        val a1 = set(0, 0, restSeconds = 90)
        val a2 = set(1, 0, restSeconds = 90)
        val straight = set(2, 0, restSeconds = 90)
        val c1 = set(3, 0, restSeconds = 30, block = "Circuit A", circuit = true)
        val c2 = set(4, 0, restSeconds = 30, block = "Circuit A", circuit = true)
        val groups = group(listOf(a1, a2, straight, c1, c2)) { pos -> if (pos == 0 || pos == 1) 1 else null }

        val r1 = effectiveRest(a1, groups)
        assertEquals(0, r1.seconds)
        assertTrue(r1.isShortTransition)

        val r2 = effectiveRest(a2, groups)
        assertEquals(90, r2.seconds)
        assertFalse(r2.isShortTransition)

        val r3 = effectiveRest(straight, groups)
        assertEquals(90, r3.seconds)
        assertFalse(r3.isShortTransition)

        val rc1 = effectiveRest(c1, groups)
        assertEquals(0, rc1.seconds)
        assertTrue(rc1.isShortTransition)

        val rc2 = effectiveRest(c2, groups)
        assertEquals(30, rc2.seconds)
        assertFalse(rc2.isShortTransition)
    }

    @Test
    fun resolveRepsMinUsesMatchingPlanItemAndFallbacks() {
        val item = PlanItemEntity(
            id = 1L,
            dayId = 1L,
            position = 0,
            exerciseId = "bench",
            block = "Main",
            circuit = false,
            sets = 3,
            repsMin = 6,
            repsMax = 8,
        )
        val plan = mapOf(0 to item)

        assertEquals(6, resolveRepsMin(set(0, 0, exerciseId = "bench"), ExerciseKind.WEIGHTED, plan))
        assertEquals(8, resolveRepsMin(set(0, 0, exerciseId = "incline"), ExerciseKind.WEIGHTED, plan))
        assertEquals(8, resolveRepsMin(set(1, 0, exerciseId = "row"), ExerciseKind.WEIGHTED, plan))
        assertNull(resolveRepsMin(set(1, 0, exerciseId = "plank"), ExerciseKind.HOLD, plan))
        assertNull(resolveRepsMin(set(1, 0, exerciseId = "pushup"), ExerciseKind.REPS, plan))
    }
}
