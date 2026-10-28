package com.forgequest.app.domain

import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.db.PlanItemEntity
import com.forgequest.app.data.db.SessionEntity
import com.forgequest.app.data.db.SetEntity
import com.forgequest.app.domain.WorkoutDebriefMath.Headline
import com.forgequest.app.domain.WorkoutDebriefMath.Next
import com.forgequest.app.domain.WorkoutDebriefMath.Watch
import com.forgequest.app.domain.WorkoutDebriefMath.Win
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The same scenario table as web domain/debrief.test.ts. */
class WorkoutDebriefMathTest {
    private fun s(reps: Int?, kg: Float? = null, effort: String = "", note: String = "", block: String = "Main", pr: Boolean = false, seconds: Int? = null) =
        SetEntity(
            sessionId = 0, exerciseId = "", itemPosition = 0, block = block, circuit = false, setIndex = 0, targetLabel = "",
            reps = reps, seconds = seconds, weightKg = kg, done = true, isPr = pr, effort = effort, note = note,
        )

    private fun n(count: Int, reps: Int?, kg: Float? = null, effort: String = "", block: String = "Main", pr: Boolean = false) =
        List(count) { s(reps, kg, effort = effort, block = block, pr = pr) }

    private fun hold(count: Int, seconds: Int, effort: String = "") = List(count) { s(null, null, effort = effort, seconds = seconds) }

    private fun range(sets: Int = 3, min: Int = 8, max: Int = 12) =
        PlanItemEntity(dayId = 1, position = 0, exerciseId = "", block = "Main", circuit = false, sets = sets, repsMin = min, repsMax = max)

    private fun holdItem(seconds: Int, sets: Int = 3) =
        PlanItemEntity(dayId = 1, position = 0, exerciseId = "", block = "Main", circuit = false, sets = sets, seconds = seconds)

    private val unset = -1

    private fun ex(
        id: String,
        kind: ExerciseKind,
        done: List<SetEntity>,
        last: List<SetEntity> = emptyList(),
        gap: Int? = unset,
        newest: Boolean = true,
        item: PlanItemEntity? = null,
        harder: String? = null,
    ) = WorkoutDebriefMath.Exercise(
        exerciseId = id,
        kind = kind,
        done = done.mapIndexed { i, x -> x.copy(sessionId = 2, exerciseId = id, setIndex = i) },
        last = last.mapIndexed { i, x -> x.copy(sessionId = 1, exerciseId = id, setIndex = i) },
        gapDays = if (gap == unset) (if (last.isNotEmpty()) 3 else null) else gap,
        newest = newest,
        item = item?.copy(exerciseId = id),
        harderName = harder,
    )

    private class Run(val result: WorkoutDebriefMath.Result, val score: WorkoutScore.Result) {
        val headline get() = result.headline
        val wins get() = result.wins
        val watch get() = result.watch
        val next get() = result.next
    }

    private fun run(
        exs: List<WorkoutDebriefMath.Exercise>,
        planned: Int? = null,
        low: Boolean = false,
        firstEver: Boolean = false,
        afterBreak: Boolean = false,
        plannedIds: List<String>? = null,
        weeklyTarget: Int = 0,
        weekCount: Int = 1,
        weekCountBefore: Int = 0,
        report: FatigueAndPlateauReport? = null,
        unit: WeightUnit = WeightUnit.KG,
        healthOn: Boolean = false,
        sessionId: Long = 6,
    ): Run {
        val score = WorkoutScore.compute(exs.map { WorkoutScore.Work(it.exerciseId, it.kind, it.done, it.last) }, planned, low, firstEver, afterBreak)
        val input = WorkoutDebriefMath.Input(
            sessionId = sessionId,
            score = score,
            exercises = exs,
            plannedExerciseIds = (plannedIds ?: exs.map { it.exerciseId }.filter { it != "jumping_jacks" }).toSet(),
            weeklyTarget = weeklyTarget,
            weekCount = weekCount,
            weekCountBefore = weekCountBefore,
            report = report,
            unit = unit,
            healthOn = healthOn,
        )
        return Run(WorkoutDebriefMath.compute(input), score)
    }

