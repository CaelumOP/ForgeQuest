import { describe, expect, it } from 'vitest'
import { calorieCard } from './calories'
import { DEFAULT_SETTINGS, type Settings } from './settings'
import type { ProgressSnapshot } from './progress'

// The desk worker from the calorie plan: 83 kg, 172 cm, 20 y, 5 training days a week (set by hand, no plan needed).
const today = 20000
const year = new Date().getFullYear()
const snap = (patch: Partial<Settings>, weights: [number, number][] = []): ProgressSnapshot => ({
  settings: { ...DEFAULT_SETTINGS, bodyType: 'MALE', heightCm: 172, birthYear: year - 20, weeklyTargetOverride: 5, nutritionGoal: 'CUT', goalSetDay: today - 3, ...patch },
  plan: null, latestWeight: 83, nutritionByDay: [], weights: weights.map(([epochDay, kg]) => ({ epochDay, kg, loggedAt: 0 })),
  sessions: [], doneSets: [], exercises: new Map(),
} as unknown as ProgressSnapshot)

describe('Home calorie card', () => {
  it('offers the new target once to people still on the old suggestion', () => {
    expect(calorieCard(snap({ kcalGoal: 2300 }), today)).toEqual({ kind: 'MIGRATION', from: 2300, to: 1850 })
    expect(calorieCard(snap({ kcalGoal: 2300, calorieV2Seen: true }), today)).toBeNull()
    // a target set by hand is left alone
    expect(calorieCard(snap({ kcalGoal: 2000 }), today)).toBeNull()
  })
  it('offers a fresh suggestion after a 2.5 kg change, unless snoozed', () => {
    const changed = snap({ kcalGoal: 2000, calorieV2Seen: true, targetWeightKg: 86 }, [[today - 3, 83], [today - 1, 83]])
    expect(calorieCard(changed, today)).toEqual({ kind: 'WEIGHT_CHANGED', to: 1850 })
    expect(calorieCard({ ...changed, settings: { ...changed.settings, calorieSnoozeUntil: today + 5 } }, today)).toBeNull()
  })
  it('stays quiet without height and age', () => {
    expect(calorieCard(snap({ kcalGoal: 2300, heightCm: 0 }), today)).toBeNull()
  })
})
