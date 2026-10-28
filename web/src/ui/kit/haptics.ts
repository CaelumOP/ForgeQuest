// Vibration and reduced-motion helpers for the kit (Android ui/Haptics.kt and LocalReducedMotion).
// navigator.vibrate is missing on iPhone Safari: every call is guarded and simply does nothing there.

let enabled = true

/** Mirrors Settings.hapticsEnabled; set by the app shell so kit controls don't read the database. */
export function setHapticsEnabled(on: boolean): void { enabled = on }

function vibrate(pattern: number | number[]): void {
  if (!enabled) return
  try { navigator.vibrate?.(pattern) } catch { /* not supported */ }
}

export const haptics = {
  /** Steppers, chips, toggles. */
  tick: () => vibrate(8),
  /** Each stepper step, each slot passed while dragging. */
  lightTick: () => vibrate(5),
  /** Ticking a set off. */
  check: () => vibrate(12),
  /** A set that beats your best: a double thud. */
  pr: () => vibrate([35, 90, 35]),
  /** The finish screen's score lands; a lifted row. */
  heavyClick: () => vibrate(30),
}

/** True when the person asked for less motion. */
export function reducedMotion(): boolean {
  try { return matchMedia('(prefers-reduced-motion: reduce)').matches } catch { return false }
}
