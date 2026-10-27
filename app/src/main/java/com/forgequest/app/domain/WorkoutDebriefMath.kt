package com.forgequest.app.domain

import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.db.PlanItemEntity
import com.forgequest.app.data.db.SessionEntity
import com.forgequest.app.data.db.SetEntity
import kotlin.math.abs

/**
 * The workout debrief, worked out from the person's own numbers (docs/plans/math-debrief-plan.md, section C). Pure: it
 * returns typed lines with their numbers and the UI turns them into English or Arabic. It reuses [WorkoutScore] for
 * trends and records, [Progression.next] for next time, [Trends.analyzeFatigueAndPlateaus] for plateau and deload and
 * [ExerciseTiers] for a harder version. Web: domain/debrief.ts.
 */
object WorkoutDebriefMath {

    /**
     * In priority order (the first that matches wins); [variants] wordings, picked by session id. [SOME_UP] is checked
     * before [MIXED] but listed last, so the string keys (debrief_h<position>_<n>) keep their numbers.
     */
    enum class Headline(val variants: Int) {
        FIRST_EVER(3), AFTER_BREAK(3), ONLY_WARM_UPS(2), ALL_NEW(2), ROUGH_HELD(3), ROUGH_SCALED(3), SHORT(3),
        PR_AND_UP(3), MOSTLY_UP(3), ALL_SAME(3), MIXED(3), MOSTLY_DOWN(3), SOME_UP(3),
    }

    /** [Headline.SOME_UP] carries its numbers: up on [up] of [compared] exercises (0 for the others). */
    data class HeadlineLine(val type: Headline, val variant: Int, val up: Int = 0, val compared: Int = 0)

    /** Weights are in kg; totals are reps, or seconds for holds. */
    sealed interface Win {
        val exerciseId: String? get() = null

        /** The record set: weight and reps, reps (with any added weight), or seconds. */
        data class Pr(override val exerciseId: String, val kind: WorkoutScore.PrKind, val weightKg: Float?, val reps: Int?, val seconds: Int?) : Win
        /** The best set improved by [percent] (by [Records.score]). */
        data class BestGain(override val exerciseId: String, val percent: Int) : Win
        data class MoreWork(override val exerciseId: String, val kind: ExerciseKind, val from: Int, val to: Int) : Win
        /** Same numbers with fewer Hard or more Easy sets than last time. */
        data class EffortMastery(override val exerciseId: String) : Win
        /** Every planned set done; [sets] is the done count. */
        data class AllSets(val sets: Int) : Win
        /** A rough day: fewer than planned, but at least the adjusted (effective) count; [sets] is the done count. */
        data class AllAdjustedSets(val sets: Int) : Win
        data class WeeklyTarget(val target: Int) : Win
        data class BackAfterGap(override val exerciseId: String, val days: Int) : Win
        /** Starting numbers set for [count] new exercises. */
        data class NewStarts(val count: Int) : Win
    }

    sealed interface Watch {
        /** Pain this time and last time: swap it for a pain-free option. */
        data class PainSwap(val exerciseId: String) : Watch
        /** Next time is eased (one load step lighter); if it hurts again, swap it. */
        data class PainEased(val exerciseId: String) : Watch
        /** Bodyweight or hold: next time keeps the same numbers; if it hurts again, swap it. */
        data class PainSameNumbers(val exerciseId: String) : Watch
        data object Deload : Watch
        data class Plateau(val exerciseId: String, val workouts: Int, val days: Int) : Watch
        data class Drop(val exerciseId: String) : Watch
        /** [count] planned exercises had no done sets; [exerciseId] is the first. */
        data class Skipped(val exerciseId: String, val count: Int) : Watch
    }

    /** Next time's numbers from [Progression.next]; weights in kg. Listed in priority order. */
    sealed interface Next {
        val exerciseId: String

        data class Swap(override val exerciseId: String) : Next
        data class Lighter(override val exerciseId: String, val weightKg: Float, val reps: Int?) : Next
        /** [harderName] null: suggest a slower tempo. */
        data class Harder(override val exerciseId: String, val harderName: String?) : Next
        data class LoadUp(override val exerciseId: String, val weightKg: Float, val reps: Int?) : Next
        data class PlusRep(override val exerciseId: String, val reps: Int, val weightKg: Float?) : Next
        data class PlusSeconds(override val exerciseId: String, val seconds: Int) : Next
        /** The top of the range was hit, but a Hard set keeps the numbers. */
        data class HoldSteady(override val exerciseId: String, val weightKg: Float?, val reps: Int?, val seconds: Int?) : Next
    }