    private fun report(needsDeload: Boolean = false, stalled: List<StalledLift> = emptyList()) =
        FatigueAndPlateauReport(trainingLoadPct = 100, stalledLifts = stalled, needsDeload = needsDeload, deloadReasons = emptyList())

    private fun same(id: String) = ex(id, ExerciseKind.WEIGHTED, n(3, 8, 50f), n(3, 8, 50f))
    private fun up(id: String, pr: Boolean = false) = ex(id, ExerciseKind.WEIGHTED, n(3, 10, 50f, pr = pr), n(3, 8, 50f))
    private fun down(id: String) = ex(id, ExerciseKind.WEIGHTED, n(3, 6, 40f), n(3, 8, 50f))
    private fun painSets(kg: Float?) = n(2, 8, kg) + s(8, kg, effort = "PAIN")

    // ---------------------------------------------------------------- Headlines

    @Test
    fun headlinesOneToFourWithVariants() {
        assertEquals(WorkoutDebriefMath.HeadlineLine(Headline.FIRST_EVER, 1), run(listOf(same("a")), firstEver = true, sessionId = 7).headline)
        assertEquals(WorkoutDebriefMath.HeadlineLine(Headline.AFTER_BREAK, 0), run(listOf(same("a")), afterBreak = true, sessionId = 9).headline)
        val warm = ex("jumping_jacks", ExerciseKind.REPS, n(2, 30, block = "Warm-up"), n(2, 30, block = "Warm-up"))
        assertEquals(WorkoutDebriefMath.HeadlineLine(Headline.ONLY_WARM_UPS, 1), run(listOf(warm), sessionId = 7).headline)
        assertEquals(WorkoutDebriefMath.HeadlineLine(Headline.ALL_NEW, 0), run(listOf(ex("a", ExerciseKind.WEIGHTED, n(3, 8, 50f))), sessionId = 4).headline)
    }

    @Test
    fun headlinesFiveAndSixLowRecoveryDays() {
        assertEquals(Headline.ROUGH_HELD, run(listOf(same("a"), up("b")), low = true).headline.type)
        assertEquals(Headline.ROUGH_SCALED, run(listOf(same("a"), down("b")), low = true).headline.type)
        // Pain alone is not a rough-day headline: its exercise is forgiven and the normal order runs.
        val painDown = ex("a", ExerciseKind.WEIGHTED, n(2, 6, 40f) + s(6, 40f, effort = "PAIN"), n(3, 8, 50f))
        assertEquals(Headline.ALL_SAME, run(listOf(painDown, same("b"))).headline.type)
        assertEquals(Headline.MOSTLY_UP, run(listOf(painDown, up("b"))).headline.type)
        // Pain forgives only its own exercise: another exercise's drop stays unforgiven.
        assertEquals(Headline.MOSTLY_DOWN, run(listOf(painDown, down("b"), down("c"))).headline.type)
        // The footnote is for low recovery (or health data on), not pain alone.
        assertFalse(run(listOf(painDown, same("b"))).result.footnote)
    }

    @Test
    fun headlineSevenShortUnlessAPr() {
        assertEquals(Headline.SHORT, run(listOf(same("a")), planned = 12).headline.type)
        assertEquals(Headline.PR_AND_UP, run(listOf(up("a", pr = true)), planned = 12).headline.type)
    }

