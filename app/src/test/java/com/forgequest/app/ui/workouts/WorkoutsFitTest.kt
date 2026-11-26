package com.forgequest.app.ui.workouts

import com.forgequest.app.data.db.SessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutsFitTest {
    @Test
    fun shouldShowMuscleRecoveryMapRequiresFinishedWorkout() {
        assertFalse(shouldShowMuscleRecoveryMap(emptyList()))
        val unfinished = SessionEntity(id = 1L, planDayId = null, title = "Push", startedAt = 1000L, finishedAt = null, epochDay = 20000L)
        assertFalse(shouldShowMuscleRecoveryMap(listOf(unfinished)))
        val finished = unfinished.copy(id = 2L, finishedAt = 2000L)
        assertTrue(shouldShowMuscleRecoveryMap(listOf(unfinished, finished)))
    }

    @Test
    fun fitsExerciseCellsIntoTwoRowsByDefaultOrThreeFourOnTallScreens() {
        assertEquals(ExerciseFit(2, 0, 0), fitExerciseRows(300, 0))
        // Default 2-row height (92 dp = 2 x 46 dp = 4 cells): up to 4 fit, >4 reserves 4th cell for "+N more"
        assertEquals(ExerciseFit(2, 4, 0), fitExerciseRows(92, 4))
        assertEquals(ExerciseFit(2, 3, 3), fitExerciseRows(92, 6))
        assertEquals(ExerciseFit(2, 3, 5), fitExerciseRows(137, 8))
        // Tall screen with 3 rows available (138..183 dp = 6 cells)
        assertEquals(ExerciseFit(3, 6, 0), fitExerciseRows(138, 6))
        assertEquals(ExerciseFit(3, 5, 3), fitExerciseRows(160, 8))
        // Tall screen with 4 rows available (>=184 dp = 8 cells, capped at 4 rows)
        assertEquals(ExerciseFit(4, 8, 0), fitExerciseRows(190, 8))
        assertEquals(ExerciseFit(4, 7, 5), fitExerciseRows(190, 12))
        assertEquals(ExerciseFit(4, 7, 5), fitExerciseRows(310, 12))
        // When itemCount <= 4 on a tall screen, card stays at 2 rows
        assertEquals(ExerciseFit(2, 4, 0), fitExerciseRows(310, 4))
    }
}