    data class Result(
        val headline: HeadlineLine,
        val wins: List<Win>,
        val watch: List<Watch>,
        val next: List<Next>,
        /** "Everything else: same numbers": some exercise shown for next time keeps its numbers. */
        val sameRest: Boolean,
        /** "The app eases off by itself on a low-recovery day." */
        val footnote: Boolean,
    )

    /** One exercise of the workout. */
    class Exercise(
        val exerciseId: String,
        val kind: ExerciseKind,
        /** This workout's done sets of it, warm-ups included (they are left out here). */
        val done: List<SetEntity>,
        /** Its previous time's done sets (the most recent earlier workout with it), empty when none. */
        val last: List<SetEntity>,
        /** Days since that previous time; null when none. */
        val gapDays: Int?,
        /** No later workout contains it, so next time applies. */
        val newest: Boolean,
        /** Its item on the plan day, for the target; null without one. */
        val item: PlanItemEntity?,
        /** [ExerciseTiers.harderVersion]'s name; null when there is none. */
        val harderName: String?,
    )

    class Input(
        val sessionId: Long,
        val score: WorkoutScore.Result,
        val exercises: List<Exercise>,
        /** The plan day's exercises, warm-ups left out; empty without a plan day. */
        val plannedExerciseIds: Set<String>,
        /** Workouts per week from the days/week setting; 0 means none. */
        val weeklyTarget: Int,
        /** Workout days this week, with this workout and before it. */
        val weekCount: Int,
        val weekCountBefore: Int,
        val report: FatigueAndPlateauReport?,
        val unit: WeightUnit,
        val healthOn: Boolean,
    )

    /** An exercise back after this many days or more is a win. */
    const val BACK_AFTER_DAYS = 42

    /** What the rules need per exercise, worked out once. */
    private class Ex(
        val e: Exercise,
        val working: List<SetEntity>,
        val lastWorking: List<SetEntity>,
        val trend: WorkoutScore.Trend?,
        val forgiven: Boolean,
        val pr: WorkoutScore.PrKind?,
        val pain: Boolean,
        val painLast: Boolean,
    ) {
        val id: String get() = e.exerciseId
        val now = WorkoutScore.stats(e.kind, id, working)
        val was = if (lastWorking.isEmpty()) null else WorkoutScore.stats(e.kind, id, lastWorking)
        val unforgivenDrop: Boolean get() = trend == WorkoutScore.Trend.DOWN && !forgiven
    }

    private fun effort(s: SetEntity) = SetEffort.of(s.effort, s.note)

    fun compute(input: Input): Result {
        val score = input.score
        val exs = input.exercises.mapNotNull { e ->
            val working = e.done.filterNot { WorkoutScore.isWarmUp(it.block) }
            if (working.isEmpty()) return@mapNotNull null
            val lastWorking = e.last.filterNot { WorkoutScore.isWarmUp(it.block) }
            val fromScore = score.exercises.firstOrNull { it.exerciseId == e.exerciseId }
            val pain = working.any { effort(it) == SetEffort.PAIN }
            Ex(
                e = e,
                working = working,
                lastWorking = lastWorking,
                trend = fromScore?.trend ?: if (lastWorking.isEmpty()) null else WorkoutScore.trend(e.kind, e.exerciseId, working, lastWorking),
                // Pain read from notes too, the same way Progression reads it.
                forgiven = (fromScore?.forgiven ?: false) || pain,
                pr = fromScore?.pr ?: if (e.done.any { it.isPr }) WorkoutScore.prKindOf(e.kind) else null,
                pain = pain,
                painLast = lastWorking.any { effort(it) == SetEffort.PAIN },
            )
        }
        val rough = score.rough || exs.any { it.pain }
        val lowRecovery = score.lowRecovery
        val headline = headline(input, exs, lowRecovery)
        val (next, sameRest) = nextTime(input, exs)
        val variant = input.sessionId.mod(headline.variants.toLong()).toInt()
        val compared = exs.filter { it.trend != null }
        return Result(
            headline = if (headline == Headline.SOME_UP) {
                HeadlineLine(headline, variant, compared.count { it.trend == WorkoutScore.Trend.UP }, compared.size)
            } else {
                HeadlineLine(headline, variant)
            },
            wins = wins(input, exs, headline),
            watch = watch(input, exs, headline, rough),
            next = next,
            sameRest = sameRest,
            footnote = input.healthOn || lowRecovery,
        )
    }

