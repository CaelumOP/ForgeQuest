import { isExtraBlock } from './pr-picker'
// Same cases as Android PlanBuilderTest.kt.
import { describe, expect, it } from 'vitest'
import { EXERCISE_CATALOG, STRETCH_IDS } from '../data/seed/exercise-catalog'
import type { ExerciseEntity, PlanDraft } from './entities'
import { EQUIPMENT_PRESETS, presetEquipment, profileFits, profileFor, type Equipment, type MovementPattern } from './exercise-swap-engine'
import { tierOf } from './exercise-tiers'
import type { Muscle } from './muscle'
import {
  blocked, CIRCUIT_BLOCKS, CIRCUIT_POST_ROUND, CIRCUIT_POST_ROUND_SHORT, COOL_DOWN_BLOCK, estimateMinutes,
  FINISHER_BLOCK, fitsItsMovement, INJURIES, isCompound, isCompoundExercise, isCore, isFly, NO_PAIR_IDS,
  PlanBuilder, safePair, spreadDays, STAPLE_IDS, TRAIN_GOALS, SUPERSET_POST_ROUND_SHORT,
  type BuiltPlan, type Injury, type PlanInput, type TrainGoal,
} from './plan-builder'
import { audit, fromDraft, OVERLAP_SETS } from './routine-auditor'

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
  it('known low maxima and selected gear constrain every slot', () => {
    let sawIncline = false
    for (const goal of TRAIN_GOALS) for (let days = 2; days <= 6; days++) for (const minutes of [20, 30, 45, 60]) {
      for (const equipment of [new Set<Equipment>(['BODYWEIGHT']), new Set<Equipment>(['BODYWEIGHT', 'PULL_UP_BAR'])]) {
        const plan = build(input(days, equipment, goal, minutes, { pushUpMax: 1, pullUpMax: 0, experience: 'SEASONED', cardio: 'FINISHER', coolDown: true }))
        expect(plan.draft.days.length).toBe(days)
        for (const ex of exercisesOf(plan.draft)) {
          expect(profileFits(profileFor(ex), equipment), ex.id).toBe(true)
          if (ex.id === 'incline_push_up') sawIncline = true
          expect(['diamond_push_up', 'decline_push_up', 'archer_push_up', 'single_arm_push_up', 'plyo_push_up', 'pseudo_planche_push_up', 'inverted_row_with_straps', 'suspended_row', 'ab_roller', 'downward_facing_balance', 'ball_leg_curl'], ex.id).not.toContain(ex.id)
          if (/push_?up|pull_?up|chin_?up|chins/.test(ex.id)) expect(tierOf(ex), ex.id).toBe('FOUNDATION')
        }
      }
    }
    // Hands-elevated push-ups need only a wall or counter: the owner's expected scaling for a max of 1.
    expect(sawIncline).toBe(true)
  }, 30_000) // ~1.5 s alone; heavy plan-matrix runs alongside it in the full suite

  it('baseline squat, hinge and plank results cap their movement patterns at Foundation', () => {
    const cases: [Partial<PlanInput>, MovementPattern[]][] = [
      [{ squatMax: 5 }, ['SQUAT_BILATERAL', 'SQUAT_UNILATERAL']],
      [{ hingeMax: 5 }, ['HIP_HINGE', 'KNEE_FLEXION']],
      [{ plankSeconds: 10 }, ['CORE_ANTI_EXTENSION', 'CORE_FLEXION_ROTATION']],
    ]
    for (const [overrides, patterns] of cases) {
      for (const equipment of [fullGym, new Set<Equipment>(['BODYWEIGHT'])]) {
        const plan = build(input(4, equipment, 'MUSCLE', 60, { experience: 'SEASONED', ...overrides }))
        for (const ex of exercisesOf(plan.draft)) {
          if (patterns.includes(profileFor(ex).pattern)) expect(tierOf(ex), ex.id).toBe('FOUNDATION')
        }
      }
    }
  })

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
    expect(strength.has('4:6')).toBe(true)
    expect([...strength].every((range) => range === '4:6' || range === '8:12')).toBe(true)
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

