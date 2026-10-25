import type { Settings } from '../../data/settings'
import { BODYWEIGHT_INDEX, DUMBBELLS_INDEX, GYM_SPLIT_INDEX } from '../../data/seed/starter-plans'
import { encodePreset, presetEquipment, type EquipmentPreset } from '../../domain/exercise-swap-engine'
import {
  calorieFloor, DAY_BASE, proteinPerKgFor, suggestedKcal, suggestedProtein, type CutPace, type DayActivity, type GainPace, type NutritionGoal,
} from '../../domain/nutrition'
import type { WeightUnit } from '../../domain/units'
import { unitCode } from '../../domain/units'
import type { BodyType } from '../../data/settings'

export const LAST_STEP = 5 // language, welcome, body, plan, goal, key

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
  dayActivity: DayActivity
  cutPace: CutPace
  gainPace: GainPace
  planIndex: number
  preset: EquipmentPreset
}

export const initialState = (): OnbState => ({
  step: 0, name: '', bodyType: 'MALE', weightKg: 75, unit: 'KG', heightCm: 0, age: 0, kcal: 2500, protein: 135,
  goalsTouched: false, goal: 'MAINTAIN', dayActivity: 'SITTING', cutPace: 'STANDARD', gainPace: 'STANDARD', planIndex: 0, preset: 'CALISTHENICS_PARK',
})

/** The starter plan to show as chosen after picking [preset] (OnboardingScreen.kt planFor). */
export function planFor(preset: EquipmentPreset, current: number): number {
  const hasDumbbells = presetEquipment(preset).has('DUMBBELLS')
  if (hasDumbbells && current === GYM_SPLIT_INDEX && preset === 'FULL_GYM') return current
  if (hasDumbbells && (current === BODYWEIGHT_INDEX || current === DUMBBELLS_INDEX)) return DUMBBELLS_INDEX
  if (hasDumbbells && current === 0 && (preset === 'FULL_GYM' || preset === 'HOME_GYM')) return DUMBBELLS_INDEX
  if (!hasDumbbells && (current === DUMBBELLS_INDEX || current === GYM_SPLIT_INDEX)) return BODYWEIGHT_INDEX
  // Hemo's routine needs bars; with nothing at all, the plain bodyweight plan is the honest start.
  if (preset === 'BODYWEIGHT_ONLY' && current === 0) return BODYWEIGHT_INDEX
  return current
}

/** The equipment to show after picking starter plan [index]: the gym split needs a gym, the dumbbell plan dumbbells. */
export function presetForPlan(index: number, current: EquipmentPreset): EquipmentPreset {
  if (index === GYM_SPLIT_INDEX) return 'FULL_GYM'
  if (index === DUMBBELLS_INDEX && !presetEquipment(current).has('DUMBBELLS')) return 'DUMBBELLS_AND_BAR'
  return current
}

/** Height and age are needed before the goals step can continue, so the calorie target always uses them. */
export const bodyStatsReady = (s: OnbState): boolean => s.heightCm >= 120 && s.heightCm <= 230 && s.age >= 10 && s.age <= 100

/** Training days of the chosen starter plan (3 when starting empty), for the calorie suggestion. */
export type DaysOf = (planIndex: number) => number

const kcalFor = (s: OnbState, days: DaysOf) =>
  suggestedKcal(s.weightKg, s.heightCm, s.age, s.bodyType === 'MALE', days(s.planIndex), 0, s.goal,
    { base: DAY_BASE[s.dayActivity], cutPace: s.cutPace, gainPace: s.gainPace })

/** The lowest target this person can set by hand. */
export const floorOf = (s: OnbState): number => calorieFloor(s.weightKg, s.heightCm, s.age, s.bodyType === 'MALE')

/** Refreshes both suggestions unless the person already set the goals by hand (Android OnboardingViewModel). */
export function withSuggestions(s: OnbState, days: DaysOf): OnbState {
  if (s.goalsTouched) return s
  const protein = suggestedProtein(s.weightKg, proteinPerKgFor(s.goal, s.cutPace, s.gainPace), 0, s.heightCm)
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
    kcalGoal: Math.max(s.kcal, floorOf(s)),
    proteinGoal: Math.max(s.protein, 60),
    unit: unitCode(s.unit),
    heightCm: s.heightCm,
    birthYear: s.age > 0 ? thisYear - s.age : 0,
    equipment: encodePreset(s.preset),
    nutritionGoal: s.goal,
    goalSetDay: today,
    proteinPerKg: proteinPerKgFor(s.goal, s.cutPace, s.gainPace),
    dayActivity: s.dayActivity, cutPace: s.cutPace, gainPace: s.gainPace,
    targetWeightKg: s.weightKg, targetSetDay: today,
    // a new person starts on the new suggestion, so the one-time "more accurate target" card never shows
    calorieV2Seen: true,
    apiKey: key ?? c.apiKey,
  })
}

