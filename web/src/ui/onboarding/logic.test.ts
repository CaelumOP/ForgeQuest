import { describe, expect, it } from 'vitest'
import { DEFAULT_SETTINGS } from '../../data/settings'
import { bodyStatsReady, initialState, setWeight, settingsPatch, withSuggestions } from './logic'

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
  it('needs real height and age before the goals step continues', () => {
    expect(bodyStatsReady(initialState())).toBe(false)
    expect(bodyStatsReady({ ...initialState(), heightCm: 175, age: 30 })).toBe(true)
    expect(bodyStatsReady({ ...initialState(), heightCm: 1.75, age: 30 })).toBe(false)
  })
  it('uses height and age for calories once they are set', () => {
    const noBody = withSuggestions(initialState(), days)
    const tall = withSuggestions({ ...initialState(), heightCm: 190, age: 20 }, days)
    const short = withSuggestions({ ...initialState(), heightCm: 155, age: 60 }, days)
    expect(noBody.kcal).toBe(2200) // no height: 22 kcal/kg x (1.25 + 3 x 0.025) = 2186
    expect(tall.kcal).toBeGreaterThan(short.kcal)
  })
})
