import type { Settings } from '../../data/settings'
import { BODYWEIGHT_INDEX, DUMBBELLS_INDEX, GYM_SPLIT_INDEX } from '../../data/seed/starter-plans'
import { encodePreset, presetEquipment, type EquipmentPreset } from '../../domain/exercise-swap-engine'
import {
  calorieFloor, DAY_BASE, proteinPerKgFor, suggestedKcal, suggestedProtein, type CutPace, type DayActivity, type GainPace, type NutritionGoal,
} from '../../domain/nutrition'
import type { WeightUnit } from '../../domain/units'
import { unitCode } from '../../domain/units'
import type { BodyType } from '../../data/settings'

export const LAST_STEP = 3 // language, name, plan (equipment + days), key
export const DAY_OPTIONS = [2, 3, 4, 5] as const
export const DEFAULT_DAYS = 3

/** Body and goal fields are only used by the food-targets setup that Home offers after the first workout. */
export type OnbState = {
  step: number
  name: string
  bodyType: BodyType
  daysPerWeek: number
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

/**
 * The starter plan to show as chosen for [days] a week and the picked equipment. Plans: 0 Hemo (5 days), 1 bodyweight and
 * 2 dumbbells (both 3 days), 3 gym split (6 days). 5 gets Hemo (bodyweight when there is nothing to hang from); 2, 3 and
 * 4 get the 3-day full body, the gentlest plan that is nearest.
 */
export function planForDays(days: number, preset: EquipmentPreset): number {
  if (days >= 5) return preset === 'BODYWEIGHT_ONLY' ? BODYWEIGHT_INDEX : 0
  return presetEquipment(preset).has('DUMBBELLS') ? DUMBBELLS_INDEX : BODYWEIGHT_INDEX
}

export const initialState = (): OnbState => ({
  step: 0, name: '', bodyType: 'MALE', daysPerWeek: DEFAULT_DAYS, weightKg: 75, unit: 'KG', heightCm: 0, age: 0, kcal: 2500, protein: 135,
  goalsTouched: false, goal: 'MAINTAIN', dayActivity: 'SITTING', cutPace: 'STANDARD', gainPace: 'STANDARD',
  planIndex: planForDays(DEFAULT_DAYS, 'CALISTHENICS_PARK'), preset: 'CALISTHENICS_PARK',
})

/** The equipment to show after picking starter plan [index]: the gym split needs a gym, the dumbbell plan dumbbells. */
export function presetForPlan(index: number, current: EquipmentPreset): EquipmentPreset {
  if (index === GYM_SPLIT_INDEX) return 'FULL_GYM'
  if (index === DUMBBELLS_INDEX && !presetEquipment(current).has('DUMBBELLS')) return 'DUMBBELLS_AND_BAR'
  return current
}

/** Height and age are needed before the food targets can be calculated. */
export const bodyStatsReady = (s: OnbState): boolean => s.heightCm >= 120 && s.heightCm <= 230 && s.age >= 10 && s.age <= 100

const kcalFor = (s: OnbState) =>
  suggestedKcal(s.weightKg, s.heightCm, s.age, s.bodyType === 'MALE', s.daysPerWeek, 0, s.goal,
    { base: DAY_BASE[s.dayActivity], cutPace: s.cutPace, gainPace: s.gainPace })

/** The lowest target this person can set by hand. */
export const floorOf = (s: OnbState): number => calorieFloor(s.weightKg, s.heightCm, s.age, s.bodyType === 'MALE')

/** Refreshes both suggestions unless the person already set the goals by hand (Android OnboardingViewModel). */
export function withSuggestions(s: OnbState): OnbState {
  if (s.goalsTouched) return s
  const protein = suggestedProtein(s.weightKg, proteinPerKgFor(s.goal, s.cutPace, s.gainPace), 0, s.heightCm)
  const next = { ...s, protein }
  return { ...next, kcal: kcalFor(next) }
}

export const setWeight = (s: OnbState, kg: number): OnbState =>
  withSuggestions({ ...s, weightKg: Math.min(250, Math.max(30, kg)) })

/** Settings written when onboarding finishes. The key is only kept when it was checked and works. */
export function settingsPatch(s: OnbState, key: string | null): (c: Settings) => Settings {
  return (c) => ({
    ...c,
    onboarded: true,
    name: s.name.trim(),
    bodyType: s.bodyType,
    equipment: encodePreset(s.preset),
    weeklyTargetOverride: s.daysPerWeek,
    // a new person starts on the new suggestion, so the one-time "more accurate target" card never shows
    calorieV2Seen: true,
    apiKey: key ?? c.apiKey,
  })
}

/** Settings written when the food-targets setup is saved (what onboarding used to write for body and goal). */
export function foodTargetsPatch(s: OnbState, today: number, thisYear: number): (c: Settings) => Settings {
  return (c) => ({
    ...c,
    bodyType: s.bodyType,
    kcalGoal: Math.max(s.kcal, floorOf(s)),
    proteinGoal: Math.max(s.protein, 60),
    unit: unitCode(s.unit),
    heightCm: s.heightCm,
    birthYear: s.age > 0 ? thisYear - s.age : 0,
    nutritionGoal: s.goal,
    goalSetDay: today,
    proteinPerKg: proteinPerKgFor(s.goal, s.cutPace, s.gainPace),
    dayActivity: s.dayActivity, cutPace: s.cutPace, gainPace: s.gainPace,
    targetWeightKg: s.weightKg, targetSetDay: today,
    calorieV2Seen: true,
  })
}

/** Home offers the food-targets card once a workout is finished and height was never given. */
export const needsFoodTargets = (heightCm: number, finishedWorkouts: number): boolean => heightCm <= 0 && finishedWorkouts > 0
