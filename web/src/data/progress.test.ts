import 'fake-indexeddb/auto';
import { liveQuery } from 'dexie';
import { beforeEach, describe, expect, it } from 'vitest';
import fixtureText from './fixtures/android-backup.json?raw';
import { applyBackup, decodeBackup } from './backup';
import { db, putSession } from './db';
import { todayEpochDay } from '../domain/dates';
import { weekTargets } from './settings';
import { applyPlan, seedLibrary } from './workouts';
import { evaluateBadges, getProgressSnapshot, logWeight, setDayTag, type ProgressSnapshot } from './progress';
import type { PlanDraft } from './seed/starter-plans';

const day = (dayOfWeek: number, isRest: boolean) => ({
  dayOfWeek, title: isRest ? 'Rest' : 'Work', isRest, note: '',
  items: isRest ? [] : [{
    exerciseId: 'burpee', name: null, kind: null, muscles: [], block: 'Main', circuit: false, sets: 3, repsMin: 8,
    repsMax: 12, seconds: null, weightKg: null, restSeconds: 60, note: '', videoUrl: null,
  }],
});
const plan = (name: string, trainingDows: number[]): PlanDraft => ({
  name, description: '', days: [1, 2, 3, 4, 5, 6, 7].map((d) => day(d, !trainingDows.includes(d))),
});

/** The first snapshot a live query (what screens use) builds; rejects if the query fails. */
const firstLive = (query: () => Promise<ProgressSnapshot>) => new Promise<ProgressSnapshot>((resolve, reject) => {
  const sub = liveQuery(query).subscribe({ next: (v) => { sub.unsubscribe(); resolve(v); }, error: reject });
});

async function finishedOn(epochDay: number) {
  const startedAt = Date.UTC(1970, 0, 1) + epochDay * 86_400_000 + 3_600_000;
  await putSession({ planDayId: null, title: 'Past', epochDay, startedAt, finishedAt: startedAt + 3_000_000, note: '', debrief: '' });
}

beforeEach(async () => {
  await Promise.all(db.tables.map((t) => t.clear()));
  await seedLibrary();
});

describe('progress', () => {
  it('changing the plan does not rewrite past-week streaks', async () => {
    const today = todayEpochDay();
    await applyPlan(plan('One day', [1]), 'test');
    await finishedOn(today - 14);
    await finishedOn(today - 7);

    const before = await getProgressSnapshot();
    const lockedBefore = await weekTargets();
    expect(lockedBefore.size).toBeGreaterThanOrEqual(2);
    expect([...lockedBefore.values()].every((t) => t === 1)).toBe(true);
    expect(before.streak.weeks).toBeGreaterThanOrEqual(2);

    await applyPlan(plan('Five days', [1, 2, 3, 4, 5]), 'test');
    const after = await getProgressSnapshot();
    expect(after.streak.targetThisWeek).toBe(5);
    expect(after.streak.weeks).toBeGreaterThanOrEqual(before.streak.weeks);
    expect([...(await weekTargets())]).toEqual([...lockedBefore]);
  });

  it('builds level, today flag, weight and day tags', async () => {
    const today = todayEpochDay();
    await finishedOn(today);
    await logWeight(81.46);
    await setDayTag(today, 'SICK');
    await setDayTag(today - 10, 'SICK'); // too far back: ignored
    const snap = await getProgressSnapshot();
    expect(snap.workoutDoneToday).toBe(true);
    expect(snap.latestWeight).toBe(81.4);
    expect(snap.excusedToday).toBe('SICK');
    expect(snap.dayTags.size).toBe(1);
    expect(snap.level.totalXp).toBe(50);
    const fresh = await evaluateBadges();
    expect(fresh.length).toBeGreaterThan(0);
    expect((await evaluateBadges())).toHaveLength(0);
  });

  it('builds inside a live query right after a restore, then locks the weeks outside it', async () => {
    await applyBackup(decodeBackup(fixtureText));
    expect((await db.plans.count()) > 0 && (await weekTargets()).size === 0).toBe(true); // weeks still to lock
    const live = await firstLive(() => getProgressSnapshot(Date.now(), false));
    expect(live.sessions).toHaveLength(1);
    expect(live.doneSets.length).toBeGreaterThan(0);
    expect(live.weights).toHaveLength(2);
    expect(await weekTargets()).toEqual(new Map()); // nothing saved inside the live query

    await getProgressSnapshot();
    expect((await weekTargets()).size).toBeGreaterThan(0);
    expect((await firstLive(() => getProgressSnapshot(Date.now(), false))).streak).toEqual(live.streak);
  });
});
