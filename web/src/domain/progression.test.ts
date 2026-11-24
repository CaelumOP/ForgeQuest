import { describe, expect, it } from 'vitest'
import { debriefForSession } from './debrief'
import { chipOf, effortOf, effortRir, type SetEffort } from './effort'
import type { ExerciseEntity } from './entities'
import { readyForHarder } from './exercise-tiers'
import type { ExerciseKind } from './muscle'
import { nextPrefill, progressionInputs, type PastSet, type Target } from './progression'
import { fromKg, toKg, type WeightUnit } from './units'
import { scoresForSessions } from './workout-score'

const sets = (n: number, reps: number | null, kg: number | null, sec: number | null = null, effort: SetEffort = 'OK'): PastSet[] =>
  Array.from({ length: n }, () => ({ reps, seconds: sec, weightKg: kg, effort }))
const target = (sets: number, repsMin: number | null, repsMax: number | null, seconds: number | null, weightKg: number | null): Target =>
  ({ sets, repsMin, repsMax, seconds, weightKg })
const next = (kind: ExerciseKind, t: Target, last: PastSet[], previous: PastSet[] = [], unit: WeightUnit = 'KG') =>
  nextPrefill(kind, t, last, previous, unit, false)

describe('ProgressionTest', () => {
  it('rangeTopAddsOneStepAndResets', () => {
    const r = next('WEIGHTED', target(3, 8, 12, null, 40), sets(3, 12, 40))
    expect(r.sets[0].reps).toBe(8)
    expect(r.sets[0].weightKg!).toBeCloseTo(42.5, 2)
  })

  it('singleTargetNeedsTwoCleanSessionsAtTheSameWeight', () => {
    const t = target(3, 8, null, null, 60)
    // one session at target: hold
    expect(next('WEIGHTED', t, sets(3, 8, 60), []).sets[0].weightKg!).toBeCloseTo(60, 2)
    // two in a row at 60: go up
    expect(next('WEIGHTED', t, sets(3, 8, 60), sets(3, 8, 60)).sets[0].weightKg!).toBeCloseTo(62.5, 2)
    // just went up (62.5 after 60): no second increase in a row
    expect(next('WEIGHTED', t, sets(3, 8, 62.5), sets(3, 8, 60)).sets[0].weightKg!).toBeCloseTo(62.5, 2)
    // a hard set in the previous session still counts as clean and goes up
    expect(next('WEIGHTED', t, sets(3, 8, 60), sets(3, 8, 60, null, 'HARD')).sets[0].weightKg!).toBeCloseTo(62.5, 2)
    // a failure set in the previous session blocks it
    expect(next('WEIGHTED', t, sets(3, 8, 60), sets(3, 8, 60, null, 'FAILURE')).sets[0].weightKg!).toBeCloseTo(60, 2)
  })

  it('poundsStepInPoundsOnLoadableNumbers', () => {
    const r = next('WEIGHTED', target(3, 8, 12, null, null), sets(3, 12, toKg('LB', 135)), [], 'LB')
    expect(fromKg('LB', r.sets[0].weightKg!)).toBeCloseTo(140, 2)
  })

  it('hardAndPainHoldOrBackOff', () => {
    const t = target(3, 8, 12, null, 40)
    // Hard sets are progress-eligible: top of range steps up and resets to bottom
    const hard = next('WEIGHTED', t, sets(3, 12, 40, null, 'HARD'))
    expect(hard.sets[0].weightKg!).toBeCloseTo(42.5, 2)
    expect(hard.sets[0].reps).toBe(8)
    // Failure holds weight and reps
    const failure = next('WEIGHTED', t, sets(3, 12, 40, null, 'FAILURE'))
    expect(failure.sets[0].weightKg!).toBeCloseTo(40, 2)
    expect(failure.sets[0].reps).toBe(12)
    const pain = next('WEIGHTED', t, sets(3, 10, 40, null, 'PAIN'))
    expect(pain.sets[0].weightKg!).toBeCloseTo(37.5, 2)
    // bodyweight move with pain: reps stay
    const bw = next('REPS', target(3, 8, 12, null, null), sets(3, 10, null, null, 'PAIN'))
    expect(bw.sets[0].reps).toBe(10)
  })

  it('holdsStopAtOneAndAHalfTimesTarget', () => {
    const t = target(3, null, null, 30, null)
    expect(next('HOLD', t, sets(3, null, null, 35)).sets[0].seconds).toBe(40)
    const atCeiling = next('HOLD', t, sets(3, null, null, 45))
    expect(atCeiling.sets[0].seconds).toBe(45)
    expect(atCeiling.holdCeilingReached).toBe(true)
  })

  it('oldNotesStillCountAsEffort', () => {
    expect(effortOf('', 'left knee twinge')).toBe('PAIN')
    expect(effortOf('', 'RIR 0')).toBe('HARD')
    expect(effortOf('EASY', 'fail')).toBe('EASY')
    expect(effortOf('', '')).toBe('OK')
  })
})