    @Test
    fun headlinesEightToTwelve() {
        assertEquals(Headline.PR_AND_UP, run(listOf(up("a", pr = true), same("b"))).headline.type)
        assertEquals(Headline.MOSTLY_UP, run(listOf(up("a"), same("b"))).headline.type)
        assertEquals(Headline.MOSTLY_UP, run(listOf(up("a", pr = true), same("b"), same("c"))).headline.type)
        assertEquals(Headline.ALL_SAME, run(listOf(same("a"), same("b"))).headline.type)
        assertEquals(Headline.MIXED, run(listOf(down("a"), same("b"), same("c"))).headline.type)
        assertEquals(Headline.MOSTLY_DOWN, run(listOf(down("a"), down("b"), same("c"))).headline.type)
    }

    @Test
    fun someUpUnderHalfNothingDroppedCarriesItsNumbers() {
        assertEquals(
            WorkoutDebriefMath.HeadlineLine(Headline.SOME_UP, 1, up = 1, compared = 4),
            run(listOf(up("a"), same("b"), same("c"), same("d")), sessionId = 7).headline,
        )
        val three = run(listOf(up("a"), same("b"), same("c"))).headline
        assertEquals(Headline.SOME_UP, three.type)
        assertEquals(1, three.up)
        assertEquals(3, three.compared)
        // A forgiven (pain) drop does not stop it; an unforgiven one makes it MIXED.
        val painDown = ex("p", ExerciseKind.WEIGHTED, n(2, 6, 40f) + s(6, 40f, effort = "PAIN"), n(3, 8, 50f))
        assertEquals(Headline.SOME_UP, run(listOf(up("a"), same("b"), painDown)).headline.type)
        assertEquals(Headline.MIXED, run(listOf(up("a"), same("b"), down("c"))).headline.type)
    }

    // ---------------------------------------------------------------- Wins

    @Test
    fun prFirstWeightBeforeReps() {
        val pushUp = ex("push_up", ExerciseKind.REPS, n(3, 16, pr = true), n(3, 15))
        assertEquals(
            listOf(
                Win.Pr("bench", WorkoutScore.PrKind.WEIGHT, 50f, 10, null),
                Win.Pr("push_up", WorkoutScore.PrKind.REPS, null, 16, null),
            ),
            run(listOf(pushUp, up("bench", pr = true))).wins,
        )
    }

    @Test
    fun biggestGainThenMoreWorkOnAnotherExercise() {
        val more = ex("b", ExerciseKind.WEIGHTED, n(3, 10, 50f), listOf(s(10, 50f), s(10, 50f), s(9, 50f)))
        assertEquals(listOf(Win.BestGain("a", 5), Win.MoreWork("b", ExerciseKind.WEIGHTED, 29, 30)), run(listOf(more, up("a"))).wins)
    }

    @Test
    fun effortMasteryAllSetsWeeklyTarget() {
        val easier = ex("a", ExerciseKind.WEIGHTED, n(3, 8, 50f), n(2, 8, 50f) + s(8, 50f, effort = "HARD"))
        assertEquals(listOf(Win.EffortMastery("a"), Win.AllSets(3)), run(listOf(easier), planned = 3).wins)
        // The done count, not the planned one.
        assertEquals(listOf(Win.AllSets(6)), run(listOf(same("a"), same("b")), planned = 5).wins)
        assertEquals(listOf(Win.WeeklyTarget(3)), run(listOf(same("a")), weeklyTarget = 3, weekCount = 3, weekCountBefore = 2).wins)
        // Only the workout that reaches the target: a second workout that day doesn't.
        assertEquals(emptyList<Win>(), run(listOf(same("a")), weeklyTarget = 3, weekCount = 3, weekCountBefore = 3).wins)
    }

    @Test
    fun roughDayEveryAdjustedSetDoneWithTheDoneCount() {
        // 7 of 9, ceil(0.67 x 9) = 7.
        val seven = listOf(ex("a", ExerciseKind.WEIGHTED, n(4, 8, 50f), n(4, 8, 50f)), same("b"))
        assertEquals(listOf(Win.AllAdjustedSets(7)), run(seven, planned = 9, low = true).wins)
        assertEquals(
            listOf(Win.AllSets(9)),
            run(seven + ex("c", ExerciseKind.WEIGHTED, n(2, 8, 50f), n(2, 8, 50f)), planned = 9, low = true).wins,
        )
        assertEquals(emptyList<Win>(), run(listOf(same("a"), same("b")), planned = 9, low = true).wins)
    }

