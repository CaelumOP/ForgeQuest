import { describe, expect, it } from 'vitest';
import { dayOfWeek } from './dates';
import type { DayTag } from './progress';
import {
  cursorAfterPlanEdit,
  cursorOnFinish,
  dueDay,
  missedYesterday,
  normalizeCursor,
  parseScheduleMode,
  rotationQueue,
  type PlanLike,
} from './rotation';

function makePlan(scheduleMode = 'ROTATION', rotationCursor = 0): PlanLike {
  return {
    plan: { id: 1, scheduleMode, rotationCursor },
    days: [
      { day: { id: 10, dayOfWeek: 1, title: 'Push', isRest: false, note: '' }, items: [{ id: 1 }] },
      { day: { id: 11, dayOfWeek: 2, title: 'Rest', isRest: true, note: '' }, items: [] },
      { day: { id: 12, dayOfWeek: 3, title: 'Pull', isRest: false, note: '' }, items: [{ id: 2 }] },
      { day: { id: 13, dayOfWeek: 4, title: 'Empty', isRest: false, note: '' }, items: [] },
      { day: { id: 14, dayOfWeek: 5, title: 'Legs', isRest: false, note: '' }, items: [{ id: 3 }] },
    ],
  };
}

describe('Rotation', () => {
  it('parses scheduleMode safely defaulting to WEEKDAY', () => {
    expect(parseScheduleMode(undefined)).toBe('WEEKDAY');
    expect(parseScheduleMode('weekday')).toBe('WEEKDAY');
    expect(parseScheduleMode('rotation')).toBe('ROTATION');
    expect(parseScheduleMode('ROTATION')).toBe('ROTATION');
  });

  it('builds Push -> Pull -> Legs queue skipping rest and empty days', () => {
    const q = rotationQueue(makePlan());
    expect(q.map((d) => d.day.title)).toEqual(['Push', 'Pull', 'Legs']);
  });

  it('returns weekday match in WEEKDAY mode and cursor day in ROTATION mode', () => {
    const weekdayPlan = makePlan('WEEKDAY', 2);
    expect(dueDay(weekdayPlan, 1)?.day.title).toBe('Push');
    expect(dueDay(weekdayPlan, 2)).toBeNull();

    expect(dueDay(makePlan('ROTATION', 0), 2)?.day.title).toBe('Push');
    expect(dueDay(makePlan('ROTATION', 1), 2)?.day.title).toBe('Pull');
    expect(dueDay(makePlan('ROTATION', 2), 2)?.day.title).toBe('Legs');
    expect(dueDay(makePlan('ROTATION', 3), 2)?.day.title).toBe('Push');
    expect(normalizeCursor(-1, 3)).toBe(2);
    expect(normalizeCursor(5, 0)).toBe(0);
  });

  it('advances cursor once on qualifying finish and ignores quick / repeated / weekday / removed day', () => {
    const queueIds = [10, 12, 14];
    expect(cursorOnFinish({ scheduleMode: 'ROTATION' }, queueIds, { planDayId: 10 }, false)).toBe(1);
    expect(cursorOnFinish({ scheduleMode: 'ROTATION' }, queueIds, { planDayId: 14 }, false)).toBe(0);
    // Repeated finish: no advance
    expect(cursorOnFinish({ scheduleMode: 'ROTATION' }, queueIds, { planDayId: 10 }, true)).toBeNull();
    // Quick workout: no advance
    expect(cursorOnFinish({ scheduleMode: 'ROTATION' }, queueIds, { planDayId: null }, false)).toBeNull();
    // Weekday mode: no advance
    expect(cursorOnFinish({ scheduleMode: 'WEEKDAY' }, queueIds, { planDayId: 10 }, false)).toBeNull();
    // Removed/rest day: no advance
    expect(cursorOnFinish({ scheduleMode: 'ROTATION' }, queueIds, { planDayId: 999 }, false)).toBeNull();
  });

  it('normalizes cursor when a plan edit shrinks the queue', () => {
    expect(cursorAfterPlanEdit(2, 2)).toBe(0);
    expect(cursorAfterPlanEdit(1, 2)).toBe(1);
    expect(cursorAfterPlanEdit(2, 0)).toBe(0);
  });

  it('offers yesterday missed workout only in WEEKDAY mode when neither day was logged or excused', () => {
    const today = 20000;
    const yesterdayDow = dayOfWeek(today - 1);
    const plan: PlanLike = {
      plan: { id: 1, scheduleMode: 'WEEKDAY', rotationCursor: 0 },
      days: [
        { day: { id: 21, dayOfWeek: yesterdayDow, title: 'Push', isRest: false, note: '' }, items: [{ id: 1 }] },
      ],
    };
    const emptyTags = new Map<number, DayTag>();

    expect(missedYesterday(plan, [], emptyTags, today)?.day.id).toBe(21);
    // Not in ROTATION mode
    expect(missedYesterday({ ...plan, plan: { ...plan.plan, scheduleMode: 'ROTATION' } }, [], emptyTags, today)).toBeNull();
    // Already finished yesterday
    expect(missedYesterday(plan, [{ epochDay: today - 1, finishedAt: 1000 }], emptyTags, today)).toBeNull();
    // Unfinished/discarded doesn't count as finished, still offers missed yesterday
    expect(missedYesterday(plan, [{ epochDay: today - 1, finishedAt: null }], emptyTags, today)?.day.id).toBe(21);
    // Already finished today
    expect(missedYesterday(plan, [{ epochDay: today, finishedAt: 1000 }], emptyTags, today)).toBeNull();
    // Excused yesterday
    expect(missedYesterday(plan, [], new Map([[today - 1, 'REST']]), today)).toBeNull();
    // Excused today
    expect(missedYesterday(plan, [], new Map([[today, 'SICK']]), today)).toBeNull();
  });
});