describe('web additions', () => {
  // Audit #9: a 0 kg session falls back to the plan weight; pain keeps at least one load step.
  it('a 0 kg session after a pain step-down falls back to the plan weight', () => {
    const t = target(3, 8, 12, null, 2.5)
    expect(next('WEIGHTED', t, sets(3, 10, 2.5, null, 'PAIN')).sets[0].weightKg).toBe(2.5)
    const back = next('WEIGHTED', t, sets(3, 10, 0))
    expect(back.sets[0].weightKg).toBe(2.5)
    expect(back.sets[0].reps).toBe(11)
    const climbs = next('WEIGHTED', t, sets(3, 12, 0))
    expect(climbs.sets[0].weightKg).toBe(5)
    expect(climbs.sets[0].reps).toBe(8)
  })

  it('a weighted move without a plan weight still stays at 0 after 0 kg', () => {
    expect(next('WEIGHTED', target(3, 8, 12, null, null), sets(3, 12, 0)).sets[0].weightKg).toBe(0)
  })
})

describe('audit 2026-10-24', () => {
  it('the default 8-12 range resets to 8 after a load step', () => {
    const r = nextPrefill('WEIGHTED', null, sets(3, 12, 60), [], 'KG', false)
    expect(r.sets).toEqual(Array(3).fill({ reps: 8, seconds: null, weightKg: 62.5 }))
  })
  it('one failed weighted set starts next time at the bottom of the range', () => {
    const last = [...sets(2, 10, 60), ...sets(1, 4, 60, null, 'HARD')]
    expect(next('WEIGHTED', target(3, 8, 12, null, 60), last).sets.map((s) => s.reps)).toEqual([10, 10, 8])
  })
})

describe('progressionInputs', () => {
  it('reads the last two workouts newest first, in set order, effort from the note', () => {
    const row = (sessionId: number, setIndex: number, reps: number, effort = '', note = '') =>
      ({ sessionId, setIndex, reps, seconds: null, weightKg: 60, effort, note })
    const recent = [row(9, 1, 8, '', 'knee pain'), row(9, 0, 10), row(5, 0, 12, 'HARD'), row(2, 0, 6)]
    const item = { block: 'Main', sets: 3, repsMin: 8, repsMax: 12, seconds: null, weightKg: 60 }
    const input = progressionInputs(item, 'squat', recent)
    expect(input.target).toEqual(target(3, 8, 12, null, 60))
    expect(input.last).toEqual([{ reps: 10, seconds: null, weightKg: 60, effort: 'OK' }, { reps: 8, seconds: null, weightKg: 60, effort: 'PAIN' }])
    expect(input.previous).toEqual([{ reps: 12, seconds: null, weightKg: 60, effort: 'HARD' }])
    expect(input.warmup).toBe(false)
    expect(progressionInputs({ ...item, block: 'warm-up' }, 'squat', recent).warmup).toBe(true)
    expect(progressionInputs(null, 'jumping_jacks', []).warmup).toBe(true)
    expect(progressionInputs(null, 'squat', []).target).toBeNull()
  })
})

