// Timer math for the live workout. Every timer keeps the moment it started (or ends) as a Date.now() value and
// works out what is left from it, so the numbers stay right after the phone was locked. Nothing counts ticks.

export const READY_SECONDS = 3
export const DEFAULT_HOLD_SECONDS = 30
const MIN_REST_TOTAL = 15

export type RestTimer = { totalSeconds: number; endsAt: number; nextExercise: string | null }

/** Whole seconds left, never below zero. Rounds up so a fresh 90 s rest reads 1:30, not 1:29. */
export function restLeft(endsAt: number, now: number): number {
  return Math.max(0, Math.ceil((endsAt - now) / 1000))
}

export const restDone = (r: RestTimer, now: number): boolean => now >= r.endsAt

export function startRest(seconds: number, now: number, nextExercise: string | null): RestTimer {
  return { totalSeconds: seconds, endsAt: now + seconds * 1000, nextExercise }
}

/** +15 s or -15 s on a running rest. The total never drops below 15 s. */
export function adjustRest(r: RestTimer, deltaSeconds: number): RestTimer {
  return { ...r, totalSeconds: Math.max(MIN_REST_TOTAL, r.totalSeconds + deltaSeconds), endsAt: r.endsAt + deltaSeconds * 1000 }
}

export type HoldTimer = { setId: number; exerciseName: string; target: number; startsAt: number }

export function startHold(setId: number, exerciseName: string, seconds: number | null, now: number): HoldTimer {
  const target = seconds != null && seconds > 0 ? seconds : DEFAULT_HOLD_SECONDS
  return { setId, exerciseName, target, startsAt: now + READY_SECONDS * 1000 }
}

/** Whole seconds held so far; negative during the 3-2-1 get-ready. */
export const holdElapsed = (h: HoldTimer, now: number): number => Math.floor((now - h.startsAt) / 1000)

export type HoldPhase = 'ready' | 'holding' | 'over'

export function holdPhase(h: HoldTimer, now: number): HoldPhase {
  const s = holdElapsed(h, now)
  return s < 0 ? 'ready' : s >= h.target ? 'over' : 'holding'
}

export const clock = (seconds: number): string => `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`

/** What the big clock shows: 3-2-1, then the time left to the target, then +extra time after zero. */
export function holdDisplay(h: HoldTimer, now: number): string {
  const s = holdElapsed(h, now)
  if (s < 0) return String(-s)
  if (s >= h.target) return `+${clock(s - h.target)}`
  return clock(h.target - s)
}

/** Seconds extra beyond the target, 0 before it. */
export const holdExtra = (h: HoldTimer, now: number): number => Math.max(0, holdElapsed(h, now) - h.target)

/** Seconds to save when Stop is pressed: held time, 0..3600. 0 means nothing to save. */
export const holdSaved = (h: HoldTimer, now: number): number => Math.min(3600, Math.max(0, holdElapsed(h, now)))

// ---------------------------------------------------------------- Saved timers (survive a reload or a locked phone)

export type SavedTimers = { sessionId: number; rest: RestTimer | null; hold: HoldTimer | null }
export type TimerStore = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>
export const TIMERS_KEY = 'fq.live.timers'
const HOLD_STALE_MS = 2 * 60 * 60 * 1000

export function saveTimers(store: TimerStore | null, s: SavedTimers): void {
  try {
    if (!store) return
    if (!s.rest && !s.hold) store.removeItem(TIMERS_KEY)
    else store.setItem(TIMERS_KEY, JSON.stringify(s))
  } catch { /* storage blocked: the timers just won't survive a reload */ }
}

/** Timers saved for this session. A rest that already ended and a long-forgotten hold are dropped. */
export function loadTimers(store: TimerStore | null, sessionId: number, now: number): { rest: RestTimer | null; hold: HoldTimer | null } {
  const none = { rest: null, hold: null }
  try {
    const raw = store?.getItem(TIMERS_KEY)
    if (!raw) return none
    const s = JSON.parse(raw) as SavedTimers
    if (!s || s.sessionId !== sessionId) return none
    const rest = s.rest && typeof s.rest.endsAt === 'number' && !restDone(s.rest, now) ? s.rest : null
    const hold = s.hold && typeof s.hold.startsAt === 'number' && now - s.hold.startsAt < HOLD_STALE_MS ? s.hold : null
    return { rest, hold }
  } catch { return none }
}