    /** Headlines 5 and 6 only on a low-recovery day; pain alone forgives its own exercise and the normal order runs. */
    private fun headline(input: Input, exs: List<Ex>, lowRecovery: Boolean): Headline {
        val score = input.score
        val compared = exs.filter { it.trend != null }
        val up = compared.count { it.trend == WorkoutScore.Trend.UP }
        val downs = compared.filter { it.trend == WorkoutScore.Trend.DOWN }
        val unforgiven = downs.count { !it.forgiven }
        val effective = score.effectiveSets
        return when {
            score.firstEver -> Headline.FIRST_EVER
            score.afterBreak -> Headline.AFTER_BREAK
            exs.isEmpty() -> Headline.ONLY_WARM_UPS
            compared.isEmpty() -> Headline.ALL_NEW
            lowRecovery && downs.isEmpty() -> Headline.ROUGH_HELD
            lowRecovery && unforgiven == 0 -> Headline.ROUGH_SCALED
            effective != null && score.doneSets * 4 < effective * 3 && score.prs == 0 -> Headline.SHORT
            score.prs > 0 && up * 2 >= compared.size -> Headline.PR_AND_UP
            up * 2 >= compared.size || score.prs > 0 -> Headline.MOSTLY_UP
            up == 0 && unforgiven == 0 -> Headline.ALL_SAME
            // Up on some (under half, no PR) and nothing left unforgiven below last time.
            up > 0 && unforgiven == 0 -> Headline.SOME_UP
            unforgiven * 2 <= compared.size -> Headline.MIXED
            else -> Headline.MOSTLY_DOWN
        }
    }

    private fun wins(input: Input, exs: List<Ex>, headline: Headline): List<Win> {
        val score = input.score
        val candidates = sequence {
            // 1. Records: weight > reps > hold.
            exs.filter { it.pr != null }.sortedBy { it.pr!!.ordinal }.forEach { x ->
                val top = x.working.filter { it.isPr }.ifEmpty { x.working }
                    .maxBy { Records.score(x.e.kind, it.reps, it.seconds, it.weightKg, x.id) }
                yield(Win.Pr(x.id, x.pr!!, top.weightKg?.takeIf { it > 0f }, top.reps, top.seconds))
            }
            // 2. The biggest gain in the best set among UP exercises.
            exs.filter { it.trend == WorkoutScore.Trend.UP && it.was != null }
                .mapNotNull { x -> Trends.percentChange(x.was!!.best, x.now.best)?.takeIf { it > 0 }?.let { x to it } }
                .maxByOrNull { it.second }?.let { (x, pct) -> yield(Win.BestGain(x.id, pct)) }
            // 3. More total work at the same or a heavier weight.
            exs.filter { x ->
                val was = x.was
                x.trend == WorkoutScore.Trend.UP && was != null && x.now.total > was.total && x.now.top >= was.top - WorkoutScore.SAME_WEIGHT_KG
            }.forEach { x -> yield(Win.MoreWork(x.id, x.e.kind, x.was!!.total, x.now.total)) }
            // 4. Effort mastery: the same numbers, felt easier.
            exs.filter { x ->
                val was = x.was
                x.trend == WorkoutScore.Trend.SAME && was != null && !x.pain && abs(x.now.top - was.top) <= WorkoutScore.SAME_WEIGHT_KG &&
                    x.now.total >= was.total && (
                        x.working.count { effort(it) == SetEffort.HARD } < x.lastWorking.count { effort(it) == SetEffort.HARD } ||
                            x.working.count { effort(it) == SetEffort.EASY } > x.lastWorking.count { effort(it) == SetEffort.EASY }
                        )
            }.forEach { x -> yield(Win.EffortMastery(x.id)) }
            // 5. Every planned set done, or on a rough day every adjusted one (as the score counts them); the done count shows.
            val planned = score.plannedSets
            val effective = score.effectiveSets
            if (planned != null && score.doneSets >= planned) {
                yield(Win.AllSets(score.doneSets))
            } else if (effective != null && score.doneSets >= effective) {
                yield(Win.AllAdjustedSets(score.doneSets))
            }
            // 6. This workout is the one that reaches the weekly target.
            if (input.weeklyTarget > 0 && input.weekCount == input.weeklyTarget && input.weekCountBefore < input.weeklyTarget) {
                yield(Win.WeeklyTarget(input.weeklyTarget))
            }
            // 7. Back after a long gap, or starting numbers on an all-new workout.
            if (headline == Headline.ALL_NEW) {
                yield(Win.NewStarts(exs.size))
            } else {
                exs.forEach { x -> x.e.gapDays?.takeIf { it >= BACK_AFTER_DAYS }?.let { yield(Win.BackAfterGap(x.id, it)) } }
            }
        }
        val out = mutableListOf<Win>()
        val used = mutableSetOf<String>()
        for (w in candidates) {
            if (out.size == 2) break
            val id = w.exerciseId
            if (id != null && id in used) continue
            if (id != null) used += id
            out += w
        }
        return out
    }

