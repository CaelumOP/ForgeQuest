import { describe, expect, it } from 'vitest'
import { computeWorkoutScore, scoresForSessions, trend, trendStats, type ScoreWork } from './workout-score'
import type { DayTag } from './progress'

type S = { reps: number; seconds: number | null; weightKg: number | null; isPr: boolean; effort: string; block: string }
const set = (reps: number, weightKg: number | null = null, extra: Partial<S> = {}): S =>
  ({ reps, seconds: null, weightKg, isPr: false, effort: '', block: 'Main', ...extra })
const sets = (n: number, reps: number, kg: number | null, extra: Partial<S> = {}) => Array.from({ length: n }, () => set(reps, kg, extra))
const hold = (seconds: number): S => ({ ...set(0, 0), reps: 0, seconds })
let next = 0
const work = (done: S[], last: S[], id = `ex${next++}`): ScoreWork => ({ exerciseId: id, kind: 'WEIGHTED', done, last })

// Each exercise has 3 sets, so 4 exercises are 12 sets.
const same = () => work(sets(3, 8, 50), sets(3, 8, 50))
const up = () => work(sets(3, 10, 50), sets(3, 8, 50))
// A real drop: fewer reps at a lighter weight.
const down = () => work(sets(3, 6, 40), sets(3, 8, 50))
const many = (f: () => ScoreWork, n: number) => Array.from({ length: n }, f)
const score = (w: ScoreWork[], planned: number | null, low = false) => computeWorkoutScore(w, planned, low)

