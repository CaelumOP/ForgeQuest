package com.forgequest.app.ui.components

import com.forgequest.app.domain.Muscle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MuscleMapSelectionTest {
    @Test
    fun selectionRequiresNonNullMatchingMuscle() {
        assertFalse(isMusclePartSelected(null, null))
        assertFalse(isMusclePartSelected(Muscle.CHEST, null))
        assertFalse(isMusclePartSelected(null, Muscle.CHEST))
        assertFalse(isMusclePartSelected(Muscle.TRICEPS, Muscle.CHEST))
        assertTrue(isMusclePartSelected(Muscle.CHEST, Muscle.CHEST))
    }
}
