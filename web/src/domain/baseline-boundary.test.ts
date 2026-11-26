// Table-driven boundary suite. Same cases and expected values as Android BaselineBoundaryTest.kt.
import { describe, expect, it } from 'vitest'
import { EXERCISE_CATALOG } from '../data/seed/exercise-catalog'
import type { ExerciseRow } from '../data/db'
import { computeTargetDisplay } from '../ui/baseline/helpers'
import {
  clampHoldInput, clampRepsInput, clampWeightKgInput, clampWeightedRepsInput, easierVariation, formatHoldSeconds,
  harderVariation, holdTarget, isOutgrown, isTooHard, repsTarget, weightedTarget,
} from './baseline-targets'
import { EQUIPMENT_PRESETS, PRESET_INFO, expandLegacyEquipment, profileFits, profileFor, type Equipment } from './exercise-swap-engine'
import { TIERS, tierOf } from './exercise-tiers'

const catalog: ExerciseRow[] = EXERCISE_CATALOG.map((e) => ({ ...e, videoUrl: e.videoUrl ?? null, custom: false }))
const ex = (id: string) => catalog.find((e) => e.id === id)!

describe('baseline boundaries: reps', () => {
  // [reps, target, tooHard, outgrown]
  const cases: [number, number | null, boolean, boolean][] = [
    [0, null, false, false],
    [1, 1, true, false],
    [2, 1, true, false],
    [3, 2, true, false],
    [4, 2, false, false],
    [10, 7, false, false],
    [24, 16, false, false],
    [25, 17, false, true],
    [29, 20, false, true],
    [51, 20, false, true],
    [200, 20, false, true],
  ]
  it.each(cases)('%i reps', (reps, target, tooHard, outgrown) => {
    expect(repsTarget(reps)).toBe(target)
    expect(isTooHard('REPS', reps)).toBe(tooHard)
    expect(isOutgrown('REPS', reps)).toBe(outgrown)
  })
  it('clamps typed reps to 200', () => {
    expect(clampRepsInput(201)).toBe(200)
    expect(clampRepsInput(-4)).toBe(0)
  })
})

describe('baseline boundaries: holds', () => {
  // [seconds, target, tooHard, outgrown]
  const cases: [number, number | null, boolean, boolean][] = [
    [0, null, false, false],
    [3, 3, true, false],
    [10, 5, true, false],
    [14, 5, true, false],
    [15, 10, false, false],
    [45, 30, false, false],
    [89, 60, false, false],
    [90, 60, false, true],
    [825, 60, false, true],
  ]
  it.each(cases)('%i s', (seconds, target, tooHard, outgrown) => {
    expect(holdTarget(seconds)).toBe(target)
    expect(isTooHard('HOLD', seconds)).toBe(tooHard)
    expect(isOutgrown('HOLD', seconds)).toBe(outgrown)
  })
  it('clamps typed holds to 600', () => {
    expect(clampHoldInput(601)).toBe(600)
  })
  const fmt: [number, string][] = [[0, '0 s'], [45, '45 s'], [59, '59 s'], [60, '1:00'], [61, '1:01'], [575, '9:35'], [825, '13:45']]
  it.each(fmt)('formats %i s as %s', (seconds, text) => {
    expect(formatHoldSeconds(seconds, 's')).toBe(text)
  })
  it('never flags weighted moves', () => {
    expect(isOutgrown('WEIGHTED', 500)).toBe(false)
    expect(isTooHard('WEIGHTED', 1)).toBe(false)
  })
})

describe('baseline boundaries: weighted', () => {
  it('maps weight and reps to a result', () => {
    expect(weightedTarget(0, 8, 8, 2.5)).toEqual({ kind: 'NONE' })
    expect(weightedTarget(60, 0, 8, 2.5)).toEqual({ kind: 'NONE' })
    expect(weightedTarget(60, 1, 8, 2.5)).toEqual({ kind: 'TOO_HEAVY' })
    expect(weightedTarget(60, 2, 8, 2.5)).toEqual({ kind: 'TOO_HEAVY' })
    expect(weightedTarget(60, 8, 8, 2.5)).toEqual({ kind: 'TARGET', kg: 50 })
    expect(weightedTarget(20, 13, 8, 2.5)).toEqual({ kind: 'TOO_LIGHT' })
    expect(weightedTarget(20, 16, 8, 2.5)).toEqual({ kind: 'TOO_LIGHT' })
  })
  it('clamps typed weight and reps', () => {
    expect(clampWeightKgInput(9999)).toBe(500)
    expect(clampWeightedRepsInput(300)).toBe(100)
  })
})