    @Test
    fun backAfterAGapOrStartingNumbers() {
        assertEquals(listOf(Win.BackAfterGap("a", 50)), run(listOf(ex("a", ExerciseKind.WEIGHTED, n(3, 8, 50f), n(3, 8, 50f), gap = 50))).wins)
        assertEquals(emptyList<Win>(), run(listOf(ex("a", ExerciseKind.WEIGHTED, n(3, 8, 50f), n(3, 8, 50f), gap = 41))).wins)
        assertEquals(
            listOf(Win.NewStarts(2)),
            run(listOf(ex("a", ExerciseKind.WEIGHTED, n(3, 8, 50f)), ex("b", ExerciseKind.HOLD, hold(3, 30)))).wins,
        )
    }

    // ---------------------------------------------------------------- Watch

    @Test
    fun painEasedRepeatBodyweightAndFromANote() {
        assertEquals(listOf(Watch.PainEased("a")), run(listOf(ex("a", ExerciseKind.WEIGHTED, painSets(50f), n(3, 8, 50f)))).watch)
        assertEquals(listOf(Watch.PainSwap("a")), run(listOf(ex("a", ExerciseKind.WEIGHTED, painSets(50f), painSets(50f)))).watch)
        assertEquals(listOf(Watch.PainSameNumbers("push_up")), run(listOf(ex("push_up", ExerciseKind.REPS, painSets(null), n(3, 8)))).watch)
        assertEquals(
            listOf(Watch.PainSameNumbers("plank")),
            run(listOf(ex("plank", ExerciseKind.HOLD, hold(2, 30) + hold(1, 30, effort = "PAIN"), hold(3, 30)))).watch,
        )
        val noted = ex("a", ExerciseKind.WEIGHTED, n(2, 8, 50f) + s(8, 50f, note = "left knee pain"), n(3, 8, 50f))
        assertEquals(listOf(Watch.PainEased("a")), run(listOf(noted)).watch)
    }

    @Test
    fun painAndDeloadTogetherDeloadNotOnAFirstWorkout() {
        assertEquals(
            listOf(Watch.PainEased("a"), Watch.Deload),
            run(listOf(ex("a", ExerciseKind.WEIGHTED, painSets(50f), n(3, 8, 50f))), report = report(needsDeload = true)).watch,
        )
        assertEquals(listOf(Watch.Deload), run(listOf(same("a")), report = report(needsDeload = true)).watch)
        assertEquals(emptyList<Watch>(), run(listOf(same("a")), report = report(needsDeload = true), firstEver = true).watch)
    }

    @Test
    fun plateauOnAnExerciseOfThisWorkoutNotAfterABreak() {
        val lifts = listOf(StalledLift("a", "A", 4, 21, 60f))
        assertEquals(listOf(Watch.Plateau("a", 4, 21)), run(listOf(same("a")), report = report(stalled = lifts)).watch)
        assertEquals(emptyList<Watch>(), run(listOf(same("b")), report = report(stalled = lifts)).watch)
        assertEquals(emptyList<Watch>(), run(listOf(same("a")), report = report(stalled = lifts), afterBreak = true).watch)
    }

    @Test
    fun unforgivenDropButNotWhenMostlyDown() {
        assertEquals(listOf(Watch.Drop("a")), run(listOf(down("a"), same("b"), same("c"))).watch)
        assertEquals(emptyList<Watch>(), run(listOf(down("a"), down("b"), same("c"))).watch)
        assertEquals(emptyList<Watch>(), run(listOf(down("a"), same("b")), low = true).watch)
    }

