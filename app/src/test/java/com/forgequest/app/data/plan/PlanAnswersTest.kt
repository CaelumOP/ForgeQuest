package com.forgequest.app.data.plan

import com.forgequest.app.data.seed.ExerciseCatalog
import com.forgequest.app.domain.Cardio
import com.forgequest.app.domain.Equipment
import com.forgequest.app.domain.EquipmentPreset
import com.forgequest.app.domain.Injury
import com.forgequest.app.domain.Muscle
import com.forgequest.app.domain.PlanBuilder
import com.forgequest.app.domain.RepPref
import com.forgequest.app.domain.TrainGoal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanAnswersTest {
    @Test fun roundTripKeepsEveryAnswer() {
        val a = PlanAnswers(
            goal = TrainGoal.STRENGTH, days = setOf(1, 3, 6), minutes = 30, equipment = EquipmentPreset.HOME_GYM.equipment,
            injuries = setOf(Injury.KNEE, Injury.WRIST), avoidIds = setOf("burpee"), pushUpMax = 12, pullUpMax = 3,
            focus = setOf(Muscle.GLUTES), minutesByDay = mapOf(3 to 20), cardio = Cardio.FINISHER, warmUp = false,
            coolDown = true, holdsOk = false, reps = RepPref.LOW,
        )
        assertEquals(a, PlanAnswers.decode(a.encode()))
    }

    @Test fun nothingSavedOrDamagedTextReadsAsNull() {
        assertNull(PlanAnswers.decode(null))
        assertNull(PlanAnswers.decode(""))
        assertNull(PlanAnswers.decode("{not json"))
    }

    @Test fun unknownFieldsAreIgnored() {
        assertNotNull(PlanAnswers.decode("""{"goal":"MUSCLE","future":1}"""))
    }

    @Test fun inputKeepsBodyweightAndDropsMinutesForDaysNotTrained() {
        val input = PlanAnswers(days = setOf(2, 4), equipment = setOf(Equipment.DUMBBELLS), minutesByDay = mapOf(2 to 20, 5 to 90)).toInput()
        assertTrue(Equipment.BODYWEIGHT in input.equipment)
        assertEquals(mapOf(2 to 20), input.minutesByDay)
    }

    @Test fun defaultAnswersBuildAPlan() {
        val plan = PlanBuilder.build(PlanAnswers().toInput(), ExerciseCatalog.all)
        assertEquals(3, plan.draft.days.size)
    }
}
