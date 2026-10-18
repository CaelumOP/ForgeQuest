package com.forgequest.app.data

import com.forgequest.app.data.db.AppDatabase
import com.forgequest.app.data.db.BadgeEntity
import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.db.MealEntity
import com.forgequest.app.data.db.SessionEntity
import com.forgequest.app.data.db.SetEntity
import com.forgequest.app.data.db.WeightEntity
import com.forgequest.app.data.prefs.Settings
import com.forgequest.app.data.prefs.SettingsRepository
import com.forgequest.app.domain.BadgeStats
import com.forgequest.app.domain.Badges
import com.forgequest.app.domain.Dates
import com.forgequest.app.domain.ExerciseKind
import com.forgequest.app.domain.LevelInfo
import com.forgequest.app.domain.Muscle
import com.forgequest.app.domain.MuscleRecovery
import com.forgequest.app.domain.Recovery
import com.forgequest.app.domain.StreakCalculator
import com.forgequest.app.domain.WeeklyStreak
import com.forgequest.app.domain.Xp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.first

data class DayNutrition(val epochDay: Long, val kcal: Int, val protein: Float, val carbs: Float = 0f)

data class ProgressSnapshot(
    val settings: Settings,
    val plan: ActivePlan?,
    val streak: WeeklyStreak,
    val level: LevelInfo,
    val recovery: Map<Muscle, MuscleRecovery>,
    val badges: List<BadgeEntity>,
    val today: DayNutrition,
    val nutritionByDay: List<DayNutrition>,
    val weights: List<WeightEntity>,
    val sessions: List<SessionEntity>,
    val doneSets: List<SetEntity>,
    val exercises: Map<String, ExerciseEntity>,
    val stats: BadgeStats,
) {
    val latestWeight: Float? get() = weights.lastOrNull()?.kg
    val workoutDoneToday: Boolean get() = sessions.any { it.epochDay == Dates.today() }
}

