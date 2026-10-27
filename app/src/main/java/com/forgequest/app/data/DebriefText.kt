package com.forgequest.app.data

import android.content.res.Resources
import com.forgequest.app.R
import com.forgequest.app.domain.ExerciseKind
import com.forgequest.app.domain.WorkoutDebriefMath
import com.forgequest.app.domain.WorkoutDebriefMath.Headline
import com.forgequest.app.domain.WorkoutDebriefMath.Next
import com.forgequest.app.domain.WorkoutDebriefMath.Watch
import com.forgequest.app.domain.WorkoutDebriefMath.Win
import com.forgequest.app.domain.WorkoutScore

/**
 * The words of a calculated debrief ([WorkoutDebriefMath.Result]): one string per line type, the headline's wording
 * picked by the result's variant. The card passes the screen's resources; the coach message passes English ones.
 * Web: data/debrief-text.ts.
 */
object DebriefText {

    class Lines(
        val headline: String,
        val wins: List<String>,
        val watch: List<String>,
        /** Each already starts with "Next time: ". */
        val next: List<String>,
        /** "Everything else: same numbers." or null. */
        val sameRest: String?,
        val footnote: String?,
    )

    private val headlines: Map<Headline, IntArray> = mapOf(
        Headline.FIRST_EVER to intArrayOf(R.string.debrief_h1_1, R.string.debrief_h1_2, R.string.debrief_h1_3),
        Headline.AFTER_BREAK to intArrayOf(R.string.debrief_h2_1, R.string.debrief_h2_2, R.string.debrief_h2_3),
        Headline.ONLY_WARM_UPS to intArrayOf(R.string.debrief_h3_1, R.string.debrief_h3_2),
        Headline.ALL_NEW to intArrayOf(R.string.debrief_h4_1, R.string.debrief_h4_2),
        Headline.ROUGH_HELD to intArrayOf(R.string.debrief_h5_1, R.string.debrief_h5_2, R.string.debrief_h5_3),
        Headline.ROUGH_SCALED to intArrayOf(R.string.debrief_h6_1, R.string.debrief_h6_2, R.string.debrief_h6_3),
        Headline.SHORT to intArrayOf(R.string.debrief_h7_1, R.string.debrief_h7_2, R.string.debrief_h7_3),
        Headline.PR_AND_UP to intArrayOf(R.string.debrief_h8_1, R.string.debrief_h8_2, R.string.debrief_h8_3),
        Headline.MOSTLY_UP to intArrayOf(R.string.debrief_h9_1, R.string.debrief_h9_2, R.string.debrief_h9_3),
        Headline.ALL_SAME to intArrayOf(R.string.debrief_h10_1, R.string.debrief_h10_2, R.string.debrief_h10_3),
        Headline.MIXED to intArrayOf(R.string.debrief_h11_1, R.string.debrief_h11_2, R.string.debrief_h11_3),
        Headline.MOSTLY_DOWN to intArrayOf(R.string.debrief_h12_1, R.string.debrief_h12_2, R.string.debrief_h12_3),
    )

    /**
     * [name] gives an exercise's name from its id; [weight] writes a weight in kg the way the person reads it
     * (their unit, "62.5 kg" or "137.5 lb").
     */
    fun render(d: WorkoutDebriefMath.Result, res: Resources, name: (String) -> String, weight: (Float) -> String): Lines {
        fun count(plural: Int, n: Int) = res.getQuantityString(plural, n, n)
        fun reps(n: Int) = count(R.plurals.debrief_n_reps, n)
        fun seconds(n: Int) = count(R.plurals.debrief_n_seconds, n)
        val ids = headlines.getValue(d.headline.type)
        return Lines(
            headline = res.getString(ids[d.headline.variant.coerceIn(0, ids.lastIndex)]),
            wins = d.wins.mapNotNull { w -> win(w, res, name, weight, ::reps, ::seconds) },
            watch = d.watch.map { w -> watch(w, res, name) },
            next = d.next.mapNotNull { n -> next(n, res, name, weight, ::reps, ::seconds) }.map { res.getString(R.string.debrief_next_time, it) },
            sameRest = if (d.sameRest) res.getString(R.string.debrief_same_rest) else null,
            footnote = if (d.footnote) res.getString(R.string.debrief_footnote) else null,
        )
    }