    private fun watch(input: Input, exs: List<Ex>, headline: Headline, rough: Boolean): List<Watch> {
        val score = input.score
        val painful = exs.filter { it.pain }
        val pain = (painful.firstOrNull { it.painLast } ?: painful.firstOrNull())?.let { x ->
            when {
                x.painLast -> Watch.PainSwap(x.id)
                // Progression keeps a bodyweight move's or hold's numbers on pain.
                x.e.kind != ExerciseKind.WEIGHTED || x.now.top <= 0f -> Watch.PainSameNumbers(x.id)
                else -> Watch.PainEased(x.id)
            }
        }
        val deload = input.report?.needsDeload == true && !score.firstEver && !score.afterBreak
        if (pain != null) return if (deload) listOf(pain, Watch.Deload) else listOf(pain)
        if (deload) return listOf(Watch.Deload)
        if (!score.afterBreak) {
            exs.firstNotNullOfOrNull { x -> input.report?.stalledLifts?.firstOrNull { it.exerciseId == x.id } }
                ?.let { return listOf(Watch.Plateau(it.exerciseId, it.sessionsCount, it.spanDays)) }
        }
        // A comeback is never told about a drop.
        if (headline != Headline.MOSTLY_DOWN && !score.afterBreak) exs.firstOrNull { it.unforgivenDrop }?.let { return listOf(Watch.Drop(it.id)) }
        val doneIds = input.exercises.filter { it.done.isNotEmpty() }.map { it.exerciseId }.toSet()
        val skipped = input.plannedExerciseIds.filter { it !in doneIds }
        // An unplanned exercise logged is a swap, not a skip.
        val swapped = exs.any { it.id !in input.plannedExerciseIds }
        if (skipped.isNotEmpty() && !swapped && !rough && headline != Headline.SHORT && !score.firstEver && !score.afterBreak) {
            return listOf(Watch.Skipped(skipped.first(), skipped.size))
        }
        return emptyList()
    }

    /**
     * Up to 3 change lines, then one hold-steady line (it changes nothing, so a fourth change never pushes it out), and
     * whether some exercise shown keeps its numbers.
     */
    private fun nextTime(input: Input, exs: List<Ex>): Pair<List<Next>, Boolean> {
        val lines = mutableListOf<Pair<Int, Next>>()
        var unchanged = false
        for (x in exs) {
            if (!x.e.newest) continue
            val p = Progression.inputs(x.e.item, x.id, x.working + x.lastWorking)
            if (p.warmup) continue
            val r = Progression.next(x.e.kind, p.target, p.last, p.previous, input.unit, lowReadiness = false, warmup = false)
            val line = nextLine(x, p, r)
            if (line == null) unchanged = true else lines += line
        }
        val sorted = lines.sortedBy { it.first }.map { it.second }
        val changes = sorted.filterNot { it is Next.HoldSteady }.take(3)
        val steady = sorted.filter { it is Next.HoldSteady }.take(1)
        return (changes + steady) to unchanged
    }

