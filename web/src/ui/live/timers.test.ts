import { afterEach, describe, expect, it, vi } from 'vitest'
import { adjustRest, holdDisplay, holdPhase, holdSaved, loadTimers, restDone, restLeft, saveTimers, startHold, startRest, type TimerStore } from './timers'

const memory = (): TimerStore & { data: Map<string, string> } => {
  const data = new Map<string, string>()
  return { data, getItem: (k) => data.get(k) ?? null, setItem: (k, v) => void data.set(k, v), removeItem: (k) => void data.delete(k) }
}

afterEach(() => vi.useRealTimers())

describe('rest timer', () => {
  it('a rest started, then a 60 s jump in Date.now(), shows 30 s left', () => {
    vi.useFakeTimers()
    vi.setSystemTime(1_000_000)
    const r = startRest(90, Date.now(), null)
    expect(restLeft(r.endsAt, Date.now())).toBe(90)
    vi.setSystemTime(1_060_000) // phone was locked for a minute: no tick ran, the clock still agrees
    expect(restLeft(r.endsAt, Date.now())).toBe(30)
    expect(restDone(r, Date.now())).toBe(false)
    vi.setSystemTime(1_100_000)
    expect(restLeft(r.endsAt, Date.now())).toBe(0)
    expect(restDone(r, Date.now())).toBe(true)
  })
  it('+15 s moves the end, and the total never drops below 15 s', () => {
    const r = adjustRest(startRest(30, 0, null), -30)
    expect(r.totalSeconds).toBe(15)
    expect(adjustRest(startRest(60, 0, null), 15).endsAt).toBe(75_000)
  })
})

describe('hold timer', () => {
  it('counts 3-2-1, then down, then extra time', () => {
    const h = startHold(1, 'Plank', 20, 0)
    expect(holdPhase(h, 0)).toBe('ready')
    expect(holdDisplay(h, 0)).toBe('3')
    expect(holdDisplay(h, 3_000)).toBe('0:20')
    expect(holdDisplay(h, 3_000 + 25_000)).toBe('+0:05')
    expect(holdSaved(h, 3_000 + 12_000)).toBe(12)
    expect(holdSaved(h, 1_000)).toBe(0)
  })
})

describe('saved timers', () => {
  it('restores a running rest for the same session after a reload', () => {
    const store = memory()
    const rest = startRest(60, 10_000, 'Squat')
    saveTimers(store, { sessionId: 5, rest, hold: null })
    expect(loadTimers(store, 5, 40_000).rest).toEqual(rest)
    expect(loadTimers(store, 6, 40_000).rest).toBeNull()
    expect(loadTimers(store, 5, 80_000).rest).toBeNull()
  })
  it('clears the entry when nothing runs, and survives broken storage', () => {
    const store = memory()
    saveTimers(store, { sessionId: 1, rest: startRest(30, 0, null), hold: null })
    saveTimers(store, { sessionId: 1, rest: null, hold: null })
    expect(store.data.size).toBe(0)
    store.data.set('fq.live.timers', '{nope')
    expect(loadTimers(store, 1, 0)).toEqual({ rest: null, hold: null })
    expect(() => saveTimers(null, { sessionId: 1, rest: null, hold: null })).not.toThrow()
  })
})
