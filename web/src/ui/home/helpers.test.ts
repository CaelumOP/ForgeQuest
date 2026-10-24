import { describe, expect, it } from 'vitest'
import { canExcuse, greetingKey } from './helpers'

describe('home helpers', () => {
  it('picks the greeting by hour like Android', () => {
    expect(greetingKey(4)).toBe('home_good_night')
    expect(greetingKey(5)).toBe('home_good_morning')
    expect(greetingKey(12)).toBe('home_good_afternoon')
    expect(greetingKey(17)).toBe('home_good_evening')
    expect(greetingKey(20)).toBe('home_good_night')
  })
  it('lets only today and the last 3 undone days be excused', () => {
    expect(canExcuse(100, 100, 'TODAY_PLANNED')).toBe(true)
    expect(canExcuse(97, 100, 'MISSED')).toBe(true)
    expect(canExcuse(96, 100, 'MISSED')).toBe(false)
    expect(canExcuse(101, 100, 'UPCOMING')).toBe(false)
    expect(canExcuse(100, 100, 'DONE')).toBe(false)
    expect(canExcuse(100, 100, 'OFF')).toBe(false)
  })
})
