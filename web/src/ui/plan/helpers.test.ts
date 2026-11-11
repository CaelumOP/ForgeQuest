import 'fake-indexeddb/auto'
import { describe, expect, it } from 'vitest'
import { EXERCISE_CATALOG } from '../../data/seed/exercise-catalog'
import { DEFAULT_SETTINGS, loadSettings, toBackupSettings, updateSettings } from '../../data/settings'
import { encodePreset, presetEquipment, type Equipment } from '../../domain/exercise-swap-engine'
import {
  PlanBuilder, defaultPlanAnswers, encodePlanAnswers, planAnswersToInput,
} from '../../domain/plan-builder'
import {
  activeEquipmentPreset, equipmentSetting, initialAnswers, localizeBuiltPlan,
  presetShortLabel, rowSummaries, scheme, swapBuiltPlanItem,
} from './helpers'

describe('plan builder helpers', () => {
  it('formats scheme text for holds, rep ranges, single reps and empty reps', () => {
    expect(scheme({ sets: 3, repsMin: null, repsMax: null, seconds: 45 }, 's')).toBe('3×45s')
    expect(scheme({ sets: 4, repsMin: 8, repsMax: 12, seconds: null }, 's')).toBe('4×8-12')
    expect(scheme({ sets: 3, repsMin: 10, repsMax: 10, seconds: null }, 's')).toBe('3×10')
    expect(scheme({ sets: 2, repsMin: null, repsMax: null, seconds: null }, 's')).toBe('2×')
  })

  it('maps equipment sets to preset encodings or custom comma-separated lists with BODYWEIGHT', () => {
    expect(activeEquipmentPreset(presetEquipment('CALISTHENICS_PARK'))).toBe('CALISTHENICS_PARK')
    expect(equipmentSetting(presetEquipment('FULL_GYM'))).toBe(encodePreset('FULL_GYM'))
    const custom = new Set<Equipment>(['DUMBBELLS', 'BAND'])
    expect(activeEquipmentPreset(custom)).toBeNull()
    expect(equipmentSetting(custom)).toBe('DUMBBELLS,BAND,BODYWEIGHT')
  })

  it('trims parenthetical detail from preset short labels in English and Arabic', () => {
    expect(presetShortLabel('HOME_GYM', 'en')).toBe('Home gym')
    expect(presetShortLabel('HOME_GYM', 'ar')).toBe('نادي منزلي')
  })

  it('localizes built plan name, description, day titles and why tags', () => {
    const a = { ...defaultPlanAnswers(), cardio: 'FINISHER' as const, focus: new Set(['CHEST'] as const) }
    const built = PlanBuilder.build(planAnswersToInput(a), EXERCISE_CATALOG)
    const tr = (k: string, ...args: (string | number)[]) =>
      args.length ? `${k}(${args.join(',')})` : k
    const draft = localizeBuiltPlan(built, 'STRENGTH', tr)
    expect(draft.name).toBe('pb_plan_name(pb_goal_strength)')
    expect(draft.description).toBe('pb_progression')
    expect(draft.days.every((d) => d.title.startsWith('pb_day_'))).toBe(true)
    expect(draft.days.some((d) => (d.note ?? '').includes('pb_why_'))).toBe(true)
  })

  it('builds summaries for the six Fit-it-to-me rows matching Android', () => {
    const tr = (k: string, ...args: (string | number)[]) =>
      k === 'list_separator' ? ', ' : args.length ? `${k}:${args.join(',')}` : k
    const base = defaultPlanAnswers()
    const defSums = rowSummaries(base, tr, 'en')
    expect(defSums.you).toBe('pb_auto')
    expect(defSums.body).toBe('pb_auto')
    expect(defSums.schedule).toBe('pb_auto')
    expect(defSums.gear).toBe('pb_auto')
    expect(defSums.style).toBe('pb_auto')
    expect(defSums.avoid).toBe('pb_auto')

    const custom = {
      ...base,
      pushUpMax: 25,
      pullUpMax: 8,
      injuries: new Set(['WRIST'] as const),
      focus: new Set(['CHEST'] as const),
      days: new Set([1, 2, 3]),
      equipment: new Set<Equipment>(['BODYWEIGHT', 'BAND']),
      reps: 'LOW' as const,
      cardio: 'FINISHER' as const,
      avoidIds: new Set(['push_up', 'burpee']),
    }
    const sums = rowSummaries(custom, tr, 'en')
    expect(sums.you).toBe('pb_exp_some · 25 / 8')
    expect(sums.body).toBe('pb_injury_wrist, muscle_chest')
    expect(sums.schedule).not.toBe('pb_auto')
    expect(sums.gear).toBe('pb_custom')
    expect(sums.style).toBe('pb_custom')
    expect(sums.avoid).toBe('pb_avoid_count:2')
  })

  it('cycles through swap alternatives before looping', () => {
    const a = defaultPlanAnswers()
    const built = PlanBuilder.build(planAnswersToInput(a), EXERCISE_CATALOG)
    const dow = built.draft.days[0]!.dayOfWeek
    const firstId = built.draft.days[0]!.items[0]!.exerciseId
    const step1 = swapBuiltPlanItem(built, a, new Map(), EXERCISE_CATALOG, dow, 0)
    const secondId = step1.review.draft.days[0]!.items[0]!.exerciseId
    expect(secondId).not.toBe(firstId)
    expect(step1.tried.get(`${dow}:0`)?.has(firstId)).toBe(true)
    expect(step1.tried.get(`${dow}:0`)?.has(secondId)).toBe(true)
  })

  it('persists planBuilderAnswers in settings and excludes it from backup JSON', async () => {
    const custom = { ...defaultPlanAnswers(), goal: 'STRENGTH' as const, minutes: 30, pushUpMax: 30 }
    const encoded = encodePlanAnswers(custom)
    await updateSettings((s) => ({ ...s, planBuilderAnswers: encoded }))
    const loaded = await loadSettings()
    expect(loaded.planBuilderAnswers).toBe(encoded)
    expect(initialAnswers(loaded)).toEqual(custom)

    const backupJson = JSON.stringify(toBackupSettings(loaded))
    expect(backupJson).not.toContain('planBuilderAnswers')
    expect(backupJson).not.toContain('plan_builder_answers')

    const fallback = initialAnswers({ ...DEFAULT_SETTINGS, equipment: 'DUMBBELLS' })
    expect(fallback.equipment).toEqual(new Set<Equipment>(['DUMBBELLS', 'BODYWEIGHT']))
  })
})
