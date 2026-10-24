// Everything the Progress screen shows, computed from sessions, sets, weights and meals. Same as Android Trends.kt.
import { weekStart } from './dates'
import type { ExerciseEntity, ReadinessDayEntity, SessionEntity, SetEntity } from './entities'
import type { ExerciseKind } from './muscle'
import { recordScore } from './progress'

const f = Math.fround
const avg = (xs: readonly number[]) => xs.reduce((a, x) => a + x, 0) / xs.length

/** How far back the Progress screen looks. `null` days means everything. */
export const TREND_RANGES = ['M1', 'M3', 'M6', 'Y1', 'ALL'] as const
export type TrendRange = (typeof TREND_RANGES)[number]
export const TREND_RANGE_DAYS: Record<TrendRange, number | null> = { M1: 30, M3: 91, M6: 182, Y1: 365, ALL: null }

export function trendRangeFrom(range: TrendRange, today: number): number {
  const days = TREND_RANGE_DAYS[range]
  return days == null ? Number.NEGATIVE_INFINITY : today - days + 1
}

export type TrendPoint = { epochDay: number; value: number; isRecord: boolean }

export type StalledLift = {
  exerciseId: string
  exerciseName: string
  sessionsCount: number
  spanDays: number
  bestScore: number
}

export type FatigueAndPlateauReport = {
  trainingLoadPct: number
  stalledLifts: StalledLift[]
  needsDeload: boolean
  deloadReasons: string[]
}

const weekStarts = (weeks: number, today: number) => {
  const thisWeek = weekStart(today)
  return Array.from({ length: weeks }, (_, i) => thisWeek - 7 * (weeks - 1 - i))
}
const countIn = (days: readonly number[], ws: number) => days.filter((d) => d >= ws && d <= ws + 6).length

/** Workouts per week, oldest first, for the [weeks] weeks ending with the current one. */
export function weeklyCounts(workoutDays: Iterable<number>, weeks: number, today: number): number[] {
  const days = [...workoutDays]
  return weekStarts(weeks, today).map((ws) => countIn(days, ws))
}

/**
 * Working sets (no warm-ups) in rolling 7-day windows of finished days (the last one ends yesterday), oldest first,
 * for the deload check. Ending yesterday keeps a Mon/Wed/Fri week whole on a Monday morning.
 */
export function rollingWeeklySets(sets: readonly SetEntity[], sessionDay: ReadonlyMap<number, number>, weeks: number, today: number): number[] {
  const days = sets.flatMap((s) => {
    const d = sessionDay.get(s.sessionId)
    return d == null || s.block.toLowerCase() === 'warm-up' ? [] : [d]
  })
  return Array.from({ length: weeks }, (_, i) => today - 1 - 7 * (weeks - 1 - i)).map((end) => days.filter((d) => d >= end - 6 && d <= end).length)
}

/** Sets done per week, same shape as [weeklyCounts]. */
export function weeklySets(sets: readonly SetEntity[], sessionDay: ReadonlyMap<number, number>, weeks: number, today: number): number[] {
  const days = sets.flatMap((s) => {
    const d = sessionDay.get(s.sessionId)
    return d == null ? [] : [d]
  })
  return weekStarts(weeks, today).map((ws) => countIn(days, ws))
}

/**
 * This week's sets as a percentage of the average of the previous full weeks that had any training.
 * 100 means "a normal week for you"; the first ever week reads 100 too, so nothing scolds a beginner.
 */
export function trainingLoad(weeklySetCounts: readonly number[]): number {
  if (weeklySetCounts.length === 0) return 0
  const current = weeklySetCounts[weeklySetCounts.length - 1]
  const previous = weeklySetCounts.slice(0, -1).filter((n) => n > 0)
  if (previous.length === 0) return current > 0 ? 100 : 0
  return Math.round(f(f(current * 100) / f(avg(previous))))
}

/**
 * Best set per training day for one exercise, oldest first, over the whole history. A point is a record when it
 * beats everything before it (the first day never is). Filter by range afterwards so records stay truthful.
 */
