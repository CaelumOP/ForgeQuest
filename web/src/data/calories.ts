// Calorie plan v2 on the web: the inputs behind a suggestion, and the one card Home shows about the calorie target
// (the one-time "more accurate target", the learned real burn, or one of the four signals). Android: CalorieCards.kt.
import { calorieSignals, loggedShare, nextTarget, realBurn } from '../domain/calorie-check';
import {
  ageFromBirthYear, bmrKcal, calorieFloor, CUT_PACE, DAY_BASE, dailyBurn, legacySuggestedKcal, suggestedKcal,
  type CutPace, type NutritionGoal,
} from '../domain/nutrition';
import { analyzeFatigueAndPlateaus } from '../domain/trends';
import type { ProgressSnapshot } from './progress';
import { trainingDays } from './workouts';
import { updateSettings, type Settings } from './settings';

/** Everything a calorie suggestion depends on; null without a logged weight. */
export type CalorieInputs = {
  kg: number; heightCm: number; age: number; male: boolean; trainingDays: number; bodyFatPct: number; base: number;
}

export function calorieInputs(s: Settings, latestKg: number | null, planDays: number, thisYear = new Date().getFullYear()): CalorieInputs | null {
  if (latestKg == null) return null
  return {
    kg: latestKg, heightCm: s.heightCm, age: ageFromBirthYear(s.birthYear, thisYear), male: s.bodyType === 'MALE',
    trainingDays: s.weeklyTargetOverride > 0 ? s.weeklyTargetOverride : planDays, bodyFatPct: s.bodyFatPct,
    base: DAY_BASE[s.dayActivity],
  }
}

export const suggestionFor = (i: CalorieInputs, s: Pick<Settings, 'nutritionGoal' | 'cutPace' | 'gainPace'>, goal: NutritionGoal = s.nutritionGoal, burn: number | null = null): number =>
  suggestedKcal(i.kg, i.heightCm, i.age, i.male, i.trainingDays, i.bodyFatPct, goal, { base: i.base, cutPace: s.cutPace, gainPace: s.gainPace, burn })
export const burnFor = (i: CalorieInputs): number => dailyBurn(i.kg, i.heightCm, i.age, i.male, i.trainingDays, i.bodyFatPct, i.base)
export const floorFor = (i: CalorieInputs): number => calorieFloor(i.kg, i.heightCm, i.age, i.male, i.bodyFatPct)

/** Saves a new calorie target, remembering when and at what weight, so updates wait 14 days and a 2.5 kg change shows. */
export const setTarget = (kcal: number, latestKg: number | null, today: number) =>
  updateSettings((s) => ({ ...s, kcalGoal: kcal, targetSetDay: today, targetWeightKg: latestKg ?? s.targetWeightKg }))

export type CalorieCard =
  | { kind: 'MIGRATION'; from: number; to: number }
  | { kind: 'ADAPTIVE'; burn: number; to: number }
  | { kind: 'UNDER_FUELLED'; pace: CutPace }
  | { kind: 'WEIGHT_CHANGED'; to: number }
  | { kind: 'SCALE_CREEP'; to: number }
  | { kind: 'STEP_DROP' }

const gentler = (p: CutPace): CutPace => (p === 'AGGRESSIVE' ? 'STANDARD' : 'GENTLE')

/**
 * The one calorie card for Home, or null. Order: the one-time "more accurate target" card for people still on the
 * old suggestion, then the learned real burn, then the signals (under-fuelled, weight changed, scale creep, steps).
 * Nothing while snoozed or without the body numbers a suggestion needs.
 */
export function calorieCard(snap: ProgressSnapshot, today: number): CalorieCard | null {
  const s = snap.settings
  const planDays = snap.plan ? trainingDays(snap.plan).size : 0
  const i = calorieInputs(s, snap.latestWeight, planDays)
  if (!i || i.heightCm <= 0 || i.age <= 0) return null
  const suggested = suggestionFor(i, s)
  if (!s.calorieV2Seen) {
    const legacy = legacySuggestedKcal(i.kg, i.heightCm, i.age, i.male, i.trainingDays, i.bodyFatPct, s.nutritionGoal)
    if (s.kcalGoal === legacy && Math.abs(suggested - legacy) >= 50) return { kind: 'MIGRATION', from: legacy, to: suggested }
  }
  if (today < s.calorieSnoozeUntil) return null

  const bmr = bmrKcal(i.kg, i.heightCm, i.age, i.male, i.bodyFatPct)
  const intake = new Map(snap.nutritionByDay.map((d) => [d.epochDay, d.kcal]))
  const weights = snap.weights.map((w) => [w.epochDay, w.kg] as const)
  const burn = realBurn(intake, weights, today, s.goalSetDay, bmr, burnFor(i))
  if (burn) {
    const to = nextTarget(s.kcalGoal, suggestionFor(i, s, s.nutritionGoal, burn.kcal), s.targetSetDay, today)
    if (to != null) return { kind: 'ADAPTIVE', burn: burn.kcal, to }
  }

  const deload = analyzeFatigueAndPlateaus(snap.sessions as never, snap.doneSets as never, snap.exercises as never, today)
  const signals = calorieSignals({
    goal: s.nutritionGoal, cutPace: s.cutPace, goalSetDay: s.goalSetDay, today, weights,
    loggedShare: loggedShare(intake, today, bmr), steps: new Map(), deloadNeeded: deload.stalledLifts.length >= 2, // no readiness on the web
    targetWeightKg: s.targetWeightKg,
  })
  for (const signal of signals) {
    if (signal === 'UNDER_FUELLED') return { kind: 'UNDER_FUELLED', pace: gentler(s.cutPace) }
    if (signal === 'WEIGHT_CHANGED' && suggested !== s.kcalGoal) return { kind: 'WEIGHT_CHANGED', to: suggested }
    if (signal === 'SCALE_CREEP') {
      const to = Math.max(s.kcalGoal - 150, floorFor(i))
      if (to < s.kcalGoal) return { kind: 'SCALE_CREEP', to }
    }
    if (signal === 'STEP_DROP') return { kind: 'STEP_DROP' }
  }
  return null
}

/** Applying a card: the target (or pace) changes and the card goes away. */
export async function acceptCalorieCard(card: CalorieCard, snap: ProgressSnapshot, today: number): Promise<void> {
  if (card.kind === 'MIGRATION') await updateSettings((s) => ({ ...s, calorieV2Seen: true }))
  if (card.kind === 'UNDER_FUELLED') {
    const planDays = snap.plan ? trainingDays(snap.plan).size : 0
    const i = calorieInputs(snap.settings, snap.latestWeight, planDays)
    const to = i ? suggestionFor(i, { ...snap.settings, cutPace: card.pace }) : snap.settings.kcalGoal
    await updateSettings((s) => ({ ...s, cutPace: card.pace, proteinPerKg: s.proteinPerKg === CUT_PACE[s.cutPace].proteinPerKg ? CUT_PACE[card.pace].proteinPerKg : s.proteinPerKg }))
    await setTarget(to, snap.latestWeight, today)
    return
  }
  if (card.kind === 'STEP_DROP') { await snoozeCalorieCards(today); return }
  await setTarget(card.to, snap.latestWeight, today)
}

/** "Not now" (or "Keep mine" on the one-time card): hides calorie cards for 14 days. */
export const snoozeCalorieCards = (today: number, migration = false) =>
  updateSettings((s) => ({ ...s, calorieSnoozeUntil: today + 14, calorieV2Seen: migration ? true : s.calorieV2Seen }))
