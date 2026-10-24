// Same cases as Android ExerciseTiersTest.kt.
import { describe, expect, it } from 'vitest'
import { EXERCISE_CATALOG } from '../data/seed/exercise-catalog'
import type { SetEffort } from './effort'
import { EQUIPMENT, presetEquipment, type Equipment } from './exercise-swap-engine'
import { harderVersion, readyForHarder, tierOf } from './exercise-tiers'
import { listSize, muscleExercises, planSwapTarget } from './muscle-exercises'
import type { PastSet } from './progression'

const all = EXERCISE_CATALOG
const ex = (id: string) => all.find((e) => e.id === id)!
const bodyweight = presetEquipment('BODYWEIGHT_ONLY')
const gym = new Set<Equipment>(EQUIPMENT)

describe('exercise tiers', () => {
  it('rates all 90: 52 Foundation, 22 Intermediate, 16 Advanced; custom has none', () => {
    expect(all.length).toBe(90)
    const count = (t: string) => all.filter((e) => tierOf(e) === t).length
    expect([count('FOUNDATION'), count('INTERMEDIATE'), count('ADVANCED')]).toEqual([52, 22, 16])
    expect(tierOf({ ...ex('push_up'), id: 'my_push_up', custom: true })).toBeNull()
  })

  it('climbs the ladder within the equipment', () => {
    const next = (id: string, eq: Set<Equipment> = gym) => harderVersion(ex(id), all, eq)?.id ?? null
    expect(next('wide_knee_push_up', bodyweight)).toBe('push_up')
    expect(next('half_squat', bodyweight)).toBe('squat')
    expect(next('squat', bodyweight)).toBe('lunge')
    expect(next('lunge')).toBe('bulgarian_split_squat')
    expect(next('australian_chin_up')).toBe('negative_pull_up')
    expect(next('negative_pull_up')).toBe('pull_up')
    expect(next('incline_diamond_knee_push_up', bodyweight)).toBe('diamond_push_up')
    expect(next('calf_raise', bodyweight)).toBeNull()
    expect(next('lunge', bodyweight)).toBeNull()
    expect(next('bench_press')).toBeNull()
  })

  it('is ready after two clean sessions at 15+', () => {
    const s = (reps: number[], effort: SetEffort = 'OK'): PastSet[] => reps.map((r) => ({ reps: r, seconds: null, weightKg: null, effort }))
    expect(readyForHarder(s([20, 18, 15]), s([16, 15, 15]))).toBe(true)
    expect(readyForHarder(s([20, 18, 14]), s([16, 15, 15]))).toBe(false)
    expect(readyForHarder(s([20, 18, 15]), [])).toBe(false)
    expect(readyForHarder(s([20, 18, 15], 'HARD'), s([16, 15, 15]))).toBe(false)
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

  it('offers a plan swap only with exactly one same-movement exercise', () => {
    expect(planSwapTarget(ex('push_up'), [ex('knee_push_up'), ex('squat')])?.id).toBe('knee_push_up')
    expect(planSwapTarget(ex('push_up'), [ex('knee_push_up'), ex('wide_knee_push_up')])).toBeNull()
    expect(planSwapTarget(ex('push_up'), [ex('push_up')])).toBeNull()
  })
})
