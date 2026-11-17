import { t } from '../../i18n'
import type { BodyType, Settings } from '../../data/settings'
import { BODYWEIGHT_INDEX, DUMBBELLS_INDEX, GYM_SPLIT_INDEX } from '../../data/seed/starter-plans'
import type { ExerciseEntity, PlanDraft } from '../../domain/entities'
import { encodePreset, presetEquipment, type EquipmentPreset } from '../../domain/exercise-swap-engine'
import {
  calorieFloor, DAY_BASE, proteinPerKgFor, suggestedKcal, suggestedProtein, type CutPace, type DayActivity, type GainPace, type NutritionGoal,
} from '../../domain/nutrition'
import { buildPlan, spreadDays, type Experience, type TrainGoal } from '../../domain/plan-builder'
import type { WeightUnit } from '../../domain/units'
import { unitCode, unitFromCodeOrLabel } from '../../domain/units'
import { localizeBuiltPlan } from '../plan/helpers'

export const LAST_STEP = 5 // 0=language, 1=welcome, 2=about, 3=goal, 4=training, 5=weight
export const DAY_OPTIONS = [2, 3, 4, 5, 6] as const
export const DEFAULT_DAYS = 3

/** Body and goal fields are also used by the food-targets setup that Home offers after the first workout. */
export type OnbState = {
  step: number
  name: string
  bodyType: BodyType
  daysPerWeek: number
  /** "Just log my training" picked on the goal step: no plan, streaks, levels, badges or readiness. */
  justLog: boolean
  weightKg: number
  weightTouched: boolean
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
  experience: Experience
  builtForMe: boolean
}

/** Maps onboarding nutrition goal to PlanBuilder's TrainGoal. */
export function trainGoalFor(goal: NutritionGoal): TrainGoal {
  switch (goal) {
    case 'GAIN': return 'MUSCLE'
    case 'CUT': return 'FAT_LOSS'
    case 'MAINTAIN': return 'GENERAL'
  }
}

/** Builds and localizes the "Built for me" plan draft (Android OnboardingScreen.kt localizeBuiltPlan). */
export function localizeOnboardingPlan(
  s: Pick<OnbState, 'goal' | 'daysPerWeek' | 'preset' | 'experience'>,
  library: readonly ExerciseEntity[],
  tr: (key: string, ...args: (string | number)[]) => string = t,
): PlanDraft {
  const trainGoal = trainGoalFor(s.goal)
  const built = buildPlan(
    {
      goal: trainGoal,
      days: spreadDays(s.daysPerWeek),
      minutes: 45,
      equipment: presetEquipment(s.preset),
      experience: s.experience,
    },
    library,
  )
  return localizeBuiltPlan(built, trainGoal, tr)
}

/** Next onboarding step (skips step 4 Training when justLog is active). */
export function nextStep(s: Pick<OnbState, 'step' | 'justLog'>): number {
  if (s.step === 3 && s.justLog) return 5
  return Math.min(LAST_STEP, s.step + 1)
}

/** Previous onboarding step (skips step 4 Training when justLog is active). */
export function prevStep(s: Pick<OnbState, 'step' | 'justLog'>): number {
  if (s.step === 5 && s.justLog) return 3
  return Math.max(0, s.step - 1)
}

/** 1-based progress index/total for steps after language. */
export function progressStep(s: Pick<OnbState, 'step' | 'justLog'>): { current: number; total: number } {
  const steps = s.justLog ? [1, 2, 3, 5] : [1, 2, 3, 4, 5]
  const idx = steps.indexOf(s.step)
  return { current: (idx >= 0 ? idx : 0) + 1, total: steps.length }
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
  step: 0, name: '', bodyType: 'MALE', daysPerWeek: DEFAULT_DAYS, justLog: false, weightKg: 75, weightTouched: false,
  unit: 'KG', heightCm: 0, age: 0, kcal: 2500, protein: 135,
  goalsTouched: false, goal: 'MAINTAIN', dayActivity: 'SITTING', cutPace: 'STANDARD', gainPace: 'STANDARD',
  planIndex: planForDays(DEFAULT_DAYS, 'CALISTHENICS_PARK'), preset: 'CALISTHENICS_PARK',
  experience: 'SOME', builtForMe: true,
})

/** Pre-fills the later food-targets setup from Settings (preserving the saved nutritionGoal). */
export function foodSetupInitialState(settings: Settings, latestWeight: number | null): OnbState {
  return withSuggestions({
    ...initialState(),
    step: 0,
    bodyType: settings.bodyType,
    unit: unitFromCodeOrLabel(settings.unit),
    weightKg: latestWeight ?? 75,
    weightTouched: latestWeight != null,
    daysPerWeek: settings.weeklyTargetOverride > 0 ? settings.weeklyTargetOverride : DEFAULT_DAYS,
    goal: settings.nutritionGoal,
  })
}

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

/** Goal card picked. "Just log" is Maintain with no plan (as Android), so a Gain/Cut picked before it never sticks. */
export function pickGoal(s: OnbState, v: NutritionGoal | 'JUST_LOG'): OnbState {
  return v === 'JUST_LOG'
    ? withSuggestions({ ...s, goal: 'MAINTAIN', justLog: true })
    : withSuggestions({ ...s, goal: v, justLog: false })
}

export const setWeight = (s: OnbState, kg: number): OnbState =>
  withSuggestions({ ...s, weightKg: Math.min(250, Math.max(30, kg)) })

/** Settings written when onboarding finishes. */
export function settingsPatch(s: OnbState): (c: Settings) => Settings {
  return (c) => ({
    ...c,
    onboarded: true,
    name: s.name.trim(),
    bodyType: s.bodyType,
    unit: unitCode(s.unit),
    nutritionGoal: s.goal,
    equipment: encodePreset(s.preset),
    weeklyTargetOverride: s.daysPerWeek,
    justLog: s.justLog,
    // a new person starts on the new suggestion, so the one-time "more accurate target" card never shows
    calorieV2Seen: true,
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
