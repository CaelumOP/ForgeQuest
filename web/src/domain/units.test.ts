import { describe, expect, it } from 'vitest'
import { formatWeight, fromKg, toKg, unitFromCodeOrLabel, weightNumber } from './units'

describe('units', () => {
  it('round-trips pounds and formats like Android', () => {
    expect(fromKg('LB', toKg('LB', 135))).toBeCloseTo(135, 3)
    expect(formatWeight('KG', 72.5, false)).toBe('⁦72.5 kg⁩')
    expect(formatWeight('KG', 72.5, true)).toBe('⁧72.5 كغ⁩')
    expect(weightNumber('KG', 60)).toBe('60')
    expect(weightNumber('KG', 62.5)).toBe('62.5')
  })

  it('reads codes and Arabic labels', () => {
    expect(unitFromCodeOrLabel('LBS')).toBe('LB')
    expect(unitFromCodeOrLabel('رطل')).toBe('LB')
    expect(unitFromCodeOrLabel('كغ')).toBe('KG')
    expect(unitFromCodeOrLabel(null)).toBe('KG')
  })
})
