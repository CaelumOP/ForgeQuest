import { describe, expect, it } from 'vitest'
import { epochDayOf, weekStart } from './dates'
import type { ExerciseEntity, SessionEntity, SetEntity } from './entities'
import {
  analyzeFatigueAndPlateaus, bestSetSeries, consistencyGrid, lifetime, movingAverage, percentChange, proteinDaysPerWeek,
  thenNow, trainingLoad, weeklyCounts, type TrendPoint,
} from './trends'

// 2026-10-14 is a Wednesday; its week starts Monday 2026-10-12.
const today = epochDayOf(2026, 10, 14)
const monday = weekStart(today)

const set = (session: number, reps: number, pr = false, exerciseId = 'pull_up'): SetEntity => ({
  id: 0, sessionId: session, exerciseId, itemPosition: 0, block: 'Main', circuit: false, setIndex: 0, targetLabel: '',
  reps, seconds: null, weightKg: null, done: true, isPr: pr, restSeconds: 60, note: '', effort: '',
})
const session = (id: number, epochDay: number, startedAt = 0, finishedAt: number | null = null): SessionEntity =>
  ({ id, planDayId: null, title: 'A', epochDay, startedAt, finishedAt, note: '', debrief: '' })
const pt = (epochDay: number, value: number): TrendPoint => ({ epochDay, value, isRecord: false })

describe('TrendsTest', () => {
  it('weeklyCountsEndWithTheCurrentWeek', () => {
    const days = new Set([monday, monday + 2, monday - 7, monday - 20])
    expect(weeklyCounts(days, 3, today)).toEqual([0, 1, 2])
    expect(weeklyCounts(days, 4, today)).toEqual([1, 0, 1, 2])
  })

  it('trainingLoadComparesToPreviousTrainedWeeks', () => {
    expect(trainingLoad([12])).toBe(100)
    expect(trainingLoad([10, 0, 10, 15])).toBe(150)
    expect(trainingLoad([])).toBe(0)
  })

  it('bestSetSeriesMarksRecordsAgainstAllEarlierDays', () => {
    const sessionDay = new Map([[1, today - 20], [2, today - 10], [3, today - 5], [4, today]])
    const series = bestSetSeries([set(1, 6), set(1, 5), set(2, 7), set(3, 7), set(4, 9)], 'REPS', sessionDay)
    expect(series.map((p) => p.value)).toEqual([6, 7, 7, 9])
    expect(series.map((p) => p.isRecord)).toEqual([false, true, false, true])
  })

  it('movingAverageUsesOnlyDaysInsideTheWindow', () => {
    expect(movingAverage([pt(1, 70), pt(2, 72), pt(20, 80)], 7).map((p) => p.value)).toEqual([70, 71, 80])
  })

  it('consistencyGridRowsAreWeekdaysAndFutureIsMarked', () => {
    const grid = consistencyGrid(new Set([monday, monday - 7]), new Set([monday - 7]), 2, today)
    expect(grid.length).toBe(7)
    expect(grid[0]).toEqual(['RECORD', 'DONE'])
    expect(grid[6][1]).toBe('FUTURE') // Sunday of this week hasn't happened
    expect(grid[1][1]).toBe('REST')
  })

  it('thenNowNeedsTwoPointsInRange', () => {
    const s = [pt(today - 100, 5), pt(today - 10, 7), pt(today, 9)]
    expect(thenNow(s, today - 30)).toEqual([7, 9])
    expect(thenNow(s, today - 5)).toBeNull()
    expect(thenNow(s, Number.NEGATIVE_INFINITY)).toEqual([5, 9])
  })

  it('lifetimeTotals', () => {
    const sessions = [session(1, today, 0, 45 * 60_000)]
    const sets = [set(1, 8, true), { ...set(1, 7), weightKg: 20 }]
    expect(lifetime(sessions, sets)).toEqual({ workouts: 1, reps: 15, minutes: 45, records: 1, kgLifted: 140 })
  })

  it('proteinDaysAndPercent', () => {
    const nutrition: [number, number][] = [[monday, 150], [monday + 1, 90], [monday - 7, 160]]
    expect(proteinDaysPerWeek(nutrition, 140, 2, today)).toEqual([1, 1])
    expect(percentChange(6, 9)).toBe(50)
    expect(percentChange(0, 3)).toBeNull()
  })
})

describe('web additions', () => {
  const pullUp: ExerciseEntity = { id: 'pull_up', name: 'Pull-up', kind: 'REPS', primary: ['UPPER_BACK'], secondary: [] }
  const exercises = new Map([['pull_up', pullUp]])
  const history = (offsets: number[], reps: number[]) => ({
    sessions: offsets.map((o, i) => session(i + 1, today - o)),
    sets: reps.map((r, i) => set(i + 1, r)),
  })
  const args = (h: { sessions: SessionEntity[]; sets: SetEntity[] }) => [h.sessions, h.sets, exercises, today] as const

  // Rule B: a plateau needs 4+ workouts over 21+ days (it was 3+ over 12+ days).
  it('flags a stalled lift across exactly 21 days', () => {
    const { sessions, sets } = history([21, 14, 7, 0], [10, 10, 9, 10])
    const report = analyzeFatigueAndPlateaus(sessions, sets, exercises, today)
    expect(report.stalledLifts).toEqual([{ exerciseId: 'pull_up', exerciseName: 'Pull-up', sessionsCount: 4, spanDays: 21, bestScore: 10 }])
    expect(report.needsDeload).toBe(false) // one stalled lift alone is not a deload
  })

  it('does not flag 20 days, 3 workouts or a recent improvement', () => {
    expect(analyzeFatigueAndPlateaus(...args(history([20, 13, 7, 0], [10, 10, 9, 10]))).stalledLifts).toEqual([])
    expect(analyzeFatigueAndPlateaus(...args(history([21, 7, 0], [10, 10, 10]))).stalledLifts).toEqual([])
    expect(analyzeFatigueAndPlateaus(...args(history([21, 14, 7, 0], [10, 10, 9, 11]))).stalledLifts).toEqual([])
  })
})