// Training-science overhaul cases from Android.
describe('PlanBuilder overhaul', () => {
  const compound = (id: string) => isCompound(profileFor(byId.get(id)!).pattern)
  it('isolationsGetEightToFifteenRepsOnMuscle', () => {
    const main = build(input(4, fullGym, 'MUSCLE', 90)).draft.days.flatMap((d) => d.items)
      .filter((i) => i.block === 'Main' && i.kind !== 'HOLD')
    const compounds = main.filter((i) => compound(i.exerciseId))
    const isolations = main.filter((i) => !compound(i.exerciseId))
    expect(compounds.length).toBeGreaterThan(0)
    expect(isolations.length).toBeGreaterThan(0)
    expect(isolations.every((i) => i.repsMin === 10 && i.repsMax === 15)).toBe(true)
    expect(compounds.filter((i) => i.kind === 'WEIGHTED').every((i) => i.repsMin === 8 && i.repsMax === 12)).toBe(true)
  })
  it('isolationsGetEightToTwelveOnStrength', () => {
    const main = build(input(4, fullGym, 'STRENGTH', 90)).draft.days.flatMap((d) => d.items)
      .filter((i) => i.block === 'Main' && i.kind === 'WEIGHTED')
    const compounds = main.filter((i) => compound(i.exerciseId))
    const isolations = main.filter((i) => !compound(i.exerciseId))
    expect(compounds.length).toBeGreaterThan(0)
    expect(isolations.length).toBeGreaterThan(0)
    expect(isolations.every((i) => i.repsMin === 8 && i.repsMax === 12)).toBe(true)
    expect(compounds.every((i) => i.repsMin === 4 && i.repsMax === 6)).toBe(true)
  })
  it('compoundsRestLongerThanIsolations', () => {
    for (const goal of ['STRENGTH', 'MUSCLE', 'GENERAL', 'FAT_LOSS', 'ENDURANCE'] as const) {
      const main = build(input(4, fullGym, goal, 90)).draft.days.flatMap((d) => d.items).filter((i) => i.block === 'Main')
      const compounds = main.filter((i) => compound(i.exerciseId))
      const isolations = main.filter((i) => !compound(i.exerciseId))
      expect(compounds.length).toBeGreaterThan(0)
      expect(isolations.length).toBeGreaterThan(0)
      expect(Math.min(...compounds.map((i) => i.restSeconds))).toBeGreaterThan(Math.max(...isolations.map((i) => i.restSeconds)))
    }
    const long = build(input(4, fullGym, 'STRENGTH', 90, { rest: 'LONG', cardio: 'FINISHER' })).draft.days.flatMap((d) => d.items)
    expect(long.filter((i) => i.block === 'Main' && compound(i.exerciseId)).every((i) => i.restSeconds === 240)).toBe(true)
    expect(long.filter((i) => i.block === 'Main' && !compound(i.exerciseId)).every((i) => i.restSeconds === 135)).toBe(true)
    const finishers = long.filter((i) => i.block === FINISHER_BLOCK)
    expect(finishers.length).toBeGreaterThan(0)
    expect(finishers.every((i) => i.restSeconds === 30)).toBe(true)
  })
  it('coreAndIsolationsComeAfterCompoundsFinisherLast', () => {
    const plan = build(input(4, fullGym, 'MUSCLE', 90, { cardio: 'FINISHER', warmUp: true, coolDown: true }))
    const seenRanks = new Set<number>()
    for (const day of plan.draft.days) {
      const ranks = day.items.filter((i) => !isExtraBlock(i.block)).map((i) => {
        const p = profileFor(byId.get(i.exerciseId)!).pattern
        return i.block === FINISHER_BLOCK ? 3 : isCore(p) ? 2 : isCompound(p) ? 0 : 1
      })
      ranks.forEach((r) => seenRanks.add(r))
      expect(ranks).toEqual([...ranks].sort((a, b) => a - b))
    }
    expect(seenRanks).toEqual(new Set([0, 1, 2, 3]))
  })
  it('existingFocusVolumeStillHolds', () => {
    const weeklySets = (plan: BuiltPlan, m: Muscle) => plan.draft.days.flatMap((d) => d.items)
      .filter((i) => !isExtraBlock(i.block) && byId.get(i.exerciseId)!.primary.includes(m)).reduce((sum, i) => sum + i.sets, 0)
    for (const m of ['BICEPS', 'SHOULDERS', 'GLUTES', 'CHEST', 'CALVES'] as const) {
      const without = weeklySets(build(input(4, fullGym, 'MUSCLE', 45)), m)
      const withFocus = weeklySets(build(input(4, fullGym, 'MUSCLE', 45, { focus: [m] })), m)
      expect(withFocus, `${m} at 45m: ${withFocus} vs ${without}`).toBeGreaterThan(without)
    }
  })

  it('treats flyes and leg_extension as isolations, rejects mismatched pattern rows and MOBILITY, and prefers staples', () => {
    expect(isFly(byId.get('db_fly')!)).toBe(true)
    expect(isFly(byId.get('cable_fly')!)).toBe(true)
    expect(isFly(byId.get('bench_press')!)).toBe(false)
    expect(isCompoundExercise(byId.get('db_fly')!)).toBe(false)
    expect(isCompoundExercise(byId.get('leg_extension')!)).toBe(false)
    expect(isCompoundExercise(byId.get('bench_press')!)).toBe(true)

    expect(fitsItsMovement(byId.get('single_leg_push_off')!)).toBe(false)
    expect(fitsItsMovement(byId.get('bench_press')!)).toBe(true)

    const ppl = build(input(6, fullGym, 'MUSCLE', 60))
    const allIds = ppl.draft.days.flatMap((d) => d.items.map((i) => i.exerciseId))
    expect(allIds.every((id) => profileFor(byId.get(id)!).pattern !== 'MOBILITY')).toBe(true)
    expect(allIds.every((id) => fitsItsMovement(byId.get(id)!))).toBe(true)

    // Pull days in 5/6-day splits no longer include HIP_HINGE accessories.
    for (const day of ppl.draft.days) {
      if (ppl.days.get(day.dayOfWeek) === 'PULL') {
        expect(day.items.some((it) => profileFor(byId.get(it.exerciseId)!).pattern === 'HIP_HINGE')).toBe(false)
      }
    }
    expect(STAPLE_IDS.has('bench_press')).toBe(true)
  })

  it('Gate 3: fill breaks ties by earlier day then plan order', () => {
    const calf = byId.get('calf_raise')!
    const plan = PlanBuilder.build(
      input(2, new Set<Equipment>(['BODYWEIGHT']), 'MUSCLE', 60, { experience: 'SOME', warmUp: false }),
      [calf],
    )
    expect(plan.draft.days.map((d) => d.items[0].sets)).toEqual([4, 2])
  })

  it('Gate 3: zero-gain pull allowance only enables when push is blocked by push/pull alone', () => {
    const mk = (id: string, pattern: MovementPattern, primary: Muscle[], secondary: Muscle[] = []): ExerciseEntity => ({
      id, name: id, kind: 'REPS', primary, secondary, pattern,
    })
    const tiny = [
      mk('t_push', 'HORIZONTAL_PUSH', ['CHEST', 'GLUTES'], ['SHOULDERS']),
      mk('t_pull', 'HORIZONTAL_PULL', ['TRAPS']),
      mk('t_squat', 'SQUAT_BILATERAL', ['QUADS', 'GLUTES']),
      mk('t_hinge', 'HIP_HINGE', ['HAMSTRINGS']),
    ]
    const plan = PlanBuilder.build(
      {
        goal: 'MUSCLE',
        days: new Set([1, 3, 5]),
        minutes: 90,
        equipment: new Set<Equipment>(['BODYWEIGHT']),
        experience: 'SOME',
        warmUp: false,
      },
      tiny,
    )
    const pullSets = plan.draft.days.flatMap((d) => d.items).filter((i) => i.exerciseId === 't_pull').reduce((s, i) => s + i.sets, 0)
    expect(pullSets).toBe(6)
  })

  it('Gate 3: core FOCUS pick is removed last in repair', () => {
    const mk = (id: string, pattern: MovementPattern, primary: Muscle[]): ExerciseEntity => ({
      id, name: id, kind: 'REPS', primary, secondary: [], pattern,
    })
    const tiny = [
      mk('t_sq', 'SQUAT_BILATERAL', ['QUADS', 'ABS']),
      mk('t_hp', 'HORIZONTAL_PUSH', ['CHEST', 'ABS']),
      mk('t_hpl', 'HORIZONTAL_PULL', ['UPPER_BACK', 'ABS']),
      mk('t_hg', 'HIP_HINGE', ['HAMSTRINGS', 'ABS']),
      mk('t_vp', 'VERTICAL_PUSH', ['SHOULDERS', 'ABS']),
      mk('t_core_main', 'CORE_ANTI_EXTENSION', ['ABS']),
      mk('t_core_focus', 'CORE_FLEXION_ROTATION', ['ABS']),
    ]
    const plan = PlanBuilder.build(
      {
        goal: 'MUSCLE',
        days: new Set([1]),
        minutes: 90,
        equipment: new Set<Equipment>(['BODYWEIGHT']),
        experience: 'SOME',
        focus: new Set<Muscle>(['ABS']),
        warmUp: false,
      },
      tiny,
    )
    const ids = plan.draft.days[0].items.map((i) => i.exerciseId)
    expect(ids).toContain('t_core_focus')
    expect(ids).not.toContain('t_core_main')
  })
})

