import { describe, expect, it } from 'vitest'
import { computeDebrief, debriefForSession, type DebriefExercise, type DebriefInput } from './debrief'
import type { ExerciseEntity } from './entities'
import type { ExerciseKind } from './muscle'
import type { FatigueAndPlateauReport } from './trends'
import { toKg, type WeightUnit } from './units'
import { computeWorkoutScore, scoresForSessions } from './workout-score'

// The same scenario table as Android WorkoutDebriefMathTest.
type SetOpts = { effort?: string; note?: string; block?: string; isPr?: boolean; seconds?: number | null }
const s = (reps: number | null, kg: number | null = null, o: SetOpts = {}) => ({
  sessionId: 0, exerciseId: '', itemPosition: 0, setIndex: 0, reps, seconds: o.seconds ?? null, weightKg: kg,
  block: o.block ?? 'Main', isPr: o.isPr ?? false, effort: o.effort ?? '', note: o.note ?? '',
})
type S = ReturnType<typeof s>
const n = (count: number, reps: number | null, kg: number | null = null, o: SetOpts = {}) => Array.from({ length: count }, () => s(reps, kg, o))
const hold = (count: number, seconds: number, o: SetOpts = {}) => n(count, null, null, { ...o, seconds })
type Item = { block: string; sets: number; repsMin: number | null; repsMax: number | null; seconds: number | null; weightKg: number | null }
const range = (sets = 3, min = 8, max = 12): Item => ({ block: 'Main', sets, repsMin: min, repsMax: max, seconds: null, weightKg: null })
const holdItem = (seconds: number, sets = 3): Item => ({ block: 'Main', sets, repsMin: null, repsMax: null, seconds, weightKg: null })

type ExOpts = { gap?: number | null; newest?: boolean; item?: Item | null; harder?: string | null }
const ex = (id: string, kind: ExerciseKind, done: S[], last: S[] = [], o: ExOpts = {}): DebriefExercise => ({
  exerciseId: id, kind,
  done: done.map((x, i) => ({ ...x, sessionId: 2, exerciseId: id, setIndex: i })),
  last: last.map((x, i) => ({ ...x, sessionId: 1, exerciseId: id, setIndex: i })),
  gapDays: o.gap === undefined ? (last.length ? 3 : null) : o.gap, newest: o.newest ?? true,
  item: o.item === undefined ? null : o.item ? { ...o.item, exerciseId: id } : null, harderName: o.harder ?? null,
})
type RunOpts = {
  planned?: number | null; low?: boolean; firstEver?: boolean; afterBreak?: boolean; plannedIds?: string[]
  weeklyTarget?: number; weekCount?: number; weekCountBefore?: number; report?: FatigueAndPlateauReport | null
  unit?: WeightUnit; healthOn?: boolean; sessionId?: number
}
const run = (exs: DebriefExercise[], o: RunOpts = {}) => {
  const score = computeWorkoutScore(
    exs.map((e) => ({ exerciseId: e.exerciseId, kind: e.kind, done: e.done, last: e.last })),
    o.planned ?? null, o.low ?? false, o.firstEver ?? false, o.afterBreak ?? false,
  )
  const input: DebriefInput = {
    sessionId: o.sessionId ?? 6, score, exercises: exs,
    plannedExerciseIds: new Set(o.plannedIds ?? exs.filter((e) => e.exerciseId !== 'jumping_jacks').map((e) => e.exerciseId)),
    weeklyTarget: o.weeklyTarget ?? 0, weekCount: o.weekCount ?? 1, weekCountBefore: o.weekCountBefore ?? 0,
    report: o.report ?? null, unit: o.unit ?? 'KG', healthOn: o.healthOn ?? false,
  }
  return { ...computeDebrief(input), score }
}
const report = (o: Partial<FatigueAndPlateauReport> = {}): FatigueAndPlateauReport =>
  ({ trainingLoadPct: 100, stalledLifts: [], needsDeload: false, deloadReasons: [], ...o })

const same = (id: string) => ex(id, 'WEIGHTED', n(3, 8, 50), n(3, 8, 50))
const up = (id: string, pr = false) => ex(id, 'WEIGHTED', n(3, 10, 50, { isPr: pr }), n(3, 8, 50))
const down = (id: string) => ex(id, 'WEIGHTED', n(3, 6, 40), n(3, 8, 50))

