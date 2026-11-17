import type { DebriefResult, Headline } from './debrief'
import { leastRecentVariant, parseInsightHistory, type Insight, type InsightKindId } from './insights'
import type { WorkoutScoreResult } from './workout-score'

export type DayType = 'PR' | 'MILESTONE' | 'FIRST_OR_BACK' | 'ROUGH' | 'ORDINARY'
export type ScoreTier = 'RECORD_DAY' | 'STEP_FORWARD' | 'HELD_STEADY' | 'LIGHT_DAY'

export const MILESTONE_KINDS = new Set<InsightKindId>([
  'CROSS_WORKOUTS', 'CROSS_SETS', 'CROSS_REPS', 'CROSS_HOURS', 'CROSS_KG',
  'ANNIVERSARY', 'FIRST_ROUND_REPS', 'FIRST_ROUND_HOLD',
])

export function dayType(score: WorkoutScoreResult, debrief: DebriefResult, insights: readonly Insight[]): DayType {
  if (score.firstEver || score.afterBreak || debrief.headline.type === 'FIRST_EVER' || debrief.headline.type === 'AFTER_BREAK') {
    return 'FIRST_OR_BACK'
  }
  if (score.prs > 0 || debrief.wins.some((w) => w.type === 'PR')) return 'PR'
  if (insights.some((i) => MILESTONE_KINDS.has(i.kind))) return 'MILESTONE'
  if (score.rough) return 'ROUGH'
  return 'ORDINARY'
}

export function scoreTier(score: WorkoutScoreResult): ScoreTier {
  if (score.prs > 0) return 'RECORD_DAY'
  if (score.effectiveSets != null && score.doneSets * 4 < score.effectiveSets * 3) return 'LIGHT_DAY'
  if (score.compared > 0 && score.up * 2 >= score.compared) return 'STEP_FORWARD'
  if (score.score <= 5) return 'LIGHT_DAY'
  return 'HELD_STEADY'
}

export const SCORE_TIER_KEY: Record<ScoreTier, string> = {
  RECORD_DAY: 'score_tier_record_day',
  STEP_FORWARD: 'score_tier_step_forward',
  HELD_STEADY: 'score_tier_held_steady',
  LIGHT_DAY: 'score_tier_light_day',
}

export const HEADLINE_VARIANTS = 8
const HEADLINE_TYPES = new Set<Headline>(['MOSTLY_UP', 'ALL_SAME', 'SOME_UP', 'MIXED', 'PR_AND_UP'])

export const headlineKey = (type: Headline): string => `H_${type}`

export function pickHeadlineVariant(type: Headline, history: string): number | null {
  if (!HEADLINE_TYPES.has(type)) return null
  return leastRecentVariant(headlineKey(type), HEADLINE_VARIANTS, parseInsightHistory(history))
}

const HISTORY_CAP = 60

export function appendHistoryEntries(raw: string, picked: readonly Insight[], day: number, cap = HISTORY_CAP): string {
  const next = [...parseInsightHistory(raw), ...picked.map((p) => ({ kind: p.kind, variant: p.variant, day }))]
  return next.slice(-cap).map((e) => `${e.kind}:${e.variant}:${e.day}`).join(';')
}

export function appendHistoryOne(raw: string, kind: string, variant: number, day: number, cap = HISTORY_CAP): string {
  const next = [...parseInsightHistory(raw), { kind, variant, day }]
  return next.slice(-cap).map((e) => `${e.kind}:${e.variant}:${e.day}`).join(';')
}