describe('workout score v2', () => {
  it('all SAME, all sets is 8 (TC-1.1)', () => {
    expect(score(many(same, 4), 12).score).toBe(8)
  })
  it('1 UP + 3 SAME is 8, 9 with a PR (TC-1.2, 1.3)', () => {
    expect(score([up(), ...many(same, 3)], 12).score).toBe(8)
    expect(score([work(sets(3, 10, 50, { isPr: true }), sets(3, 8, 50)), ...many(same, 3)], 12).score).toBe(9)
  })
  it('2 UP + 2 SAME is 9, 10 with a PR (TC-1.5)', () => {
    expect(score([...many(up, 2), ...many(same, 2)], 12).score).toBe(9)
    const r = score([up(), work(sets(3, 10, 50, { isPr: true }), sets(3, 8, 50)), ...many(same, 2)], 12)
    expect(r).toMatchObject({ score: 10, up: 2, compared: 4, prs: 3 })
  })
  it('1 DOWN + 3 SAME is 7 (TC-1.4)', () => {
    expect(score([down(), ...many(same, 3)], 12).score).toBe(7)
  })
  it('2 of 9 sets with 1 UP of 1 and a PR is 4: under 75 % progress counts for nothing', () => {
    expect(score([work([set(10, 50, { isPr: true }), set(10, 50)], [set(8, 50)])], 9).score).toBe(4)
  })
  it('nothing to compare scores progress 2: all sets is 8', () => {
    expect(score(many(() => work(sets(3, 8, 50), []), 3), 9)).toMatchObject({ score: 8, compared: 0 })
  })
  it('a free workout with 2 UP of 2 is 9', () => {
    expect(score(many(up, 2), null)).toMatchObject({ score: 9, plannedSets: null })
  })
  it('a rough day: all SAME is 8, 1 DOWN + 3 SAME is 8, all DOWN is 8 (TC-2.1)', () => {
    expect(score(many(same, 4), 12, true)).toMatchObject({ score: 8, rough: true, lowRecovery: true })
    expect(score([down(), ...many(same, 3)], 12, true).score).toBe(8)
    expect(score(many(down, 4), 12, true).score).toBe(8)
  })
  it('a rough day needs only ceil(0.67 × planned) sets (TC-2.2)', () => {
    // 9 of 12 sets, all DOWN, recovery under 50: ceil(0.67 × 12) = ceil(8.04) = 9, so 9 / 9 is full completion.
    const w = [...many(() => work(sets(2, 8, 40), sets(3, 8, 50)), 3), down()]
    expect(score(w, 12, true)).toMatchObject({ score: 8, doneSets: 9 })
    expect(score(w.slice(1), 12, true).score).toBe(7)
  })
  it('PAIN forgives only its own exercise (TC-2.3) and cuts the planned sets (TC-2.4)', () => {
    const painDown = work([...sets(2, 8, 40), set(8, 40, { effort: 'PAIN' })], sets(3, 8, 50))
    expect(score([...many(down, 3), painDown], 12)).toMatchObject({ score: 6, rough: true, lowRecovery: false })
    const stopped = work([set(8, 40, { effort: 'PAIN' })], sets(3, 8, 50))
    expect(score([stopped, ...many(same, 3)], 12).score).toBe(8)
  })
  it('trend: 3×12@60 → 3×8@62.5 is UP (rule A), 3×10@60 → 3×5@67.5 is SAME, 3×10@60 → 1×1@65 is DOWN (TC-3.x)', () => {
    expect(trend('WEIGHTED', 'ex', sets(3, 8, 62.5), sets(3, 12, 60))).toBe('UP')
    expect(trend('WEIGHTED', 'ex', sets(3, 5, 67.5), sets(3, 10, 60))).toBe('SAME')
    expect(trend('WEIGHTED', 'ex', [set(1, 65)], sets(3, 10, 60))).toBe('DOWN')
    expect(trend('WEIGHTED', 'ex', sets(3, 8, 62.5), sets(3, 8, 60))).toBe('UP')
  })
  it('trend: a small drop is SAME, a real drop is DOWN (TC-4.3, 4.4)', () => {
    expect(trend('HOLD', 'plank', [hold(59), hold(59), hold(59)], [hold(60), hold(60), hold(60)])).toBe('SAME')
    // WEIGHTED "total fell" uses the volume (kg x reps): 57.5 vs 60 at the same reps is -4 %, so it holds steady.
    expect(trend('WEIGHTED', 'ex', sets(3, 8, 57.5), sets(3, 8, 60))).toBe('SAME')
    // 12 vs 14 at the same reps: the best set and the volume both fell (-14 %), so it is a drop.
    expect(trend('WEIGHTED', 'ex', sets(3, 10, 12), sets(3, 10, 14))).toBe('DOWN')
    expect(trendStats('WEIGHTED', 'ex', sets(3, 10, 14)).volume).toBe(420)
    expect(trend('WEIGHTED', 'ex', sets(3, 6, 57.5), sets(3, 8, 60))).toBe('DOWN')
  })
  it('rule A scenarios', () => {
    // The reset day.
    expect(trend('WEIGHTED', 'ex', sets(3, 8, 62.5), sets(3, 12, 60))).toBe('UP')
    // A one-rep wobble at the same weight.
    expect(trend('WEIGHTED', 'ex', sets(3, 9, 30), [set(10, 30), set(9, 30), set(8, 30)])).toBe('SAME')
    // Three reps fewer on every set.
    expect(trend('WEIGHTED', 'ex', sets(3, 7, 50), sets(3, 10, 50))).toBe('DOWN')
    // A heavy single on top keeps the 60 kg sets in the total (30 + 1 reps).
    expect(trendStats('WEIGHTED', 'ex', [...sets(3, 10, 60), set(1, 102.5)])).toMatchObject({ total: 31, top: 60 })
    // Holds: 10 % shorter holds steady, 20 % shorter with a lower total is down.
    const holds = (s: number) => [hold(s), hold(s), hold(s)].map((h) => ({ ...h, weightKg: null }))
    expect(trend('HOLD', 'plank', holds(54), holds(60))).toBe('SAME')
    expect(trend('HOLD', 'plank', holds(48), holds(60))).toBe('DOWN')
    // REPS: one rep fewer holds steady.
    expect(trend('REPS', 'push_up', sets(3, 9, null), sets(3, 10, null))).toBe('SAME')
    expect(trend('REPS', 'push_up', sets(3, 8, null), sets(3, 10, null))).toBe('DOWN')
  })
  it('trend: ramp-up sets under 60 % of the heaviest are left out of the total (TC-6.1, 6.2, 6.3)', () => {
    expect(trend('WEIGHTED', 'ex', [set(10, 20), set(5, 35), ...sets(3, 8, 60)], sets(3, 8, 60))).toBe('SAME')
    expect(trend('WEIGHTED', 'ex', [...sets(3, 8, 60), set(10, 45)], sets(3, 8, 60))).toBe('UP')
    expect(trend('WEIGHTED', 'ex', [set(9, 0), set(8, 0), set(8, 0)], sets(3, 8, 0))).toBe('UP')
  })
  it('warm-up block sets count for nothing: skipping them scores the same as doing them', () => {
    const warm = sets(5, 10, null, { block: 'Warm-up' })
    const withWarm = [work(warm, warm, 'jumping_jacks'), ...many(same, 4)]
    const skipped = many(same, 4)
    const a = score(withWarm, 12)
    const b = score(skipped, 12)
    expect(a).toMatchObject({ score: 8, doneSets: 12, compared: 4 })
    expect(a.score).toBe(b.score)
    expect(a.doneSets).toBe(b.doneSets)
  })
  it('a warm-up matched case-insensitively by its stored block name', () => {
    expect(score([work(sets(5, 10, null, { block: 'warm-up' }), []), ...many(same, 4)], 12).doneSets).toBe(12)
  })
})