describe('debrief headlines', () => {
  it('1-4: first ever, after a break, only warm-ups, all new; variant is sessionId % count', () => {
    expect(run([same('a')], { firstEver: true, sessionId: 7 }).headline).toEqual({ type: 'FIRST_EVER', variant: 1 })
    expect(run([same('a')], { afterBreak: true, sessionId: 9 }).headline).toEqual({ type: 'AFTER_BREAK', variant: 0 })
    const warm = ex('jumping_jacks', 'REPS', n(2, 30, null, { block: 'Warm-up' }), n(2, 30, null, { block: 'Warm-up' }))
    expect(run([warm], { sessionId: 7 }).headline).toEqual({ type: 'ONLY_WARM_UPS', variant: 1 })
    expect(run([ex('a', 'WEIGHTED', n(3, 8, 50))], { sessionId: 4 }).headline).toEqual({ type: 'ALL_NEW', variant: 0 })
  })
  it('5-6: a rough day held steady, or scaled back smartly', () => {
    expect(run([same('a'), up('b')], { low: true }).headline.type).toBe('ROUGH_HELD')
    expect(run([same('a'), down('b')], { low: true }).headline.type).toBe('ROUGH_SCALED')
    // Pain forgives only its own exercise: another exercise's drop stays unforgiven.
    const painDown = ex('a', 'WEIGHTED', [...n(2, 6, 40), s(6, 40, { effort: 'PAIN' })], n(3, 8, 50))
    expect(run([painDown, same('b')]).headline.type).toBe('ROUGH_SCALED')
    expect(run([painDown, down('b'), down('c')]).headline.type).toBe('MOSTLY_DOWN')
  })
  it('7: under 75 % of the planned sets with no PR; a PR wins over it', () => {
    expect(run([same('a')], { planned: 12 }).headline.type).toBe('SHORT')
    expect(run([up('a', true)], { planned: 12 }).headline.type).toBe('PR_AND_UP')
  })
  it('8-12', () => {
    expect(run([up('a', true), same('b')]).headline.type).toBe('PR_AND_UP')
    expect(run([up('a'), same('b')]).headline.type).toBe('MOSTLY_UP')
    expect(run([up('a', true), same('b'), same('c')]).headline.type).toBe('MOSTLY_UP')
    expect(run([same('a'), same('b')]).headline.type).toBe('ALL_SAME')
    expect(run([down('a'), same('b'), same('c')]).headline.type).toBe('MIXED')
    expect(run([down('a'), down('b'), same('c')]).headline.type).toBe('MOSTLY_DOWN')
  })
})

describe('debrief wins', () => {
  it('PR first, weight before reps, the record set', () => {
    const pushUp = ex('push_up', 'REPS', n(3, 16, null, { isPr: true }), n(3, 15))
    const r = run([pushUp, up('bench', true)])
    expect(r.wins).toEqual([
      { type: 'PR', exerciseId: 'bench', kind: 'WEIGHT', weightKg: 50, reps: 10, seconds: null },
      { type: 'PR', exerciseId: 'push_up', kind: 'REPS', weightKg: null, reps: 16, seconds: null },
    ])
  })
  it('biggest gain, then more total work on another exercise (one win per exercise)', () => {
    const more = ex('b', 'WEIGHTED', n(3, 10, 50), [s(10, 50), s(10, 50), s(9, 50)])
    expect(run([more, up('a')]).wins).toEqual([
      { type: 'BEST_GAIN', exerciseId: 'a', percent: 5 },
      { type: 'MORE_WORK', exerciseId: 'b', kind: 'WEIGHTED', from: 29, to: 30 },
    ])
  })
  it('effort mastery, all planned sets, weekly target', () => {
    const easier = ex('a', 'WEIGHTED', n(3, 8, 50), [...n(2, 8, 50), s(8, 50, { effort: 'HARD' })])
    expect(run([easier], { planned: 3 }).wins).toEqual([{ type: 'EFFORT_MASTERY', exerciseId: 'a' }, { type: 'ALL_SETS', sets: 3 }])
    expect(run([same('a')], { weeklyTarget: 3, weekCount: 3, weekCountBefore: 2 }).wins).toEqual([{ type: 'WEEKLY_TARGET', target: 3 }])
    // Only the workout that reaches the target: a second workout that day doesn't.
    expect(run([same('a')], { weeklyTarget: 3, weekCount: 3, weekCountBefore: 3 }).wins).toEqual([])
  })
  it('back after 42+ days, or starting numbers for new exercises', () => {
    expect(run([ex('a', 'WEIGHTED', n(3, 8, 50), n(3, 8, 50), { gap: 50 })]).wins).toEqual([{ type: 'BACK_AFTER_GAP', exerciseId: 'a', days: 50 }])
    expect(run([ex('a', 'WEIGHTED', n(3, 8, 50), n(3, 8, 50), { gap: 41 })]).wins).toEqual([])
    expect(run([ex('a', 'WEIGHTED', n(3, 8, 50)), ex('b', 'HOLD', hold(3, 30))]).wins).toEqual([{ type: 'NEW_STARTS', count: 2 }])
  })
})

