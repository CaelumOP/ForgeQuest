import { describe, expect, it } from 'vitest'
import { EXERCISE_BY_ID, EXERCISE_CATALOG } from '../data/seed/exercise-catalog'
import { easierVariation, holdTarget, repsTarget, weightedTarget } from './baseline-targets'
import { presetEquipment } from './exercise-swap-engine'

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

  it('finds an easier variation for a real catalog bodyweight push move', () => {
    const pushUp = EXERCISE_BY_ID.get('push_up')!
    const easier = easierVariation(pushUp, EXERCISE_CATALOG, presetEquipment('BODYWEIGHT_ONLY'))
    expect(easier?.id).toBe('knee_push_up')
  })
})
