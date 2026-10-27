import { describe, expect, it } from 'vitest'
import { stepReorder } from './reorder'

const h = () => 60

describe('stepReorder', () => {
  it('does nothing before half a row', () => {
    expect(stepReorder([1, 2, 3], 1, 29, h)).toEqual({ order: [1, 2, 3], offset: 29, swapped: false })
  })
  it('swaps down past half the next row and keeps the remainder', () => {
    expect(stepReorder([1, 2, 3], 1, 35, h)).toEqual({ order: [2, 1, 3], offset: -25, swapped: true })
  })
  it('swaps up', () => {
    expect(stepReorder([1, 2, 3], 3, -40, h)).toEqual({ order: [1, 3, 2], offset: 20, swapped: true })
  })
  it('stops at the ends', () => {
    expect(stepReorder([1, 2], 1, -100, h).swapped).toBe(false)
    expect(stepReorder([1, 2], 2, 100, h).swapped).toBe(false)
  })
})