    @Test
    fun skippedExerciseNotAfterASwapOrOnARoughDay() {
        assertEquals(listOf(Watch.Skipped("c", 1)), run(listOf(same("a"), same("b")), plannedIds = listOf("a", "b", "c")).watch)
        assertEquals(emptyList<Watch>(), run(listOf(same("a"), same("d")), plannedIds = listOf("a", "b", "c")).watch)
        assertEquals(emptyList<Watch>(), run(listOf(same("a"), same("b")), plannedIds = listOf("a", "b", "c"), low = true).watch)
        // Headline 7 (short).
        assertEquals(emptyList<Watch>(), run(listOf(same("a")), plannedIds = listOf("a", "b", "c"), planned = 9).watch)
    }

    // ---------------------------------------------------------------- Next time

    @Test
    fun everyLineTypeInOrderAtMostThree() {
        val swap = ex("swap", ExerciseKind.WEIGHTED, painSets(50f), listOf(s(8, 50f, effort = "PAIN")))
        val lighter = ex("lighter", ExerciseKind.WEIGHTED, painSets(50f), n(3, 8, 50f))
        val harder = ex("push_up", ExerciseKind.REPS, n(3, 15), n(3, 15), harder = "Diamond push-up")
        val loadUp = ex("squat", ExerciseKind.WEIGHTED, n(3, 12, 60f), n(3, 11, 60f))
        assertEquals(
            listOf(Next.Swap("swap"), Next.Lighter("lighter", 47.5f, 8), Next.Harder("push_up", "Diamond push-up")),
            run(listOf(loadUp, harder, lighter, swap)).next,
        )
        assertEquals(listOf(Next.LoadUp("squat", 62.5f, 8)), run(listOf(loadUp)).next)
        assertEquals(listOf(Next.PlusRep("row", 11, 50f)), run(listOf(ex("row", ExerciseKind.WEIGHTED, n(3, 10, 50f), n(3, 10, 50f)))).next)
        assertEquals(listOf(Next.PlusSeconds("plank", 35)), run(listOf(ex("plank", ExerciseKind.HOLD, hold(3, 30), hold(3, 30), item = holdItem(30)))).next)
        // A hold at 1.5x its target: a harder version, or a slower tempo with none.
        assertEquals(listOf(Next.Harder("plank", null)), run(listOf(ex("plank", ExerciseKind.HOLD, hold(3, 45), hold(3, 45), item = holdItem(30)))).next)
        val hard = ex("bench", ExerciseKind.WEIGHTED, n(2, 12, 60f) + s(12, 60f, effort = "HARD"), n(3, 11, 60f))
        assertEquals(listOf(Next.HoldSteady("bench", 60f, 12, null)), run(listOf(hard)).next)
    }

    @Test
    fun sameRestFootnoteAndNewestFalseHidesNextTime() {
        val kept = ex("curl", ExerciseKind.WEIGHTED, listOf(s(10, 12.5f, effort = "HARD")) + n(2, 10, 12.5f), n(3, 10, 12.5f))
        val r = run(listOf(ex("row", ExerciseKind.WEIGHTED, n(3, 10, 50f), n(3, 10, 50f)), kept))
        assertEquals(listOf(Next.PlusRep("row", 11, 50f)), r.next)
        assertTrue(r.result.sameRest)
        assertFalse(r.result.footnote)
        assertTrue(run(listOf(same("a")), healthOn = true).result.footnote)
        assertTrue(run(listOf(same("a")), low = true).result.footnote)
        val old = run(listOf(ex("row", ExerciseKind.WEIGHTED, n(3, 10, 50f), n(3, 10, 50f), newest = false)))
        assertEquals(emptyList<Next>(), old.next)
        assertFalse(old.result.sameRest)
    }

