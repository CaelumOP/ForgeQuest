/** When a workout counts as ended. Android: domain/WorkoutEnd.kt. */
export const IDLE_MS = 20 * 60_000
export const GRACE_MS = 2 * 60_000

/** `now`, unless the last set was ticked more than 20 minutes ago: then two minutes after that set. */
export const endTime = (now: number, lastDoneAt: number | null | undefined): number =>
  lastDoneAt != null && now - lastDoneAt > IDLE_MS ? lastDoneAt + GRACE_MS : now

/** Latest tick time among done sets, or null when none has one. */
export const lastDoneAt = (sets: { done: boolean; doneAt?: number | null }[]): number | null =>
  sets.reduce<number | null>((m, s) => (s.done && s.doneAt != null && (m == null || s.doneAt > m) ? s.doneAt : m), null)
