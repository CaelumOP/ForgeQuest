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

  // Audit #7 and #14: a lift with no gain over 12+ days is stalled (the code's 12, not the old comment's 14).
  it('flags a stalled lift across exactly 12 days', () => {
    const { sessions, sets } = history([12, 8, 4, 0], [10, 10, 9, 10])
    const report = analyzeFatigueAndPlateaus(sessions, sets, exercises, today)
    expect(report.stalledLifts).toEqual([{ exerciseId: 'pull_up', exerciseName: 'Pull-up', sessionsCount: 4, spanDays: 12, bestScore: 10 }])
    expect(report.needsDeload).toBe(false) // one stalled lift alone is not a deload
  })

  it('does not flag 11 days or a recent improvement', () => {
    expect(analyzeFatigueAndPlateaus(...args(history([11, 7, 3, 0], [10, 10, 9, 10]))).stalledLifts).toEqual([])
    expect(analyzeFatigueAndPlateaus(...args(history([12, 8, 4, 0], [10, 10, 9, 11]))).stalledLifts).toEqual([])
  })

})
