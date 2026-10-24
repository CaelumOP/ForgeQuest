import 'fake-indexeddb/auto';
import { beforeEach, describe, expect, it } from 'vitest';
import { db, putSession, putSet, type SetRow } from './db';
import { FULL_BODY_BEGINNER } from './seed/starter-plans';
import {
  addExercise, applyPlan, finishSession, getActivePlan, getSets, moveSession, openSessionNow, seedLibrary,
  startSession, updateSet, deleteSession, recentDoneSets, type PlanDayWithItems,
} from './workouts';

const pause = () => new Promise((r) => setTimeout(r, 5));

beforeEach(async () => {
  await Promise.all(db.tables.map((t) => t.clear()));
  await seedLibrary();
});

/** First training day's REPS exercise that is not a warm-up: where prefill changes are easy to see. */
async function pickItem(day: PlanDayWithItems) {
  for (const item of day.items) {
    const ex = await db.exercises.get(item.exerciseId);
    if (ex?.kind === 'REPS' && item.block.toLowerCase() !== 'warm-up' && ex.id !== 'jumping_jacks') return item;
  }
  throw new Error('no REPS item in the starter day');
}

async function logAndFinish(sessionId: number, exerciseId: string, reps: number, effort = 'HARD') {
  for (const s of (await getSets(sessionId)).filter((x) => x.exerciseId === exerciseId)) {
    await updateSet({ ...s, reps, done: true, effort });
  }
  return finishSession(sessionId);
}

describe('library and plans', () => {
  it('seeds the catalog once and uses Arabic names for ar', async () => {
    const n = await db.exercises.count();
    expect(n).toBeGreaterThan(50);
    await seedLibrary();
    expect(await db.exercises.count()).toBe(n);
    await seedLibrary('ar');
    expect((await db.exercises.get('burpee'))!.name).toBe('البيربي');
    await seedLibrary('en');
    expect((await db.exercises.get('burpee'))!.name).toBe('Burpee');
  });

  it('applies a starter plan as the only active plan with seven days', async () => {
    await applyPlan(FULL_BODY_BEGINNER, 'starter');
    const second = await applyPlan(FULL_BODY_BEGINNER, 'starter');
    const active = await getActivePlan();
    expect(active!.plan.id).toBe(second);
    expect((await db.plans.toArray()).filter((p) => p.active)).toHaveLength(1);
    expect(active!.days).toHaveLength(7);
    expect(active!.days.filter((d) => !d.day.isRest).length).toBe(3);
  });
});

describe('live session', () => {
  it('starter plan to session to sets to finish; the next prefill uses those sets', async () => {
    await applyPlan(FULL_BODY_BEGINNER, 'starter');
    const plan = (await getActivePlan())!;
    const day = plan.days.find((d) => !d.day.isRest)!;
    const item = await pickItem(day);

    const id = await startSession(day);
    expect((await openSessionNow())!.id).toBe(id);
    expect(await startSession(day)).toBe(id); // resume, not a second one
    const sets = (await getSets(id)).filter((s) => s.exerciseId === item.exerciseId);
    expect(sets).toHaveLength(item.sets);
    expect(sets[0].reps).toBe(item.repsMin ?? 10);

    const res = await logAndFinish(id, item.exerciseId, 17);
    expect(res.doneSets).toBe(item.sets);
    expect(res.prs).toEqual([]); // first time: nothing to beat
    expect(await openSessionNow()).toBeUndefined();
    expect(await db.sets.where('sessionId').equals(id).count()).toBe(item.sets); // unticked sets dropped

    const next = await startSession(day);
    const prefilled = (await getSets(next)).filter((s) => s.exerciseId === item.exerciseId);
    expect(prefilled.map((s) => s.reps)).toEqual(Array(item.sets).fill(17)); // HARD: repeat last numbers
  });

  it('a backdated workout does not drive the next prefill', async () => {
    await applyPlan(FULL_BODY_BEGINNER, 'starter');
    const day = (await getActivePlan())!.days.find((d) => !d.day.isRest)!;
    const item = await pickItem(day);

    const a = await startSession(day);
    await logAndFinish(a, item.exerciseId, 17);

    // Logged afterwards but dated a month ago.
    await pause();
    const old = await startSession(null);
    await addExercise(old, item.exerciseId);
    await logAndFinish(old, item.exerciseId, 5);
    await moveSession(old, (await db.sessions.get(old))!.epochDay - 30);

    const recent = await recentDoneSets(item.exerciseId);
    expect(recent[0].sessionId).toBe(a);

    const next = await startSession(day);
    const prefilled = (await getSets(next)).filter((s) => s.exerciseId === item.exerciseId);
    expect(prefilled[0].reps).toBe(17);
  });

  it('detects a personal record, and not a lower session', async () => {
    const run = async (reps: number) => {
      await pause();
      const id = await startSession(null);
      await addExercise(id, 'burpee');
      return logAndFinish(id, 'burpee', reps, 'OK');
    };
    expect((await run(10)).prs).toEqual([]);
    const better = await run(14);
    expect(better.prs).toEqual(['Burpee']);
    expect(better.xpGained).toBeGreaterThan(0);
    expect((await run(8)).prs).toEqual([]);
    const flagged = (await db.sets.toArray()).filter((s: SetRow) => s.isPr);
    expect(flagged).toHaveLength(1);
    expect(flagged[0].reps).toBe(14);
  });

  it('deleting the record workout moves the PR tag to what is now the best', async () => {
    const ids: number[] = [];
    for (const reps of [10, 14, 12]) {
      await pause();
      const id = await startSession(null);
      await addExercise(id, 'burpee');
      await logAndFinish(id, 'burpee', reps, 'OK');
      ids.push(id);
    }
    await deleteSession(ids[1]);
    const prs = (await db.sets.toArray()).filter((s) => s.isPr);
    expect(prs.map((s) => s.reps)).toEqual([12]);
  });

  it('every tap is a single-row write', async () => {
    const id = await startSession(null);
    await addExercise(id, 'burpee');
    const first = (await getSets(id))[0];
    await updateSet({ ...first, done: true, reps: 9 });
    expect((await db.sets.get(first.id!))!.reps).toBe(9);
    expect(await putSession({ ...(await db.sessions.get(id))!, note: 'x' })).toBe(id);
    expect(await putSet({ ...first, reps: 11 })).toBe(first.id);
  });
});