    @Test
    fun holdSteadyAtTheTopOfTheRangeAndEverythingElseKeepsItsNumbers() {
        // Bench 3x12 @16 (item 3 sets, 8-12), the last set Hard; the previous time 3x12 @16, all OK.
        val bench = ex("bench", ExerciseKind.WEIGHTED, n(2, 12, 16f) + s(12, 16f, effort = "HARD"), n(3, 12, 16f), item = range())
        val kept = ex("curl", ExerciseKind.WEIGHTED, listOf(s(10, 12.5f, effort = "HARD")) + n(2, 10, 12.5f), n(3, 10, 12.5f), item = range())
        val r = run(listOf(bench, kept))
        assertEquals(listOf(Next.HoldSteady("bench", 16f, 12, null)), r.next)
        assertTrue(r.result.sameRest)
    }

    @Test
    fun threeChangesNeverPushTheHoldSteadyLineOut() {
        // The QA seed day: squat +1 rep, row +1 rep, plank +5 s, bench held by a Hard set.
        val bench = ex("bench", ExerciseKind.WEIGHTED, n(2, 12, 16f) + s(12, 16f, effort = "HARD"), n(3, 12, 16f), item = range())
        val r = run(
            listOf(
                ex("squat", ExerciseKind.WEIGHTED, n(3, 9, 15f), n(3, 8, 15f), item = range()),
                bench,
                ex("row", ExerciseKind.WEIGHTED, n(3, 10, 20f), n(3, 10, 20f), item = range()),
                ex("plank", ExerciseKind.HOLD, hold(3, 35), hold(3, 35)),
            ),
        )
        assertEquals(
            listOf(Next.PlusRep("squat", 10, 15f), Next.PlusRep("row", 11, 20f), Next.PlusSeconds("plank", 40), Next.HoldSteady("bench", 16f, 12, null)),
            r.next,
        )
        assertFalse(r.result.sameRest)
    }

    @Test
    fun aPoundUserStepsUpFivePounds() {
        val kg135 = WeightUnit.LB.toKg(135f)
        val r = run(listOf(ex("squat", ExerciseKind.WEIGHTED, n(3, 12, kg135), n(3, 11, kg135))), unit = WeightUnit.LB)
        val line = r.next.single() as Next.LoadUp
        assertEquals(WeightUnit.LB.toKg(140f), line.weightKg, 0.001f)
    }

    // ---------------------------------------------------------------- forSession

    @Test
    fun aWorkoutFromBeforeV9StillGetsItsDebrief() {
        val sessions = listOf(
            SessionEntity(id = 1, planDayId = null, title = "A", epochDay = 100, startedAt = 100_000, finishedAt = 100_001),
            SessionEntity(id = 2, planDayId = null, title = "A", epochDay = 103, startedAt = 103_000, finishedAt = 103_001),
        )
        fun row(session: Long, index: Int, reps: Int) = s(reps, 50f).copy(sessionId = session, exerciseId = "row", setIndex = index)
        val sets = listOf(row(1, 0, 8), row(1, 1, 8), row(1, 2, 8), row(2, 0, 10), row(2, 1, 10), row(2, 2, 10))
        val lib = mapOf("row" to ExerciseEntity("row", "Row", ExerciseKind.WEIGHTED, listOf(Muscle.UPPER_BACK), emptyList()))
        val scores = WorkoutScore.forSessions(sessions, sets, mapOf("row" to ExerciseKind.WEIGHTED), emptyMap(), emptySet())
        val r = WorkoutDebriefMath.forSession(sessions[1], sessions, sets, lib, scores.getValue(2), emptyList(), 0, null, WeightUnit.KG, false, emptySet())
        assertEquals(Headline.MOSTLY_UP, r.headline.type)
        assertEquals(listOf(Win.BestGain("row", 5)), r.wins)
        assertEquals(listOf(Next.PlusRep("row", 11, 50f)), r.next)
        // The older workout is not the newest with the row, so it shows no next time.
        val older = WorkoutDebriefMath.forSession(sessions[0], sessions, sets, lib, scores.getValue(1), emptyList(), 0, null, WeightUnit.KG, false, emptySet())
        assertEquals(Headline.FIRST_EVER, older.headline.type)
        assertEquals(emptyList<Next>(), older.next)
    }