    /** The one line for an exercise with its order, or null when its numbers stay the same. */
    private fun nextLine(x: Ex, p: Progression.Inputs, r: Progression.Result): Pair<Int, Next>? {
        val kind = x.e.kind
        if (r.sets.isEmpty()) return null
        val nowTop = p.last.maxOf { it.weightKg ?: 0f }
        val nextTop = r.sets.maxOf { it.weightKg ?: 0f }
        val firstReps = r.sets.firstOrNull()?.reps
        if (x.pain && x.painLast) return 1 to Next.Swap(x.id)
        if (kind == ExerciseKind.WEIGHTED && nextTop < nowTop - WorkoutScore.SAME_WEIGHT_KG) return 2 to Next.Lighter(x.id, nextTop, firstReps)
        val harder = (kind == ExerciseKind.HOLD && r.holdCeilingReached) ||
            (kind == ExerciseKind.REPS && x.id != "jumping_jacks" && ExerciseTiers.readyForHarder(p.last, p.previous))
        if (harder) return 3 to Next.Harder(x.id, x.e.harderName)
        if (nextTop > nowTop + WorkoutScore.SAME_WEIGHT_KG) return 4 to Next.LoadUp(x.id, nextTop, firstReps)
        val lastAt = { i: Int -> p.last.getOrNull(i) ?: p.last.last() }
        if (kind == ExerciseKind.HOLD) {
            if (r.sets.indices.any { i -> (r.sets[i].seconds ?: 0) > (lastAt(i).seconds ?: 0) }) {
                return 5 to Next.PlusSeconds(x.id, r.sets.maxOf { it.seconds ?: 0 })
            }
        } else if (r.sets.maxOf { it.reps ?: 0 } > p.last.maxOf { it.reps ?: 0 }) {
            // The top set gains reps (a failed set back to the bottom of the range alone doesn't count).
            return 5 to Next.PlusRep(x.id, r.sets.maxOf { it.reps ?: 0 }, nextTop.takeIf { it > 0f })
        }
        // The top of the range was hit, but a Hard set kept the numbers.
        val count = p.target?.sets ?: 3
        val hitTop = p.last.size >= count && when (kind) {
            ExerciseKind.HOLD -> p.last.all { (it.seconds ?: 0) >= (p.target?.seconds ?: 30) }
            else -> p.last.all { (it.reps ?: 0) >= (p.target?.repsMax ?: p.target?.repsMin ?: 12) }
        }
        if (!x.pain && hitTop && p.last.any { it.effort == SetEffort.HARD }) {
            val holds = kind == ExerciseKind.HOLD
            return 6 to Next.HoldSteady(x.id, nowTop.takeIf { it > 0f }, if (holds) null else p.last.maxOf { it.reps ?: 0 }, if (holds) p.last.maxOf { it.seconds ?: 0 } else null)
        }
        return null
    }

    /**
     * Gathers the inputs for one finished workout from data already loaded and returns its debrief. [sessions] and
     * [doneSets] are every finished workout and its done sets; [score] is this workout's [WorkoutScore] result;
     * [planItems] the items of its plan day as the plan is now (empty without one); [weeklyTarget] from the days/week
     * setting ([StreakCalculator.fullTarget]); [report] from [Trends.analyzeFatigueAndPlateaus] as of the workout's day.
     */
    fun forSession(
        session: SessionEntity,
        sessions: List<SessionEntity>,
        doneSets: List<SetEntity>,
        exercises: Map<String, ExerciseEntity>,
        score: WorkoutScore.Result,
        planItems: List<PlanItemEntity>,
        weeklyTarget: Int,
        report: FatigueAndPlateauReport?,
        unit: WeightUnit,
        healthOn: Boolean,
        equipment: Set<Equipment>,
    ): Result {
        val byId = sessions.associateBy { it.id }
        fun earlier(o: SessionEntity, s: SessionEntity) = o.epochDay < s.epochDay || (o.epochDay == s.epochDay && o.startedAt < s.startedAt)
        val mine = doneSets.filter { it.sessionId == session.id }.sortedWith(compareBy({ it.itemPosition }, { it.setIndex }))
        val library = exercises.values.toList()
        val list = mine.groupBy { it.exerciseId }.map { (id, done) ->
            val others = doneSets.filter { it.exerciseId == id && it.sessionId != session.id }
                .groupBy { it.sessionId }.mapNotNull { (sid, l) -> byId[sid]?.let { it to l } }
            val previous = others.filter { earlier(it.first, session) }.maxWithOrNull(compareBy({ it.first.epochDay }, { it.first.startedAt }))
            val ex = exercises[id]
            val kind = ex?.kind ?: ExerciseKind.REPS
            Exercise(
                exerciseId = id,
                kind = kind,
                done = done,
                last = previous?.second.orEmpty().sortedBy { it.setIndex },
                gapDays = previous?.let { (session.epochDay - it.first.epochDay).toInt() },
                newest = others.none { earlier(session, it.first) },
                item = planItems.firstOrNull { it.exerciseId == id },
                harderName = ex?.takeIf { kind == ExerciseKind.REPS }?.let { ExerciseTiers.harderVersion(it, library, equipment)?.name },
            )
        }
        val weekStart = Dates.weekStart(session.epochDay)
        val before = sessions.filter { earlier(it, session) && it.epochDay in weekStart..weekStart + 6 }.map { it.epochDay }.toSet()
        return compute(
            Input(
                sessionId = session.id,
                score = score,
                exercises = list,
                plannedExerciseIds = planItems.filterNot { WorkoutScore.isWarmUp(it.block) }.map { it.exerciseId }.toSet(),
                weeklyTarget = weeklyTarget,
                weekCount = (before + session.epochDay).size,
                weekCountBefore = before.size,
                report = report,
                unit = unit,
                healthOn = healthOn,
            ),
        )
    }
}
