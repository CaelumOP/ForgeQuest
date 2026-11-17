package com.forgequest.app.data.friends

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.forgequest.app.data.ActivePlan
import com.forgequest.app.data.PlanDayWithItems
import com.forgequest.app.data.ProgressRepository
import com.forgequest.app.data.WorkoutRepository
import com.forgequest.app.data.db.AppDatabase
import com.forgequest.app.data.db.FoodCategory
import com.forgequest.app.data.db.MealEntity
import com.forgequest.app.data.db.PlanDayEntity
import com.forgequest.app.data.db.PlanEntity
import com.forgequest.app.data.db.PlanItemEntity
import com.forgequest.app.data.db.SessionEntity
import com.forgequest.app.data.db.SetEntity
import com.forgequest.app.data.prefs.SettingsRepository
import com.forgequest.app.domain.Dates
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
import java.time.LocalDateTime
import java.time.ZoneId
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

    @Test
    fun snapshotJsonIncludesSortedTrainDaysWhenPlanHasDaysAndNullWhenNone() = runBlocking {
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
            val initial = progress.freshSnapshot()

            val noPlanSnap = repo.buildSnapshot(initial.copy(plan = null), sharePrs = false, today = today, zone = ZoneOffset.UTC)
            assertNull(noPlanSnap.trainDays)
            val noPlanJson = FriendsApi.json.parseToJsonElement(
                FriendsApi.json.encodeToString(Snapshot.serializer(), noPlanSnap),
            ).jsonObject
            assertTrue(noPlanJson["trainDays"]?.jsonPrimitive?.content == "null" || noPlanJson["trainDays"] == null)

            fun itemFor(dayId: Long) = PlanItemEntity(
                dayId = dayId, position = 0, exerciseId = "bench", block = "Main", circuit = false, sets = 3,
            )
            val activePlan = ActivePlan(
                plan = PlanEntity(id = 1, name = "PPL", source = "manual", active = true, createdAt = 0L),
                days = listOf(
                    PlanDayWithItems(PlanDayEntity(id = 1, planId = 1, dayOfWeek = 5, title = "Fri", isRest = false), listOf(itemFor(1))),
                    PlanDayWithItems(PlanDayEntity(id = 2, planId = 1, dayOfWeek = 1, title = "Mon", isRest = false), listOf(itemFor(2))),
                    PlanDayWithItems(PlanDayEntity(id = 3, planId = 1, dayOfWeek = 3, title = "Wed", isRest = true), listOf(itemFor(3))),
                    PlanDayWithItems(PlanDayEntity(id = 4, planId = 1, dayOfWeek = 7, title = "Sun", isRest = false), emptyList()),
                ),
            )
            val withPlanSnap = repo.buildSnapshot(initial.copy(plan = activePlan), sharePrs = false, today = today, zone = ZoneOffset.UTC)
            assertEquals(listOf(1, 5), withPlanSnap.trainDays)
            val withPlanJson = FriendsApi.json.parseToJsonElement(
                FriendsApi.json.encodeToString(Snapshot.serializer(), withPlanSnap),
            ).jsonObject
            assertEquals(listOf(1, 5), withPlanJson["trainDays"]!!.jsonArray.map { it.jsonPrimitive.int })
        } finally {
            scope.cancel()
            db.close()
            cache.clear()
            session.clear()
        }
    }

    @Test
    fun legacyNudgedKeysMigrateOnceAndExpiredEntriesArePruned() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val session = FriendsSession(context).also { it.clear() }
        val cache = FriendsCache(context).also { it.clear() }
        val prefs = context.getSharedPreferences(FriendsSession.PREFS_NAME, Context.MODE_PRIVATE)
        try {
            val today = Dates.today()
            val zone = ZoneId.systemDefault()
            val nowSec = LocalDateTime.of(
                LocalDate.ofEpochDay(today),
                java.time.LocalTime.of(12, 0),
            ).atZone(zone).toEpochSecond()
            val midnight = NudgeGate.localMidnightSec(nowSec, zone)
            prefs.edit()
                .putLong("nudged_day", today)
                .putStringSet("nudged_ids", setOf("f_legacy"))
                .putString("nudge_next_at", """{"f_expired":${nowSec - 10},"f_active":${nowSec + 1800}}""")
                .commit()

            val progress = ProgressRepository(db, SettingsRepository(context), WorkoutRepository(db), scope)
            val api = FriendsApi(OkHttpClient(), { "https://example.invalid" }, { null })
            val repo = FriendsRepository(context, api, session, cache, progress) { nowSec }

            assertEquals(
                mapOf("f_active" to nowSec + 1800L, "f_legacy" to midnight),
                repo.nudgeLocal.value,
            )
            assertFalse(prefs.contains("nudged_day"))
            assertFalse(prefs.contains("nudged_ids"))
        } finally {
            scope.cancel()
            db.close()
            cache.clear()
            session.clear()
        }
    }
}
