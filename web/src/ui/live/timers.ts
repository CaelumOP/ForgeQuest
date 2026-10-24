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
