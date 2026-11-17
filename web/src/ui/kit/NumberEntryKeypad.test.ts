import { describe, expect, it } from 'vitest'
import {
  createKeypadDraft,
  keypadApplyDelta,
  keypadBackspace,
  keypadPressDecimal,
  keypadPressDigit,
  keypadSetExact,
  parseKeypadText,
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
})
