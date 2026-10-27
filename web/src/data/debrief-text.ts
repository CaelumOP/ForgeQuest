// The words of a calculated debrief (domain/debrief.ts): one string per line type, the headline's wording picked by the
// result's variant. Port of app/.../data/DebriefText.kt; the strings are shared through the Android export.
import { pluralIn, tIn, type Lang } from '../i18n'
import { HEADLINE_VARIANTS, type DebriefResult, type Next, type Watch, type Win } from '../domain/debrief'

export type DebriefLines = {
  headline: string
  wins: string[]
  watch: string[]
  /** Each already starts with "Next time: ". */
  next: string[]
  /** "Everything else: same numbers." or null. */
  sameRest: string | null
  footnote: string | null
}

/**
 * `name` gives an exercise's name from its id; `weight` writes a weight in kg the way the person reads it (their unit).
 * `lang` is the app language for the card and English for the message to the coach.
 */
export function debriefLines(d: DebriefResult, lang: Lang, name: (id: string) => string, weight: (kg: number) => string): DebriefLines {
  const t = (key: string, ...args: unknown[]) => tIn(lang, key, ...args)
  const n = (key: string, count: number) => pluralIn(lang, key, count, count)
  const reps = (c: number) => n('debrief_n_reps', c)
  const seconds = (c: number) => n('debrief_n_seconds', c)

  const win = (w: Win): string | null => {
    switch (w.type) {
      case 'PR': {
        const nm = name(w.exerciseId)
        if (w.kind === 'HOLD' && w.seconds != null) return t('debrief_win_pr_hold', nm, seconds(w.seconds))
        if (w.kind === 'WEIGHT' && w.weightKg != null && w.reps != null) return t('debrief_win_pr_weight', nm, weight(w.weightKg), reps(w.reps))
        if (w.reps != null && w.weightKg != null) return t('debrief_win_pr_reps_weight', nm, reps(w.reps), weight(w.weightKg))
        if (w.reps != null) return t('debrief_win_pr_reps', nm, reps(w.reps))
        if (w.seconds != null) return t('debrief_win_pr_hold', nm, seconds(w.seconds))
        return null
      }
      case 'BEST_GAIN': return t('debrief_win_best_gain', name(w.exerciseId), w.percent)
      case 'MORE_WORK':
        return w.kind === 'HOLD'
          ? t('debrief_win_more_secs', name(w.exerciseId), w.from, seconds(w.to))
          : t('debrief_win_more_reps', name(w.exerciseId), w.from, reps(w.to))
      case 'EFFORT_MASTERY': return t('debrief_win_effort', name(w.exerciseId))
      case 'ALL_SETS': return t('debrief_win_all_sets', n('debrief_n_sets', w.sets))
      case 'WEEKLY_TARGET': return t('debrief_win_weekly', n('debrief_n_workouts', w.target))
      case 'BACK_AFTER_GAP': return t('debrief_win_back', name(w.exerciseId), n('debrief_n_weeks', Math.trunc(w.days / 7)))
      case 'NEW_STARTS': return t('debrief_win_new_starts', n('debrief_n_new_exercises', w.count))
    }
  }

  const watch = (w: Watch): string => {
    switch (w.type) {
      case 'PAIN_SWAP': return t('debrief_watch_pain_swap', name(w.exerciseId))
      case 'PAIN_EASED': return t('debrief_watch_pain_eased', name(w.exerciseId))
      case 'PAIN_SAME_NUMBERS': return t('debrief_watch_pain_same', name(w.exerciseId))
      case 'DELOAD': return t('debrief_watch_deload')
      case 'PLATEAU':
        return t('debrief_watch_plateau', name(w.exerciseId), n('debrief_n_workouts', w.workouts), n('debrief_n_weeks', Math.max(1, Math.trunc((w.days + 3) / 7))))
      case 'DROP': return t('debrief_watch_drop', name(w.exerciseId))
      case 'SKIPPED':
        return w.count <= 1
          ? t('debrief_watch_skipped_one', name(w.exerciseId))
          : t('debrief_watch_skipped_more', name(w.exerciseId), n('debrief_n_more_exercises', w.count - 1))
    }
  }

  const next = (x: Next): string | null => {
    const nm = name(x.exerciseId)
    switch (x.type) {
      case 'SWAP': return t('debrief_next_swap', nm)
      case 'LIGHTER':
        return x.reps != null ? t('debrief_next_lighter', nm, weight(x.weightKg), reps(x.reps)) : t('debrief_next_lighter_only', nm, weight(x.weightKg))
      case 'HARDER':
        return x.harderName != null ? t('debrief_next_harder', nm, x.harderName) : t('debrief_next_tempo', nm)
      case 'LOAD_UP':
        return x.reps != null ? t('debrief_next_load_reps', nm, weight(x.weightKg), reps(x.reps)) : t('debrief_next_goal', nm, weight(x.weightKg))
      case 'PLUS_REP':
        return x.weightKg != null ? t('debrief_next_goal_at', nm, reps(x.reps), weight(x.weightKg)) : t('debrief_next_goal', nm, reps(x.reps))
      case 'PLUS_SECONDS': return t('debrief_next_goal', nm, seconds(x.seconds))
      case 'HOLD_STEADY':
        if (x.weightKg != null && x.reps != null) return t('debrief_next_steady_weight', nm, weight(x.weightKg), reps(x.reps))
        if (x.reps != null) return t('debrief_next_steady', nm, reps(x.reps))
        if (x.seconds != null) return t('debrief_next_steady', nm, seconds(x.seconds))
        if (x.weightKg != null) return t('debrief_next_steady', nm, weight(x.weightKg))
        return null
    }
  }

  const order = Object.keys(HEADLINE_VARIANTS).indexOf(d.headline.type) + 1
  const variants = HEADLINE_VARIANTS[d.headline.type]
  const variant = Math.min(Math.max(d.headline.variant, 0), variants - 1)
  return {
    headline: t(`debrief_h${order}_${variant + 1}`),
    wins: d.wins.map(win).filter((x): x is string => x != null),
    watch: d.watch.map(watch),
    next: d.next.map(next).filter((x): x is string => x != null).map((x) => t('debrief_next_time', x)),
    sameRest: d.sameRest ? t('debrief_same_rest') : null,
    footnote: d.footnote ? t('debrief_footnote') : null,
  }
}
