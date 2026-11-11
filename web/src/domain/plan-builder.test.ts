// Same cases as Android PlanBuilderTest.kt.
import { describe, expect, it } from 'vitest'
import { EXERCISE_CATALOG, STRETCH_IDS } from '../data/seed/exercise-catalog'
import type { ExerciseEntity, PlanDraft } from './entities'
import { EQUIPMENT_PRESETS, presetEquipment, profileFor, type Equipment, type MovementPattern } from './exercise-swap-engine'
import { tierOf } from './exercise-tiers'
import type { Muscle } from './muscle'
import {
  blocked, COOL_DOWN_BLOCK, estimateMinutes, INJURIES, PlanBuilder, spreadDays,
  type BuiltPlan, type Injury, type PlanInput, type TrainGoal,
} from './plan-builder'

const library: ExerciseEntity[] = EXERCISE_CATALOG
const byId = new Map(library.map((e) => [e.id, e]))
const fullGym = presetEquipment('FULL_GYM')

function input(
  days = 4,
  equipment: Set<Equipment> = fullGym,
  goal: TrainGoal = 'MUSCLE',
  minutes = 60,
  overrides: Partial<PlanInput> = {},
): PlanInput {
  return {
    goal,
    days: spreadDays(days),
    minutes,
    equipment,
    ...overrides,
  }
}

const build = (i: PlanInput): BuiltPlan => PlanBuilder.build(i, library)

const exercisesOf = (draft: PlanDraft): ExerciseEntity[] =>
  draft.days.flatMap((d) => d.items.map((it) => byId.get(it.exerciseId)!))