describe('debrief watch', () => {
  const painSet = (kg: number | null) => [...n(2, 8, kg), s(8, kg, { effort: 'PAIN' })]
  it('pain: eased, repeat (swap), bodyweight (same numbers), from a note too', () => {
    expect(run([ex('a', 'WEIGHTED', painSet(50), n(3, 8, 50))]).watch).toEqual([{ type: 'PAIN_EASED', exerciseId: 'a' }])
    expect(run([ex('a', 'WEIGHTED', painSet(50), painSet(50))]).watch).toEqual([{ type: 'PAIN_SWAP', exerciseId: 'a' }])
    expect(run([ex('push_up', 'REPS', painSet(null), n(3, 8))]).watch).toEqual([{ type: 'PAIN_SAME_NUMBERS', exerciseId: 'push_up' }])
    expect(run([ex('plank', 'HOLD', [...hold(2, 30), ...hold(1, 30, { effort: 'PAIN' })], hold(3, 30))]).watch)
      .toEqual([{ type: 'PAIN_SAME_NUMBERS', exerciseId: 'plank' }])
    const noted = ex('a', 'WEIGHTED', [...n(2, 8, 50), s(8, 50, { note: 'left knee pain' })], n(3, 8, 50))
    expect(run([noted]).watch).toEqual([{ type: 'PAIN_EASED', exerciseId: 'a' }])
  })
  it('pain and deload together; deload alone, not on a first workout', () => {
    expect(run([ex('a', 'WEIGHTED', painSet(50), n(3, 8, 50))], { report: report({ needsDeload: true }) }).watch)
      .toEqual([{ type: 'PAIN_EASED', exerciseId: 'a' }, { type: 'DELOAD' }])
    expect(run([same('a')], { report: report({ needsDeload: true }) }).watch).toEqual([{ type: 'DELOAD' }])
    expect(run([same('a')], { report: report({ needsDeload: true }), firstEver: true }).watch).toEqual([])
  })
  it('plateau on an exercise of this workout, not after a break', () => {
    const lifts = [{ exerciseId: 'a', exerciseName: 'A', sessionsCount: 4, spanDays: 21, bestScore: 60 }]
    expect(run([same('a')], { report: report({ stalledLifts: lifts }) }).watch).toEqual([{ type: 'PLATEAU', exerciseId: 'a', workouts: 4, days: 21 }])
    expect(run([same('b')], { report: report({ stalledLifts: lifts }) }).watch).toEqual([])
    expect(run([same('a')], { report: report({ stalledLifts: lifts }), afterBreak: true }).watch).toEqual([])
  })
  it('unforgiven drop, but not when the headline already says mostly down', () => {
    expect(run([down('a'), same('b'), same('c')]).watch).toEqual([{ type: 'DROP', exerciseId: 'a' }])
    expect(run([down('a'), down('b'), same('c')]).watch).toEqual([])
    expect(run([down('a'), same('b')], { low: true }).watch).toEqual([])
  })
  it('skipped exercise, not after a swap or on a rough day', () => {
    expect(run([same('a'), same('b')], { plannedIds: ['a', 'b', 'c'] }).watch).toEqual([{ type: 'SKIPPED', exerciseId: 'c', count: 1 }])
    expect(run([same('a'), same('d')], { plannedIds: ['a', 'b', 'c'] }).watch).toEqual([])
    expect(run([same('a'), same('b')], { plannedIds: ['a', 'b', 'c'], low: true }).watch).toEqual([])
    expect(run([same('a')], { plannedIds: ['a', 'b', 'c'], planned: 9 }).watch).toEqual([]) // headline 7 (short)
  })
})

