import 'fake-indexeddb/auto'
import { describe, expect, it } from 'vitest'
import { EXERCISE_CATALOG } from '../../data/seed/exercise-catalog'
import { DEFAULT_SETTINGS, loadSettings, toBackupSettings, updateSettings } from '../../data/settings'
import { encodeEquipmentSet, presetEquipment, profileFor, type Equipment } from '../../domain/exercise-swap-engine'
import { tierOf } from '../../domain/exercise-tiers'
import { decodePlanAnswers } from '../../domain/plan-answers'
import { tIn } from '../../i18n'
import { audit, fromDraft } from '../../domain/routine-auditor'
import {
  PlanBuilder, WORKOUT_STYLES, defaultPlanAnswers, encodePlanAnswers, planAnswersToInput, spreadDays,
} from '../../domain/plan-builder'
import {
  PLAN_BUILDER_DRAFT_KEY, activeEquipmentPreset, consumePlanBuilderDraft, equipmentSetting,
  initialAnswers, localizeBuiltPlan, mergeBaselineMaxes, presetShortLabel, rowSummaries,
  savePlanBuilderDraft, scheme, swapBuiltPlanItem, stepperCountFromValue, workoutStyleKey,
  type DraftStore,
} from './helpers'

describe('PlanBuilderStepperTest (Android parity)', () => {
  it('zeroPullUpsEnteredReachesFoundation', () => {
    const entered = stepperCountFromValue(0)
    expect(entered).toBe(0)
    const saved = { ...defaultPlanAnswers(), days: spreadDays(5), equipment: presetEquipment('FULL_GYM'), experience: 'SEASONED' as const, pullUpMax: entered }
    const decoded = decodePlanAnswers(encodePlanAnswers(saved))!
    expect(decoded.pullUpMax).toBe(0)
    const plan = PlanBuilder.build(planAnswersToInput(decoded), EXERCISE_CATALOG)
    const byId = new Map(EXERCISE_CATALOG.map((e) => [e.id, e]))
    const pulls = plan.draft.days.flatMap((d) => d.items).map((i) => byId.get(i.exerciseId)!)
      .filter((e) => profileFor(e).pattern === 'VERTICAL_PULL')
    expect(pulls.length).toBeGreaterThan(0)
    expect(pulls.every((e) => (tierOf(e) ?? 'FOUNDATION') === 'FOUNDATION')).toBe(true)
  })
  it('preserves zero push-ups and leaves absent old answers unset', () => {
    const answers = { ...defaultPlanAnswers(), pushUpMax: stepperCountFromValue(0) }
    expect(decodePlanAnswers(encodePlanAnswers(answers))!.pushUpMax).toBe(0)
    const old = JSON.parse(encodePlanAnswers(answers))
    delete old.pushUpMax
    delete old.pullUpMax
    const decoded = decodePlanAnswers(JSON.stringify(old))!
    expect(decoded.pushUpMax).toBeNull()
    expect(decoded.pullUpMax).toBeNull()
    expect(stepperCountFromValue(-1)).toBe(0)
    expect(stepperCountFromValue(2.8)).toBe(2)
  })
})

