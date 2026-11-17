package com.forgequest.app.data.friends

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.forgequest.app.data.ProgressRepository
import com.forgequest.app.data.WorkoutRepository
import com.forgequest.app.data.db.AppDatabase
import com.forgequest.app.data.db.FoodCategory
import com.forgequest.app.data.db.MealEntity
import com.forgequest.app.data.db.SessionEntity
import com.forgequest.app.data.db.SetEntity
import com.forgequest.app.data.prefs.SettingsRepository
import com.forgequest.app.domain.Dates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class FriendsSnapshotTest {
    @Test
    fun rawSenderUsesMealCrossingTimeAndCountsPrivatePrsBeyondDetailLimit() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val session = FriendsSession(context).also { it.clear() }
        val cache = FriendsCache(context).also { it.clear() }
        try {
            val progress = ProgressRepository(db, SettingsRepository(context), WorkoutRepository(db), scope)
            val api = FriendsApi(OkHttpClient(), { "https://example.invalid" }, { null })
            val repo = FriendsRepository(context, api, session, cache, progress)
            val today = LocalDate.of(2026, 11, 18).toEpochDay()
            val ws = Dates.weekStart(today)
            val finish = LocalDate.ofEpochDay(today).atTime(10, 0).toEpochSecond(ZoneOffset.UTC) * 1000
            val workout = SessionEntity(id = 1, planDayId = null, title = "Free workout", epochDay = today,
                startedAt = finish - 1000, finishedAt = finish)
            val set = SetEntity(sessionId = 1, exerciseId = "bench", itemPosition = 0, block = "Main",
                circuit = false, setIndex = 0, targetLabel = "5", reps = 5, weightKg = 50f, done = true, isPr = true)
            val initial = progress.freshSnapshot()
            val snap = initial.copy(settings = initial.settings.copy(weeklyTargetOverride = 1, proteinGoal = 100),
                plan = null, sessions = listOf(workout, workout.copy(id = 2, epochDay = ws - 1), workout.copy(id = 3, finishedAt = null)),
                doneSets = (1L..12L).map { set.copy(id = it) } + listOf(
                    set.copy(setType = "WARMUP"), set.copy(setType = "DROPSET"), set.copy(setType = "RESTPAUSE"),
                    set.copy(block = "Warm-up"), set.copy(done = false), set.copy(sessionId = 2), set.copy(sessionId = 3)))
            val crossing = finish + 7200000
            val meal = MealEntity(epochDay = today, loggedAt = crossing, name = "Meal", category = FoodCategory.LUNCH,
                kcal = 300, protein = 60f, quantity = 4f)
            val meals = listOf(meal, meal.copy(loggedAt = finish + 3600000, protein = 40f),
                meal.copy(loggedAt = crossing + 3600000, protein = 50f))
            db.food().insertMeals(meals + meal.copy(epochDay = ws - 1))
            val storedMeals = progress.mealsForScore(ws)
            assertEquals(3, storedMeals.size)
            val hidden = repo.buildSnapshot(snap, false, today, ZoneOffset.UTC, storedMeals)
            assertEquals(1, hidden.done)
            assertEquals(1, hidden.target)
            assertEquals(12, hidden.prCount)
            assertEquals(1, hidden.proteinDays)
            assertEquals(112, hidden.score)
            assertEquals(crossing / 1000, hidden.reachedAt)
            assertNull(hidden.prs)
            val shared = repo.buildSnapshot(snap, true, today, ZoneOffset.UTC, storedMeals)
            assertEquals(10, shared.prs!!.size)
            assertEquals(hidden, shared.copy(prs = null))
            val noGoal = repo.buildSnapshot(snap.copy(settings = snap.settings.copy(proteinGoal = 0)), false, today, ZoneOffset.UTC, storedMeals)
            assertEquals(0, noGoal.proteinDays)
            assertEquals(110, noGoal.score)
            assertEquals(finish / 1000, noGoal.reachedAt)

            val me = Me("me", sharePhoto = false, sharePrs = false, joined = true)
            cache.save(FriendsView(me, today, ws, mySnapshot = hidden.copy(score = 17)))
            val restored = FriendsRepository(context, api, session, cache, progress)
            assertEquals(17, restored.state.value!!.mySnapshot!!.score)
            cache.save(FriendsView(me, today, ws, mySnapshot = null))
            assertNull(FriendsRepository(context, api, session, cache, progress).state.value!!.mySnapshot)
        } finally {
            scope.cancel()
            db.close()
            cache.clear()
            session.clear()
        }
    }
}
