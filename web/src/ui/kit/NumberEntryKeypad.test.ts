import { describe, expect, it } from 'vitest'
import { calculatePlates, DEFAULT_KG_PLATES } from '../../domain/plate-math'
import {
  createKeypadDraft,
  keypadApplyDelta,
  keypadBackspace,
  keypadPressDecimal,
  keypadPressDigit,
  keypadSetExact,
  parseKeypadText,
  resolveKeypadPlateBreakdown,
} from './NumberEntryKeypad'

describe('NumberEntryKeypad pure helpers', () => {
  it('replaces initial fresh value on first digit and handles decimal + backspace', () => {
    let d = createKeypadDraft(60, 2)
    d = keypadPressDigit(d, '8', 2)
    d = keypadPressDigit(d, '2', 2)
    d = keypadPressDecimal(d, 2)
    d = keypadPressDigit(d, '5', 2)
    expect(d.text).toBe('82.5')
    expect(parseKeypadText(d.text)).toBe(82.5)

    d = keypadBackspace(d)
    expect(d.text).toBe('82.')
    d = keypadBackspace(d)
    expect(d.text).toBe('82')
  })

  it('applies quick deltas, clamps bounds, and sets exact last-time value', () => {
    const start = createKeypadDraft(80, 2)
    const bumped = keypadApplyDelta(start, 2.5, 80, 0, 999, 2)
    expect(bumped.text).toBe('82.5')

    const clamped = keypadApplyDelta(bumped, 2000, 80, 0, 999, 2)
    expect(clamped.text).toBe('999')

    const exact = keypadSetExact(77.5, 0, 999, 2)
    expect(exact.text).toBe('77.5')
  })

  it('resolves barbell plate breakdown inside keypad when weight is at or above bar weight', () => {
    const calc = (w: number) => calculatePlates(w, 'KG', 20, DEFAULT_KG_PLATES)
    expect(resolveKeypadPlateBreakdown(null, 60, calc)?.perSide[0]).toEqual({ weight: 20, count: 1 })
    expect(resolveKeypadPlateBreakdown(15, 60, calc)).toBeNull()
    expect(resolveKeypadPlateBreakdown(0, 0, calc)).toBeNull()
    expect(resolveKeypadPlateBreakdown(60, 60, null)).toBeNull()
  })
})
