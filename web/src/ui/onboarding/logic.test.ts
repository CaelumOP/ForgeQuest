import { describe, expect, it } from 'vitest'
import { DEFAULT_SETTINGS } from '../../data/settings'
import {
  bodyStatsReady, foodTargetsPatch, initialState, needsFoodTargets, planForDays, presetForPlan, setWeight, settingsPatch, withSuggestions,
} from './logic'

describe('onboarding logic', () => {
  it('starts at 3 days a week with the 3-day bodyweight plan', () => {
    const s = initialState()
    expect(s.daysPerWeek).toBe(3)
    expect(s.planIndex).toBe(1)
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
  it('writes only name, body type, equipment, weekly target and key on finish', () => {
    const s = { ...initialState(), name: '  Sam ', daysPerWeek: 4 }
    const out = settingsPatch(s, null)({ ...DEFAULT_SETTINGS, apiKey: 'old' })
    expect(out).toMatchObject({ onboarded: true, name: 'Sam', weeklyTargetOverride: 4, calorieV2Seen: true, apiKey: 'old', heightCm: 0, kcalGoal: 2200 })
    expect(settingsPatch(s, 'k')(DEFAULT_SETTINGS).apiKey).toBe('k')
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

  // Plans: 0 Hemo (5 days), 1 bodyweight (3), 2 dumbbells (3), 3 gym split (6).
  it('preselects a starter plan for 2, 3, 4 and 5 days a week', () => {
    for (const d of [2, 3, 4]) {
      expect(planForDays(d, 'CALISTHENICS_PARK')).toBe(1)
      expect(planForDays(d, 'FULL_GYM')).toBe(2)
      expect(planForDays(d, 'DUMBBELLS_AND_BAR')).toBe(2)
    }
    expect(planForDays(5, 'CALISTHENICS_PARK')).toBe(0)
    expect(planForDays(5, 'FULL_GYM')).toBe(0)
    expect(planForDays(5, 'BODYWEIGHT_ONLY')).toBe(1)
  })
  it('shows Hemo preselected only when 5 days is picked', () => {
    for (const p of ['CALISTHENICS_PARK', 'FULL_GYM', 'HOME_GYM', 'PULL_UP_BAR_ONLY', 'DUMBBELLS_AND_BAR'] as const) {
      for (const d of [2, 3, 4]) expect(planForDays(d, p)).not.toBe(0)
      expect(planForDays(5, p)).toBe(0)
    }
  })
  it('picking the gym split sets Full gym; the dumbbell plan without dumbbells sets Dumbbells & pull-up bar', () => {
    expect(presetForPlan(3, 'BODYWEIGHT_ONLY')).toBe('FULL_GYM')
    expect(presetForPlan(2, 'CALISTHENICS_PARK')).toBe('DUMBBELLS_AND_BAR')
    expect(presetForPlan(2, 'DUMBBELLS_AND_KETTLEBELL')).toBe('DUMBBELLS_AND_KETTLEBELL')
    expect(presetForPlan(1, 'HOME_GYM')).toBe('HOME_GYM')
  })
})