    // ---------------------------------------------------------------- Gemini's 8 cases
    // docs/plans/math-debrief-gemini-reply.md, section 4, expected from the app's real Progression rules.

    @Test
    fun gemini1RepRangeLoadGraduation() {
        val r = run(
            listOf(
                ex("squat", ExerciseKind.WEIGHTED, n(3, 8, 62.5f), n(3, 12, 60f), item = range()),
                ex("bench", ExerciseKind.WEIGHTED, n(3, 8, 42.5f), n(3, 12, 40f), item = range()),
            ),
            planned = 6,
        )
        assertEquals(9, r.score.score)
        assertEquals(Headline.MOSTLY_UP, r.headline.type)
        assertEquals(listOf(Win.AllSets(6)), r.wins)
        assertEquals(emptyList<Watch>(), r.watch)
        assertEquals(listOf(Next.PlusRep("squat", 9, 62.5f), Next.PlusRep("bench", 9, 42.5f)), r.next)
        assertFalse(r.result.sameRest)
    }

    @Test
    fun gemini2RoughDayForgivenDrop() {
        val r = run(
            listOf(
                ex("deadlift", ExerciseKind.WEIGHTED, n(2, 8, 75f), n(3, 8, 80f), item = range()),
                ex("row", ExerciseKind.WEIGHTED, n(2, 10, 45f), n(3, 10, 45f), item = range()),
                ex("plank", ExerciseKind.HOLD, hold(2, 45, effort = "EASY"), hold(3, 45), item = holdItem(45)),
            ),
            planned = 9,
            low = true,
        )
        assertEquals(7, r.score.score)
        assertEquals(Headline.ROUGH_SCALED, r.headline.type)
        assertEquals(emptyList<Win>(), r.wins)
        assertEquals(emptyList<Watch>(), r.watch)
        assertEquals(emptyList<Next>(), r.next)
        assertTrue(r.result.sameRest)
        assertTrue(r.result.footnote)
    }

    @Test
    fun gemini3EarlyStopForPain() {
        val r = run(
            listOf(
                ex("overhead_press", ExerciseKind.WEIGHTED, listOf(s(6, 35f, effort = "PAIN")), n(3, 10, 35f), item = range()),
                ex("lat_pulldown", ExerciseKind.WEIGHTED, listOf(s(11, 50f, pr = true)) + n(2, 11, 50f), n(3, 10, 50f), item = range()),
            ),
            planned = 6,
        )
        assertEquals(9, r.score.score)
        // Pain alone: the press is forgiven and the normal order runs.
        assertEquals(Headline.PR_AND_UP, r.headline.type)
        assertEquals(listOf(Win.Pr("lat_pulldown", WorkoutScore.PrKind.WEIGHT, 50f, 11, null)), r.wins)
        assertEquals(listOf(Watch.PainEased("overhead_press")), r.watch)
        assertEquals(listOf(Next.Lighter("overhead_press", 32.5f, 8), Next.PlusRep("lat_pulldown", 12, 50f)), r.next)
        // No footnote: pain alone is not low recovery.
        assertFalse(r.result.footnote)
    }

    @Test
    fun gemini4TopOfRangeWithAHardSet() {
        val r = run(
            listOf(
                ex("romanian_deadlift", ExerciseKind.WEIGHTED, n(2, 10, 70f) + s(10, 70f, effort = "HARD"), n(3, 9, 70f), item = range(3, 8, 10)),
                ex("curl", ExerciseKind.WEIGHTED, n(3, 10, 12.5f), n(3, 10, 12.5f, effort = "HARD"), item = range(3, 8, 10)),
            ),
            planned = 6,
        )
        assertEquals(9, r.score.score)
        assertEquals(Headline.MOSTLY_UP, r.headline.type)
        assertEquals(listOf(Win.BestGain("romanian_deadlift", 3), Win.EffortMastery("curl")), r.wins)
        assertEquals(emptyList<Watch>(), r.watch)
        assertEquals(listOf(Next.LoadUp("curl", 15f, 8), Next.HoldSteady("romanian_deadlift", 70f, 10, null)), r.next)
    }