describe('rule B: plateau and deload', () => {
  const cableRow: ExerciseEntity = { id: 'cable_row', name: 'Cable row', kind: 'WEIGHTED', primary: ['UPPER_BACK'], secondary: [] }
  const lib = new Map([['cable_row', cableRow]])
  const row = (sessionId: number, reps: number): SetEntity => ({ ...set(sessionId, reps, false, 'cable_row'), weightKg: 50 })
  const workouts = (reps: number[][]) => ({
    sessions: reps.map((_, i) => session(i + 1, today - 21 + 7 * i)),
    sets: reps.flatMap((r, i) => r.map((x) => row(i + 1, x))),
  })

  it('the cable-row example: best set flat, total rising, is not a plateau', () => {
    const { sessions, sets } = workouts([[10, 9, 9], [10, 9, 9], [10, 10, 9], [10, 10, 10]])
    expect(analyzeFatigueAndPlateaus(sessions, sets, lib, today).stalledLifts).toEqual([])
  })

  it('4 flat workouts over 21 days is a plateau', () => {
    const { sessions, sets } = workouts([[10, 10, 10], [10, 10, 10], [10, 10, 10], [10, 10, 10]])
    expect(analyzeFatigueAndPlateaus(sessions, sets, lib, today).stalledLifts.map((l) => [l.exerciseId, l.sessionsCount, l.spanDays]))
      .toEqual([['cable_row', 4, 21]])
  })

  // One workout per rolling week (the windows end yesterday) with the given number of sets, oldest first.
  const weeks = (counts: number[]) => {
    const sessions = counts.map((_, i) => session(i + 1, today - 1 - 7 * (counts.length - 1 - i)))
    const sets = counts.flatMap((n, i) => Array.from({ length: n }, () => set(i + 1, 8, false, 'squat')))
    return analyzeFatigueAndPlateaus(sessions, sets, new Map(), today)
  }

  it('a vacation week does not trigger the volume deload', () => {
    // Old rule: 24 / avg(20, 20, 3, 20) = 152 %. Active weeks only: 24 / 20 = 120 %.
    expect(weeks([20, 20, 3, 20, 24])).toMatchObject({ trainingLoadPct: 120, needsDeload: false })
    expect(weeks([20, 20, 3, 20, 28])).toMatchObject({ trainingLoadPct: 140, needsDeload: true })
  })

  it('no active week among the previous 4: no volume trigger', () => {
    expect(weeks([0, 0, 2, 5, 24]).needsDeload).toBe(false)
  })
})

describe('audit 2026-10-24', () => {
  // Two lifts done the same way Mon/Wed/Fri for five weeks; checked on a Monday morning before training.
  const bench: ExerciseEntity = { id: 'bench', name: 'Bench', kind: 'WEIGHTED', primary: ['CHEST'], secondary: [] }
  const row: ExerciseEntity = { id: 'row', name: 'Row', kind: 'WEIGHTED', primary: ['UPPER_BACK'], secondary: [] }
  const plank: ExerciseEntity = { id: 'plank', name: 'Plank', kind: 'HOLD', primary: ['ABS'], secondary: [] }
  const lib = new Map([bench, row, plank].map((e) => [e.id, e]))
  const trainingDays = Array.from({ length: 5 }, (_, w) => [0, 2, 4].map((d) => monday - 35 + 7 * w + d)).flat()
  const sessions = trainingDays.map((d, i) => session(i + 1, d))
  const lift = (sessionId: number, exerciseId: string, block = 'Main'): SetEntity =>
    ({ ...set(sessionId, 8, false, exerciseId), block, weightKg: 60 })
  const doneSets = sessions.flatMap((s) => [1, 2, 3].flatMap(() => [lift(s.id, 'bench'), lift(s.id, 'row')]))

  it('the deload check still sees a full week on Monday', () => {
    const report = analyzeFatigueAndPlateaus(sessions, doneSets, lib, monday)
    expect(report.trainingLoadPct).toBe(100)
    expect(report.stalledLifts.map((s) => s.exerciseId).sort()).toEqual(['bench', 'row'])
    expect(report.needsDeload).toBe(true)
  })

  it('warm-ups are not counted and a held plank is not a stalled lift', () => {
    const warmups = sessions.map((s) => lift(s.id, 'bench', 'Warm-up'))
    const planks = sessions.map((s) => ({ ...set(s.id, 0, false, 'plank'), reps: null, seconds: 60 }))
    const report = analyzeFatigueAndPlateaus(sessions, [...doneSets, ...warmups, ...planks], lib, monday)
    expect(report.trainingLoadPct).toBe(100)
    expect(report.stalledLifts.map((s) => s.exerciseId)).not.toContain('plank')
  })
})
