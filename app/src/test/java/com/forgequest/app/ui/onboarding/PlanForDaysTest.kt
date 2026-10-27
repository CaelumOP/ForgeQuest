package com.forgequest.app.ui.onboarding

import com.forgequest.app.domain.EquipmentPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Plans: 0 Hemo (5 days), 1 bodyweight (3), 2 dumbbells (3), 3 gym split (6). */
class PlanForDaysTest {
    @Test fun twoThreeAndFourDaysGetTheThreeDayFullBody() {
        for (d in 2..4) {
            assertEquals(1, planForDays(d, EquipmentPreset.CALISTHENICS_PARK))
            assertEquals(2, planForDays(d, EquipmentPreset.FULL_GYM))
        }
    }

    @Test fun fiveDaysGetsHemoExceptWithNoEquipment() {
        assertEquals(0, planForDays(5, EquipmentPreset.CALISTHENICS_PARK))
        assertEquals(0, planForDays(5, EquipmentPreset.FULL_GYM))
        assertEquals(1, planForDays(5, EquipmentPreset.BODYWEIGHT_ONLY))
    }

    @Test fun hemoIsNeverPreselectedBelowFiveDays() {
        for (p in EquipmentPreset.entries) for (d in 2..4) assertNotEquals(0, planForDays(d, p))
    }
}