    @Test
    fun gemini5AfterABreak() {
        val r = run(
            listOf(
                ex("squat", ExerciseKind.WEIGHTED, n(3, 8, 60f), n(3, 10, 70f), item = range(), gap = 21),
                ex("push_up", ExerciseKind.REPS, n(3, 10), n(3, 12), item = range(), gap = 21),
            ),
            planned = 6,
            afterBreak = true,
        )
        assertFalse(r.score.showNumber)
        assertEquals(Headline.AFTER_BREAK, r.headline.type)
        assertEquals(listOf(Win.AllSets(6)), r.wins)
        // A comeback is never told about a drop.
        assertEquals(emptyList<Watch>(), r.watch)
        assertEquals(listOf(Next.PlusRep("squat", 9, 60f), Next.PlusRep("push_up", 11, null)), r.next)
    }

    @Test
    fun gemini6AllNewExercises() {
        val r = run(
            listOf(
                ex("goblet_squat", ExerciseKind.WEIGHTED, n(3, 10, 20f), item = range()),
                ex("side_plank", ExerciseKind.HOLD, hold(3, 30), item = holdItem(30)),
            ),
            planned = 6,
        )
        assertEquals(8, r.score.score)
        assertEquals(Headline.ALL_NEW, r.headline.type)
        assertEquals(listOf(Win.AllSets(6), Win.NewStarts(2)), r.wins)
        assertEquals(emptyList<Watch>(), r.watch)
        assertEquals(listOf(Next.PlusRep("goblet_squat", 11, 20f), Next.PlusSeconds("side_plank", 35)), r.next)
    }

    @Test
    fun gemini7OneRepWobbleVersusATrueDrop() {
        val r = run(
            listOf(
                ex("bench", ExerciseKind.WEIGHTED, n(3, 9, 60f), listOf(s(10, 60f), s(9, 60f), s(9, 60f)), item = range()),
                ex("row", ExerciseKind.WEIGHTED, n(3, 7, 50f), n(3, 10, 50f), item = range()),
            ),
            planned = 6,
        )
        assertEquals(7, r.score.score)
        assertEquals(Headline.MIXED, r.headline.type)
        assertEquals(listOf(Win.AllSets(6)), r.wins)
        assertEquals(listOf(Watch.Drop("row")), r.watch)
        // The row climbs back from the bottom of the range (8) plus one rep.
        assertEquals(listOf(Next.PlusRep("bench", 10, 60f), Next.PlusRep("row", 9, 50f)), r.next)
    }

    @Test
    fun gemini8ReadyForHarderAndASwapHidesTheSkip() {
        val r = run(
            listOf(
                ex("push_up", ExerciseKind.REPS, n(3, 15, effort = "EASY"), n(3, 15), item = range(3, 8, 12), harder = "Diamond push-up"),
                ex("db_lunge", ExerciseKind.WEIGHTED, n(3, 10, 15f), n(3, 10, 15f)),
            ),
            planned = 6,
            plannedIds = listOf("push_up", "leg_extension"),
        )
        assertEquals(8, r.score.score)
        assertEquals(Headline.ALL_SAME, r.headline.type)
        assertEquals(listOf(Win.EffortMastery("push_up"), Win.AllSets(6)), r.wins)
        assertEquals(emptyList<Watch>(), r.watch)
        assertEquals(listOf(Next.Harder("push_up", "Diamond push-up"), Next.PlusRep("db_lunge", 11, 15f)), r.next)
    }
}