export function bestSetSeries(sets: readonly SetEntity[], kind: ExerciseKind, sessionDay: ReadonlyMap<number, number>): TrendPoint[] {
  const byDay = new Map<number, number>()
  for (const s of sets) {
    const day = sessionDay.get(s.sessionId)
    if (day == null || day < 0) continue
    const score = recordScore(kind, s.reps, s.seconds, s.weightKg, s.exerciseId)
    const prev = byDay.get(day)
    if (prev == null || score > prev) byDay.set(day, score)
  }
  let best = Number.NEGATIVE_INFINITY
  return [...byDay.keys()].sort((a, b) => a - b).map((day, i) => {
    const top = byDay.get(day)!
    const isRecord = i > 0 && top > best
    if (top > best) best = top
    return { epochDay: day, value: top, isRecord }
  })
}

/** Trailing [window]-day mean of a daily series (gaps allowed); each point averages the entries within the window. */
export function movingAverage(points: readonly TrendPoint[], window = 7): TrendPoint[] {
  const sorted = [...points].sort((a, b) => a.epochDay - b.epochDay)
  return sorted.map((p) => {
    const inWindow = sorted.filter((q) => q.epochDay >= p.epochDay - window + 1 && q.epochDay <= p.epochDay)
    return { epochDay: p.epochDay, value: f(avg(inWindow.map((q) => q.value))), isRecord: false }
  })
}

export type DayCell = 'FUTURE' | 'REST' | 'DONE' | 'RECORD'

/** 7 rows (Mon..Sun) × [weeks] columns (oldest first) for the consistency heatmap. */
export function consistencyGrid(workoutDays: ReadonlySet<number>, recordDays: ReadonlySet<number>, weeks: number, today: number): DayCell[][] {
  const starts = weekStarts(weeks, today)
  return Array.from({ length: 7 }, (_, row) =>
    starts.map((ws): DayCell => {
      const day = ws + row
      if (day > today) return 'FUTURE'
      if (recordDays.has(day)) return 'RECORD'
      if (workoutDays.has(day)) return 'DONE'
      return 'REST'
    }),
  )
}

/** First and last value of a series inside the range, or null when the range holds fewer than two points. */
export function thenNow(series: readonly TrendPoint[], from: number): [number, number] | null {
  const inRange = series.filter((p) => p.epochDay >= from)
  if (inRange.length < 2) return null
  return [inRange[0].value, inRange[inRange.length - 1].value]
}

/** Days per week on which protein reached [goal], oldest week first. Only logged days count. */
export function proteinDaysPerWeek(nutrition: ReadonlyArray<readonly [number, number]>, goal: number, weeks: number, today: number): number[] {
  const hits = nutrition.filter(([, g]) => g >= goal).map(([d]) => d)
  return weekStarts(weeks, today).map((ws) => countIn(hits, ws))
}

export type Lifetime = { workouts: number; reps: number; minutes: number; records: number; kgLifted: number }

export function lifetime(sessions: readonly SessionEntity[], sets: readonly SetEntity[]): Lifetime {
  return {
    workouts: sessions.length,
    reps: sets.reduce((a, s) => a + (s.reps ?? 0), 0),
    minutes: sessions.reduce((a, s) => a + (s.finishedAt == null ? 0 : Math.min(Math.max(Math.trunc((s.finishedAt - s.startedAt) / 60_000), 1), 300)), 0),
    records: sets.filter((s) => s.isPr).length,
    kgLifted: f(sets.reduce((a, s) => a + f(f(s.weightKg ?? 0) * (s.reps ?? 0)), 0)),
  }
}

/** Exercise ids by how often they appear in done sets, most first. */
export function topExercises(sets: readonly SetEntity[], n: number): string[] {
  const counts = new Map<string, number>()
  for (const s of sets) counts.set(s.exerciseId, (counts.get(s.exerciseId) ?? 0) + 1)
  return [...counts].sort((a, b) => b[1] - a[1]).slice(0, n).map(([id]) => id)
}

