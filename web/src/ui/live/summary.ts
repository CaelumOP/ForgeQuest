import type { BadgeDef } from '../../domain/badges'
import type { LevelInfo } from '../../domain/progress'
import type { WorkoutScoreResult } from '../../domain/workout-score'

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
