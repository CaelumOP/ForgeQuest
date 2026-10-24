import { describe, expect, it } from 'vitest'
import { DEFAULT_SETTINGS } from '../../data/settings'
import { initialState, setWeight, settingsPatch, withSuggestions } from './logic'

const days = () => 3

describe('onboarding logic', () => {
  it('suggests protein and calories from weight and goal', () => {
    const s = setWeight(initialState(), 80, days)
    expect(s.weightKg).toBe(80)
    expect(s.protein).toBeGreaterThanOrEqual(140)
    expect(s.kcal % 50).toBe(0)
    const cut = withSuggestions({ ...s, goal: 'CUT' }, days)
    expect(cut.kcal).toBeLessThan(s.kcal)
  })
  it('keeps hand-set goals when the weight changes', () => {
    const s = setWeight({ ...initialState(), goalsTouched: true, kcal: 3000, protein: 200 }, 90, days)
    expect([s.kcal, s.protein]).toEqual([3000, 200])
  })
  it('clamps weight to 30..250 kg', () => {
    expect(setWeight(initialState(), 10, days).weightKg).toBe(30)
    expect(setWeight(initialState(), 999, days).weightKg).toBe(250)
  })
  it('builds the settings written on finish', () => {
    const s = { ...initialState(), name: '  Sam ', unit: 'LB' as const, age: 30, kcal: 900, protein: 20 }
    const out = settingsPatch(s, 20000, 2026, null)({ ...DEFAULT_SETTINGS, apiKey: 'old' })
    expect(out).toMatchObject({ onboarded: true, name: 'Sam', unit: 'lb', birthYear: 1996, kcalGoal: 1200, proteinGoal: 60, goalSetDay: 20000, apiKey: 'old' })
    expect(settingsPatch(s, 1, 2026, 'k')(DEFAULT_SETTINGS).apiKey).toBe('k')
  })
})
