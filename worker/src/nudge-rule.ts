import { isoWeekday, riyadhDayStartSec } from "./time";

/** On the recipient's planned training day "Go train" may be sent again after this long; other days once a day. */
export const TRAIN_DAY_NUDGE_COOLDOWN_SEC = 4 * 60 * 60;

/** True when [today] (Riyadh epoch day) is one of the recipient's planned training weekdays. */
export function isTrainDay(trainDays: readonly number[] | undefined, today: number): boolean {
  return !!trainDays && trainDays.includes(isoWeekday(today));
}

/**
 * When the sender may nudge again (Unix seconds), or null when allowed now.
 * [lastSentSec] is today's nudge row time (null when none today). A new Riyadh day always allows one.
 */
export function nudgeNextAt(lastSentSec: number | null, trainDay: boolean, today: number, nowSec: number): number | null {
  if (lastSentSec === null) return null;
  const tomorrow = riyadhDayStartSec(today + 1);
  const next = trainDay ? Math.min(lastSentSec + TRAIN_DAY_NUDGE_COOLDOWN_SEC, tomorrow) : tomorrow;
  return next <= nowSec ? null : next;
}
