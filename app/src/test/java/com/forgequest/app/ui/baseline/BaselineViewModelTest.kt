package com.forgequest.app.ui.baseline

import android.app.Application
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.forgequest.app.data.BaselineRepository
import com.forgequest.app.data.db.AppDatabase
import com.forgequest.app.data.prefs.Settings
import com.forgequest.app.data.seed.ExerciseCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BaselineViewModelTest {
    @Test fun savingRepsPersistsTestAndComputesTarget() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        val repo = BaselineRepository(db)
        val vm = BaselineViewModel(repo, flowOf(ExerciseCatalog.all), flowOf(null), flowOf(Settings()))
        try {
            val result = vm.recordReps("push_up", 12)
            assertEquals(BaselineTargetDisplay.Reps(8), result?.target)
            assertEquals(12, repo.latest("push_up")?.reps)
            assertTrue(db.sessions().finished().isEmpty())
            assertTrue(db.sessions().allRecentDoneSets().isEmpty())
        } finally {
            vm.viewModelScope.cancel()
            db.close()
            Dispatchers.resetMain()
        }
    }

    @Test fun outgrownRepsOfferHarderVariation() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        val vm = BaselineViewModel(BaselineRepository(db), flowOf(ExerciseCatalog.all), flowOf(null), flowOf(Settings()))
        try {
            val big = vm.recordReps("squat", 51)?.target as BaselineTargetDisplay.Reps
            assertEquals(20, big.reps)
            assertEquals("jump_squat", big.harderExercise?.id)
            assertEquals(51, db.baselineTests().latestFor("squat")?.reps)
            assertEquals("jump_squat", (vm.recordReps("squat", 25)?.target as BaselineTargetDisplay.Reps).harderExercise?.id)
            assertEquals(BaselineTargetDisplay.Reps(16), vm.recordReps("squat", 24)?.target)
        } finally {
            vm.viewModelScope.cancel()
            db.close()
            Dispatchers.resetMain()
        }
    }

    @Test fun zeroRepsOffersEasierVariation() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        val vm = BaselineViewModel(BaselineRepository(db), flowOf(ExerciseCatalog.all), flowOf(null), flowOf(Settings()))
        try {
            val result = vm.recordReps("push_up", 0)
            val target = result?.target as BaselineTargetDisplay.EasierVariation
            assertNotNull(target.easierExercise)
            assertNotEquals("push_up", target.easierExercise?.id)
            assertEquals(0, db.baselineTests().latestFor("push_up")?.reps)
        } finally {
            vm.viewModelScope.cancel()
            db.close()
            Dispatchers.resetMain()
        }
    }

    @Test fun holdAndLowRepBoundariesOfferHarderAndEasierMoves() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        val vm = BaselineViewModel(BaselineRepository(db), flowOf(ExerciseCatalog.all), flowOf(null), flowOf(Settings()))
        try {
            val long = vm.recordHold("plank", 825)?.target as BaselineTargetDisplay.Hold
            assertEquals(60, long.seconds)
            assertEquals("hollow_hold", long.harderExercise?.id)
            assertNull(long.easierExercise)
            assertEquals(600, db.baselineTests().latestFor("plank")?.seconds)
            val short = vm.recordHold("plank", 3)?.target as BaselineTargetDisplay.Hold
            assertEquals(3, short.seconds)
            assertNull(short.harderExercise)
            assertNotNull(short.easierExercise)
            val low = vm.recordReps("push_up", 2)?.target as BaselineTargetDisplay.Reps
            assertEquals(1, low.reps)
            assertNull(low.harderExercise)
            assertEquals("knee_push_up", low.easierExercise?.id)
            val huge = vm.recordReps("squat", 51)?.target as BaselineTargetDisplay.Reps
            assertEquals(20, huge.reps)
            assertEquals("jump_squat", huge.harderExercise?.id)
            assertNull(huge.easierExercise)
        } finally {
            vm.viewModelScope.cancel()
            db.close()
            Dispatchers.resetMain()
        }
    }

    @Test fun entryCardRequiresNoFinishedWorkoutNoBaselineAndNoDismissal() {
        for (finished in listOf(false, true)) {
            for (tested in listOf(false, true)) {
                for (dismissed in listOf(false, true)) {
                    assertEquals(!finished && !tested && !dismissed,
                        showBaselineEntry(finished, tested, dismissed))
                }
            }
        }
    }
}
