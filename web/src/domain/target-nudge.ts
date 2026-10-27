// The one-time "make N your weekly target?" offer on the Home streak card. Android: domain/TargetNudge.kt.
import { weekStart } from './dates'

/**
 * The weekly number to offer, or null. Looks at the last two finished weeks: when in both of them the person trained at
 * least twice but fewer times than [target], the lower of the two counts is offered. Weeks before the person's first
 * week do not count.
 */
export function nudgeTarget(workoutDays: ReadonlySet<number>, target: number, startEpochDay: number, today: number): number | null {
  const thisWeek = weekStart(today)
  const weeks = [thisWeek - 14, thisWeek - 7]
  if (weeks[0] < weekStart(startEpochDay)) return null
  const done = weeks.map((ws) => [...workoutDays].filter((d) => d >= ws && d <= ws + 6).length)
  return done.every((n) => n >= 2 && n < target) ? Math.min(...done) : null
}

/** Whether the offer is on Home right now: once, and never again after any answer. */
export const showTargetNudge = (answered: boolean, offer: number | null): offer is number => !answered && offer != null