class ProgressRepository(
    private val db: AppDatabase,
    private val settingsRepo: SettingsRepository,
    private val workouts: WorkoutRepository,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    private data class Raw(
        val sessions: List<SessionEntity>,
        val sets: List<SetEntity>,
        val exercises: List<ExerciseEntity>,
        val badges: List<BadgeEntity>,
        val meals: List<MealEntity>,
        val mealCount: Int,
        val scannedCount: Int,
    )

    /** Only the last [NUTRITION_DAYS] days of meals are loaded; totals come from COUNT queries so badges still see everything. */
    private val raw: Flow<Raw> = combine(
        combine(db.sessions().observeFinished(), db.sessions().observeAllDoneSets(), db.exercises().observeAll()) { s, sets, ex -> Triple(s, sets, ex) },
        db.badges().observeAll(),
        db.food().observeMealsSince(Dates.today() - NUTRITION_DAYS),
        db.food().observeMealCount(),
        db.food().observeScannedCount(),
    ) { (s, sets, ex), b, m, count, scanned -> Raw(s, sets, ex, b, m, count, scanned) }

    /**
     * Rebuilt on every change and off the main thread: with years of data this is real work. Shared, so the
     * screens on the back stack read one copy instead of each re-running the queries and the build.
     */
    val snapshot: Flow<ProgressSnapshot> = combine(
        raw, settingsRepo.settings, workouts.activePlan, db.weights().observeAll(),
    ) { r, settings, plan, weights -> build(r, settings, plan, weights) }.flowOn(Dispatchers.Default)
        .shareIn(scope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), replay = 1)

    private fun build(r: Raw, settings: Settings, plan: ActivePlan?, weights: List<WeightEntity>): ProgressSnapshot {
        val today = Dates.today()
        val exercises = r.exercises.associateBy { it.id }
        val workoutDays = r.sessions.map { it.epochDay }.toSet()
        val start = minOf(workoutDays.minOrNull() ?: today, today)
        val streak = StreakCalculator.compute(
            workoutDays = workoutDays,
            plannedTrainingDays = plan?.trainingDays ?: emptySet(),
            targetOverride = settings.weeklyTargetOverride,
            startEpochDay = start,
            today = today,
        )

        val sessionById = r.sessions.associateBy { it.id }
        val hits = r.sets.mapNotNull { set ->
            val ex = exercises[set.exerciseId] ?: return@mapNotNull null
            val at = sessionById[set.sessionId]?.finishedAt ?: return@mapNotNull null
            ex to at
        }.distinct().flatMap { (ex, at) ->
            ex.primary.map { Triple(it, at, 1f) } + ex.secondary.map { Triple(it, at, 0.5f) }
        }
        val recovery = Recovery.compute(hits, System.currentTimeMillis())

        val byDay = r.meals.groupBy { it.epochDay }.map { (day, meals) ->
            DayNutrition(day, meals.sumOf { it.kcal }, meals.sumOf { it.protein.toDouble() }.toFloat(), meals.sumOf { it.carbs.toDouble() }.toFloat())
        }.sortedBy { it.epochDay }
        val todayNutrition = byDay.firstOrNull { it.epochDay == today } ?: DayNutrition(today, 0, 0f)

        val stats = statsFor(r, exercises, streak.best, byDay, settings.proteinGoal)
        val level = Xp.levelFor(
            Xp.total(r.sessions.size, r.sets.size, r.sets.count { it.isPr }, r.badges.size)
        )
        return ProgressSnapshot(
            settings, plan, streak, level, recovery, r.badges, todayNutrition, byDay, weights,
            r.sessions, r.sets, exercises, stats,
        )
    }

    private fun statsFor(
        r: Raw,
        exercises: Map<String, ExerciseEntity>,
        bestStreak: Int,
        byDay: List<DayNutrition>,
        proteinGoal: Int,
    ): BadgeStats {
        val bestReps = mutableMapOf<String, Int>()
        val bestSeconds = mutableMapOf<String, Int>()
        val bestWeight = mutableMapOf<String, Float>()
        for (s in r.sets) {
            val kind = exercises[s.exerciseId]?.kind ?: ExerciseKind.REPS
            s.reps?.let { bestReps.merge(s.exerciseId, it, ::maxOf) }
            s.seconds?.let { bestSeconds.merge(s.exerciseId, it, ::maxOf) }
            if (kind == ExerciseKind.WEIGHTED) s.weightKg?.let { bestWeight.merge(s.exerciseId, it, ::maxOf) }
        }
        val hitDays = byDay.filter { it.protein >= proteinGoal }.map { it.epochDay }.toSet()
        var run = 0
        var day = Dates.today()
        if (day !in hitDays) day -= 1
        while (day in hitDays) { run++; day-- }
        return BadgeStats(
            workouts = r.sessions.size,
            bestStreakWeeks = bestStreak,
            bestReps = bestReps,
            bestSeconds = bestSeconds,
            bestWeight = bestWeight,
            mealsLogged = r.mealCount,
            mealsScanned = r.scannedCount,
            proteinGoalDaysInARow = run,
        )
    }

    /** Unlocks any badges whose rule now passes. Returns the newly unlocked ones. */
    suspend fun evaluateBadges(): List<com.forgequest.app.domain.BadgeDef> {
        val snap = snapshot.first()
        val owned = snap.badges.map { it.id }.toSet()
        val fresh = Badges.all.filter { it.id !in owned && it.unlocked(snap.stats) }
        if (fresh.isNotEmpty()) {
            val now = System.currentTimeMillis()
            db.badges().insertAll(fresh.map { BadgeEntity(it.id, now) })
        }
        return fresh
    }

    suspend fun markBadgesSeen() = db.badges().markAllSeen()

    private companion object { const val NUTRITION_DAYS = 90L }

    suspend fun logWeight(kg: Float) =
        db.weights().upsert(WeightEntity(Dates.today(), (kg * 10).toInt() / 10f, System.currentTimeMillis()))
}
