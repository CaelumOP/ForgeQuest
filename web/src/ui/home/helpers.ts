import { EXCUSE_BACK_DAYS } from '../../data/progress'
import type { DayState } from '../../domain/progress'

/** Android Header: 5-11 morning, 12-16 afternoon, 17-19 evening, otherwise night. */
export function greetingKey(hour: number): string {
  if (hour >= 5 && hour <= 11) return 'home_good_morning'
  if (hour >= 12 && hour <= 16) return 'home_good_afternoon'
  if (hour >= 17 && hour <= 19) return 'home_good_evening'
  return 'home_good_night'
}

/** Today and the last 3 days can be excused with a tap; finished workouts and days off can't. */
export function canExcuse(day: number, today: number, state: DayState): boolean {
  return day >= today - EXCUSE_BACK_DAYS && day <= today && state !== 'DONE' && state !== 'OFF'
}