describe('debrief next time', () => {
  it('every line type, in order, at most 3', () => {
    const swap = ex('swap', 'WEIGHTED', [...n(2, 8, 50), s(8, 50, { effort: 'PAIN' })], [s(8, 50, { effort: 'PAIN' })])
    const lighter = ex('lighter', 'WEIGHTED', [...n(2, 8, 50), s(8, 50, { effort: 'PAIN' })], n(3, 8, 50))
    const harder = ex('push_up', 'REPS', n(3, 15), n(3, 15), { harder: 'Diamond push-up' })
    const loadUp = ex('squat', 'WEIGHTED', n(3, 12, 60), n(3, 11, 60))
    expect(run([loadUp, harder, lighter, swap]).next).toEqual([
      { type: 'SWAP', exerciseId: 'swap' },
      { type: 'LIGHTER', exerciseId: 'lighter', weightKg: 47.5, reps: 8 },
      { type: 'HARDER', exerciseId: 'push_up', harderName: 'Diamond push-up' },
    ])
    expect(run([loadUp]).next).toEqual([{ type: 'LOAD_UP', exerciseId: 'squat', weightKg: 62.5, reps: 8 }])
    expect(run([ex('row', 'WEIGHTED', n(3, 10, 50), n(3, 10, 50))]).next).toEqual([{ type: 'PLUS_REP', exerciseId: 'row', reps: 11, weightKg: 50 }])
    expect(run([ex('plank', 'HOLD', hold(3, 30), hold(3, 30), { item: holdItem(30) })]).next).toEqual([{ type: 'PLUS_SECONDS', exerciseId: 'plank', seconds: 35 }])
    // A hold at 1.5x its target: a harder version, or a slower tempo with none.
    expect(run([ex('plank', 'HOLD', hold(3, 45), hold(3, 45), { item: holdItem(30) })]).next).toEqual([{ type: 'HARDER', exerciseId: 'plank', harderName: null }])
    const hard = ex('bench', 'WEIGHTED', [...n(2, 12, 60), s(12, 60, { effort: 'HARD' })], n(3, 11, 60))
    expect(run([hard]).next).toEqual([{ type: 'HOLD_STEADY', exerciseId: 'bench', weightKg: 60, reps: 12, seconds: null }])
  })
  it('everything else: same numbers; the footnote; newest = false hides next time', () => {
    const kept = ex('curl', 'WEIGHTED', [s(10, 12.5, { effort: 'HARD' }), ...n(2, 10, 12.5)], n(3, 10, 12.5))
    const r = run([ex('row', 'WEIGHTED', n(3, 10, 50), n(3, 10, 50)), kept])
    expect(r.next).toEqual([{ type: 'PLUS_REP', exerciseId: 'row', reps: 11, weightKg: 50 }])
    expect(r.sameRest).toBe(true)
    expect(r.footnote).toBe(false)
    expect(run([same('a')], { healthOn: true }).footnote).toBe(true)
    expect(run([same('a')], { low: true }).footnote).toBe(true)
    const old = run([ex('row', 'WEIGHTED', n(3, 10, 50), n(3, 10, 50), { newest: false })])
    expect(old.next).toEqual([])
    expect(old.sameRest).toBe(false)
  })
  it('a pound user steps up 5 lb', () => {
    const kg135 = toKg('LB', 135)
    const r = run([ex('squat', 'WEIGHTED', n(3, 12, kg135), n(3, 11, kg135))], { unit: 'LB' })
    expect(r.next).toHaveLength(1)
    const line = r.next[0]
    expect(line.type).toBe('LOAD_UP')
    if (line.type === 'LOAD_UP') expect(line.weightKg).toBeCloseTo(toKg('LB', 140), 3)
  })
})

describe('debriefForSession', () => {
  it('a workout from before v9 (no doneAt, no plannedSets) still gets its debrief', () => {
    const sessions = [{ id: 1, epochDay: 100, startedAt: 100_000, planDayId: null }, { id: 2, epochDay: 103, startedAt: 103_000, planDayId: null }]
    const row = (sessionId: number, setIndex: number, reps: number) =>
      ({ ...s(reps, 50), sessionId, exerciseId: 'row', setIndex })
    const sets = [row(1, 0, 8), row(1, 1, 8), row(1, 2, 8), row(2, 0, 10), row(2, 1, 10), row(2, 2, 10)]
    const lib = new Map<string, ExerciseEntity>([['row', { id: 'row', name: 'Row', kind: 'WEIGHTED', primary: ['UPPER_BACK'], secondary: [] }]])
    const score = scoresForSessions(sessions, sets, new Map([['row', 'WEIGHTED' as const]]), new Map(), new Set()).get(2)!
    const r = debriefForSession(sessions[1], sessions, sets, lib, score, [], 0, null, 'KG', false, new Set())
    expect(r.headline.type).toBe('MOSTLY_UP')
    expect(r.wins).toEqual([{ type: 'BEST_GAIN', exerciseId: 'row', percent: 5 }])
    expect(r.next).toEqual([{ type: 'PLUS_REP', exerciseId: 'row', reps: 11, weightKg: 50 }])
    // The older workout is not the newest with the row, so it shows no next time.
    const first = scoresForSessions(sessions, sets, new Map([['row', 'WEIGHTED' as const]]), new Map(), new Set()).get(1)!
    const older = debriefForSession(sessions[0], sessions, sets, lib, first, [], 0, null, 'KG', false, new Set())
    expect(older.headline.type).toBe('FIRST_EVER')
    expect(older.next).toEqual([])
  })
})

