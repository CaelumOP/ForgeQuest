import { describe, expect, it } from 'vitest'
import type { SetEffort } from './effort'
import { effortOf } from './effort'
import type { ExerciseKind } from './muscle'
import { nextPrefill, progressionInputs, type PastSet, type Target } from './progression'
import { fromKg, toKg, type WeightUnit } from './units'

const sets = (n: number, reps: number | null, kg: number | null, sec: number | null = null, effort: SetEffort = 'OK'): PastSet[] =>
  Array.from({ length: n }, () => ({ reps, seconds: sec, weightKg: kg, effort }))
const target = (sets: number, repsMin: number | null, repsMax: number | null, seconds: number | null, weightKg: number | null): Target =>
  ({ sets, repsMin, repsMax, seconds, weightKg })
const next = (kind: ExerciseKind, t: Target, last: PastSet[], previous: PastSet[] = [], unit: WeightUnit = 'KG', low = false) =>
  nextPrefill(kind, t, last, previous, unit, low, false)

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
    // a hard set in the previous session blocks it
    expect(next('WEIGHTED', t, sets(3, 8, 60), sets(3, 8, 60, null, 'HARD')).sets[0].weightKg!).toBeCloseTo(60, 2)
  })

  it('poundsStepInPoundsOnLoadableNumbers', () => {
    const r = next('WEIGHTED', target(3, 8, 12, null, null), sets(3, 12, toKg('LB', 135)), [], 'LB')
    expect(fromKg('LB', r.sets[0].weightKg!)).toBeCloseTo(140, 2)
  })

  it('hardPainAndLowReadinessHoldOrBackOff', () => {
    const t = target(3, 8, 12, null, 40)
    const hard = next('WEIGHTED', t, sets(3, 12, 40, null, 'HARD'))
    expect(hard.sets[0].weightKg!).toBeCloseTo(40, 2)
    expect(hard.sets[0].reps).toBe(12)
    const pain = next('WEIGHTED', t, sets(3, 10, 40, null, 'PAIN'))
    expect(pain.sets[0].weightKg!).toBeCloseTo(37.5, 2)
    const low = next('WEIGHTED', t, sets(3, 12, 40), [], 'KG', true)
    expect(low.sets[0].weightKg!).toBeCloseTo(40, 2)
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
  // Audit #9: a pain step-down to 0 kg used to lock the prefill at 0 kg for good.
  it('a 0 kg session after a pain step-down falls back to the plan weight', () => {
    const t = target(3, 8, 12, null, 2.5)
    expect(next('WEIGHTED', t, sets(3, 10, 2.5, null, 'PAIN')).sets[0].weightKg).toBe(0)
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
    const r = nextPrefill('WEIGHTED', null, sets(3, 12, 60), [], 'KG', false, false)
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
