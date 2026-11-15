import { riyadhEpochDay, sundayWeekStart } from "./time";

/** One user's result for one Sunday week, as reported by their device (the server never re-derives it). */
export type WeekState = "hit" | "free" | "open" | "miss";

export const WEEK_STATES: ReadonlySet<string> = new Set(["hit", "free", "open", "miss"]);

/** What one finished week does to a shared streak. */
export type WeekVerdict = "count" | "skip" | "stop";

/**
 * Combines my state and my friend's state for one FINISHED week (missing rows and `open` already mapped to `miss`).
 * Mirrors the personal streak rule (web/src/domain/progress.ts computeStreak): a hit counts, a free week
 * (target 0 and no workout) neither counts nor breaks, anything else breaks.
 */
export function combineWeek(mine: WeekState, theirs: WeekState): WeekVerdict {
  if (mine === "hit" && theirs === "hit") return "count";
  const keeps = (s: WeekState) => s === "hit" || s === "free";
  return keeps(mine) && keeps(theirs) ? "skip" : "stop";
}

/**
 * Consecutive Sunday weeks in which both users hit their target, counted only from the week the friendship began.
 * - Current week: +1 when both hit, otherwise ignored (an unfinished week never breaks a streak).
 * - Sunday grace: on Riyadh Sunday and Monday, last week is treated like the current week, so a late sync (offline
 *   Saturday workout, or a device west of Riyadh still on Saturday) does not zero the streak.
 */
export function sharedStreak(params: {
  friendsSinceSec: number;
  today: number;
  mine: ReadonlyMap<number, WeekState>;
  theirs: ReadonlyMap<number, WeekState>;
}): number {
  const { today, mine, theirs } = params;
  const currentWeek = sundayWeekStart(today);
  const firstWeek = sundayWeekStart(riyadhEpochDay(params.friendsSinceSec * 1000));
  const bothHit = (w: number) => mine.get(w) === "hit" && theirs.get(w) === "hit";
  const finished = (s: WeekState | undefined): WeekState => (s === undefined || s === "open" ? "miss" : s);

  let streak = bothHit(currentWeek) ? 1 : 0;
  for (let w = currentWeek - 7; w >= firstWeek; w -= 7) {
    const graceWeek = w === currentWeek - 7 && today <= currentWeek + 1;
    if (graceWeek) {
      if (bothHit(w)) streak++;
      continue;
    }
    const verdict = combineWeek(finished(mine.get(w)), finished(theirs.get(w)));
    if (verdict === "stop") break;
    if (verdict === "count") streak++;
  }
  return streak;
}
