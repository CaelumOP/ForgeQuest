import { describe, expect, it } from 'vitest'
import type { ExerciseEntity, PlanDraftDay, PlanDraftItem, PlanItemEntity } from './entities'
import {
  adaptPlanToEquipment, inferEquipmentFromText, presetEquipment, profileFor, rankSubstitutes, translatePlanItem,
  type Equipment,
} from './exercise-swap-engine'
import type { ExerciseKind } from './muscle'
import { EXERCISE_CATALOG } from '../data/seed/exercise-catalog'

const ex = (id: string): ExerciseEntity => ({ id, name: id, kind: 'WEIGHTED', primary: ['QUADS'], secondary: [] })
const set = (...e: Equipment[]) => new Set<Equipment>(e)

// The built-in catalog, converted from the Android ExerciseCatalog.kt (see data/seed/exercise-catalog.ts).
const CATALOG: ExerciseEntity[] = EXERCISE_CATALOG
const LIBRARY = new Map(CATALOG.map((e) => [e.id, e]))

describe('ExerciseSwapEngineTest', () => {
  it('weightedCalisthenicsNeedBarsNotABarbell', () => {
    const calisthenics = presetEquipment('CALISTHENICS_PARK')
    for (const id of ['weighted_pull_up', 'weighted_chin_up', 'weighted_dip']) {
      expect(calisthenics.has(profileFor(ex(id)).equipment), id).toBe(true)
    }
  })

  it('swappingInThePlanEditorDropsTheOldLoad', () => {
    const squat: PlanItemEntity = {
      id: 0, dayId: 1, position: 0, exerciseId: 'back_squat', block: 'Main', circuit: false, sets: 3,
      repsMin: 5, repsMax: 8, seconds: null, weightKg: 100, restSeconds: 60, note: '',
    }
    const goblet = translatePlanItem(squat, ex('goblet_squat'))
    expect(goblet.exerciseId).toBe('goblet_squat')
    expect(goblet.weightKg).toBe(0)
    expect(goblet.repsMin).toBe(5)
  })

  it('equipmentFromTextReadsArabicAndNegation', () => {
    // «بار عقلة» is a pull-up bar, not a barbell.
    expect(inferEquipmentFromText('عندي بار عقلة في البيت')).toEqual(set('PULL_UP_BAR', 'BODYWEIGHT'))
    expect(inferEquipmentFromText('عندي دمبل، ما عندي كيتل بيل')).toEqual(set('DUMBBELLS', 'BODYWEIGHT'))
    expect(inferEquipmentFromText('ما عندي كيتل بيل ولكن عندي دمبل')).toEqual(set('DUMBBELLS', 'BODYWEIGHT'))
    expect(inferEquipmentFromText("I don't have dumbbells, but I have a barbell")).toEqual(set('BARBELL_RACK', 'BODYWEIGHT'))
    expect(inferEquipmentFromText('بدون معدات')).toEqual(set('BODYWEIGHT'))
    expect(inferEquipmentFromText('ما عندي أجهزة')).toBeNull()
  })
})

describe('web additions', () => {
  // Audit #6: Unicode word edges, so Arabic matches in JavaScript too.
  it('reads three English and three Arabic sentences', () => {
    expect(inferEquipmentFromText('I have a pull-up bar and dumbbells')).toEqual(set('PULL_UP_BAR', 'DUMBBELLS', 'BODYWEIGHT'))
    expect(inferEquipmentFromText('No equipment, just me')).toEqual(set('BODYWEIGHT'))
    expect(inferEquipmentFromText('I train at a commercial gym')?.size).toBe(7)
    expect(inferEquipmentFromText('عندي بار عقلة')).toEqual(set('PULL_UP_BAR', 'BODYWEIGHT'))
    expect(inferEquipmentFromText('ما عندي دمبل لكن عندي عقلة')).toEqual(set('PULL_UP_BAR', 'BODYWEIGHT'))
    expect(inferEquipmentFromText('أتمرن في النادي')?.size).toBe(7)
  })

  // Audit #7: swaps, plan adaptation (plateau case is in trends.test.ts).
  it('swaps back squat for a goblet squat with dumbbells and a pull-up bar', () => {
    const ranked = rankSubstitutes(LIBRARY.get('back_squat')!, CATALOG, presetEquipment('DUMBBELLS_AND_BAR'))
    expect(ranked.find((c) => c.matchesUserEquipment)?.exercise.id).toBe('goblet_squat')
  })

  it('adapts a small plan to dumbbells and a pull-up bar', () => {
    const item = (exerciseId: string, kind: ExerciseKind, weightKg: number | null): PlanDraftItem => ({
      exerciseId, name: null, kind: null, muscles: [], block: 'Main', circuit: false, sets: 3,
      repsMin: kind === 'HOLD' ? null : 8, repsMax: kind === 'HOLD' ? null : 12, seconds: kind === 'HOLD' ? 30 : null,
      weightKg, restSeconds: 60, note: '', videoUrl: null,
    })
    const days: PlanDraftDay[] = [
      { dayOfWeek: 1, title: 'Full body', isRest: false, note: '', items: [
        item('back_squat', 'WEIGHTED', 100), item('bench_press', 'WEIGHTED', 80), item('pull_up', 'REPS', null),
        item('leg_press', 'WEIGHTED', 120), item('plank', 'HOLD', null),
      ] },
      { dayOfWeek: 2, title: 'Rest', isRest: true, note: '', items: [] },
    ]
    const [adapted, swaps] = adaptPlanToEquipment({ name: 'Starter', days }, LIBRARY, presetEquipment('DUMBBELLS_AND_BAR'))
    // bench press goes to weighted push-up (same muscles beat the curated dumbbell bench); leg press skips the goblet
    // squat already used that day.
    expect(swaps).toBe(3)
    expect(adapted.days[0].items.map((i) => i.exerciseId)).toEqual(['goblet_squat', 'weighted_push_up', 'pull_up', 'squat', 'plank'])
    expect(adapted.days[0].items[0].weightKg).toBeNull()
    expect(adapted.days[0].items[0].restSeconds).toBe(90)
    expect(adapted.days[1]).toBe(days[1])
  })
})

describe('audit 2026-10-24', () => {
  it('keeps a curl when no bodyweight move is a real match, instead of a push-up or burpee', () => {
    const curl: PlanDraftItem = {
      exerciseId: 'bicep_curl', name: null, kind: null, muscles: [], block: 'Main', circuit: false, sets: 3,
      repsMin: 10, repsMax: 12, seconds: null, weightKg: null, restSeconds: 60, note: '', videoUrl: null,
    }
    const days: PlanDraftDay[] = [{ dayOfWeek: 1, title: 'Arms', isRest: false, note: '', items: [curl] }]
    const [adapted, swaps] = adaptPlanToEquipment({ name: 'P', days }, LIBRARY, set('BODYWEIGHT'))
    expect(swaps).toBe(0)
    expect(adapted.days[0].items[0].exerciseId).toBe('bicep_curl')
    // Setup drops it instead (dropUnfit), like the Android onboarding.
    const [dropped, dropSwaps] = adaptPlanToEquipment({ name: 'P', days }, LIBRARY, set('BODYWEIGHT'), true)
    expect(dropSwaps).toBe(1)
    expect(dropped.days[0].items).toEqual([])
  })
})
