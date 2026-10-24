import type { Settings } from '../../data/settings'
import { encodePreset, type EquipmentPreset } from '../../domain/exercise-swap-engine'
import { MIN_SAFE_KCAL, proteinPerKgFor, suggestedKcal, suggestedProtein, type NutritionGoal } from '../../domain/nutrition'
import type { WeightUnit } from '../../domain/units'
import { unitCode } from '../../domain/units'
import type { BodyType } from '../../data/settings'

export const LAST_STEP = 4 // language, welcome, goals, plan, key

export type OnbState = {
  step: number
  name: string
  bodyType: BodyType
  weightKg: number
  unit: WeightUnit
  heightCm: number
  age: number
  kcal: number
  protein: number
  goalsTouched: boolean
  goal: NutritionGoal
  planIndex: number
  preset: EquipmentPreset
}

export const initialState = (): OnbState => ({
  step: 0, name: '', bodyType: 'MALE', weightKg: 75, unit: 'KG', heightCm: 0, age: 0, kcal: 2500, protein: 135,
  goalsTouched: false, goal: 'MAINTAIN', planIndex: 0, preset: 'CALISTHENICS_PARK',
})

/** Training days of the chosen starter plan (3 when starting empty), for the calorie suggestion. */
export type DaysOf = (planIndex: number) => number

const kcalFor = (s: OnbState, days: DaysOf) =>
  suggestedKcal(s.weightKg, s.heightCm, s.age, s.bodyType === 'MALE', days(s.planIndex), 0, s.goal)

/** Refreshes both suggestions unless the person already set the goals by hand (Android OnboardingViewModel). */
export function withSuggestions(s: OnbState, days: DaysOf): OnbState {
  if (s.goalsTouched) return s
  const protein = suggestedProtein(s.weightKg, proteinPerKgFor(s.goal))
  const next = { ...s, protein }
  return { ...next, kcal: kcalFor(next, days) }
}

export const setWeight = (s: OnbState, kg: number, days: DaysOf): OnbState =>
  withSuggestions({ ...s, weightKg: Math.min(250, Math.max(30, kg)) }, days)

/** Settings written when onboarding finishes. The key is only kept when it was checked and works. */
export function settingsPatch(s: OnbState, today: number, thisYear: number, key: string | null): (c: Settings) => Settings {
  return (c) => ({
    ...c,
    onboarded: true,
    name: s.name.trim(),
    bodyType: s.bodyType,
    kcalGoal: Math.max(s.kcal, MIN_SAFE_KCAL),
    proteinGoal: Math.max(s.protein, 60),
    unit: unitCode(s.unit),
    heightCm: s.heightCm,
    birthYear: s.age > 0 ? thisYear - s.age : 0,
    equipment: encodePreset(s.preset),
    nutritionGoal: s.goal,
    goalSetDay: today,
    proteinPerKg: proteinPerKgFor(s.goal),
    apiKey: key ?? c.apiKey,
  })
}