describe('PlanBuilderTest', () => {
  it('every output id exists in the catalog', () => {
    for (const preset of EQUIPMENT_PRESETS) {
      for (let n = 2; n <= 6; n++) {
        const ids = build(input(n, presetEquipment(preset))).draft.days.flatMap((d) => d.items.map((i) => i.exerciseId))
        expect(ids.length).toBeGreaterThan(0)
        expect(ids.every((id) => byId.has(id)), `${preset}/${n} has unknown ids`).toBe(true)
      }
    }
  })

  it('training day count equals input days', () => {
    for (let n = 1; n <= 7; n++) {
      const plan = build(input(n))
      const trained = new Set(plan.draft.days.filter((d) => !d.isRest && d.items.length > 0).map((d) => d.dayOfWeek))
      expect(trained, `days for n=${n}`).toEqual(spreadDays(n))
      expect(new Set(plan.days.keys())).toEqual(trained)
    }
  })

  it('no empty training day for any preset and day count', () => {
    for (const preset of EQUIPMENT_PRESETS) {
      for (let n = 2; n <= 6; n++) {
        const plan = build(input(n, presetEquipment(preset)))
        expect(plan.draft.days.length, `${preset}/${n}`).toBe(n)
        expect(plan.draft.days.every((d) => d.items.length > 0), `${preset}/${n}`).toBe(true)
      }
    }
  })

  it('no blocked exercise for any single injury or all together', () => {
    const sets: Set<Injury>[] = [...INJURIES.map((i) => new Set<Injury>([i])), new Set<Injury>(INJURIES)]
    for (const injuries of sets) {
      for (const preset of ['FULL_GYM', 'CALISTHENICS_PARK', 'BODYWEIGHT_ONLY'] as const) {
        const plan = build(input(5, presetEquipment(preset), 'MUSCLE', 60, { injuries }))
        for (const ex of exercisesOf(plan.draft)) {
          expect(blocked(ex, profileFor(ex), injuries), `${ex.id} blocked for ${[...injuries]}`).toBe(false)
        }
      }
    }
    // Spot checks that the rules block what the brief names and keep what it protects.
    const knee = exercisesOf(build(input(5, fullGym, 'MUSCLE', 60, { injuries: new Set<Injury>(['KNEE']) })).draft).map((e) => e.id)
    expect(knee.some((id) => id === 'pistol_squat' || id === 'burpee' || id === 'jumping_jacks')).toBe(false)
    const wrist = exercisesOf(
      build(input(5, presetEquipment('CALISTHENICS_PARK'), 'MUSCLE', 60, { injuries: new Set<Injury>(['WRIST']) })).draft,
    ).map((e) => e.id)
    expect(wrist.some((id) => id.includes('push_up') || id.includes('planche') || id.includes('handstand'))).toBe(false)
  })

  it('avoid ids are honoured', () => {
    const avoid = new Set(['push_up', 'squat', 'pull_up', 'plank', 'back_squat', 'bench_press', 'deadlift'])
    for (const preset of ['FULL_GYM', 'BODYWEIGHT_ONLY', 'CALISTHENICS_PARK'] as const) {
      const ids = exercisesOf(build(input(4, presetEquipment(preset), 'MUSCLE', 60, { avoidIds: avoid })).draft).map((e) => e.id)
      expect(ids.some((id) => avoid.has(id))).toBe(false)
      expect(ids.length).toBeGreaterThan(0)
    }
  })

  it('holds off gives no hold items when anything else fits', () => {
    for (const preset of ['FULL_GYM', 'HOME_GYM', 'DUMBBELLS_AND_BAR'] as const) {
      const plan = build(input(5, presetEquipment(preset), 'MUSCLE', 60, { holdsOk: false }))
      expect(exercisesOf(plan.draft).some((e) => e.kind === 'HOLD'), preset).toBe(false)
    }
    // Documented fallback: a core slot may still use a hold when it has no other option.
    const bodyweight = build(input(3, presetEquipment('BODYWEIGHT_ONLY'), 'MUSCLE', 60, { holdsOk: false }))
    const holds = exercisesOf(bodyweight.draft).filter((e) => e.kind === 'HOLD')
    const allowedFallback = new Set<MovementPattern>(['HIP_HINGE', 'CORE_ANTI_EXTENSION', 'CORE_FLEXION_ROTATION'])
    expect(holds.every((e) => allowedFallback.has(profileFor(e).pattern))).toBe(true)
  })

  it('zero pull-ups gives no harder vertical pull', () => {
    for (const preset of ['FULL_GYM', 'CALISTHENICS_PARK', 'PULL_UP_BAR_ONLY'] as const) {
      const plan = build(input(5, presetEquipment(preset), 'MUSCLE', 60, { pullUpMax: 0, experience: 'SEASONED' }))
      const pulls = exercisesOf(plan.draft).filter((e) => profileFor(e).pattern === 'VERTICAL_PULL')
      expect(pulls.length, `${preset} has no vertical pull`).toBeGreaterThan(0)
      expect(pulls.every((e) => tierOf(e) === 'FOUNDATION'), `${preset}: ${pulls.map((e) => e.id)}`).toBe(true)
    }
  })

  it('estimated minutes stay within five of the budget', () => {
    for (const preset of EQUIPMENT_PRESETS) {
      for (const minutes of [20, 30, 45, 60, 90]) {
        for (const n of [2, 4, 6]) {
          const plan = build(input(n, presetEquipment(preset), 'STRENGTH', minutes, { coolDown: true, cardio: 'FINISHER' }))
          for (const day of plan.draft.days) {
            const est = estimateMinutes(day)
            expect(est, `${preset} ${minutes}min n=${n} day ${day.dayOfWeek}: ${est}`).toBeLessThanOrEqual(minutes + 5)
          }
        }
      }
    }
    const perDay = build(input(3, fullGym, 'MUSCLE', 60, { minutesByDay: new Map([[1, 25], [3, 40]]) }))
    expect(estimateMinutes(perDay.draft.days.find((d) => d.dayOfWeek === 1)!)).toBeLessThanOrEqual(30)
    expect(estimateMinutes(perDay.draft.days.find((d) => d.dayOfWeek === 3)!)).toBeLessThanOrEqual(45)
  })

  it('same input twice gives equal result', () => {
    const i = input(5, fullGym, 'MUSCLE', 60, {
      focus: new Set<Muscle>(['BICEPS', 'GLUTES']),
      injuries: new Set<Injury>(['KNEE']),
      cardio: 'OWN_DAY',
    })
    expect(build(i)).toEqual(build(i))
    expect(build(i)).toEqual(PlanBuilder.build(i, [...library].reverse()))
  })

  it('strength and endurance use different rep ranges', () => {
    const ranges = (goal: TrainGoal) =>
      new Set(
        build(input(3, fullGym, goal)).draft.days
          .flatMap((d) => d.items)
          .filter((it) => it.kind === 'WEIGHTED')
          .map((it) => `${it.repsMin}:${it.repsMax}`),
      )
    const strength = ranges('STRENGTH')
    const endurance = ranges('ENDURANCE')
    expect(strength).toEqual(new Set(['4:6']))
    expect(endurance).toEqual(new Set(['15:20']))
    expect(strength).not.toEqual(endurance)
    const rest = (goal: TrainGoal) =>
      build(input(3, fullGym, goal)).draft.days[0].items.find((it) => it.block === 'Main')!.restSeconds
    expect(rest('STRENGTH')).toBeGreaterThan(rest('ENDURANCE'))
  })

  it('bodyweight rep moves never go under six reps', () => {
    const plan = build(input(4, presetEquipment('BODYWEIGHT_ONLY'), 'STRENGTH'))
    const reps = plan.draft.days.flatMap((d) => d.items).filter((it) => it.kind === 'REPS')
    expect(reps.length).toBeGreaterThan(0)
    expect(reps.every((it) => (it.repsMin ?? 6) >= 6)).toBe(true)
  })

  it('focus muscle gets more weekly sets', () => {
    const weeklySets = (plan: BuiltPlan, m: Muscle) =>
      plan.draft.days
        .flatMap((d) => d.items)
        .filter((it) => byId.get(it.exerciseId)!.primary.includes(m))
        .reduce((sum, it) => sum + it.sets, 0)
    for (const m of ['BICEPS', 'SHOULDERS', 'GLUTES', 'CHEST', 'CALVES'] as const) {
      const without = weeklySets(build(input(4)), m)
      const withFocus = weeklySets(build(input(4, fullGym, 'MUSCLE', 60, { focus: new Set<Muscle>([m]) })), m)
      expect(withFocus, `${m}: ${withFocus} vs ${without}`).toBeGreaterThan(without)
    }
  })

  it('stretches only appear in the cool-down block', () => {
    for (const preset of EQUIPMENT_PRESETS) {
      for (let n = 2; n <= 6; n++) {
        const plan = build(input(n, presetEquipment(preset), 'MUSCLE', 60, { coolDown: true }))
        const items = plan.draft.days.flatMap((d) => d.items)
        expect(items.some((it) => it.block === COOL_DOWN_BLOCK), `${preset}/${n} has no cool-down`).toBe(true)
        for (const item of items) {
          expect(STRETCH_IDS.has(item.exerciseId), `${item.exerciseId} in ${item.block}`).toBe(item.block === COOL_DOWN_BLOCK)
        }
        for (const day of plan.draft.days.filter((d) => d.items.some((it) => it.block === COOL_DOWN_BLOCK))) {
          expect(day.items.filter((it) => it.block === COOL_DOWN_BLOCK).length).toBe(2)
        }
      }
    }
    const off = build(input(4)).draft.days.flatMap((d) => d.items)
    expect(off.some((it) => STRETCH_IDS.has(it.exerciseId))).toBe(false)
  })

  it('offset zero matches default and offset one rotates', () => {
    const base = build(input())
    expect(build(input(4, fullGym, 'MUSCLE', 60, { offset: 0 }))).toEqual(base)
    expect(exercisesOf(build(input(4, fullGym, 'MUSCLE', 60, { offset: 1 })).draft).map((e) => e.id)).not.toEqual(
      exercisesOf(base.draft).map((e) => e.id),
    )
  })
})
