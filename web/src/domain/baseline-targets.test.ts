import { describe, expect, it } from 'vitest'
import { EXERCISE_BY_ID, EXERCISE_CATALOG } from '../data/seed/exercise-catalog'
import {
  easierVariation, harderVariation, holdTarget, isOutgrown, OUTGROWN_REPS, repsTarget, weightedTarget,
} from './baseline-targets'
import { ALL_ACTIVE_EQUIPMENT, profileFits, profileFor, type Equipment } from './exercise-swap-engine'

describe('baseline targets', () => {
  it('computes bodyweight reps targets', () => {
    expect(repsTarget(0)).toBeNull()
    expect(repsTarget(1)).toBe(1)
    expect(repsTarget(10)).toBe(7)
    expect(repsTarget(14)).toBe(9)
  })

  it('computes hold targets snapped to 5 s', () => {
    expect(holdTarget(0)).toBeNull()
    expect(holdTarget(6)).toBe(5)
    expect(holdTarget(30)).toBe(20)
    expect(holdTarget(60)).toBe(40)
  })

  it('computes weighted targets with submax e1RM and step snapping', () => {
    expect(weightedTarget(60, 8, 8, 2.5)).toEqual({ kind: 'TARGET', kg: 50 })
    expect(weightedTarget(20, 13, 8, 2.5)).toEqual({ kind: 'TOO_LIGHT' })
    expect(weightedTarget(10, 10, 12, 2.5)).toEqual({ kind: 'TARGET', kg: 7.5 })
    expect(weightedTarget(4, 5, 10, 2.5)).toEqual({ kind: 'TARGET', kg: 2.5 })
    expect(weightedTarget(2, 5, 10, 2.5)).toEqual({ kind: 'NONE' })
  })

  it('easierVariation follows tier line and equipment (Android BaselineTargetsTest parity)', () => {
    const pushUp = EXERCISE_BY_ID.get('push_up')!
    const kneePushUp = EXERCISE_BY_ID.get('knee_push_up')!
    const kneeScapular = EXERCISE_BY_ID.get('knee_scapular_push_up')!
    const pullUp = EXERCISE_BY_ID.get('pull_up')!
    const pistolSquat = EXERCISE_BY_ID.get('pistol_squat')!
    const bodyweight = new Set<Equipment>(['BODYWEIGHT'])
    const pullUpBar = new Set<Equipment>(['PULL_UP_BAR'])
    const allActive = new Set<Equipment>(ALL_ACTIVE_EQUIPMENT)

    expect(easierVariation(pushUp, EXERCISE_CATALOG, bodyweight)?.id).toBe('knee_push_up')
    expect(easierVariation(kneePushUp, EXERCISE_CATALOG, bodyweight)?.id).toBe('knee_scapular_push_up')
    expect(easierVariation(kneeScapular, EXERCISE_CATALOG, bodyweight)).toBeNull()

    expect(easierVariation(pullUp, EXERCISE_CATALOG, pullUpBar)?.id).toBe('negative_pull_up')
    expect(easierVariation(pullUp, EXERCISE_CATALOG, bodyweight)?.id).toBe('towel_floor_pull')

    expect(easierVariation(pistolSquat, EXERCISE_CATALOG, bodyweight)?.id).toBe('lunge')
    expect(easierVariation(pistolSquat, EXERCISE_CATALOG, allActive)?.id).toBe('bulgarian_split_squat')
  })

  it('harderVariation for squat with no gear is a move that fits no gear', () => {
    const squat = EXERCISE_BY_ID.get('squat')!
    const noGear = new Set<Equipment>(['BODYWEIGHT'])
    const harder = harderVariation(squat, EXERCISE_CATALOG, noGear)
    expect(harder).not.toBeNull()
    expect(harder?.id).toBe('jump_squat')
    expect(profileFits(profileFor(harder!), noGear)).toBe(true)
  })

  it('outgrown rule: reps kind at 25 or more, never weighted or hold', () => {
    expect(OUTGROWN_REPS).toBe(25)
    expect(isOutgrown('REPS', 51)).toBe(true)
    expect(isOutgrown('REPS', 25)).toBe(true)
    expect(isOutgrown('REPS', 24)).toBe(false)
    expect(isOutgrown('REPS', 0)).toBe(false)
    expect(isOutgrown('WEIGHTED', 51)).toBe(false)
    expect(isOutgrown('HOLD', 99)).toBe(false)
  })
})
