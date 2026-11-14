// Pure helpers of the Profile screen (ports of the small lambdas in ProfileScreen.kt / ProfileViewModel).
import {
  DAY_BASE, MIN_FAT_SHARE_PCT, proteinPerKgFor, suggestedCarbs, suggestedKcal, suggestedProtein,
  type NutritionGoal,
} from '../../domain/nutrition'
import { getLang, plural, t } from '../../i18n'
import {
  ALL_ACTIVE_EQUIPMENT, expandLegacyEquipment, matchTrainingPlace, TRAINING_PLACE_INFO, type Equipment,
} from '../../domain/exercise-swap-engine'
import type { Settings } from '../../data/settings'

/** "a · b · c" of the non-blank parts, like Kotlin summary(). */
export const summary = (...parts: (string | null | undefined | false)[]): string =>
  parts.filter((p): p is string => !!p && p.trim() !== '').join(' · ')

/** Encoded equipment setting after ticking/unticking one item; bodyweight is always included. */
export function toggledEquipmentSet(current: ReadonlySet<Equipment>, item: Equipment): Set<Equipment> {
  const next = expandLegacyEquipment(current)
  if (item !== 'BODYWEIGHT') {
    if (next.has(item)) next.delete(item); else next.add(item)
  }
  next.add('BODYWEIGHT')
  return next
}

export function toggleEquipment(current: ReadonlySet<Equipment>, item: Equipment): string {
  const next = toggledEquipmentSet(current, item)
  return ALL_ACTIVE_EQUIPMENT.filter((e) => next.has(e)).join(',')
}

export function equipmentSummary(configured: ReadonlySet<Equipment>): string {
  const clean = expandLegacyEquipment(configured)
  if (clean.size === 0) return t('summary_equipment_unset')
  const place = matchTrainingPlace(clean)
  if (place) return getLang() === 'ar' ? TRAINING_PLACE_INFO[place].labelAr : TRAINING_PLACE_INFO[place].labelEn
  const count = [...clean].filter((e) => e !== 'BODYWEIGHT').length
  const countLabel = plural('equipment_item_count', count, count)
  return count === 0 ? countLabel : `${t('equipment_custom')} · ${countLabel}`
}

export function gearCustomizeSummary(equipment: ReadonlySet<Equipment>): string {
  const clean = expandLegacyEquipment(equipment)
  const place = matchTrainingPlace(clean)
  const placeLabel = place
    ? (getLang() === 'ar' ? TRAINING_PLACE_INFO[place].labelAr : TRAINING_PLACE_INFO[place].labelEn)
    : t('equipment_custom')
  const count = [...clean].filter((e) => e !== 'BODYWEIGHT').length
  const countLabel = plural('equipment_item_count', count, count)
  return t('equipment_customize_row', placeLabel, countLabel)
}

export const noteLines = (notes: string): string[] => notes.split('\n').map((l) => l.trim()).filter(Boolean)
export const addNote = (notes: string, note: string): string => {
  const n = note.trim().replace(/\s+/g, ' ')
  return n ? [...noteLines(notes), n].join('\n') : notes
}
export const removeNote = (notes: string, note: string): string => noteLines(notes).filter((l) => l !== note).join('\n')

/** Workouts per week stepper: 0 means "follow the plan" whenever the value equals the plan's days. */
export const stepWeekly = (shown: number, delta: 1 | -1, planDays: number): number => {
  const n = Math.min(7, Math.max(1, shown + delta))
  return n === planDays ? 0 : n
}

export type GoalInputs = { settings: Settings; latestKg: number | null; age: number; planDays: number }

export const trainingDaysFor = (s: Settings, planDays: number): number => (s.weeklyTargetOverride > 0 ? s.weeklyTargetOverride : planDays)
export const hasBodyStats = (s: Settings, age: number): boolean => (s.heightCm > 0 && age > 0) || (s.bodyFatPct >= 4 && s.bodyFatPct <= 60)

export function kcalFor(i: GoalInputs, goal: NutritionGoal): number | null {
  const { settings: s, latestKg } = i
  return latestKg == null ? null
    : suggestedKcal(latestKg, s.heightCm, i.age, s.bodyType === 'MALE', trainingDaysFor(s, i.planDays), s.bodyFatPct, goal,
      { base: DAY_BASE[s.dayActivity], cutPace: s.cutPace, gainPace: s.gainPace })
}

export const proteinFor = (i: GoalInputs, perKg: number): number | null =>
  i.latestKg == null ? null : suggestedProtein(i.latestKg, Math.max(perKg, 1.6), i.settings.bodyFatPct, i.settings.heightCm)

export const carbsFor = (i: GoalInputs, kcal: number, protein: number): number =>
  suggestedCarbs(kcal, protein, Math.max(i.settings.fatSharePct, MIN_FAT_SHARE_PCT), i.latestKg ?? 0)

export type NutritionPatch = Partial<Pick<Settings, 'nutritionGoal' | 'cutPace' | 'gainPace' | 'dayActivity'>>

/**
 * Changing the goal, pace or normal day: targets still on the old suggestion follow the new one, numbers set by hand
 * stay. A new goal restarts its clock (trend notes, diet break); a changed calorie target records when and at what weight.
 */
export function applyNutrition(i: GoalInputs, patch: NutritionPatch, today: number): Settings {
  const s = i.settings
  const next: Settings = { ...s, ...patch }
  const ni = { ...i, settings: next }
  const oldPerKg = proteinPerKgFor(s.nutritionGoal, s.cutPace, s.gainPace)
  const perKg = s.proteinPerKg === oldPerKg ? proteinPerKgFor(next.nutritionGoal, next.cutPace, next.gainPace) : s.proteinPerKg
  const kcal = hasBodyStats(s, i.age) && kcalFor(i, s.nutritionGoal) === s.kcalGoal ? kcalFor(ni, next.nutritionGoal) : null
  const protein = proteinFor(i, s.proteinPerKg) === s.proteinGoal ? proteinFor(ni, perKg) : null
  const oldCarbs = carbsFor(i, s.kcalGoal, s.proteinGoal)
  const carbs = s.trackCarbs && oldCarbs === s.carbsGoal ? carbsFor(ni, kcal ?? s.kcalGoal, protein ?? s.proteinGoal) : null
  const kcalChanged = kcal != null && kcal !== s.kcalGoal
  return {
    ...next,
    goalSetDay: next.nutritionGoal !== s.nutritionGoal ? today : s.goalSetDay,
    proteinPerKg: perKg, kcalGoal: kcal ?? s.kcalGoal, proteinGoal: protein ?? s.proteinGoal, carbsGoal: carbs ?? s.carbsGoal,
    targetSetDay: kcalChanged ? today : s.targetSetDay, targetWeightKg: kcalChanged ? i.latestKg ?? s.targetWeightKg : s.targetWeightKg,
  }
}

/** Switching goal (kept for its callers and tests). */
export const applyGoal = (i: GoalInputs, goal: NutritionGoal, today: number): Settings =>
  goal === i.settings.nutritionGoal ? i.settings : applyNutrition(i, { nutritionGoal: goal }, today)

/** The share of calories that protein + carb grams leave for fat, or null when kcalGoal <= 0. */
export const impliedFatPct = (s: Pick<Settings, 'kcalGoal' | 'proteinGoal' | 'carbsGoal'>): number | null =>
  s.kcalGoal > 0 ? Math.min(100, Math.max(0, Math.round(((s.kcalGoal - 4 * (s.proteinGoal + s.carbsGoal)) * 100) / s.kcalGoal))) : null
