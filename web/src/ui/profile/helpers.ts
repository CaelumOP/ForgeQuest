// Pure helpers of the Profile screen (ports of the small lambdas in ProfileScreen.kt / ProfileViewModel).
import {
  MIN_FAT_SHARE_PCT, proteinPerKgFor, suggestedCarbs, suggestedKcal, suggestedProtein,
  type NutritionGoal,
} from '../../domain/nutrition'
import { EQUIPMENT, type Equipment } from '../../domain/exercise-swap-engine'
import type { Settings } from '../../data/settings'

/** "a · b · c" of the non-blank parts, like Kotlin summary(). */
export const summary = (...parts: (string | null | undefined | false)[]): string =>
  parts.filter((p): p is string => !!p && p.trim() !== '').join(' · ')

/** Encoded equipment setting after ticking/unticking one item; bodyweight is always included. */
export function toggleEquipment(current: ReadonlySet<Equipment>, item: Equipment): string {
  const next = new Set<Equipment>(current)
  if (next.has(item)) next.delete(item); else next.add(item)
  next.add('BODYWEIGHT')
  return EQUIPMENT.filter((e) => next.has(e)).join(',')
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
    : suggestedKcal(latestKg, s.heightCm, i.age, s.bodyType === 'MALE', trainingDaysFor(s, i.planDays), s.bodyFatPct, goal)
}

export const proteinFor = (i: GoalInputs, perKg: number): number | null =>
  i.latestKg == null ? null : suggestedProtein(i.latestKg, Math.max(perKg, 1.6), i.settings.bodyFatPct)

export const carbsFor = (i: GoalInputs, kcal: number, protein: number): number =>
  suggestedCarbs(kcal, protein, Math.max(i.settings.fatSharePct, MIN_FAT_SHARE_PCT), i.latestKg ?? 0)

/** Switching goal: targets still on the old suggestion follow the new goal, numbers set by hand stay. */
export function applyGoal(i: GoalInputs, goal: NutritionGoal, today: number): Settings {
  const s = i.settings
  if (goal === s.nutritionGoal) return s
  const perKg = s.proteinPerKg === proteinPerKgFor(s.nutritionGoal) ? proteinPerKgFor(goal) : s.proteinPerKg
  const kcal = hasBodyStats(s, i.age) && kcalFor(i, s.nutritionGoal) === s.kcalGoal ? kcalFor(i, goal) : null
  const protein = proteinFor(i, s.proteinPerKg) === s.proteinGoal ? proteinFor(i, perKg) : null
  const oldCarbs = carbsFor(i, s.kcalGoal, s.proteinGoal)
  const carbs = s.trackCarbs && oldCarbs === s.carbsGoal ? carbsFor(i, kcal ?? s.kcalGoal, protein ?? s.proteinGoal) : null
  return {
    ...s, nutritionGoal: goal, goalSetDay: today, proteinPerKg: perKg,
    kcalGoal: kcal ?? s.kcalGoal, proteinGoal: protein ?? s.proteinGoal, carbsGoal: carbs ?? s.carbsGoal,
  }
}
