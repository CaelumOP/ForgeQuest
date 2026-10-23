import { describe, expect, it } from 'vitest'
import { effortOf, parseEffort } from './effort'

// Audit #8: one word list decides effort from an old note, for progression and recovery alike.
describe('effortOf', () => {
  it('reads hard, easy and pain words in English and Arabic', () => {
    expect(effortOf('', 'to failure')).toBe('HARD')
    expect(effortOf('', 'صعب')).toBe('HARD')
    expect(effortOf('', 'فشل في آخر عدة')).toBe('HARD')
    expect(effortOf('', 'felt easy')).toBe('EASY')
    expect(effortOf('', 'RIR 3')).toBe('EASY')
    expect(effortOf('', 'سهل')).toBe('EASY')
    expect(effortOf('', 'ألم في الركبة')).toBe('PAIN')
  })

  it('pain beats hard beats easy, and a tapped chip beats the note', () => {
    expect(effortOf('', 'easy until a twinge')).toBe('PAIN')
    expect(effortOf('', 'easy start, rpe 10 finish')).toBe('HARD')
    expect(effortOf('OK', 'failure')).toBe('OK')
  })

  it('parses stored names, unknown is OK', () => {
    expect(parseEffort('HARD')).toBe('HARD')
    expect(parseEffort('hard')).toBe('OK')
    expect(parseEffort(null)).toBe('OK')
  })
})