describe('deload & reasons', () => {
  it('halves sets on deload week and keeps numbers without progressing', () => {
    const r = nextPrefill('WEIGHTED', target(4, 8, 12, null, 60), sets(4, 12, 60), [], 'KG', false, true)
    expect(r.sets.length).toBe(2)
    expect(r.sets[0]).toEqual({ reps: 12, seconds: null, weightKg: 60 })
  })
  it('returns progression reasons matching Android Progression.kt', () => {
    expect(nextPrefill('WEIGHTED', target(3, 8, 12, null, 60), sets(3, 12, 60), [], 'KG', false).reason).toBe('LOAD_UP')
    expect(nextPrefill('WEIGHTED', target(3, 8, 12, null, 60), sets(3, 10, 60), [], 'KG', false).reason).toBe('MORE_REPS')
    expect(nextPrefill('WEIGHTED', target(3, 8, 12, null, 60), sets(3, 10, 60, null, 'HARD'), [], 'KG', false).reason).toBe('MORE_REPS')
    expect(nextPrefill('WEIGHTED', target(3, 8, 12, null, 60), sets(3, 10, 60, null, 'FAILURE'), [], 'KG', false).reason).toBe('HARD')
    expect(nextPrefill('WEIGHTED', target(3, 8, 12, null, 60), sets(3, 10, 60, null, 'PAIN'), [], 'KG', false).reason).toBe('PAIN')
    expect(nextPrefill('WEIGHTED', target(3, 8, 12, null, 60), sets(2, 10, 60), [], 'KG', false).reason).toBe('MISSED')
  })
})

describe('ProgressionMode LINEAR / MAINTAIN & WP1a set-types', () => {
  it('LINEAR adds +2.5 kg upper and +5 kg lower regardless of display unit', () => {
    const t = target(3, 5, 5, null, 60)
    const upper = nextPrefill('WEIGHTED', t, sets(3, 5, 60), [], 'KG', false, false, 'LINEAR', false)
    expect(upper.reason).toBe('LOAD_UP')
    expect(upper.sets[0].weightKg).toBeCloseTo(62.5, 2)

    const lower = nextPrefill('WEIGHTED', t, sets(3, 5, 100), [], 'KG', false, false, 'LINEAR', true)
    expect(lower.reason).toBe('LOAD_UP')
    expect(lower.sets[0].weightKg).toBeCloseTo(105, 2)

    const lowerLb = nextPrefill('WEIGHTED', t, sets(3, 5, toKg('LB', 225)), [], 'LB', false, false, 'LINEAR', true)
    expect(fromKg('LB', lowerLb.sets[0].weightKg!)).toBeCloseTo(235, 1)
  })

  it('MAINTAIN holds weight and reps even when top of rep range is reached', () => {
    const t = target(3, 8, 12, null, 60)
    const maintain = nextPrefill('WEIGHTED', t, sets(3, 12, 60), [], 'KG', false, false, 'MAINTAIN', false)
    expect(maintain.reason).toBe('SAME')
    expect(maintain.sets[0]).toEqual({ reps: 12, seconds: null, weightKg: 60 })
  })

  it('enforces precedence pain > hard > missed > deload > mode rules, and never auto-resets on failures', () => {
    const t = target(3, 5, 5, null, 100)
    // Pain takes back weight even under LINEAR or MAINTAIN
    expect(nextPrefill('WEIGHTED', t, sets(3, 5, 100, null, 'PAIN'), [], 'KG', false, false, 'LINEAR', true).sets[0].weightKg).toBeCloseTo(97.5, 2)
    expect(nextPrefill('WEIGHTED', t, sets(3, 5, 100, null, 'PAIN'), [], 'KG', false, false, 'MAINTAIN', false).sets[0].weightKg).toBeCloseTo(97.5, 2)
    // Failure blocks linear increment and never resets when target reps are met
    const hardLinear = nextPrefill('WEIGHTED', t, sets(3, 5, 100, null, 'FAILURE'), sets(3, 5, 100, null, 'FAILURE'), 'KG', false, false, 'LINEAR', true)
    expect(hardLinear.reason).toBe('HARD')
    expect(hardLinear.sets[0].weightKg).toBeCloseTo(100, 2)
    // Missed / deload also hold under LINEAR
    expect(nextPrefill('WEIGHTED', t, sets(2, 5, 100), [], 'KG', false, false, 'LINEAR', true).reason).toBe('MISSED')
    expect(nextPrefill('WEIGHTED', t, sets(3, 5, 100), [], 'KG', false, true, 'LINEAR', true).sets).toHaveLength(2)
  })

  it('progressionInputs excludes WARMUP, DROPSET, and RESTPAUSE sets while keeping WORKING and FAILURE', () => {
    const row = (setIndex: number, reps: number, weightKg: number, setType: string) =>
      ({ sessionId: 10, setIndex, reps, seconds: null, weightKg, effort: '', note: '', setType, block: 'Main' })
    const item = { block: 'Main', sets: 2, repsMin: 8, repsMax: 12, seconds: null, weightKg: 80, progressionMode: 'LINEAR' }
    const input = progressionInputs(item, 'bench_press', [
      row(0, 8, 40, 'WARMUP'),
      row(1, 10, 80, 'WORKING'),
      row(2, 9, 80, 'FAILURE'),
      row(3, 12, 60, 'DROPSET'),
      row(4, 4, 80, 'RESTPAUSE'),
    ])
    expect(input.mode).toBe('LINEAR')
    expect(input.last.map((s) => s.reps)).toEqual([10, 9])
  })
})

