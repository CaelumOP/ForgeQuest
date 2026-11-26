// Same cases as Android ExerciseTiersTest.kt.
import { describe, expect, it } from 'vitest'
import { EXERCISE_CATALOG } from '../data/seed/exercise-catalog'
import type { SetEffort } from './effort'
import { EQUIPMENT, presetEquipment, type Equipment } from './exercise-swap-engine'
import { easierVariation } from './baseline-targets'
import { harderHoldVersion, harderVersion, readyForHarder, tierOf } from './exercise-tiers'
import { listSize, muscleExercises, replaceOrder } from './muscle-exercises'
import type { PastSet } from './progression'

const all = EXERCISE_CATALOG
const ex = (id: string) => all.find((e) => e.id === id)!
const bodyweight = presetEquipment('BODYWEIGHT_ONLY')
const gym = new Set<Equipment>(EQUIPMENT)

describe('exercise tiers', () => {
  it('rates all 866: 533 Foundation, 266 Intermediate, 67 Advanced; custom has none', () => {
    expect(all.length).toBe(866)
    const count = (t: string) => all.filter((e) => tierOf(e) === t).length
    expect([count('FOUNDATION'), count('INTERMEDIATE'), count('ADVANCED')]).toEqual([533, 266, 67])
    expect(tierOf({ ...ex('push_up'), id: 'my_push_up', custom: true })).toBeNull()
  })

  it('climbs the ladder within the equipment', () => {
    const next = (id: string, eq: Set<Equipment> = gym) => harderVersion(ex(id), all, eq)?.id ?? null
    expect(next('wide_knee_push_up', bodyweight)).toBe('push_up')
    expect(next('half_squat', bodyweight)).toBe('squat')
    expect(next('squat', bodyweight)).toBe('jump_squat')
    expect(next('lunge')).toBe('reverse_lunge')
    expect(next('australian_chin_up')).toBe('australian_pull_up')
    expect(next('negative_pull_up')).toBe('pull_up')
    expect(next('incline_diamond_knee_push_up', bodyweight)).toBe('body_up')
    expect(next('calf_raise', bodyweight)).toBeNull()
    expect(next('lunge', bodyweight)).toBe('reverse_lunge')
    expect(next('bench_press')).toBeNull()
  })

  it('named rungs step up within one tier', () => {
    const next = (id: string, eq: Set<Equipment> = gym) => harderVersion(ex(id), all, eq)?.id ?? null
    expect(next('push_up')).toBe('diamond_push_up')
    expect(next('push_up', bodyweight)).toBe('diamond_push_up')
    expect(next('knee_push_up', bodyweight)).toBe('push_up')
    expect(next('pull_up')).toBe('archer_pull_up')
    expect(next('chin_up')).toBe('archer_pull_up')
    expect(next('australian_pull_up')).toBe('pull_up')
    expect(next('squat')).toBe('jump_squat')
  })

  it('hold ladder has harder and easier rungs', () => {
    const nextHold = (id: string, eq: Set<Equipment> = gym) => harderHoldVersion(ex(id), all, eq)?.id ?? null
    expect(nextHold('plank', bodyweight)).toBe('hollow_hold')
    expect(nextHold('hollow_hold')).toBe('l_sit')
    expect(nextHold('side_plank')).toBe('copenhagen_plank_short')
    expect(nextHold('push_up')).toBeNull()
    expect(easierVariation(ex('plank'), all, bodyweight)?.id).toBe('incline_hollow_plank')
    expect(easierVariation(ex('hollow_hold'), all, bodyweight)?.id).toBe('plank')
    expect(easierVariation(ex('l_sit'), all, bodyweight)?.id).toBe('hollow_hold')
  })

  it('is ready after two clean sessions at 15+', () => {
    const s = (reps: number[], effort: SetEffort = 'OK'): PastSet[] => reps.map((r) => ({ reps: r, seconds: null, weightKg: null, effort }))
    expect(readyForHarder(s([20, 18, 15]), s([16, 15, 15]))).toBe(true)
    expect(readyForHarder(s([20, 18, 14]), s([16, 15, 15]))).toBe(false)
    expect(readyForHarder(s([20, 18, 15]), [])).toBe(false)
    expect(readyForHarder(s([20, 18, 15], 'HARD'), s([16, 15, 15]))).toBe(true)
    expect(readyForHarder(s([20, 18, 15], 'FAILURE'), s([16, 15, 15]))).toBe(false)
    expect(readyForHarder(s([20, 18, 15], 'PAIN'), s([16, 15, 15]))).toBe(false)
    // S7: topReps sets ready threshold to min(15, topReps + 2), so 8-12 done 14 x 3 twice is ready.
    expect(readyForHarder(s([14, 14, 14]), s([14, 14, 14]), 12)).toBe(true)
    expect(readyForHarder(s([14, 14, 13]), s([14, 14, 14]), 12)).toBe(false)
    expect(readyForHarder(s([15, 15, 15]), s([15, 15, 15]), 16)).toBe(true)
    expect(readyForHarder(s([14, 14, 14]), s([14, 14, 14]), 16)).toBe(false)
  })
})

describe('muscle exercises', () => {
  it('lists the plan first, then main muscle and Foundation first, filtered to the equipment', () => {
    const chest = muscleExercises('CHEST', all, new Set(['knee_push_up']), bodyweight)
    expect(chest.inPlan.map((r) => r.exercise.id)).toEqual(['knee_push_up'])
    expect(chest.others.some((r) => r.exercise.id === 'bench_press')).toBe(false)
    expect(chest.hidden).toBeGreaterThan(0)
    const firstSecondary = chest.others.findIndex((r) => !r.main)
    expect(firstSecondary === -1 || chest.others.slice(firstSecondary).every((r) => !r.main)).toBe(true)
    expect(chest.others[0].tier).toBe('FOUNDATION')
    expect(muscleExercises('CHEST', all, new Set(), bodyweight, true).others.some((r) => r.exercise.id === 'bench_press')).toBe(true)
    const obliques = muscleExercises('OBLIQUES', all, new Set(), gym)
    expect(listSize(obliques)).toBeGreaterThanOrEqual(5)
    expect(obliques.others.some((r) => r.exercise.id === 'crunch')).toBe(true)
  })

  it('replace order puts the same movement first', () => {
    const order = replaceOrder(ex('push_up'), [ex('squat'), ex('knee_push_up'), ex('push_up')])
    expect(order.map((r) => r[0].id)).toEqual(['knee_push_up', 'squat'])
    expect(order.map((r) => r[1])).toEqual([true, false])
  })
})
