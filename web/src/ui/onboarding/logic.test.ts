import { describe, expect, it } from 'vitest'
import { tIn } from '../../i18n'
import { EXERCISE_CATALOG } from '../../data/seed/exercise-catalog'
import { DEFAULT_SETTINGS } from '../../data/settings'
import {
  bodyStatsReady, foodSetupInitialState, foodTargetsPatch, initialState, localizeOnboardingPlan,
  needsFoodTargets, nextStep, onboardingPlanChoice, pickGoal, prevStep, progressStep, setWeight,
  settingsPatch, trainGoalFor, withSuggestions,
} from './logic'

describe('onboarding logic', () => {
  it('starts at 3 days a week with Built for me', () => {
    const s = initialState()
    expect(s.daysPerWeek).toBe(3)
    expect(s.builtForMe).toBe(true)
  })
  it('suggests protein and calories from weight and goal', () => {
    const s = setWeight(initialState(), 80)
    expect(s.weightKg).toBe(80)
    expect(s.protein).toBeGreaterThanOrEqual(140)
    expect(s.kcal % 50).toBe(0)
    const cut = withSuggestions({ ...s, goal: 'CUT' })
    expect(cut.kcal).toBeLessThan(s.kcal)
  })
  it('keeps hand-set goals when the weight changes', () => {
    const s = setWeight({ ...initialState(), goalsTouched: true, kcal: 3000, protein: 200 }, 90)
    expect([s.kcal, s.protein]).toEqual([3000, 200])
  })
  it('clamps weight to 30..250 kg', () => {
    expect(setWeight(initialState(), 10).weightKg).toBe(30)
    expect(setWeight(initialState(), 999).weightKg).toBe(250)
  })
  it('writes name, body type, equipment, weekly target and justLog on finish', () => {
    const s = { ...initialState(), name: '  Sam ', daysPerWeek: 4 }
    const out = settingsPatch(s)({ ...DEFAULT_SETTINGS })
    expect(out).toMatchObject({ onboarded: true, name: 'Sam', weeklyTargetOverride: 4, calorieV2Seen: true, heightCm: 0, kcalGoal: 2200 })
    expect(out.justLog).toBe(false)
    expect(settingsPatch({ ...s, justLog: true })(DEFAULT_SETTINGS).justLog).toBe(true)
  })
  it('writes the body and goal fields when the food targets are saved', () => {
    const s = { ...initialState(), unit: 'LB' as const, age: 30, heightCm: 175, kcal: 900, protein: 20 }
    const out = foodTargetsPatch(s, 20000, 2026)({ ...DEFAULT_SETTINGS })
    expect(out).toMatchObject({ unit: 'lb', birthYear: 1996, heightCm: 175, proteinGoal: 60, dayActivity: 'SITTING', targetSetDay: 20000, goalSetDay: 20000 })
    expect(out.kcalGoal).toBeGreaterThanOrEqual(1200)
  })
  it('needs real height and age before the food targets can be calculated', () => {
    expect(bodyStatsReady(initialState())).toBe(false)
    expect(bodyStatsReady({ ...initialState(), heightCm: 175, age: 30 })).toBe(true)
    expect(bodyStatsReady({ ...initialState(), heightCm: 1.75, age: 30 })).toBe(false)
  })
  it('uses height and age for calories once they are set', () => {
    const noBody = withSuggestions(initialState())
    const tall = withSuggestions({ ...initialState(), heightCm: 190, age: 20 })
    const short = withSuggestions({ ...initialState(), heightCm: 155, age: 60 })
    expect(noBody.kcal).toBe(2200)
    expect(tall.kcal).toBeGreaterThan(short.kcal)
  })
  it('offers the food-targets card after the first workout while height is missing', () => {
    expect(needsFoodTargets(0, 0)).toBe(false)
    expect(needsFoodTargets(0, 1)).toBe(true)
    expect(needsFoodTargets(178, 5)).toBe(false)
  })

  it('start-empty yields an empty 7-day manual plan instead of a starter plan', () => {
    const s = { ...initialState(), builtForMe: false, daysPerWeek: 5, preset: 'FULL_GYM' as const }
    const choice = onboardingPlanChoice(s, EXERCISE_CATALOG, (k, ...a) => tIn('en', k, ...a))
    expect(choice.source).toBe('manual')
    expect(choice.plan.days).toHaveLength(7)
    expect(choice.plan.days.every((d) => d.isRest && d.items.length === 0)).toBe(true)
  })

  it('maps nutrition goals to training goals and saves nutritionGoal and unit on finish', () => {
    expect(trainGoalFor('GAIN')).toBe('MUSCLE')
    expect(trainGoalFor('CUT')).toBe('FAT_LOSS')
    expect(trainGoalFor('MAINTAIN')).toBe('GENERAL')

    const s = { ...initialState(), goal: 'GAIN' as const, unit: 'LB' as const, daysPerWeek: 4, justLog: false }
    expect(settingsPatch(s)({ ...DEFAULT_SETTINGS })).toMatchObject({
      onboarded: true,
      nutritionGoal: 'GAIN',
      unit: 'lb',
      weeklyTargetOverride: 4,
      justLog: false,
    })
  })

  it('skips the training step when justLog is selected', () => {
    const habit = { step: 3, justLog: false }
    expect(nextStep(habit)).toBe(4)
    expect(prevStep({ step: 5, justLog: false })).toBe(4)
    expect(progressStep({ step: 5, justLog: false })).toEqual({ current: 5, total: 5 })

    const logOnly = { step: 3, justLog: true }
    expect(nextStep(logOnly)).toBe(5)
    expect(prevStep({ step: 5, justLog: true })).toBe(3)
    expect(progressStep({ step: 5, justLog: true })).toEqual({ current: 4, total: 4 })
  })

  it('pre-fills FoodSetup with the saved nutritionGoal instead of MAINTAIN', () => {
    const cutState = foodSetupInitialState({ ...DEFAULT_SETTINGS, nutritionGoal: 'CUT', unit: 'lb', weeklyTargetOverride: 4 }, 80)
    expect(cutState.goal).toBe('CUT')
    expect(cutState.unit).toBe('LB')
    expect(cutState.daysPerWeek).toBe(4)

    const gainState = foodSetupInitialState({ ...DEFAULT_SETTINGS, nutritionGoal: 'GAIN' }, null)
    expect(gainState.goal).toBe('GAIN')
    expect(gainState.kcal).toBeGreaterThan(cutState.kcal)
  })

  it('localizes the built-for-me onboarding plan in English and Arabic', () => {
    const s = { ...initialState(), goal: 'GAIN' as const, daysPerWeek: 4, preset: 'FULL_GYM' as const, experience: 'SOME' as const }
    const enPlan = localizeOnboardingPlan(s, EXERCISE_CATALOG, (k, ...a) => tIn('en', k, ...a))
    expect(enPlan.name).toBe('Build muscle plan')
    expect(enPlan.days.map((d) => d.title)).toEqual(['Upper body', 'Lower body', 'Upper body', 'Lower body'])

    const arPlan = localizeOnboardingPlan(s, EXERCISE_CATALOG, (k, ...a) => tIn('ar', k, ...a))
    expect(arPlan.name).toBe('خطة بناء العضلات')
    expect(arPlan.days.map((d) => d.title)).toEqual(['الجزء العلوي', 'الجزء السفلي', 'الجزء العلوي', 'الجزء السفلي'])
    expect(arPlan.description).not.toBe(enPlan.description)
  })

  it('Just log resets a Gain/Cut goal to Maintain and keeps targets in step', () => {
    const gain = pickGoal(initialState(), 'GAIN')
    const log = pickGoal(gain, 'JUST_LOG')
    expect(log.goal).toBe('MAINTAIN')
    expect(log.justLog).toBe(true)
    expect(log.kcal).toBe(pickGoal(initialState(), 'MAINTAIN').kcal)
    expect(pickGoal(log, 'CUT')).toMatchObject({ goal: 'CUT', justLog: false })
  })
})
