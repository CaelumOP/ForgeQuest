package com.forgequest.app.ui.onboarding

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.forgequest.app.domain.NutritionGoal
import com.forgequest.app.domain.TrainGoal
import com.forgequest.app.ui.components.CoachMarks
import com.forgequest.app.ui.components.canShowCoachMark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class OnboardingGoalMappingTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clearPrefs() {
        context.getSharedPreferences(TourPrefs.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun goalCardMapsToNutritionGoalJustLogAndTrainGoal() {
        val state = OnboardingState()

        val muscle = state.selectGoal(OnboardingGoalOption.MUSCLE)
        assertEquals(NutritionGoal.GAIN, muscle.goal)
        assertFalse(muscle.justLog)
        assertEquals(TrainGoal.MUSCLE, muscle.goalOption.trainGoal)

        val fat = state.selectGoal(OnboardingGoalOption.FAT)
        assertEquals(NutritionGoal.CUT, fat.goal)
        assertFalse(fat.justLog)
        assertEquals(TrainGoal.FAT_LOSS, fat.goalOption.trainGoal)

        val fit = state.selectGoal(OnboardingGoalOption.FIT)
        assertEquals(NutritionGoal.MAINTAIN, fit.goal)
        assertFalse(fit.justLog)
        assertEquals(TrainGoal.GENERAL, fit.goalOption.trainGoal)

        val justLog = state.selectGoal(OnboardingGoalOption.JUST_LOG)
        assertEquals(NutritionGoal.MAINTAIN, justLog.goal)
        assertTrue(justLog.justLog)
        assertEquals(TrainGoal.GENERAL, justLog.goalOption.trainGoal)
    }

    @Test
    fun goalOptionFromSettingsRoundTrips() {
        assertEquals(OnboardingGoalOption.MUSCLE, OnboardingGoalOption.from(NutritionGoal.GAIN, justLog = false))
        assertEquals(OnboardingGoalOption.FAT, OnboardingGoalOption.from(NutritionGoal.CUT, justLog = false))
        assertEquals(OnboardingGoalOption.FIT, OnboardingGoalOption.from(NutritionGoal.MAINTAIN, justLog = false))
        assertEquals(OnboardingGoalOption.JUST_LOG, OnboardingGoalOption.from(NutritionGoal.GAIN, justLog = true))
    }

    @Test
    fun coachMarksRequireFirstTickBeforeShowingAndRespectResetTips() {
        TourPrefs.setLanguageChosen(context)
        TourPrefs.setGsEligible(context)
        TourPrefs.setGsDismissed(context)
        TourPrefs.setGsFriendDone(context)

        // Before the first set tick, no coach tips show.
        assertFalse(TourPrefs.firstTickDone(context))
        AppTour.ALL_COACH_KEYS.forEach { key ->
            assertFalse("Expected $key to stay hidden before first tick", CoachMarks.shouldShow(context, key))
        }

        TourPrefs.setFirstTickDone(context)
        AppTour.ALL_COACH_KEYS.forEach { key ->
            assertTrue("Expected $key to show after first tick", CoachMarks.shouldShow(context, key))
        }

        AppTour.ALL_COACH_KEYS.forEach { CoachMarks.markSeen(context, it) }
        assertTrue(AppTour.skip(context))
        assertFalse(AppTour.skip(context))
        assertFalse(CoachMarks.shouldShow(context, AppTour.PROGRESS))

        AppTour.resetTips(context)

        assertFalse(TourPrefs.tourSeen(context))
        AppTour.ALL_COACH_KEYS.forEach { key ->
            assertFalse("Expected $key to be cleared", CoachMarks.seen(context, key))
            assertTrue("Expected $key to show again", CoachMarks.shouldShow(context, key))
        }
        assertTrue(TourPrefs.firstTickDone(context))
        assertTrue(TourPrefs.languageChosen(context))
        assertTrue(TourPrefs.gsEligible(context))
        assertTrue(TourPrefs.gsDismissed(context))
        assertTrue(TourPrefs.gsFriendDone(context))
    }

    @Test
    fun canShowCoachMarkChecksFirstTickTourAndSeenFlags() {
        assertFalse(canShowCoachMark(firstTickDone = false, tourSeen = false, stepSeen = false))
        assertFalse(canShowCoachMark(firstTickDone = true, tourSeen = true, stepSeen = false))
        assertFalse(canShowCoachMark(firstTickDone = true, tourSeen = false, stepSeen = true))
        assertTrue(canShowCoachMark(firstTickDone = true, tourSeen = false, stepSeen = false))
    }
}