describe('effort scale (reps in reserve)', () => {
  const t = target(3, 8, null, null, 60)
  it('all Easy adds load right away, mixed Easy and Hard does not', () => {
    expect(next('WEIGHTED', t, sets(3, 8, 60, null, 'EASY'), []).sets[0].weightKg!).toBeCloseTo(62.5, 2)
    const mixed = [...sets(2, 8, 60, null, 'EASY'), ...sets(1, 8, 60, null, 'HARD')]
    expect(next('WEIGHTED', t, mixed, []).sets[0].weightKg!).toBeCloseTo(60, 2)
  })
  it('Failure holds the load; twice in a row backs off; Pain still backs off at once', () => {
    expect(next('WEIGHTED', t, sets(3, 8, 60, null, 'FAILURE'), sets(3, 8, 60)).sets[0].weightKg!).toBeCloseTo(60, 2)
    const missedFail = [...sets(2, 8, 60), ...sets(1, 6, 60, null, 'FAILURE')]
    expect(next('WEIGHTED', t, missedFail, missedFail).sets[0].weightKg!).toBeCloseTo(57.5, 2)
    expect(next('WEIGHTED', t, sets(3, 8, 60, null, 'PAIN'), []).sets[0].weightKg!).toBeCloseTo(57.5, 2)
  })
})