    private fun win(w: Win, res: Resources, name: (String) -> String, weight: (Float) -> String, reps: (Int) -> String, seconds: (Int) -> String): String? = when (w) {
        is Win.Pr -> {
            val nm = name(w.exerciseId)
            when {
                w.kind == WorkoutScore.PrKind.HOLD && w.seconds != null -> res.getString(R.string.debrief_win_pr_hold, nm, seconds(w.seconds))
                w.kind == WorkoutScore.PrKind.WEIGHT && w.weightKg != null && w.reps != null -> res.getString(R.string.debrief_win_pr_weight, nm, weight(w.weightKg), reps(w.reps))
                w.reps != null && w.weightKg != null -> res.getString(R.string.debrief_win_pr_reps_weight, nm, reps(w.reps), weight(w.weightKg))
                w.reps != null -> res.getString(R.string.debrief_win_pr_reps, nm, reps(w.reps))
                w.seconds != null -> res.getString(R.string.debrief_win_pr_hold, nm, seconds(w.seconds))
                else -> null
            }
        }
        is Win.BestGain -> res.getString(R.string.debrief_win_best_gain, name(w.exerciseId), w.percent)
        is Win.MoreWork ->
            if (w.kind == ExerciseKind.HOLD) res.getString(R.string.debrief_win_more_secs, name(w.exerciseId), w.from, seconds(w.to))
            else res.getString(R.string.debrief_win_more_reps, name(w.exerciseId), w.from, reps(w.to))
        is Win.EffortMastery -> res.getString(R.string.debrief_win_effort, name(w.exerciseId))
        is Win.AllSets -> res.getString(R.string.debrief_win_all_sets, res.getQuantityString(R.plurals.debrief_n_sets, w.sets, w.sets))
        is Win.WeeklyTarget -> res.getString(R.string.debrief_win_weekly, res.getQuantityString(R.plurals.debrief_n_workouts, w.target, w.target))
        is Win.BackAfterGap -> (w.days / 7).let { weeks -> res.getString(R.string.debrief_win_back, name(w.exerciseId), res.getQuantityString(R.plurals.debrief_n_weeks, weeks, weeks)) }
        is Win.NewStarts -> res.getString(R.string.debrief_win_new_starts, res.getQuantityString(R.plurals.debrief_n_new_exercises, w.count, w.count))
    }

    private fun watch(w: Watch, res: Resources, name: (String) -> String): String = when (w) {
        is Watch.PainSwap -> res.getString(R.string.debrief_watch_pain_swap, name(w.exerciseId))
        is Watch.PainEased -> res.getString(R.string.debrief_watch_pain_eased, name(w.exerciseId))
        is Watch.PainSameNumbers -> res.getString(R.string.debrief_watch_pain_same, name(w.exerciseId))
        Watch.Deload -> res.getString(R.string.debrief_watch_deload)
        is Watch.Plateau -> {
            val weeks = ((w.days + 3) / 7).coerceAtLeast(1)
            res.getString(
                R.string.debrief_watch_plateau, name(w.exerciseId),
                res.getQuantityString(R.plurals.debrief_n_workouts, w.workouts, w.workouts),
                res.getQuantityString(R.plurals.debrief_n_weeks, weeks, weeks),
            )
        }
        is Watch.Drop -> res.getString(R.string.debrief_watch_drop, name(w.exerciseId))
        is Watch.Skipped ->
            if (w.count <= 1) res.getString(R.string.debrief_watch_skipped_one, name(w.exerciseId))
            else res.getString(R.string.debrief_watch_skipped_more, name(w.exerciseId), res.getQuantityString(R.plurals.debrief_n_more_exercises, w.count - 1, w.count - 1))
    }

    private fun next(n: Next, res: Resources, name: (String) -> String, weight: (Float) -> String, reps: (Int) -> String, seconds: (Int) -> String): String? {
        val nm = name(n.exerciseId)
        return when (n) {
            is Next.Swap -> res.getString(R.string.debrief_next_swap, nm)
            is Next.Lighter ->
                if (n.reps != null) res.getString(R.string.debrief_next_lighter, nm, weight(n.weightKg), reps(n.reps))
                else res.getString(R.string.debrief_next_lighter_only, nm, weight(n.weightKg))
            is Next.Harder ->
                if (n.harderName != null) res.getString(R.string.debrief_next_harder, nm, n.harderName)
                else res.getString(R.string.debrief_next_tempo, nm)
            is Next.LoadUp ->
                if (n.reps != null) res.getString(R.string.debrief_next_load_reps, nm, weight(n.weightKg), reps(n.reps))
                else res.getString(R.string.debrief_next_goal, nm, weight(n.weightKg))
            is Next.PlusRep ->
                if (n.weightKg != null) res.getString(R.string.debrief_next_goal_at, nm, reps(n.reps), weight(n.weightKg))
                else res.getString(R.string.debrief_next_goal, nm, reps(n.reps))
            is Next.PlusSeconds -> res.getString(R.string.debrief_next_goal, nm, seconds(n.seconds))
            is Next.HoldSteady -> when {
                n.weightKg != null && n.reps != null -> res.getString(R.string.debrief_next_steady_weight, nm, weight(n.weightKg), reps(n.reps))
                n.reps != null -> res.getString(R.string.debrief_next_steady, nm, reps(n.reps))
                n.seconds != null -> res.getString(R.string.debrief_next_steady, nm, seconds(n.seconds))
                n.weightKg != null -> res.getString(R.string.debrief_next_steady, nm, weight(n.weightKg))
                else -> null
            }
        }
    }
}
