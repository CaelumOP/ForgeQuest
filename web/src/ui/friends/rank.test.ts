import { describe, expect, it } from 'vitest'
import { englishOrdinalSuffix, formatOrdinal, rankTier } from './rank'

describe('englishOrdinalSuffix and formatOrdinal', () => {
  it('handles 1-13, 21-24, 101-103, and 111-114', () => {
    const cases: Array<[number, 'st' | 'nd' | 'rd' | 'th']> = [
      [1, 'st'],
      [2, 'nd'],
      [3, 'rd'],
      [4, 'th'],
      [10, 'th'],
      [11, 'th'],
      [12, 'th'],
      [13, 'th'],
      [14, 'th'],
      [20, 'th'],
      [21, 'st'],
      [22, 'nd'],
      [23, 'rd'],
      [24, 'th'],
      [100, 'th'],
      [101, 'st'],
      [102, 'nd'],
      [103, 'rd'],
      [111, 'th'],
      [112, 'th'],
      [113, 'th'],
      [114, 'th'],
      [121, 'st'],
      [122, 'nd'],
      [123, 'rd'],
    ]
    for (const [rank, suffix] of cases) {
      expect(englishOrdinalSuffix(rank)).toBe(suffix)
      expect(formatOrdinal(rank)).toBe(`${rank}${suffix}`)
    }
  })

  it('maps top 3 to medal tiers and others to neutral', () => {
    expect(rankTier(1)).toBe('gold')
    expect(rankTier(2)).toBe('silver')
    expect(rankTier(3)).toBe('bronze')
    expect(rankTier(4)).toBe('neutral')
    expect(rankTier(null)).toBe('neutral')
  })
})
