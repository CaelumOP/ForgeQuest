package com.forgequest.app.ui.plan

import com.forgequest.app.data.plan.PlanAnswers
import com.forgequest.app.data.seed.ExerciseCatalog
import com.forgequest.app.domain.EquipmentPreset
import com.forgequest.app.domain.ExerciseSwapEngine
import com.forgequest.app.domain.ExerciseTiers
import com.forgequest.app.domain.Experience
import com.forgequest.app.domain.MovementPattern
import com.forgequest.app.domain.PlanBuilder
import com.forgequest.app.domain.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanBuilderStepperTest {

    @Test
    fun zeroPullUpsEnteredReachesFoundation() {
        val entered = stepperCountFromValue(0f)
        assertEquals(0, entered)

        val saved = PlanAnswers(
            days = PlanBuilder.spreadDays(5),
            equipment = EquipmentPreset.FULL_GYM.equipment,
            experience = Experience.SEASONED,
            pullUpMax = entered,
        )
        val decoded = PlanAnswers.decode(saved.encode())!!
        assertEquals(0, decoded.pullUpMax)

        val plan = PlanBuilder.build(decoded.toInput(), ExerciseCatalog.all)
        val byId = ExerciseCatalog.all.associateBy { it.id }
        val verticalPulls = plan.draft.days.flatMap { it.items }
            .map { byId.getValue(it.exerciseId) }
            .filter { ExerciseSwapEngine.profileFor(it).pattern == MovementPattern.VERTICAL_PULL }

        assertTrue(verticalPulls.isNotEmpty())
        assertTrue(verticalPulls.all { (ExerciseTiers.of(it) ?: Tier.FOUNDATION) == Tier.FOUNDATION })
    }
}