describe('Workout styles, supersets, circuits, fat-loss A/B & warm-ups', () => {
  it('AUTO 3d x 20m MUSCLE FULL_GYM produces adjacent, safe superset pairs with equal sets and rest 40', () => {
    const plan = build(input(3, fullGym, 'MUSCLE', 20))
    for (const day of plan.draft.days) {
      const work = day.items.filter((i) => !isExtraBlock(i.block))
      const byGroup = new Map<number, typeof work>()
      work.forEach((it, idx) => {
        if (it.supersetGroup == null) return
        byGroup.set(it.supersetGroup, [...(byGroup.get(it.supersetGroup) ?? []), it])
        // Pairs must be adjacent
        const prevSame = idx > 0 && work[idx - 1].supersetGroup === it.supersetGroup
        const nextSame = idx + 1 < work.length && work[idx + 1].supersetGroup === it.supersetGroup
        expect(prevSame || nextSame).toBe(true)
      })
      expect(byGroup.size).toBeGreaterThan(0)
      for (const [, pair] of byGroup) {
        expect(pair).toHaveLength(2)
        expect(pair[0].sets).toBe(pair[1].sets)
        expect(pair[0].restSeconds).toBe(SUPERSET_POST_ROUND_SHORT)
        expect(pair[1].restSeconds).toBe(SUPERSET_POST_ROUND_SHORT)
        expect(pair[0].circuit).toBe(false)
        expect(pair[1].circuit).toBe(false)
        expect(NO_PAIR_IDS.has(pair[0].exerciseId)).toBe(false)
        expect(NO_PAIR_IDS.has(pair[1].exerciseId)).toBe(false)
        expect(safePair(byId.get(pair[0].exerciseId)!, byId.get(pair[1].exerciseId)!)).toBe(true)
      }
    }
  })

  it('STRENGTH keeps the first MAIN pick alone on SUPERSET days', () => {
    const plan = build(input(3, fullGym, 'STRENGTH', 20))
    for (const day of plan.draft.days) {
      const work = day.items.filter((i) => !isExtraBlock(i.block))
      expect(work.length).toBeGreaterThan(0)
      expect(work[0].supersetGroup).toBeNull()
    }
    const over30 = build(input(4, fullGym, 'STRENGTH', 60, { style: 'SUPERSET' }))
    for (const day of over30.draft.days) {
      const work = day.items.filter((i) => !isExtraBlock(i.block))
      expect(work[0].supersetGroup).toBeNull()
    }
    expect(over30.draft.days.some((d) => d.items.some((i) => i.supersetGroup != null))).toBe(true)
  })

  it('FAT_LOSS 6d x 20m alternates PUSH_LEGS and PULL_HINGE without heavy consecutive-day overlap', () => {
    const plan = build(input(6, fullGym, 'FAT_LOSS', 20))
    const focuses = [...plan.days.entries()].sort((a, b) => a[0] - b[0]).map(([, f]) => f)
    expect(focuses).toEqual(['PUSH_LEGS', 'PULL_HINGE', 'PUSH_LEGS', 'PULL_HINGE', 'PUSH_LEGS', 'PULL_HINGE'])
    const findings = audit(fromDraft(plan.draft), byId)
    expect(findings.filter((f) => ['RECOVERY_OVERLAP', 'ABOVE_MRV', 'JUNK_VOLUME', 'ISOLATION_FIRST'].includes(f.kind))).toEqual([])
    const primarySets = (dayIdx: number) => {
      const m = new Map<Muscle, number>()
      for (const item of plan.draft.days[dayIdx].items) {
        if (isExtraBlock(item.block)) continue
        for (const mu of byId.get(item.exerciseId)!.primary) m.set(mu, (m.get(mu) ?? 0) + item.sets)
      }
      return m
    }
    for (let i = 0; i < plan.draft.days.length - 1; i++) {
      const cur = primarySets(i)
      const nxt = primarySets(i + 1)
      for (const [mu, s1] of cur) {
        const s2 = nxt.get(mu) ?? 0
        if (s2 > 0) {
          // Catalog squats/lunges list GLUTES as co-primary alongside QUADS, kept well below OVERLAP_SETS.
          expect(mu).toBe('GLUTES')
          expect(Math.min(s1, s2)).toBeLessThan(OVERLAP_SETS)
        }
      }
    }
  })

  it('CIRCUIT days produce <= 3 pairwise-safe moves in distinct non-extra blocks with 30/40/60 rest', () => {
    const c20 = build(input(3, fullGym, 'FAT_LOSS', 20))
    for (const day of c20.draft.days) {
      const work = day.items.filter((i) => !isExtraBlock(i.block))
      const byBlock = new Map<string, typeof work>()
      for (const it of work) {
        expect(isExtraBlock(it.block)).toBe(false)
        if (it.circuit) byBlock.set(it.block, [...(byBlock.get(it.block) ?? []), it])
      }
      expect(byBlock.size).toBeGreaterThan(0)
      for (const [blk, members] of byBlock) {
        expect((CIRCUIT_BLOCKS as readonly string[]).includes(blk)).toBe(true)
        expect(members.length).toBeGreaterThanOrEqual(2)
        expect(members.length).toBeLessThanOrEqual(3)
        const expectedRest = members.length === 2 ? SUPERSET_POST_ROUND_SHORT : CIRCUIT_POST_ROUND_SHORT
        for (const m of members) {
          expect(m.sets).toBe(members[0].sets)
          expect(m.restSeconds).toBe(expectedRest)
          expect(m.supersetGroup).toBeNull()
        }
        for (let a = 0; a < members.length; a++) {
          for (let b = a + 1; b < members.length; b++) {
            expect(safePair(byId.get(members[a].exerciseId)!, byId.get(members[b].exerciseId)!)).toBe(true)
          }
        }
      }
    }

    const c45 = build(input(3, fullGym, 'MUSCLE', 45, { style: 'CIRCUIT' }))
    for (const day of c45.draft.days) {
      const circ = day.items.filter((i) => !isExtraBlock(i.block) && i.circuit)
      expect(circ.length).toBeGreaterThan(0)
      expect(circ.every((i) => i.restSeconds === CIRCUIT_POST_ROUND)).toBe(true)
    }
  })

  it('STRAIGHT over 30 min matches AUTO for non-fat-loss and keeps COND/FINISHER as circuits', () => {
    const autoPlan = build(input(4, fullGym, 'MUSCLE', 60, { cardio: 'FINISHER' }))
    const straightPlan = build(input(4, fullGym, 'MUSCLE', 60, { cardio: 'FINISHER', style: 'STRAIGHT' }))
    expect(straightPlan).toEqual(autoPlan)
    for (const day of straightPlan.draft.days) {
      const main = day.items.filter((i) => i.block === 'Main')
      expect(main.every((i) => !i.circuit && i.supersetGroup == null)).toBe(true)
      const fin = day.items.filter((i) => i.block === FINISHER_BLOCK)
      expect(fin.every((i) => i.circuit)).toBe(true)
    }
  })

  it('estimateMinutes costs superset pairs as grouped rounds', () => {
    const plan = build(input(3, fullGym, 'MUSCLE', 20))
    const first = plan.draft.days[0]
    const mins = estimateMinutes(first)
    expect(mins).toBeLessThanOrEqual(25)
    const pairDay = {
      dayOfWeek: 1,
      title: 'Test',
      isRest: false,
      note: '',
      items: [
        { exerciseId: 'db_bench_press', name: 'DB Bench', kind: 'WEIGHTED', muscles: ['CHEST'], block: 'Main', circuit: false, supersetGroup: 1, sets: 3, repsMin: 8, repsMax: 12, seconds: null, weightKg: null, restSeconds: 40, note: '', videoUrl: null },
        { exerciseId: 'db_row', name: 'DB Row', kind: 'WEIGHTED', muscles: ['UPPER_BACK'], block: 'Main', circuit: false, supersetGroup: 1, sets: 3, repsMin: 8, repsMax: 12, seconds: null, weightKg: null, restSeconds: 40, note: '', videoUrl: null },
      ],
    }
    expect(estimateMinutes(pairDay)).toBe(6)
  })

  it('warm-ups match day focus and never include calf_raise on upper days', () => {
    const ppl = build(input(6, fullGym, 'MUSCLE', 60))
    for (const day of ppl.draft.days) {
      const focus = ppl.days.get(day.dayOfWeek)!
      const warmIds = day.items.filter((i) => i.block === 'Warm-up').map((i) => i.exerciseId)
      expect(warmIds.length).toBe(2)
      if (focus === 'UPPER' || focus === 'PUSH' || focus === 'PULL') {
        expect(warmIds).not.toContain('calf_raise')
        expect(warmIds[0]).toBe('jumping_jacks')
        expect(['band_pull_apart', 'scapular_pull_up', 'scapular_australian_pull_up', 'knee_scapular_push_up']).toContain(warmIds[1])
      } else if (focus === 'LOWER' || focus === 'LEGS') {
        expect(warmIds).toEqual(['jumping_jacks', 'half_squat'])
      }
    }
  })

  it('FAT_LOSS 5d x 45m NEW BICEPS clears SHORT_PLAN without violating audit rules or budget', () => {
    const plan = build(input(5, fullGym, 'FAT_LOSS', 45, { experience: 'NEW', focus: ['BICEPS'] }))
    const avg = plan.draft.days.reduce((sum, d) => sum + estimateMinutes(d), 0) / plan.draft.days.length
    expect(avg).toBeGreaterThan(25)
    for (const day of plan.draft.days) {
      expect(estimateMinutes(day)).toBeLessThanOrEqual(50)
    }
    const kinds = audit(fromDraft(plan.draft), byId).map((f) => f.kind)
    expect(kinds.filter((k) => k !== 'BELOW_MEV')).toEqual([])
  })
})