describe('baseline boundaries: rung table', () => {
  const gears: [string, Set<Equipment>][] = [
    ...EQUIPMENT_PRESETS.map((p) => [p, new Set<Equipment>(PRESET_INFO[p].equipment)] as [string, Set<Equipment>]),
    ['BODYWEIGHT', new Set<Equipment>(['BODYWEIGHT'])],
    ['DUMBBELLS_ONLY', new Set<Equipment>(['BODYWEIGHT', 'DUMBBELLS'])],
    ['DUMBBELLS_BENCH', new Set<Equipment>(['BODYWEIGHT', 'DUMBBELLS', 'FLAT_BENCH'])],
  ]
  const allowedFor = (gear: Set<Equipment>) => {
    const a = expandLegacyEquipment(gear)
    a.add('BODYWEIGHT')
    return a
  }
  // move -> [easier id, harder id], the same on every gear set the move fits
  const rungs: Record<string, [string | null, string | null]> = {
    push_up: ['knee_push_up', 'diamond_push_up'],
    pull_up: ['negative_pull_up', 'archer_pull_up'],
    australian_pull_up: ['scapular_australian_pull_up', 'pull_up'],
    squat: ['half_squat', 'jump_squat'],
    hip_bridge: [null, 'single_leg_hip_thrust'],
    plank: ['incline_hollow_plank', 'hollow_hold'],
  }
  const fitCount: Record<string, number> = { push_up: 10, pull_up: 5, australian_pull_up: 4, squat: 10, hip_bridge: 10, plank: 10 }

  for (const [move, [easier, harder]] of Object.entries(rungs)) {
    it(`${move}: ${easier} below, ${harder} above, on every gear it fits`, () => {
      const src = ex(move)
      let cells = 0
      for (const [, gear] of gears) {
        const allowed = allowedFor(gear)
        if (!profileFits(profileFor(src), allowed)) continue
        cells++
        const lo = easierVariation(src, catalog, gear)
        const hi = harderVariation(src, catalog, gear)
        expect(lo?.id ?? null).toBe(easier)
        expect(hi?.id ?? null).toBe(harder)
        for (const rung of [lo, hi]) {
          if (!rung) continue
          expect(profileFits(profileFor(rung), allowed)).toBe(true)
          expect(rung.kind).toBe(src.kind)
          const gap = Math.abs(TIERS.indexOf(tierOf(rung)!) - TIERS.indexOf(tierOf(src)!))
          expect(gap).toBeLessThanOrEqual(1)
        }
      }
      expect(cells).toBe(fitCount[move])
    })
  }

  const staples = ['db_bench_press', 'db_row', 'goblet_squat', 'db_romanian_deadlift', 'bench_press', 'lat_pulldown', 'barbell_row', 'back_squat', 'romanian_deadlift']
  it.each(staples)('%s has no harder move; any easier move fits the gear', (id) => {
    const src = ex(id)
    for (const [, gear] of gears) {
      const allowed = allowedFor(gear)
      if (!profileFits(profileFor(src), allowed)) continue
      expect(harderVariation(src, catalog, gear)).toBeNull()
      const lo = easierVariation(src, catalog, gear)
      if (lo) expect(profileFits(profileFor(lo), allowed)).toBe(true)
    }
  })
})

describe('baseline boundaries: display', () => {
  const noGear = new Set<Equipment>(['BODYWEIGHT'])
  const show = (id: string, test: { reps?: number; seconds?: number }) =>
    computeTargetDisplay(ex(id), { reps: test.reps ?? null, seconds: test.seconds ?? null, weightKg: null }, catalog, noGear, 'KG')
  it('825 s plank: 60 s target, hollow hold, no easier', () => {
    expect(show('plank', { seconds: 825 })).toMatchObject({ kind: 'HOLD', seconds: 60, harderExercise: { id: 'hollow_hold' }, easierExercise: null })
  })
  it('3 s plank: 3 s target, easier version', () => {
    expect(show('plank', { seconds: 3 })).toMatchObject({ kind: 'HOLD', seconds: 3, harderExercise: null, easierExercise: { id: 'incline_hollow_plank' } })
  })
  it('2 push-ups: 1 rep, knee push-up', () => {
    expect(show('push_up', { reps: 2 })).toMatchObject({ kind: 'REPS', reps: 1, harderExercise: null, easierExercise: { id: 'knee_push_up' } })
  })
  it('51 squats: 20 reps, jump squat', () => {
    expect(show('squat', { reps: 51 })).toMatchObject({ kind: 'REPS', reps: 20, harderExercise: { id: 'jump_squat' }, easierExercise: null })
  })
  it('0 s plank: too short, easier plank', () => {
    expect(show('plank', { seconds: 0 })).toMatchObject({ kind: 'HOLD_TOO_SHORT', easierExercise: { id: 'incline_hollow_plank' } })
  })
  it('2 reps at 60 kg is too heavy', () => {
    expect(computeTargetDisplay(ex('db_bench_press'), { reps: 2, seconds: null, weightKg: 60 }, catalog, new Set(['DUMBBELLS', 'FLAT_BENCH'] as Equipment[]), 'KG', 8)).toEqual({ kind: 'TOO_HEAVY' })
  })
})
