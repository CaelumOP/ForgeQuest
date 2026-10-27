import { describe, expect, it } from 'vitest'
import { computeWorkoutScore, scoresForSessions, type ScoreWork } from './workout-score'

const set = (reps: number, weightKg: number | null = null, extra: { isPr?: boolean; effort?: string } = {}) =>
  ({ reps, seconds: null, weightKg, isPr: false, effort: '', ...extra })
const work = (done: ReturnType<typeof set>[], last: ReturnType<typeof set>[]): ScoreWork => ({ exerciseId: 'ex', kind: 'WEIGHTED', done, last })

const same = work([set(8, 50)], [set(8, 50)])
const up = work([set(10, 50)], [set(8, 50)])
const down = work([set(6, 40)], [set(8, 50)])
const many = (w: ScoreWork, n: number) => Array.from({ length: n }, () => w)

describe('workout score', () => {
  it('every planned set done, everything the same, no PR, normal day is 7', () => {
    expect(computeWorkoutScore(many(same, 4), 4, false).score).toBe(7)
  })
  it('3 up + 1 same with a PR is 9', () => {
    const withPr = work([set(10, 50, { isPr: true })], [set(8, 50)])
    const r = computeWorkoutScore([...many(up, 2), withPr, same], 4, false)
    expect(r).toMatchObject({ score: 9, up: 3, compared: 4, prs: 1 })
  })
  it('the first workout ever (nothing to compare) is 7', () => {
    const r = computeWorkoutScore(many(work([set(8, 50)], []), 3), 3, false)
    expect(r).toMatchObject({ score: 7, compared: 0 })
  })
  it('a rough day with everything the same is 9, and a PAIN set counts as rough', () => {
    expect(computeWorkoutScore(many(same, 4), 4, true).score).toBe(9)
    expect(computeWorkoutScore([work([set(8, 50, { effort: 'PAIN' })], [set(8, 50)])], 1, false).score).toBe(9)
  })
  it('half the planned sets done, everything down, is 4; 2 of 12 is clamped to 4', () => {
    expect(computeWorkoutScore(many(down, 6), 12, false).score).toBe(4)
    expect(computeWorkoutScore(many(down, 2), 12, false).score).toBe(4)
  })
  it('a free workout with 2 up of 2 is 9', () => {
    expect(computeWorkoutScore(many(up, 2), null, false)).toMatchObject({ score: 9, plannedSets: null })
  })
  it('scoresForSessions finds last time and the planned sets', () => {
    const sess = (id: number, day: number) => ({ id, planDayId: 7, epochDay: day, startedAt: day * 1000 })
    const sets = [
      { sessionId: 1, exerciseId: 'ex', ...set(8, 50) },
      { sessionId: 2, exerciseId: 'ex', ...set(10, 50) },
    ]
    const r = scoresForSessions([sess(1, 100), sess(2, 102)], sets, new Map([['ex', 'WEIGHTED']]), new Map([[7, 1]]), new Set())
    expect(r.get(1)?.score).toBe(7)
    expect(r.get(2)?.score).toBe(9)
  })
})
