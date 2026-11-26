import 'fake-indexeddb/auto'
import { beforeEach, describe, expect, it } from 'vitest'
import { db, putMeal, putSession, putWeight } from '../../data/db'
import {
  GS_DISMISSED_KEY, GS_ELIGIBLE_KEY,
  buildGettingStartedItems, isGettingStartedDismissed, isGettingStartedEligible,
  markGettingStartedDismissed, markGettingStartedEligible, queryGettingStartedDone,
  shouldShowGettingStarted, type GsStore,
} from './getting-started'

const memoryStore = (): GsStore & { data: Map<string, string> } => {
  const data = new Map<string, string>()
  return {
    data,
    getItem: (k) => data.get(k) ?? null,
    setItem: (k, v) => { data.set(k, v) },
  }
}

beforeEach(async () => {
  await Promise.all(db.tables.map((t) => t.clear()))
})

describe('getting started visibility and flags', () => {
  it('stays hidden until eligible, then hides once dismissed', () => {
    const store = memoryStore()
    expect(isGettingStartedEligible(store)).toBe(false)
    expect(shouldShowGettingStarted(store)).toBe(false)

    markGettingStartedEligible(store)
    expect(store.data.get(GS_ELIGIBLE_KEY)).toBe('1')
    expect(isGettingStartedEligible(store)).toBe(true)
    expect(shouldShowGettingStarted(store)).toBe(true)

    markGettingStartedDismissed(store)
    expect(store.data.get(GS_DISMISSED_KEY)).toBe('1')
    expect(isGettingStartedDismissed(store)).toBe(true)
    expect(shouldShowGettingStarted(store)).toBe(false)
  })

  it('survives blocked storage without throwing', () => {
    const broken: GsStore = {
      getItem: () => { throw new Error('blocked') },
      setItem: () => { throw new Error('blocked') },
    }
    expect(shouldShowGettingStarted(broken)).toBe(false)
    expect(() => markGettingStartedEligible(broken)).not.toThrow()
    expect(() => markGettingStartedDismissed(broken)).not.toThrow()
  })
})

describe('getting started Dexie done-ness', () => {
  it('detects finished sessions, meals and weights', async () => {
    expect(await queryGettingStartedDone()).toEqual({
      workout: false, food: false, weight: false,
    })

    // Unfinished session does not tick workout.
    await putSession({ planDayId: null, title: 'Open', epochDay: 20000, startedAt: 1000, finishedAt: null, note: '', debrief: '' })
    expect((await queryGettingStartedDone()).workout).toBe(false)

    await putSession({ planDayId: null, title: 'Done', epochDay: 20000, startedAt: 1000, finishedAt: 2000, note: '', debrief: '' })
    await putMeal({ epochDay: 20000, loggedAt: 1000, name: 'Eggs', category: 'BREAKFAST', kcal: 200, protein: 14, quantity: 1, carbs: 1, photoPath: null, foodId: null })
    await putWeight({ epochDay: 20000, kg: 75, loggedAt: 1000 })

    const counts = await queryGettingStartedDone()
    expect(counts).toEqual({ workout: true, food: true, weight: true })

    const items = buildGettingStartedItems(counts)
    expect(items.map((i) => [i.id, i.href, i.done])).toEqual([
      ['workout', '#/workouts', true],
      ['food', '#/food', true],
      ['weight', '#/progress', true],
    ])
  })
})
