import { afterEach, describe, expect, it } from 'vitest'
import {
  COACH_STEPS, TOUR_SEEN_KEY, acquireTip, allSeen, placeTip, releaseTip, resetTipSlot,
  seen, shouldShow, skipTour, stepSeen, tourSeen, waitForTipSlot, type CoachStore,
} from './coach-mark'

const memoryStore = (): CoachStore & { data: Map<string, string> } => {
  const data = new Map<string, string>()
  return {
    data,
    getItem: (k) => data.get(k) ?? null,
    setItem: (k, v) => { data.set(k, v) },
  }
}

afterEach(() => resetTipSlot())

describe('coach mark tour state', () => {
  it('shows once per id while the tour is not over, and respects enabled', () => {
    const store = memoryStore()
    expect(shouldShow('coach_home', true, store)).toBe(true)
    expect(shouldShow('coach_home', false, store)).toBe(false)

    stepSeen('coach_home', store)
    expect(seen('coach_home', store)).toBe(true)
    expect(shouldShow('coach_home', true, store)).toBe(false)
    expect(shouldShow('coach_food', true, store)).toBe(true)
    expect(tourSeen(store)).toBe(false)
  })

  it('keeps tips quiet when fq_tour_seen is already set from the old tour', () => {
    const store = memoryStore()
    store.setItem(TOUR_SEEN_KEY, '1')
    for (const id of COACH_STEPS) {
      expect(shouldShow(id, true, store)).toBe(false)
    }
  })

  it('ends the tour when all four steps have been seen', () => {
    const store = memoryStore()
    for (const id of COACH_STEPS.slice(0, 3)) {
      stepSeen(id, store)
      expect(allSeen(store)).toBe(false)
      expect(tourSeen(store)).toBe(false)
    }
    stepSeen(COACH_STEPS[3], store)
    expect(allSeen(store)).toBe(true)
    expect(tourSeen(store)).toBe(true)
    expect(store.data.get(TOUR_SEEN_KEY)).toBe('1')
  })

  it('skipTour ends the tour for good so remaining steps never show', () => {
    const store = memoryStore()
    stepSeen('coach_home', store)
    skipTour(store)
    expect(tourSeen(store)).toBe(true)
    expect(shouldShow('coach_plan', true, store)).toBe(false)
    expect(shouldShow('coach_tick', true, store)).toBe(false)
  })

  it('survives missing or throwing storage without crashing', () => {
    const broken: CoachStore = {
      getItem: () => { throw new Error('blocked') },
      setItem: () => { throw new Error('blocked') },
    }
    expect(shouldShow('coach_home', true, null)).toBe(true)
    expect(shouldShow('coach_home', true, broken)).toBe(true)
    expect(() => stepSeen('coach_home', broken)).not.toThrow()
    expect(() => skipTour(broken)).not.toThrow()
  })

  it('allows only one active tip at a time and wakes waiters when closed', () => {
    const store = memoryStore()
    const first = Symbol('row-1')
    const second = Symbol('row-2')
    const otherScreen = Symbol('food')
    const shown: symbol[] = []

    expect(acquireTip(first)).toBe(true)
    expect(acquireTip(second)).toBe(false)

    // Second row with the same id waits; when the first row finishes, coach_tick is already seen so row 2 stays quiet.
    waitForTipSlot(() => {
      if (shouldShow('coach_tick', true, store) && acquireTip(second)) shown.push(second)
    })
    // Another tip with an unseen id wakes up next and claims the slot.
    waitForTipSlot(() => {
      if (shouldShow('coach_food', true, store) && acquireTip(otherScreen)) shown.push(otherScreen)
    })

    stepSeen('coach_tick', store)
    releaseTip(first)
    expect(shown).toEqual([otherScreen])
  })
})

describe('placeTip', () => {
  const viewport = { width: 390, height: 844 }
  const tip = { width: 280, height: 120 }
  const tabBar = { top: 780, bottom: 844, left: 0, right: 390, width: 390, height: 64 }

  it('places below when below is true and flips above near the tab bar', () => {
    const mid = placeTip({
      anchor: { top: 200, bottom: 252, left: 20, right: 370, width: 350, height: 52 },
      tip, viewport, below: true, topBarBottom: 56, tabBar,
    })
    expect(mid.blockStart).toBe(260)
    expect(mid.inlineStart).toBe(20)

    const low = placeTip({
      anchor: { top: 710, bottom: 762, left: 20, right: 370, width: 350, height: 52 },
      tip, viewport, below: true, topBarBottom: 56, tabBar,
    })
    expect(low.blockStart).toBe(582) // 710 - 8 - 120: above the anchor, clear of the tab bar
  })

  it('keeps 16 px gutters and mirrors inline-start in RTL', () => {
    // LTR: small button near the right edge clamps so tip's right edge stays 16 px inside viewport (390 - 16 - 280 = 94).
    const ltrEnd = placeTip({
      anchor: { top: 300, bottom: 348, left: 320, right: 368, width: 48, height: 48 },
      tip, viewport, below: true, rtl: false,
    })
    expect(ltrEnd.inlineStart).toBe(94)

    // RTL: small button near the left edge (x = 22..70) has raw inline-start 390 - 70 = 320, clamped to 94 from the right edge.
    const rtlEnd = placeTip({
      anchor: { top: 300, bottom: 348, left: 22, right: 70, width: 48, height: 48 },
      tip, viewport, below: true, rtl: true,
    })
    expect(rtlEnd.inlineStart).toBe(94)
  })
})