describe('plan builder helpers', () => {
  it('formats scheme text for holds, rep ranges, single reps and empty reps', () => {
    expect(scheme({ sets: 3, repsMin: null, repsMax: null, seconds: 45 }, 's')).toBe('3×45s')
    expect(scheme({ sets: 4, repsMin: 8, repsMax: 12, seconds: null }, 's')).toBe('4×8-12')
    expect(scheme({ sets: 3, repsMin: 10, repsMax: 10, seconds: null }, 's')).toBe('3×10')
    expect(scheme({ sets: 2, repsMin: null, repsMax: null, seconds: null }, 's')).toBe('2×')
  })

  it('maps equipment sets to expanded active item encodings with BODYWEIGHT', () => {
    expect(activeEquipmentPreset(presetEquipment('CALISTHENICS_PARK'))).toBe('CALISTHENICS_PARK')
    expect(equipmentSetting(presetEquipment('FULL_GYM'))).toBe(encodeEquipmentSet(presetEquipment('FULL_GYM')))
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

  it('maps workout styles to EN/AR labels and shows chosen style in the Style row summary', () => {
    expect(WORKOUT_STYLES).toEqual(['AUTO', 'STRAIGHT', 'SUPERSET', 'CIRCUIT'])
    expect(WORKOUT_STYLES.map((s) => tIn('en', workoutStyleKey(s)))).toEqual([
      'Auto', 'Straight sets', 'Supersets', 'Circuit',
    ])
    expect(WORKOUT_STYLES.map((s) => tIn('ar', workoutStyleKey(s)))).toEqual([
      'تلقائي', 'مجموعات عادية', 'مجموعات مزدوجة', 'دائرة',
    ])
    expect(tIn('en', 'pb_style')).toBe('Workout style')
    expect(tIn('ar', 'pb_style')).toBe('أسلوب التمرين')
    expect(tIn('en', 'pb_style_auto_hint')).toBe('Supersets on sessions of 30 min or less')
    expect(tIn('ar', 'pb_style_auto_hint')).toBe('مجموعات مزدوجة في الجلسات التي لا تتجاوز 30 دقيقة')

    const tr = (k: string, ...args: (string | number)[]) =>
      k === 'list_separator' ? ', ' : args.length ? `${k}:${args.join(',')}` : k
    const base = defaultPlanAnswers()
    expect(rowSummaries(base, tr, 'en').style).toBe('pb_auto')
    expect(rowSummaries({ ...base, style: 'STRAIGHT' }, tr, 'en').style).toBe('pb_style_straight')
    expect(rowSummaries({ ...base, style: 'SUPERSET' }, tr, 'en').style).toBe('pb_style_superset')
    expect(rowSummaries({ ...base, style: 'CIRCUIT' }, tr, 'en').style).toBe('pb_style_circuit')
  })

  it('produces no Plan check findings for a freshly built plan with default answers', () => {
    const byId = new Map(EXERCISE_CATALOG.map((e) => [e.id, e]))
    for (const style of WORKOUT_STYLES) {
      const built = PlanBuilder.build(planAnswersToInput({ ...defaultPlanAnswers(), style }), EXERCISE_CATALOG)
      expect(audit(fromDraft(built.draft), byId)).toEqual([])
    }
  })

  it('round-trips plan builder draft via sessionStorage, removes key on consume, and merges latest baseline maxes', () => {
    const backing = new Map<string, string>()
    const store: DraftStore = {
      getItem: (k) => backing.get(k) ?? null,
      setItem: (k, v) => { backing.set(k, v) },
      removeItem: (k) => { backing.delete(k) },
    }

    const draft = {
      ...defaultPlanAnswers(),
      goal: 'STRENGTH' as const,
      days: new Set([1, 3, 5, 6]),
      minutes: 60,
      equipment: presetEquipment('FULL_GYM'),
      experience: 'SEASONED' as const,
      injuries: new Set(['WRIST'] as const),
      avoidIds: new Set(['burpee']),
      pushUpMax: 12,
      pullUpMax: 4,
      focus: new Set(['CHEST', 'UPPER_BACK'] as const),
      minutesByDay: new Map([[1, 40], [5, 75]]),
      cardio: 'FINISHER' as const,
      warmUp: false,
      coolDown: true,
      holdsOk: false,
      reps: 'LOW' as const,
      rest: 'LONG' as const,
      style: 'SUPERSET' as const,
    }

    savePlanBuilderDraft(draft, store)
    expect(backing.has(PLAN_BUILDER_DRAFT_KEY)).toBe(true)

    const restored = consumePlanBuilderDraft(store)
    expect(restored).toEqual(draft)
    expect(backing.has(PLAN_BUILDER_DRAFT_KEY)).toBe(false)
    expect(consumePlanBuilderDraft(store)).toBeNull()

    const merged = mergeBaselineMaxes(
      restored!,
      new Map([
        ['push_up', { kind: 'REPS', reps: 25 }],
        ['pull_up', { kind: 'REPS', reps: 9 }],
      ]),
    )
    expect(merged).toEqual({ ...draft, pushUpMax: 25, pullUpMax: 9 })

    // Throwing storage is safely ignored without throwing
    const brokenStore: DraftStore = {
      getItem: () => { throw new Error('blocked') },
      setItem: () => { throw new Error('blocked') },
      removeItem: () => { throw new Error('blocked') },
    }
    expect(() => savePlanBuilderDraft(draft, brokenStore)).not.toThrow()
    expect(consumePlanBuilderDraft(brokenStore)).toBeNull()
  })
})
