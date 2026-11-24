/** Reaction-time grace for timed holds: stopping within HOLD_GRACE_SEC seconds after the target saves the target. */
export const HOLD_GRACE_SEC = 3

export function recordedHold(held: number, target: number): number {
  return target > 0 && held >= target && held <= target + HOLD_GRACE_SEC ? target : held
}