/** Percent change between two values, rounded, or null when the base is zero. */
export function percentChange(then: number, now: number): number | null {
  return then === 0 ? null : Math.round(f(f(f(now - then) / then) * 100))
}

/**
 * Evaluates recent training history for:
 * 1. Stalled lifts: exercises trained >= 3 times across >= 12 days in the last 35 days where the best score
 *    of the most recent 2 sessions failed to beat the earlier sessions in that window.
 * 2. Deload triggers: this week's sets >= 135 % of the 4-week average (with >= 18 sets),
 *    >= 3 of the last 5 readiness days below 45 % recovery, or >= 2 stalled lifts at normal or higher load.
 */
export function analyzeFatigueAndPlateaus(
  sessions: readonly SessionEntity[],
  doneSets: readonly SetEntity[],
  exercises: ReadonlyMap<string, ExerciseEntity>,
  today: number,
  readinessDays: readonly ReadinessDayEntity[] = [],
): FatigueAndPlateauReport {
  const sessionDay = new Map(sessions.map((s) => [s.id, s.epochDay]))
  // Rolling 7-day windows, not calendar weeks: on a Monday the calendar week has 0 sets and the check went blind.
  const weekly = rollingWeeklySets(doneSets, sessionDay, 5, today)
  const loadPct = trainingLoad(weekly)
  const currentWeekSets = weekly[weekly.length - 1] ?? 0

  const windowStart = today - 35
  const byExercise = new Map<string, SetEntity[]>()
  for (const s of doneSets) {
    const d = sessionDay.get(s.sessionId)
    if (d == null || d < windowStart || d > today || s.block.toLowerCase() === 'warm-up') continue
    const list = byExercise.get(s.exerciseId)
    if (list) list.push(s)
    else byExercise.set(s.exerciseId, [s])
  }

  const stalled: StalledLift[] = []
  for (const [exerciseId, exSets] of byExercise) {
    const ex = exercises.get(exerciseId)
    if (!ex || ex.id === 'jumping_jacks') continue
    // A plank or dead hang held at the same time is maintenance, not a stalled lift.
    if (ex.kind === 'HOLD' && !exSets.some((s) => (s.weightKg ?? 0) > 0)) continue
    const series = bestSetSeries(exSets, ex.kind, sessionDay)
    if (series.length < 3) continue
    const span = series[series.length - 1].epochDay - series[0].epochDay
    if (span < 12) continue
    const earlierBest = Math.max(...series.slice(0, -2).map((p) => p.value))
    const recentBest = Math.max(...series.slice(-2).map((p) => p.value))
    if (recentBest <= f(earlierBest + f(0.01))) {
      stalled.push({ exerciseId, exerciseName: ex.name, sessionsCount: series.length, spanDays: span, bestScore: Math.max(earlierBest, recentBest) })
    }
  }

  const reasons: string[] = []
  if (loadPct >= 135 && currentWeekSets >= 18) {
    reasons.push(`acute weekly volume is ${loadPct}% of your 4-week average (${currentWeekSets} sets)`)
  }
  const recentReadiness = readinessDays.filter((r) => r.epochDay >= today - 4 && r.epochDay <= today)
  const lowRecoveryDays = recentReadiness.filter((r) => r.recovery >= 1 && r.recovery <= 44).length
  if (lowRecoveryDays >= 3) {
    reasons.push(`recovery has been below 45% on ${lowRecoveryDays} of the last ${recentReadiness.length} days`)
  }
  if (stalled.length >= 2 && loadPct >= 95) {
    reasons.push(`${stalled.length} lifts have plateaued for 2+ weeks (${stalled.slice(0, 3).map((s) => s.exerciseName).join(', ')})`)
  }

  return {
    trainingLoadPct: loadPct,
    stalledLifts: [...stalled].sort((a, b) => b.sessionsCount - a.sessionsCount),
    needsDeload: reasons.length > 0,
    deloadReasons: reasons,
  }
}