describe('scoresForSessions', () => {
  const sess = (id: number, day: number, extra: { plannedSets?: number | null; planDayId?: number | null } = {}) =>
    ({ id, planDayId: 7, epochDay: day, startedAt: day * 1000, ...extra })
  const row = (sessionId: number, reps: number, extra: Partial<S> = {}) => ({ sessionId, exerciseId: 'ex', ...set(reps, 50, extra) })
  const kinds = new Map([['ex', 'WEIGHTED' as const]])

  it('finds last time and the planned sets; the first-ever workout hides the number', () => {
    const r = scoresForSessions([sess(1, 100), sess(2, 102)], [row(1, 8), row(2, 10)], kinds, new Map([[7, 1]]), new Set())
    expect(r.get(1)).toMatchObject({ score: 8, firstEver: true, afterBreak: false, showNumber: false })
    expect(r.get(2)).toMatchObject({ score: 9, firstEver: false, showNumber: true })
  })
  it('only: scores just that workout, the same as the full run', () => {
    const sessions = [sess(1, 100), sess(2, 102), sess(3, 102), sess(4, 105)]
    const sets = [row(1, 8), row(2, 10), row(3, 6), row(4, 12)]
    const full = scoresForSessions(sessions, sets, kinds, new Map([[7, 1]]), new Set())
    for (const id of [1, 2, 3, 4]) {
      const one = scoresForSessions(sessions, sets, kinds, new Map([[7, 1]]), new Set(), new Map(), new Set(), id)
      expect([...one.keys()]).toEqual([id])
      expect(one.get(id)).toEqual(full.get(id))
    }
  })
  it('a saved plannedSets wins over the plan as it is now (TC-6.4)', () => {
    const done = (id: number) => Array.from({ length: 9 }, () => row(id, 8))
    const sessions = [sess(1, 100, { plannedSets: 9 }), sess(2, 102, { plannedSets: 9 }), sess(3, 104)]
    const r = scoresForSessions(sessions, [...done(1), ...done(2), ...done(3)], kinds, new Map([[7, 15]]), new Set())
    expect(r.get(2)).toMatchObject({ score: 8, plannedSets: 9 })
    expect(r.get(3)).toMatchObject({ plannedSets: 15, score: 4 })
  })
  it('after a sick day since the last workout, afterBreak is true and the number is hidden', () => {
    const tags = new Map<number, DayTag>([[101, 'SICK']])
    const r = scoresForSessions([sess(1, 100), sess(2, 103), sess(3, 104)], [row(1, 8), row(2, 8), row(3, 8)], kinds, new Map([[7, 1]]), new Set(), tags)
    expect(r.get(2)).toMatchObject({ afterBreak: true, showNumber: false, score: 8 })
    expect(r.get(3)).toMatchObject({ afterBreak: false, showNumber: true })
  })
  it('low recovery days are rough', () => {
    const r = scoresForSessions([sess(1, 100), sess(2, 102)], [row(1, 8), row(2, 6)], kinds, new Map([[7, 1]]), new Set([102]))
    expect(r.get(2)).toMatchObject({ rough: true, score: 8 })
  })
})
