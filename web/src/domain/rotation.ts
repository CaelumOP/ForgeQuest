import { dayOfWeek } from './dates';
import type { SessionEntity } from './entities';
import type { DayTag } from './progress';

export const SCHEDULE_MODES = ['WEEKDAY', 'ROTATION'] as const;
export type ScheduleMode = (typeof SCHEDULE_MODES)[number];

export function parseScheduleMode(raw: string | null | undefined): ScheduleMode {
  return raw?.toUpperCase() === 'ROTATION' ? 'ROTATION' : 'WEEKDAY';
}

export type PlanLike = {
  plan: { id?: number; scheduleMode?: string; rotationCursor?: number };
  days: { day: { id?: number; dayOfWeek: number; title: string; isRest: boolean; note: string }; items: readonly { id?: number }[] }[];
};

export function rotationQueue<T extends PlanLike>(plan: T): T['days'] {
  return [...plan.days]
    .filter((d) => !d.day.isRest && d.items.length > 0)
    .sort((a, b) => a.day.dayOfWeek - b.day.dayOfWeek);
}

export function normalizeCursor(cursor: number, queueSize: number): number {
  if (queueSize <= 0) return 0;
  const mod = cursor % queueSize;
  return mod < 0 ? mod + queueSize : mod;
}

/** Today's due workout day: day-of-week lookup in WEEKDAY mode, or queue[cursor] in ROTATION mode. */
export function dueDay<T extends PlanLike>(plan: T | null | undefined, todayDayOfWeek: number): T['days'][number] | null {
  if (!plan) return null;
  if (parseScheduleMode(plan.plan.scheduleMode) === 'WEEKDAY') {
    const d = plan.days.find((x) => x.day.dayOfWeek === todayDayOfWeek);
    return d && !d.day.isRest && d.items.length > 0 ? d : null;
  }
  const q = rotationQueue(plan);
  if (q.length === 0) return null;
  return q[normalizeCursor(plan.plan.rotationCursor ?? 0, q.length)];
}

/**
 * Next rotation cursor when `session` finishes.
 * Returns null when the plan's cursor must NOT change (WEEKDAY plan, quick workout, repeated finish,
 * session belongs to another plan, or the session's day was removed/turned into rest).
 */
export function cursorOnFinish(
  plan: { scheduleMode?: string },
  queueDayIds: readonly number[],
  session: Pick<SessionEntity, 'planDayId'>,
  wasAlreadyFinished: boolean,
): number | null {
  if (wasAlreadyFinished) return null;
  if (parseScheduleMode(plan.scheduleMode) !== 'ROTATION') return null;
  if (session.planDayId == null || queueDayIds.length === 0) return null;
  const idx = queueDayIds.indexOf(session.planDayId);
  if (idx < 0) return null;
  return (idx + 1) % queueDayIds.length;
}

/** Re-normalizes the cursor after a plan edit changes the number of training days. */
export function cursorAfterPlanEdit(currentCursor: number, newQueueSize: number): number {
  return normalizeCursor(currentCursor, newQueueSize);
}

/**
 * Yesterday's missed planned workout in WEEKDAY mode, offered as a one-tap user action ("Do missed workout today").
 * Returns null in ROTATION mode, or if today/yesterday already has a workout or excuse tag.
 */
export function missedYesterday<T extends PlanLike>(
  plan: T | null | undefined,
  sessions: readonly Pick<SessionEntity, 'epochDay' | 'finishedAt'>[],
  dayTags: ReadonlyMap<number, DayTag>,
  todayEpochDay: number,
): T['days'][number] | null {
  if (!plan || parseScheduleMode(plan.plan.scheduleMode) !== 'WEEKDAY') return null;
  if (dayTags.has(todayEpochDay)) return null;
  const finishedDays = new Set(sessions.filter((s) => s.finishedAt != null).map((s) => s.epochDay));
  if (finishedDays.has(todayEpochDay)) return null;
  const yesterday = todayEpochDay - 1;
  if (finishedDays.has(yesterday) || dayTags.has(yesterday)) return null;
  const yesterdayDow = dayOfWeek(yesterday);
  const d = plan.days.find((x) => x.day.dayOfWeek === yesterdayDow);
  return d && !d.day.isRest && d.items.length > 0 ? d : null;
}