// Gemini's 8 cases (docs/plans/math-debrief-gemini-reply.md, section 4), expected from the app's real Progression rules.
describe("Gemini's 8 cases", () => {
  it('1. rep-range load graduation: both UP (rule A), no PR (the e1RM is lower), +1 rep next', () => {
    const r = run([
      ex('squat', 'WEIGHTED', n(3, 8, 62.5), n(3, 12, 60), { item: range() }),
      ex('bench', 'WEIGHTED', n(3, 8, 42.5), n(3, 12, 40), { item: range() }),
    ], { planned: 6 })
    expect(r.score.score).toBe(9)
    expect(r.headline.type).toBe('MOSTLY_UP')
    expect(r.wins).toEqual([{ type: 'ALL_SETS', sets: 6 }])
    expect(r.watch).toEqual([])
    expect(r.next).toEqual([
      { type: 'PLUS_REP', exerciseId: 'squat', reps: 9, weightKg: 62.5 },
      { type: 'PLUS_REP', exerciseId: 'bench', reps: 9, weightKg: 42.5 },
    ])
    expect(r.sameRest).toBe(false)
  })
  it('2. rough day: deadlift drop forgiven, 6 of ceil(0.67 x 9) = 7 sets, missed sets keep the numbers', () => {
    const r = run([
      ex('deadlift', 'WEIGHTED', n(2, 8, 75), n(3, 8, 80), { item: range() }),
      ex('row', 'WEIGHTED', n(2, 10, 45), n(3, 10, 45), { item: range() }),
      ex('plank', 'HOLD', hold(2, 45, { effort: 'EASY' }), hold(3, 45), { item: holdItem(45) }),
    ], { planned: 9, low: true })
    expect(r.score.score).toBe(7)
    expect(r.headline.type).toBe('ROUGH_SCALED')
    expect(r.wins).toEqual([])
    expect(r.watch).toEqual([])
    expect(r.next).toEqual([])
    expect(r.sameRest).toBe(true)
    expect(r.footnote).toBe(true)
  })
  it('3. early stop for pain: forgiven, eased next time', () => {
    const r = run([
      ex('overhead_press', 'WEIGHTED', [s(6, 35, { effort: 'PAIN' })], n(3, 10, 35), { item: range() }),
      ex('lat_pulldown', 'WEIGHTED', [s(11, 50, { isPr: true }), ...n(2, 11, 50)], n(3, 10, 50), { item: range() }),
    ], { planned: 6 })
    expect(r.score.score).toBe(9)
    expect(r.headline.type).toBe('ROUGH_SCALED')
    expect(r.wins).toEqual([{ type: 'PR', exerciseId: 'lat_pulldown', kind: 'WEIGHT', weightKg: 50, reps: 11, seconds: null }])
    expect(r.watch).toEqual([{ type: 'PAIN_EASED', exerciseId: 'overhead_press' }])
    expect(r.next).toEqual([
      { type: 'LIGHTER', exerciseId: 'overhead_press', weightKg: 32.5, reps: 8 },
      { type: 'PLUS_REP', exerciseId: 'lat_pulldown', reps: 12, weightKg: 50 },
    ])
    expect(r.footnote).toBe(true)
  })
  it('4. top of the range with a Hard set holds; the curl that felt easier loads up', () => {
    const r = run([
      ex('romanian_deadlift', 'WEIGHTED', [...n(2, 10, 70), s(10, 70, { effort: 'HARD' })], n(3, 9, 70), { item: range(3, 8, 10) }),
      ex('curl', 'WEIGHTED', n(3, 10, 12.5), n(3, 10, 12.5, { effort: 'HARD' }), { item: range(3, 8, 10) }),
    ], { planned: 6 })
    expect(r.score.score).toBe(9)
    expect(r.headline.type).toBe('MOSTLY_UP')
    expect(r.wins).toEqual([{ type: 'BEST_GAIN', exerciseId: 'romanian_deadlift', percent: 3 }, { type: 'EFFORT_MASTERY', exerciseId: 'curl' }])
    expect(r.watch).toEqual([])
    expect(r.next).toEqual([
      { type: 'LOAD_UP', exerciseId: 'curl', weightKg: 15, reps: 8 },
      { type: 'HOLD_STEADY', exerciseId: 'romanian_deadlift', weightKg: 70, reps: 10, seconds: null },
    ])
  })
  it('5. after a break: score hidden, the drop still shows (only headline 12 hides it)', () => {
    const r = run([
      ex('squat', 'WEIGHTED', n(3, 8, 60), n(3, 10, 70), { item: range(), gap: 21 }),
      ex('push_up', 'REPS', n(3, 10), n(3, 12), { item: range(), gap: 21 }),
    ], { planned: 6, afterBreak: true })
    expect(r.score.showNumber).toBe(false)
    expect(r.headline.type).toBe('AFTER_BREAK')
    expect(r.wins).toEqual([{ type: 'ALL_SETS', sets: 6 }])
    expect(r.watch).toEqual([{ type: 'DROP', exerciseId: 'squat' }])
    expect(r.next).toEqual([
      { type: 'PLUS_REP', exerciseId: 'squat', reps: 9, weightKg: 60 },
      { type: 'PLUS_REP', exerciseId: 'push_up', reps: 11, weightKg: null },
    ])
  })
  it('6. all-new exercises: starting numbers', () => {
    const r = run([
      ex('goblet_squat', 'WEIGHTED', n(3, 10, 20), [], { item: range() }),
      ex('side_plank', 'HOLD', hold(3, 30), [], { item: holdItem(30) }),
    ], { planned: 6 })
    expect(r.score.score).toBe(8)
    expect(r.headline.type).toBe('ALL_NEW')
    expect(r.wins).toEqual([{ type: 'ALL_SETS', sets: 6 }, { type: 'NEW_STARTS', count: 2 }])
    expect(r.watch).toEqual([])
    expect(r.next).toEqual([
      { type: 'PLUS_REP', exerciseId: 'goblet_squat', reps: 11, weightKg: 20 },
      { type: 'PLUS_SECONDS', exerciseId: 'side_plank', seconds: 35 },
    ])
  })
  it('7. a one-rep wobble is SAME, a 3-rep drop is DOWN; the row climbs back from the bottom of the range', () => {
    const r = run([
      ex('bench', 'WEIGHTED', n(3, 9, 60), [s(10, 60), s(9, 60), s(9, 60)], { item: range() }),
      ex('row', 'WEIGHTED', n(3, 7, 50), n(3, 10, 50), { item: range() }),
    ], { planned: 6 })
    expect(r.score.score).toBe(7)
    expect(r.headline.type).toBe('MIXED')
    expect(r.wins).toEqual([{ type: 'ALL_SETS', sets: 6 }])
    expect(r.watch).toEqual([{ type: 'DROP', exerciseId: 'row' }])
    expect(r.next).toEqual([
      { type: 'PLUS_REP', exerciseId: 'bench', reps: 10, weightKg: 60 },
      { type: 'PLUS_REP', exerciseId: 'row', reps: 9, weightKg: 50 },
    ])
  })
  it('8. ready for a harder push-up; a swapped-in exercise hides the skip', () => {
    const r = run([
      ex('push_up', 'REPS', n(3, 15, null, { effort: 'EASY' }), n(3, 15), { item: range(3, 8, 12), harder: 'Diamond push-up' }),
      ex('db_lunge', 'WEIGHTED', n(3, 10, 15), n(3, 10, 15)),
    ], { planned: 6, plannedIds: ['push_up', 'leg_extension'] })
    expect(r.score.score).toBe(8)
    expect(r.headline.type).toBe('ALL_SAME')
    expect(r.wins).toEqual([{ type: 'EFFORT_MASTERY', exerciseId: 'push_up' }, { type: 'ALL_SETS', sets: 6 }])
    expect(r.watch).toEqual([])
    expect(r.next).toEqual([
      { type: 'HARDER', exerciseId: 'push_up', harderName: 'Diamond push-up' },
      { type: 'PLUS_REP', exerciseId: 'db_lunge', reps: 11, weightKg: 15 },
    ])
  })
})
