import { plural, t, type Lang } from '../../i18n'
import type { BadgeDef } from '../../domain/badges'
import type { DebriefResult } from '../../domain/debrief'
import { INSIGHT_KINDS, type Insight, type InsightKindId, type InsightSlot } from '../../domain/insights'
import type { DayType, ScoreTier } from '../../domain/finish-summary'
import type { ExerciseKind } from '../../domain/muscle'
import type { LevelInfo } from '../../domain/progress'
import type { RecapInput } from '../../domain/workout-recap'
import { formatWeight, type WeightUnit } from '../../domain/units'
import type { WorkoutScoreResult } from '../../domain/workout-score'
import { exerciseCueKeys } from '../../data/seed/exercise-cues'
import { dayName, muscleLabel } from '../workouts/helpers'

export type PrHeroData = {
  exerciseId: string
  kind: ExerciseKind
  weightKg: number | null
  reps: number | null
  seconds: number | null
  oldKg: number | null
  oldReps: number | null
  oldSeconds: number | null
}

/** What the "Workout complete" screen shows (Android WorkoutSummary). */
export type WorkoutSummaryData = {
  sessionId: number
  title: string
  doneSets: number
  minutes: number
  prs: string[]
  newBadges: BadgeDef[]
  xpGained: number
  before: LevelInfo
  after: LevelInfo
  /** Next planned training day after today: day of week (1 = Monday) and title. */
  nextUp: { dow: number; title: string } | null
  /** The calculated workout score and its reasons (domain/workout-score.ts). */
  score?: WorkoutScoreResult | null
  /** Set when the end was pulled back to the last set: when the workout ended (epoch ms). */
  clippedEndAt?: number | null
  /** "Just log" mode: no XP, level or badges on the finish screen. */
  justLog?: boolean
  debrief?: DebriefResult | null
  exerciseNames?: ReadonlyMap<string, string>
  unit?: WeightUnit
  dayType?: DayType
  tier?: ScoreTier | null
  headlineVariant?: number | null
  insights?: Insight[]
  prHero?: PrHeroData | null
  recap?: RecapInput
}

type PlanDays = { day: { dayOfWeek: number; title: string; isRest: boolean }; items: unknown[] }[]

/** The next training day after today (Android: smallest (dow - today + 6) % 7, so tomorrow first and today last). */
export function nextUpDay(days: PlanDays, todayDow: number): { dow: number; title: string } | null {
  let best: { dow: number; title: string } | null = null
  let bestKey = Infinity
  for (const d of days) {
    if (d.day.isRest || d.items.length === 0) continue
    const key = (((d.day.dayOfWeek - todayDow + 6) % 7) + 7) % 7
    if (key < bestKey) { bestKey = key; best = { dow: d.day.dayOfWeek, title: d.day.title } }
  }
  return best
}

/** Whole minutes since the workout started, at least 1. */
export const workoutMinutes = (startedAt: number, now: number): number => Math.max(1, Math.floor((now - startedAt) / 60_000))

/** "62.5 kg × 5", "12 reps", or "45 seconds"; null when nothing to show. */
export function prValueText(
  unit: WeightUnit,
  arabic: boolean,
  kind: ExerciseKind,
  weightKg: number | null,
  reps: number | null,
  seconds: number | null,
): string | null {
  if (kind === 'HOLD') {
    if (seconds == null || seconds <= 0) return null
    return plural('debrief_n_seconds', seconds, seconds)
  }
  if (kind === 'WEIGHTED') {
    if (weightKg == null || weightKg <= 0 || reps == null || reps <= 0) return null
    return `${formatWeight(unit, weightKg, arabic)} × ${reps}`
  }
  if (reps == null || reps <= 0) return null
  const r = plural('debrief_n_reps', reps, reps)
  return weightKg != null && weightKg > 0 ? `${r} · +${formatWeight(unit, weightKg, arabic)}` : r
}

const INSIGHT_KEY_BASE: Record<InsightKindId, string> = {
  FIRST_ROUND_REPS: 'insight_first_round_reps',
  FIRST_ROUND_HOLD: 'insight_first_round_hold',
  CROSS_WORKOUTS: 'insight_cross_workouts',
  ANNIVERSARY: 'insight_anniversary',
  NEAR_RECORD_WEIGHT: 'insight_near_record_weight',
  NEAR_RECORD_REPS: 'insight_near_record_reps',
  BIGGEST_SESSION: 'insight_biggest_session',
  CROSS_HOURS: 'insight_cross_hours',
  CROSS_KG: 'insight_cross_kg',
  CROSS_SETS: 'insight_cross_sets',
  CROSS_REPS: 'insight_cross_reps',
  EXERCISE_TIMES: 'insight_exercise_times',
  BACK_AFTER_GAP: 'insight_back_after_gap',
  VS_FOUR_WEEKS: 'insight_vs_four_weeks',
  EASY_STREAK: 'insight_easy_streak',
  HARD_TWICE: 'insight_hard_twice',
  SAME_WEEKDAY: 'insight_same_weekday',
  MONTH_UP: 'insight_month_up',
  STREAK_BEST: 'insight_streak_best',
  WEEK_ONE_LEFT: 'insight_week_one_left',
  STREAK_NEXT: 'insight_streak_next',
  PACE_FASTER: 'insight_pace_faster',
  MUSCLE_NEGLECTED: 'insight_muscle_neglected',
  MUSCLE_WEEK: 'insight_muscle_week',
  MUSCLE_TOP: 'insight_muscle_top',
  AVG_REST: 'insight_avg_rest',
  FORM_TIP: 'insight_form_tip',
}

function formatSlot(slot: InsightSlot, lang: Lang, names: ReadonlyMap<string, string>, unit: WeightUnit): string | number {
  const arabic = lang === 'ar'
  switch (slot.type) {
    case 'count': return slot.n
    case 'kg': return formatWeight(unit, slot.kg, arabic)
    case 'seconds': return plural('debrief_n_seconds', slot.s, slot.s)
    case 'exercise': return names.get(slot.id) ?? slot.id
    case 'cue': {
      const cues = exerciseCueKeys(slot.id)
      return cues ? t(cues[Math.min(2, Math.max(0, slot.line))]) : ''
    }
    case 'group': return muscleLabel(slot.muscle)
    case 'weekday': return dayName(slot.day, lang)
  }
}

export function renderInsightText(ins: Insight, lang: Lang, names: ReadonlyMap<string, string>, unit: WeightUnit): string {
  const meta = INSIGHT_KINDS.find((k) => k.id === ins.kind)
  const v = Math.min(meta?.variants ?? 3, Math.max(0, ins.variant)) + 1
  const base = ins.kind === 'CROSS_KG' && unit === 'LB' ? 'insight_cross_lb' : INSIGHT_KEY_BASE[ins.kind]
  const key = `${base}_${v}`
  const args = ins.slots.map((s) => formatSlot(s, lang, names, unit))
  if (ins.plural) {
    const firstCount = ins.slots.find((s): s is Extract<InsightSlot, { type: 'count' }> => s.type === 'count')?.n ?? 0
    return plural(key, firstCount, ...args)
  }
  return t(key, ...args)
}
