const RIYADH_OFFSET_MS = 3 * 60 * 60 * 1000;
const DAY_MS = 24 * 60 * 60 * 1000;

/** Epoch day in Riyadh (UTC+3, no DST). 1970-01-01 = 0 (Thursday). */
export function riyadhEpochDay(nowMs: number = Date.now()): number {
  return Math.floor((nowMs + RIYADH_OFFSET_MS) / DAY_MS);
}

/** True when epochDay falls on Sunday (1970-01-01 was Thursday = 0, so Sunday % 7 == 3). */
export function isSunday(epochDay: number): boolean {
  return ((epochDay % 7) + 7) % 7 === 3;
}

/** Sunday epochDay starting the Riyadh week containing `epochDay`. */
export function sundayWeekStart(epochDay: number): number {
  const daysSinceSunday = (((epochDay - 3) % 7) + 7) % 7;
  return epochDay - daysSinceSunday;
}

/** Quiet hours for pushes: 22:00 (inclusive) to 07:00 (exclusive), Riyadh time, for every user. */
export function isQuietHours(nowMs: number): boolean {
  const msIntoDay = (((nowMs + RIYADH_OFFSET_MS) % DAY_MS) + DAY_MS) % DAY_MS;
  const hour = Math.floor(msIntoDay / (60 * 60 * 1000));
  return hour >= 22 || hour < 7;
}