describe('RIR semantics', () => {
  const weightedTarget = (setsCount = 3, min = 8, max: number | null = 12, kg = 60) =>
    target(setsCount, min, max, null, kg)
  const bwTarget = (setsCount = 3, min = 8, max: number | null = 12) =>
    target(setsCount, min, max, null, null)
  const setEntity = (o: Partial<{
    sessionId: number; exerciseId: string; setIndex: number; reps: number | null; seconds: number | null
    weightKg: number | null; effort: string; note: string; setType: string; block: string; isPr: boolean
  }> = {}) => ({
    sessionId: o.sessionId ?? 1,
    exerciseId: o.exerciseId ?? 'bench_press',
    itemPosition: 0,
    block: o.block ?? 'Main',
    circuit: false,
    setIndex: o.setIndex ?? 0,
    targetLabel: '',
    reps: o.reps !== undefined ? o.reps : 8,
    seconds: o.seconds ?? null,
    weightKg: o.weightKg !== undefined ? o.weightKg : 60,
    done: true,
    isPr: o.isPr ?? false,
    restSeconds: 90,
    effort: o.effort ?? '',
    note: o.note ?? '',
    setType: o.setType ?? 'WORKING',
  })

  it('blankAtTopOfRangeLoadsUp', () => {
    const r = nextPrefill('WEIGHTED', weightedTarget(), sets(3, 12, 60, null, 'OK'), [], 'KG', false)
    expect(r.reason).toBe('LOAD_UP')
    expect(r.sets.map((s) => s.reps)).toEqual([8, 8, 8])
    expect(r.sets[0].weightKg!).toBeCloseTo(62.5, 2)
  })

  it('hardAtTopOfRangeLoadsUp', () => {
    const r = nextPrefill('WEIGHTED', weightedTarget(), sets(3, 12, 60, null, 'HARD'), [], 'KG', false)
    expect(r.reason).toBe('LOAD_UP')
    expect(r.sets.map((s) => s.reps)).toEqual([8, 8, 8])
    expect(r.sets[0].weightKg!).toBeCloseTo(62.5, 2)

    // Holds also advance on HARD and block on FAILURE
    const holdTarget = target(3, null, null, 30, null)
    const hardHold = nextPrefill('HOLD', holdTarget, sets(3, null, null, 30, 'HARD'), [], 'KG', false)
    expect(hardHold.reason).toBe('MORE_TIME')
    expect(hardHold.sets[0].seconds).toBe(35)

    const failHold = nextPrefill('HOLD', holdTarget, sets(3, null, null, 30, 'FAILURE'), [], 'KG', false)
    expect(failHold.reason).toBe('HARD')
    expect(failHold.sets[0].seconds).toBe(30)
  })

  it('failureAtTargetHoldsWithHardReason', () => {
    const r = nextPrefill(
      'WEIGHTED',
      weightedTarget(),
      [...sets(2, 12, 60, null, 'OK'), ...sets(1, 12, 60, null, 'FAILURE')],
      [],
      'KG',
      false,
    )
    expect(r.reason).toBe('HARD')
    expect(r.sets.map((s) => s.reps)).toEqual([12, 12, 12])
    expect(r.sets[0].weightKg!).toBeCloseTo(60, 2)

    // A11: setType === 'FAILURE' with blank effort reads as FAILURE in progressionInputs
    const item = { block: 'Main', sets: 3, repsMin: 8, repsMax: 12, seconds: null, weightKg: 60 }
    const recent = [
      setEntity({ setIndex: 0, reps: 12, weightKg: 60 }),
      setEntity({ setIndex: 1, reps: 12, weightKg: 60 }),
      setEntity({ setIndex: 2, reps: 12, weightKg: 60, effort: '', setType: 'FAILURE' }),
    ]
    const inputs = progressionInputs(item, 'bench_press', recent)
    expect(inputs.last.at(-1)?.effort).toBe('FAILURE')
  })

  it('missedRepsKeepMetSetsAndRestartTheMissedOneAtTheMinimum', () => {
    const last: PastSet[] = [12, 12, 6].map((reps) => ({ reps, seconds: null, weightKg: 60, effort: 'OK' }))
    const r = nextPrefill('WEIGHTED', weightedTarget(), last, [], 'KG', false)
    expect(r.reason).toBe('MISSED')
    expect(r.sets.map((s) => s.reps)).toEqual([12, 12, 8])
    expect(r.sets.map((s) => s.weightKg)).toEqual([60, 60, 60])
  })

  it('missedRepsUntaggedHoldsAtMinimumNotPlusOne', () => {
    const r = nextPrefill('WEIGHTED', weightedTarget(), sets(3, 6, 60, null, 'OK'), [], 'KG', false)
    expect(r.reason).toBe('MISSED')
    expect(r.sets.map((s) => s.reps)).toEqual([8, 8, 8])
    expect(r.sets[0].weightKg!).toBeCloseTo(60, 2)
  })

  it('missedAtFailureHoldsWithFatigue', () => {
    const r = nextPrefill(
      'WEIGHTED',
      weightedTarget(),
      [...sets(2, 8, 60, null, 'OK'), ...sets(1, 6, 60, null, 'FAILURE')],
      [],
      'KG',
      false,
    )
    expect(r.reason).toBe('FATIGUE')
    expect(r.sets.map((s) => s.reps)).toEqual([8, 8, 8])
    expect(r.sets[0].weightKg!).toBeCloseTo(60, 2)

    // Deload overrides FATIGUE per A6 precedence
    const deloaded = nextPrefill('WEIGHTED', weightedTarget(), sets(3, 6, 60, null, 'FAILURE'), [], 'KG', false, true)
    expect(deloaded.reason).toBe('SAME')
    expect(deloaded.sets).toHaveLength(2)
    expect(deloaded.sets[0].weightKg!).toBeCloseTo(60, 2)
  })

  it('missedAtFailureTwiceStepsDownBackOff', () => {
    const missedFail = [...sets(2, 8, 60, null, 'OK'), ...sets(1, 6, 60, null, 'FAILURE')]
    const r = nextPrefill('WEIGHTED', weightedTarget(), missedFail, missedFail, 'KG', false)
    expect(r.reason).toBe('BACK_OFF')
    expect(r.sets[0].weightKg!).toBeCloseTo(57.5, 2)
    expect(r.sets.map((s) => s.reps)).toEqual([8, 8, 8])

    // BACK_OFF takes precedence over deload per A6
    const deloadBackOff = nextPrefill('WEIGHTED', weightedTarget(), missedFail, missedFail, 'KG', false, true)
    expect(deloadBackOff.reason).toBe('BACK_OFF')
    expect(deloadBackOff.sets[0].weightKg!).toBeCloseTo(57.5, 2)
  })

  it('bodyweightMissedRepsHoldsAtRepsDone', () => {
    const r = nextPrefill(
      'REPS',
      bwTarget(),
      [
        { reps: 8, seconds: null, weightKg: null, effort: 'OK' },
        { reps: 7, seconds: null, weightKg: null, effort: 'OK' },
        { reps: 6, seconds: null, weightKg: null, effort: 'OK' },
      ],
      [],
      'KG',
      false,
    )
    expect(r.reason).toBe('MISSED')
    expect(r.sets.map((s) => s.reps)).toEqual([8, 7, 6])
  })

  it('bodyweightFailureHolds', () => {
    const r = nextPrefill('REPS', bwTarget(), sets(3, 10, null, null, 'FAILURE'), [], 'KG', false)
    expect(r.reason).toBe('HARD')
    expect(r.sets.map((s) => s.reps)).toEqual([10, 10, 10])
  })

  it('linearInPoundsStepsFiveAndTen', () => {
    const unit: WeightUnit = 'LB'
    const startKg = toKg(unit, 135)
    const upper = nextPrefill(
      'WEIGHTED',
      weightedTarget(3, 5, 5, startKg),
      sets(3, 5, startKg, null, 'HARD'),
      [],
      unit,
      false,
      false,
      'LINEAR',
      false,
    )
    expect(upper.reason).toBe('LOAD_UP')
    expect(fromKg(unit, upper.sets[0].weightKg!)).toBeCloseTo(140, 1)

    const lower = nextPrefill(
      'WEIGHTED',
      weightedTarget(3, 5, 5, startKg),
      sets(3, 5, startKg, null, 'OK'),
      [],
      unit,
      false,
      false,
      'LINEAR',
      true,
    )
    expect(lower.reason).toBe('LOAD_UP')
    expect(fromKg(unit, lower.sets[0].weightKg!)).toBeCloseTo(145, 1)
  })

  it('legacyOkChipShowsNothing', () => {
    expect(chipOf('OK')).toBe('')
    expect(chipOf('')).toBe('')
    expect(chipOf('HARD')).toBe('HARD')
    expect(chipOf('EASY')).toBe('EASY')
    expect(chipOf('FAILURE')).toBe('FAILURE')
  })

  it('rirMapping', () => {
    expect(effortRir('EASY')).toBe(3)
    expect(effortRir('OK')).toBe(2)
    expect(effortRir('HARD')).toBe(1)
    expect(effortRir('FAILURE')).toBe(0)
    expect(effortRir('PAIN')).toBe(2)
  })

  it('readyForHarderIgnoresHardButNotFailure', () => {
    const hard = sets(3, 15, null, null, 'HARD')
    const ok = sets(3, 15, null, null, 'OK')
    const fail = sets(3, 15, null, null, 'FAILURE')
    expect(readyForHarder(hard, ok)).toBe(true)
    expect(readyForHarder(fail, ok)).toBe(false)
  })

  it('debriefNextTimeMatchesLivePrefillInDeloadWeek', () => {
    const squatItem = {
      exerciseId: 'back_squat',
      block: 'Main',
      sets: 3,
      repsMin: 8,
      repsMax: 12,
      seconds: null,
      weightKg: 100,
      progressionMode: 'LINEAR',
    }
    const squatEx: ExerciseEntity = { id: 'back_squat', name: 'Back Squat', kind: 'WEIGHTED', primary: ['QUADS'], secondary: [] }
    const sessions = [{ id: 1, planDayId: 10, title: 'Legs', epochDay: 100, startedAt: 100_000, finishedAt: 100_001 }]
    const doneSets = [0, 1, 2].map((i) => setEntity({ sessionId: 1, exerciseId: 'back_squat', setIndex: i, reps: 12, weightKg: 100 }))
    const lib = new Map<string, ExerciseEntity>([['back_squat', squatEx]])
    const scores = scoresForSessions(sessions, doneSets, new Map([['back_squat', 'WEIGHTED' as const]]), new Map([[10, 3]]))

    // During a deload week: live prefill holds weight; debrief must NOT suggest LOAD_UP.
    const deloadDebrief = debriefForSession(
      sessions[0], sessions, doneSets, lib, scores.get(1)!, [squatItem], 3, null, 'KG', false, new Set(), true,
    )
    expect(deloadDebrief.next).toEqual([])

    // Outside deload, LINEAR lower-body steps +5 kg (105 kg), matching live prefill.
    const normalDebrief = debriefForSession(
      sessions[0], sessions, doneSets, lib, scores.get(1)!, [squatItem], 3, null, 'KG', false, new Set(), false,
    )
    expect(normalDebrief.next).toEqual([{ type: 'LOAD_UP', exerciseId: 'back_squat', weightKg: 105, reps: 8 }])
  })

  it('legacy blank and OK sets progress identically as clean sets', () => {
    const item = { block: 'Main', sets: 3, repsMin: 8, repsMax: 12, seconds: null, weightKg: 60 }
    const makeSets = (effort: string, reps: number) =>
      [0, 1, 2].map((i) => setEntity({ sessionId: 1, setIndex: i, reps, weightKg: 60, effort, note: '' }))

    const blankMid = progressionInputs(item, 'bench_press', makeSets('', 10))
    const okMid = progressionInputs(item, 'bench_press', makeSets('OK', 10))
    expect(okMid.last).toEqual(blankMid.last)
    expect(nextPrefill('WEIGHTED', blankMid.target, blankMid.last, [], 'KG', false).reason).toBe('MORE_REPS')
    expect(nextPrefill('WEIGHTED', okMid.target, okMid.last, [], 'KG', false)).toEqual(
      nextPrefill('WEIGHTED', blankMid.target, blankMid.last, [], 'KG', false),
    )

    const blankTop = progressionInputs(item, 'bench_press', makeSets('', 12))
    const okTop = progressionInputs(item, 'bench_press', makeSets('OK', 12))
    const blankRes = nextPrefill('WEIGHTED', blankTop.target, blankTop.last, [], 'KG', false)
    const okRes = nextPrefill('WEIGHTED', okTop.target, okTop.last, [], 'KG', false)
    expect(blankRes.reason).toBe('LOAD_UP')
    expect(blankRes.sets[0].weightKg!).toBeCloseTo(62.5, 2)
    expect(okRes).toEqual(blankRes)
  })
})
