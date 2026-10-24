import { describe, expect, it } from 'vitest'
import { DEFAULT_SETTINGS } from '../../data/settings'
import { parseEquipmentSet } from '../../domain/exercise-swap-engine'
import { addNote, applyGoal, removeNote, stepWeekly, summary, toggleEquipment } from './helpers'

describe('profile helpers', () => {
  it('summary skips blanks', () => expect(summary('a', null, ' ', 'b', false)).toBe('a · b'))
  it('toggleEquipment keeps bodyweight', () => {
    const on = toggleEquipment(parseEquipmentSet('BODYWEIGHT'), 'DUMBBELLS')
    expect(on).toBe('BODYWEIGHT,DUMBBELLS')
    expect(toggleEquipment(parseEquipmentSet(on), 'BODYWEIGHT')).toBe('BODYWEIGHT,DUMBBELLS')
    expect(toggleEquipment(parseEquipmentSet(on), 'DUMBBELLS')).toBe('BODYWEIGHT')
  })
  it('notes add and remove', () => {
    const n = addNote(addNote('', ' sore  knee '), 'only rings')
    expect(n).toBe('sore knee\nonly rings')
    expect(removeNote(n, 'sore knee')).toBe('only rings')
  })
  it('weekly stepper returns 0 on the plan value and clamps', () => {
    expect(stepWeekly(4, 1, 5)).toBe(0)
    expect(stepWeekly(1, -1, 4)).toBe(1)
    expect(stepWeekly(7, 1, 3)).toBe(7)
  })
  it('applyGoal moves suggested targets and records the day', () => {
    const settings = { ...DEFAULT_SETTINGS, heightCm: 180, bodyType: 'MALE' as const }
    const i = { settings, latestKg: 80, age: 30, planDays: 4 }
    const next = applyGoal(i, 'CUT', 20000)
    expect(next.nutritionGoal).toBe('CUT')
    expect(next.goalSetDay).toBe(20000)
    expect(next.proteinPerKg).toBe(2)
    expect(applyGoal(i, 'MAINTAIN', 1)).toBe(settings)
  })
})
