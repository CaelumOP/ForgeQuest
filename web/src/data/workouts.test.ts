import 'fake-indexeddb/auto';
import { beforeEach, describe, expect, it } from 'vitest';
import { db, putSession, putSet, type SetRow, type PlanItemRow } from './db';
import { countsForProgression } from '../domain/set-types';
import { generateWarmupRamp } from '../domain/warmup-ramp';
import { FULL_BODY_BEGINNER } from './seed/starter-plans';
import {
  addExercise, applyPlan, finishSession, getActivePlan, getSets, importSharedPlan, moveSession,
  openSessionNow, recomputeRecords, seedLibrary, setPairWithNext, startSession, stepUp, updateFinishedSet,
  updatePlanDay, updatePlanItem, updateSet, deleteSession, restoreSession, recentDoneSets, withoutDeloadSessions, type PlanDayWithItems,
  allRecentDoneSets, baselineStatusFor, harderHint,
} from './workouts';
import { saveBaselineTest } from './baseline-tests';
import { exportSharedPlan, parseAndValidateSharedPlan } from '../domain/plan-share';

const pause = () => new Promise((r) => setTimeout(r, 5));

describe('baseline prefill and live status', () => {
  const row = (sessionId: number, exerciseId = 'bench_press', overrides: Partial<SetRow> = {}): SetRow => ({
    sessionId, exerciseId, itemPosition: 0, block: 'Main', circuit: false, setIndex: 0, targetLabel: '8-12',
    reps: 1, seconds: null, weightKg: 100, done: true, isPr: false, restSeconds: 90,
    note: '', effort: 'FAILURE', setType: 'WORKING', ...overrides,
  });
  const session = (epochDay = 100, startedAt = 1000, finishedAt: number | null = 2000) => putSession({
    title: 'Baseline', planDayId: null, epochDay, startedAt, finishedAt, note: '', debrief: '',
  });
  const item = (exerciseId: string, weightKg: number | null = null): PlanItemRow => ({
    dayId: 1, position: 0, exerciseId, block: 'Main', circuit: false, sets: 3, repsMin: 8,
    repsMax: 12, seconds: null, weightKg, restSeconds: 90, note: '',
  });

  it('prefills a new dumbbell exercise after barbell bench, while own history keeps normal progression', async () => {
    const old = await session();
    await putSet(row(old));
    const sid = await startSession(null);
    await addExercise(sid, 'db_bench_press');
    expect((await getSets(sid)).filter(countsForProgression).every((s) => s.weightKg === 25)).toBe(true);
    await db.sessions.update(sid, { finishedAt: 3000 });
    const historySession = await session(101);
    await putSet(row(historySession, 'db_bench_press', { weightKg: 17.5, reps: 8, effort: 'HARD' }));
    const next = await startSession(null);
    await addExercise(next, 'db_bench_press');
    const sets = (await getSets(next)).filter(countsForProgression);
    expect(sets.length).toBeGreaterThan(0);
    expect(sets.every((s) => s.weightKg === 17.5)).toBe(true);
  });

  it('orders donors by workout date/start/setIndex, excluding open and unticked sets and capping at 4000', async () => {
    const newest = await session(200, 2000);
    const olderStart = await session(200, 1000);
    const backdated = await session(100, 9000);
    const open = await session(300, 10000, null);
    await db.sets.bulkAdd([
      row(newest, 'bench_press', { setIndex: 2 }), row(newest, 'bench_press', { setIndex: 1 }),
      row(olderStart), row(backdated, 'chest_press_machine', { weightKg: 200 }),
      row(open), row(newest, 'bench_press', { done: false }),
    ]);
    expect((await allRecentDoneSets()).map((s) => [s.sessionId, s.setIndex])).toEqual([
      [newest, 1], [newest, 2], [olderStart, 0], [backdated, 0],
    ]);
    await addExercise(open, 'db_bench_press');
    expect((await getSets(open)).filter((s) => s.exerciseId === 'db_bench_press').every((s) => s.weightKg === 25)).toBe(true);
    await db.sets.bulkAdd(Array.from({ length: 4000 }, (_, i) => row(newest, 'bench_press', { setIndex: i + 3 })));
    const capped = await allRecentDoneSets();
    expect(capped).toHaveLength(4000);
    expect(capped.every((s) => s.sessionId === newest)).toBe(true);
    expect(capped.at(-1)!.setIndex).toBe(4000);
  });

  it('applies estimated load before generating the unchanged warm-up ramp', async () => {
    await putSet(row(await session()));
    const day: PlanDayWithItems = {
      day: { id: 1, planId: 1, dayOfWeek: 1, title: 'Push', isRest: false, note: '' },
      items: [{ ...item('incline_bench_press'), autoWarmup: true }],
    };
    const sets = await getSets(await startSession(day));
    const working = sets.filter(countsForProgression);
    expect(working).toHaveLength(3);
    expect(working.every((s) => s.weightKg === 65)).toBe(true);
    expect(sets.filter((s) => s.setType === 'WARMUP').map((s) => s.weightKg))
      .toEqual(generateWarmupRamp(65, 'KG', 20).map((s) => s.weightKg));
  });

  it('resolveBaselineStatusReportsCalibratedEstimatedAndNone (Android parity)', async () => {
    const old = await session();
    const donor = row(old);
    await putSet(donor);
    const bench = await db.exercises.get('bench_press');
    const dumbbell = await db.exercises.get('db_bench_press');
    const own = [row(20, 'db_bench_press', { weightKg: 25, done: false })];
    expect(await baselineStatusFor(bench, null, [], [donor])).toBe('CALIBRATED');
    expect(await baselineStatusFor(dumbbell, item('db_bench_press', 0), own, [])).toBe('ESTIMATED');
    expect(await baselineStatusFor(dumbbell, item('db_bench_press', 20), own, [])).toBe('NONE');
    expect(await baselineStatusFor(await db.exercises.get('overhead_press'), null, [row(20, 'overhead_press', { weightKg: 0, done: false })], [])).toBe('NONE');
    expect(await baselineStatusFor(await db.exercises.get('push_up'), null, [row(20, 'push_up', { weightKg: null, done: false })], [])).toBe('NONE');
    await db.sets.clear();
    expect(await baselineStatusFor(dumbbell, null, own, [])).toBe('NONE');
  });

  it('leaves an explicit plan load unchanged', async () => {
    await putSet(row(await session()));
    const sets = await getSets(await startSession({
      day: { id: 1, planId: 1, dayOfWeek: 1, title: 'Push', isRest: false, note: '' },
      items: [item('db_bench_press', 15)],
    }));
    expect(sets.filter(countsForProgression).every((s) => s.weightKg === 15)).toBe(true);
  });

  it('applies baseline precedence: history > baseline row > cold-start donor > blank', async () => {
    await seedLibrary();
    // Donor in history gives db_bench_press 25 kg if no baseline row exists.
    await putSet(row(await session()));
    // Baseline row for db_bench_press: 20 kg x 8, repsMin 8 -> target 15 kg (beats 25 kg donor).
    await saveBaselineTest({ exerciseId: 'db_bench_press', kind: 'WEIGHTED', weightKg: 20, reps: 8, createdAt: 1000 });
    // Baseline row for push_up: max 10 -> target 7 < repsMin 8, so replaces reps.
    await saveBaselineTest({ exerciseId: 'push_up', kind: 'REPS', reps: 10, createdAt: 1000 });
    // Baseline row for diamond_push_up: max 20 -> target 14 >= repsMin 8, so keeps plan reps (8).
    await saveBaselineTest({ exerciseId: 'diamond_push_up', kind: 'REPS', reps: 20, createdAt: 1000 });
    // Baseline row for plank: 60 s -> holdTarget 40 s.
    await saveBaselineTest({ exerciseId: 'plank', kind: 'HOLD', seconds: 60, createdAt: 1000 });
    // Too-light weighted baseline row falls back to donor/blank (incline_bench_press donor -> 65 kg).
    await saveBaselineTest({ exerciseId: 'incline_bench_press', kind: 'WEIGHTED', weightKg: 20, reps: 13, createdAt: 1000 });

    const day: PlanDayWithItems = {
      day: { id: 1, planId: 1, dayOfWeek: 1, title: 'Mix', isRest: false, note: '' },
      items: [
        { ...item('db_bench_press'), position: 0 },
        { ...item('push_up'), position: 1 },
        { ...item('diamond_push_up'), position: 2 },
        { dayId: 1, position: 3, exerciseId: 'plank', block: 'Core', circuit: false, sets: 3, repsMin: null, repsMax: null, seconds: 30, weightKg: null, restSeconds: 60, note: '' },
        { ...item('incline_bench_press'), position: 4 },
      ],
    };
    const sid = await startSession(day);
    const sets = await getSets(sid);

    const dbSets = sets.filter((s) => s.exerciseId === 'db_bench_press' && countsForProgression(s));
    expect(dbSets.every((s) => s.weightKg === 15)).toBe(true);
    expect(await baselineStatusFor(await db.exercises.get('db_bench_press'), day.items[0], dbSets, [])).toBe('FROM_BASELINE');

    const pushSets = sets.filter((s) => s.exerciseId === 'push_up' && countsForProgression(s));
    expect(pushSets.every((s) => s.reps === 7)).toBe(true);
    expect(await baselineStatusFor(await db.exercises.get('push_up'), day.items[1], pushSets, [])).toBe('FROM_BASELINE');

    const diamondSets = sets.filter((s) => s.exerciseId === 'diamond_push_up' && countsForProgression(s));
    expect(diamondSets.every((s) => s.reps === 8)).toBe(true);
    expect(await baselineStatusFor(await db.exercises.get('diamond_push_up'), day.items[2], diamondSets, [])).toBe('NONE');

    const plankSets = sets.filter((s) => s.exerciseId === 'plank' && countsForProgression(s));
    expect(plankSets.every((s) => s.seconds === 40)).toBe(true);
    expect(await baselineStatusFor(await db.exercises.get('plank'), day.items[3], plankSets, [])).toBe('FROM_BASELINE');

    const inclineSets = sets.filter((s) => s.exerciseId === 'incline_bench_press' && countsForProgression(s));
    expect(inclineSets.every((s) => s.weightKg === 65)).toBe(true);
    expect(await baselineStatusFor(await db.exercises.get('incline_bench_press'), day.items[4], inclineSets, [])).toBe('ESTIMATED');

    // Once real history exists, history wins over baseline row and status is CALIBRATED.
    await db.sessions.update(sid, { finishedAt: 5000 });
    const hist = await session(102);
    const doneRow = row(hist, 'db_bench_press', { weightKg: 22.5, reps: 8, effort: 'HARD' });
    await putSet(doneRow);
    const sid2 = await startSession(day);
    const dbSets2 = (await getSets(sid2)).filter((s) => s.exerciseId === 'db_bench_press' && countsForProgression(s));
    expect(dbSets2.every((s) => s.weightKg === 22.5)).toBe(true);
    expect(await baselineStatusFor(await db.exercises.get('db_bench_press'), day.items[0], dbSets2, [doneRow])).toBe('CALIBRATED');
  });
});

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
    expect((await db.exercises.get('burpee'))!.name).toBe('بيربي');
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

  it('a workout from a deload week is skipped when reading progression history', async () => {
    await applyPlan(FULL_BODY_BEGINNER, 'starter');
    const plan = (await getActivePlan())!;
    const day = plan.days.find((d) => !d.day.isRest)!;
    const item = await pickItem(day);

    const before = await startSession(day);
    await logAndFinish(before, item.exerciseId, 12, '');
    await pause();
    const deloadDay = await startSession(day);
    await logAndFinish(deloadDay, item.exerciseId, 6, '');
    // The plan started 3 weeks before the second workout, so that one fell in a deload week; the first a week earlier.
    const today = (await db.sessions.get(deloadDay))!.epochDay;
    await moveSession(before, today - 7);
    await db.plans.update(plan.plan.id!, { createdAt: Date.now() - 21 * 86_400_000 });

    const recent = await recentDoneSets(item.exerciseId);
    expect(recent[0].sessionId).toBe(deloadDay);
    const usable = await withoutDeloadSessions(recent);
    expect(new Set(usable.map((s) => s.sessionId))).toEqual(new Set([before]));
    // Only deload workouts: kept, so there is still something to go on.
    const onlyDeload = recent.filter((s) => s.sessionId === deloadDay);
    expect(await withoutDeloadSessions(onlyDeload)).toEqual(onlyDeload);
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

  it('undoing a deleted workout puts it back with its PR tag', async () => {
    const ids: number[] = [];
    for (const reps of [10, 14, 12]) {
      await pause();
      const id = await startSession(null);
      await addExercise(id, 'burpee');
      await logAndFinish(id, 'burpee', reps, 'OK');
      ids.push(id);
    }
    const session = (await db.sessions.get(ids[1]))!;
    const sets = await getSets(ids[1]);
    await deleteSession(ids[1]);
    await restoreSession(session, sets);
    expect((await db.sessions.get(ids[1]))?.id).toBe(ids[1]);
    expect((await db.sets.toArray()).filter((x) => x.isPr).map((x) => x.reps)).toEqual([14]);
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

  it('excludes warm-ups from doneSets and drops skipped warm-ups on tickAll', async () => {
    const id = await startSession(null);
    await putSet({
      sessionId: id, exerciseId: 'jumping_jacks', itemPosition: 0, block: 'Warm-up', circuit: false,
      setIndex: 0, targetLabel: '10 reps', reps: 10, seconds: null, weightKg: null,
      done: false, isPr: false, restSeconds: 20, note: '', effort: '', doneAt: null,
    });
    await putSet({
      sessionId: id, exerciseId: 'squat', itemPosition: 1, block: 'Main', circuit: false,
      setIndex: 0, targetLabel: '10 reps', reps: 10, seconds: null, weightKg: null,
      done: true, isPr: false, restSeconds: 60, note: '', effort: '', doneAt: Date.now(),
    });
    await putSet({
      sessionId: id, exerciseId: 'squat', itemPosition: 1, block: 'Main', circuit: false,
      setIndex: 1, targetLabel: '10 reps', reps: 10, seconds: null, weightKg: null,
      done: false, isPr: false, restSeconds: 60, note: '', effort: '', doneAt: null,
    });
    const res = await finishSession(id, true);
    expect(res.doneSets).toBe(2);
    const saved = await getSets(id);
    expect(saved.map((s) => s.exerciseId)).toEqual(['squat', 'squat']);
  });

  it('seedLibrary translates plan-builder titles and session titles in both directions', async () => {
    await applyPlan({
      name: 'Build muscle plan',
      description: 'Progression: when you hit the top of the range on every set, add load or reps. Every 4th week, halve the sets.',
      days: [{ dayOfWeek: 1, title: 'Full body', isRest: false, note: '', items: [] }],
    }, 'builder');
    const sessId = await putSession({
      planDayId: null, title: 'Full body', epochDay: 20000, startedAt: 1000, finishedAt: 2000, note: '', debrief: '',
    });
    await seedLibrary('ar');
    expect((await getActivePlan())!.plan.name).toBe('خطة بناء العضلات');
    expect((await db.sessions.get(sessId))!.title).toBe('الجسم كاملًا');
    await seedLibrary('en');
    expect((await getActivePlan())!.plan.name).toBe('Build muscle plan');
    expect((await db.sessions.get(sessId))!.title).toBe('Full body');
  });
});

describe('WP4 plan41 rules (rotation, supersets, autoWarmup, linear/maintain, sharing)', () => {
  async function seedRotationPlan() {
    await applyPlan({
      name: 'PPL Rotation',
      description: 'Push Pull Legs',
      days: [
        {
          dayOfWeek: 1, title: 'Push', isRest: false, note: '',
          items: [
            { name: null, kind: null, muscles: [], videoUrl: null, exerciseId: 'bench_press', block: 'Main', circuit: false, sets: 3, repsMin: 5, repsMax: 5, seconds: null, weightKg: 60, restSeconds: 120, note: '' },
            { name: null, kind: null, muscles: [], videoUrl: null, exerciseId: 'db_bench_press', block: 'Main', circuit: false, sets: 3, repsMin: 8, repsMax: 12, seconds: null, weightKg: 20, restSeconds: 90, note: '' },
          ],
        },
        {
          dayOfWeek: 3, title: 'Pull', isRest: false, note: '',
          items: [
            { name: null, kind: null, muscles: [], videoUrl: null, exerciseId: 'barbell_row', block: 'Main', circuit: false, sets: 3, repsMin: 8, repsMax: 10, seconds: null, weightKg: 60, restSeconds: 90, note: '' },
          ],
        },
        {
          dayOfWeek: 5, title: 'Legs', isRest: false, note: '',
          items: [
            { name: null, kind: null, muscles: [], videoUrl: null, exerciseId: 'back_squat', block: 'Main', circuit: false, sets: 3, repsMin: 5, repsMax: 5, seconds: null, weightKg: 100, restSeconds: 150, note: '' },
          ],
        },
      ],
    }, 'manual', 'ROTATION');
  }

  it('advances rotationCursor atomically once on finish, ignoring discard / quick / repeated finish / restore / edit', async () => {
    await seedRotationPlan();
    let plan = (await getActivePlan())!;
    expect(plan.plan.scheduleMode).toBe('ROTATION');
    expect(plan.plan.rotationCursor).toBe(0);

    // 1. Discard open session -> cursor stays 0
    const pushDay = plan.days.find((d) => d.day.title === 'Push')!;
    const discardedId = await startSession(pushDay);
    await deleteSession(discardedId);
    expect((await getActivePlan())!.plan.rotationCursor).toBe(0);

    // 2. Quick workout -> cursor stays 0
    const quickId = await startSession(null);
    await addExercise(quickId, 'burpee');
    await finishSession(quickId, true);
    expect((await getActivePlan())!.plan.rotationCursor).toBe(0);

    // 3. Qualifying workout finishes -> cursor becomes 1 (Pull)
    const s1 = await startSession(pushDay);
    await finishSession(s1, true);
    expect((await getActivePlan())!.plan.rotationCursor).toBe(1);

    // 4. Repeated finish on already-finished session -> cursor stays 1
    await finishSession(s1, true);
    expect((await getActivePlan())!.plan.rotationCursor).toBe(1);

    // 5. Restore deleted finished session -> cursor unchanged
    const s1Row = (await db.sessions.get(s1))!;
    const s1Sets = await getSets(s1);
    await deleteSession(s1);
    await restoreSession(s1Row, s1Sets);
    expect((await getActivePlan())!.plan.rotationCursor).toBe(1);

    // 6. Plan edit advancing cursor wrap: turn Legs into rest while cursor is 2 -> clamps/wraps back to 0
    const s2 = await startSession((await getActivePlan())!.days.find((d) => d.day.title === 'Pull')!);
    await finishSession(s2, true);
    expect((await getActivePlan())!.plan.rotationCursor).toBe(2);
    const legsDay = (await getActivePlan())!.days.find((d) => d.day.title === 'Legs')!.day;
    await updatePlanDay({ ...legsDay, isRest: true });
    plan = (await getActivePlan())!;
    expect(plan.plan.rotationCursor).toBe(0);
  });

  it('auto-enables autoWarmup only on heavy barbell compounds and generates WARMUP sets before Set 1 that do not affect progression/PRs', async () => {
    await seedRotationPlan();
    const push = (await getActivePlan())!.days.find((d) => d.day.title === 'Push')!;
    expect(push.items[0].autoWarmup).toBe(true); // bench_press
    expect(push.items[1].autoWarmup).toBe(false); // db_bench_press

    const sid = await startSession(push);
    const sets = (await getSets(sid)).filter((s) => s.exerciseId === 'bench_press').sort((a, b) => a.setIndex - b.setIndex);
    const warmups = sets.filter((s) => (s.setType ?? 'WORKING') === 'WARMUP');
    const workings = sets.filter((s) => (s.setType ?? 'WORKING') === 'WORKING');
    expect(warmups.map((s) => s.weightKg)).toEqual([20, 30, 42.5, 50]); // empty bar then 50/70/85% of 60 kg
    expect(warmups[warmups.length - 1].setIndex).toBeLessThan(workings[0].setIndex);
    expect(workings).toHaveLength(3);

    // Complete warm-ups at heavier weight + drop/work sets, then check finish/recomputeRecords excludes warmups
    await updateSet({ ...warmups[0], weightKg: 999, done: true, effort: 'PAIN' });
    for (const w of workings) await updateSet({ ...w, reps: 5, weightKg: 60, done: true, effort: 'OK' });
    const res = await finishSession(sid, false);
    expect(res.doneSets).toBe(3); // only 3 working bench sets counted in Push after db_bench dropped as unticked
    await recomputeRecords('bench_press');
    const saved = (await getSets(sid)).filter((s) => s.exerciseId === 'bench_press');
    expect(saved.find((s) => s.setType === 'WARMUP')?.isPr).toBe(false);
  });

  it('enforces equal working sets for supersets and preserves/clears on edit', async () => {
    await seedRotationPlan();
    const push = (await getActivePlan())!.days.find((d) => d.day.title === 'Push')!;
    await setPairWithNext(push.day.id!, push.items[0].id!, true);
    let updated = (await getActivePlan())!.days.find((d) => d.day.id === push.day.id)!;
    expect(updated.items[0].supersetGroup).toBe(1);
    expect(updated.items[1].supersetGroup).toBe(1);

    // Changing set count to 4 breaks the superset invariant automatically
    await updatePlanItem({ ...updated.items[0], sets: 4 });
    updated = (await getActivePlan())!.days.find((d) => d.day.id === push.day.id)!;
    expect(updated.items[0].supersetGroup).toBeNull();
    expect(updated.items[1].supersetGroup).toBeNull();

    // Trying to pair mismatched set counts (4 vs 3) does nothing
    await setPairWithNext(push.day.id!, updated.items[0].id!, true);
    updated = (await getActivePlan())!.days.find((d) => d.day.id === push.day.id)!;
    expect(updated.items[0].supersetGroup).toBeNull();
  });

  it('supports LINEAR and MAINTAIN progression modes in live sessions', async () => {
    await applyPlan({
      name: 'Linear Test',
      description: '',
      days: [
        {
          dayOfWeek: 1, title: 'Full', isRest: false, note: '',
          items: [
            { name: null, kind: null, muscles: [], videoUrl: null, exerciseId: 'back_squat', block: 'Main', circuit: false, sets: 3, repsMin: 5, repsMax: 5, seconds: null, weightKg: 100, restSeconds: 120, note: '' },
          ],
        },
      ],
    }, 'manual');
    const item = (await getActivePlan())!.days.find((d) => !d.day.isRest)!.items[0];
    await updatePlanItem({ ...item, progressionMode: 'LINEAR', autoWarmup: false });

    const s1 = await startSession((await getActivePlan())!.days.find((d) => !d.day.isRest)!);
    for (const row of await getSets(s1)) await updateSet({ ...row, reps: 5, weightKg: 100, done: true, effort: 'OK' });
    await finishSession(s1, false);

    // Next session steps up +5 kg for lower-body LINEAR lift
    const s2 = await startSession((await getActivePlan())!.days.find((d) => !d.day.isRest)!);
    expect((await getSets(s2))[0].weightKg).toBeCloseTo(105, 2);
  });

  it('exports and imports a shared plan without overwriting existing plans', async () => {
    await seedRotationPlan();
    const active = (await getActivePlan())!;
    const lib = new Map((await db.exercises.toArray()).map((e) => [e.id, e]));
    const json = exportSharedPlan(active, lib);
    const parsed = parseAndValidateSharedPlan(json, lib);
    expect(parsed.ok).toBe(true);
    if (!parsed.ok) return;

    const importedId = await importSharedPlan(parsed.plan);
    const allPlans = await db.plans.toArray();
    expect(allPlans).toHaveLength(2);
    expect(allPlans.find((p) => p.id === active.plan.id)?.active).toBe(false);
    expect(allPlans.find((p) => p.id === importedId)?.name).toBe('PPL Rotation (2)');
    expect(allPlans.find((p) => p.id === importedId)?.scheduleMode).toBe('ROTATION');
  });
});

describe('v4.2 audit fixes (stepUp done-set guard & autoTicked PR exclusion)', () => {
  it('stepUp keeps positions with done sets on the old exercise, updates plan items, and returns swapped count', async () => {
    await applyPlan({
      name: 'StepUp Test',
      description: '',
      days: [
        {
          dayOfWeek: 1, title: 'Day 1', isRest: false, note: '',
          items: [
            { name: null, kind: null, muscles: [], videoUrl: null, exerciseId: 'push_up', block: 'Main', circuit: false, sets: 3, repsMin: 10, repsMax: 12, seconds: null, weightKg: null, restSeconds: 60, note: '' },
          ],
        },
      ],
    }, 'manual');
    const day = (await getActivePlan())!.days.find((d) => !d.day.isRest)!;
    const sid = await startSession(day);
    const initialSets = (await getSets(sid)).filter((s) => s.exerciseId === 'push_up');
    await updateSet({ ...initialSets[0], reps: 12, done: true, doneAt: Date.now() });

    const swappedDone = await stepUp(sid, 'push_up', 'diamond_push_up');
    expect(swappedDone).toBe(0);
    const keptSets = await getSets(sid);
    expect(keptSets.every((s) => s.exerciseId === 'push_up')).toBe(true);
    expect(keptSets[0].done).toBe(true);
    expect(keptSets[0].reps).toBe(12);
    const updatedDay = (await getActivePlan())!.days.find((d) => d.day.id === day.day.id)!;
    expect(updatedDay.items[0].exerciseId).toBe('diamond_push_up');

    // A fresh session with no done sets swaps in-place and returns 1
    await finishSession(sid, false);
    const sid2 = await startSession(updatedDay);
    await addExercise(sid2, 'push_up');
    const swappedUndone = await stepUp(sid2, 'push_up', 'diamond_push_up');
    expect(swappedUndone).toBe(1);
    const sets2 = (await getSets(sid2)).filter((s) => s.itemPosition === 1);
    expect(sets2.every((s) => s.exerciseId === 'diamond_push_up')).toBe(true);
  });

  it('finishSession(tickAll) marks autoTicked sets and recomputeRecords never turns them into PRs after edits', async () => {
    const s1 = await startSession(null);
    await addExercise(s1, 'bench_press');
    const sets1 = await getSets(s1);
    await updateSet({ ...sets1[0], reps: 8, weightKg: 60, done: true, doneAt: Date.now() });
    await updateSet({ ...sets1[1], reps: 10, weightKg: 100, done: false });
    await finishSession(s1, true);

    const saved1 = await getSets(s1);
    expect(saved1[0].autoTicked).toBe(false);
    expect(saved1[1].autoTicked).toBe(true);
    expect(saved1[1].isPr).toBe(false);

    // Later workout beats set 0 (60kg) while remaining below autoTicked set 1 (100kg)
    await pause();
    const s2 = await startSession(null);
    await addExercise(s2, 'bench_press');
    const sets2 = await getSets(s2);
    await updateSet({ ...sets2[0], reps: 8, weightKg: 70, done: true, doneAt: Date.now() });
    await finishSession(s2, false);

    // Editing a finished set triggers recomputeRecords: autoTicked set must stay non-PR, and s2 beats s1's manual set
    const saved2 = await getSets(s2);
    await updateFinishedSet({ ...saved2[0], reps: 9 });

    const after1 = await getSets(s1);
    const after2 = await getSets(s2);
    expect(after1.find((s) => s.autoTicked)?.isPr).toBe(false);
    expect(after2[0].isPr).toBe(true);

    // Editing the load confirms an auto-ticked set and makes it eligible for future comparisons
    await updateFinishedSet({ ...after1[1], weightKg: 120 });
    const afterEditAuto = await getSets(s1);
    expect(afterEditAuto[1].autoTicked).toBe(false);
    expect(afterEditAuto[1].isPr).toBe(false); // first session still never earns a PR
    expect((await getSets(s2))[0].isPr).toBe(false);
  });
});

describe('W2 review fixes', () => {
  const finished = (epochDay: number) => putSession({
    title: 'Past', planDayId: null, epochDay, startedAt: epochDay * 1000, finishedAt: epochDay * 1000 + 500, note: '', debrief: '',
  });
  const pushUp = (sessionId: number, setIndex: number, reps: number, overrides: Partial<SetRow> = {}): SetRow => ({
    sessionId, exerciseId: 'push_up', itemPosition: 0, block: 'Main', circuit: false, setIndex, targetLabel: '',
    reps, seconds: null, weightKg: null, done: true, isPr: false, restSeconds: 60,
    note: '', effort: 'EASY', setType: 'WORKING', ...overrides,
  });
  const twoSessions = async (extra: (sid: number) => SetRow) => {
    for (const day of [100, 101]) {
      const sid = await finished(day);
      await db.sets.bulkAdd([pushUp(sid, 0, 15), pushUp(sid, 1, 15), pushUp(sid, 2, 15), extra(sid)]);
    }
  };

  it('harderHint ignores rest-pause burnout sets (Android countsForProgression)', async () => {
    await twoSessions((sid) => pushUp(sid, 3, 6, { setType: 'RESTPAUSE' }));
    const ex = (await db.exercises.get('push_up'))!;
    expect(await harderHint(ex, false, -1)).toBeDefined();
  });

  it('harderHint reads a blank-effort FAILURE set as Failure (A11)', async () => {
    await twoSessions((sid) => pushUp(sid, 3, 15, { effort: '', setType: 'FAILURE' }));
    const ex = (await db.exercises.get('push_up'))!;
    expect(await harderHint(ex, false, -1)).toBeUndefined();
  });

  it('harderHint respects plan topReps (S7)', async () => {
    for (const day of [200, 201]) {
      const sid = await finished(day);
      await db.sets.bulkAdd([pushUp(sid, 0, 14), pushUp(sid, 1, 14), pushUp(sid, 2, 14)]);
    }
    const ex = (await db.exercises.get('push_up'))!;
    expect(await harderHint(ex, false, -1)).toBeUndefined();
    expect(await harderHint(ex, false, -1, 12)).toBeDefined();
  });

  it('harderHint marks a 10-rep fixed item (repsMax null) ready at 12 x 3 twice', async () => {
    for (const day of [202, 203]) {
      const sid = await finished(day);
      await db.sets.bulkAdd([pushUp(sid, 0, 12), pushUp(sid, 1, 12), pushUp(sid, 2, 12)]);
    }
    const ex = (await db.exercises.get('push_up'))!;
    expect(await harderHint(ex, false, -1)).toBeUndefined();
    const fixed = { repsMin: 10, repsMax: null as number | null };
    expect(await harderHint(ex, false, -1, fixed.repsMax ?? fixed.repsMin)).toBeDefined();
  });

  it('a deload week halves working items but keeps the warm-up block sets', async () => {
    await applyPlan(FULL_BODY_BEGINNER, 'starter');
    const plan = (await getActivePlan())!;
    await db.plans.update(plan.plan.id!, { createdAt: Date.now() - 21 * 86_400_000 });
    const first = plan.days.find((d) => !d.day.isRest && d.items.filter((i) => i.sets > 1).length >= 2)!;
    const [warmItem] = first.items.filter((i) => i.sets > 1);
    await db.planItems.update(warmItem.id!, { block: 'Warm-up' });
    const day = (await getActivePlan())!.days.find((d) => d.day.id === first.day.id)!;
    const warm = day.items.find((i) => i.id === warmItem.id)!;
    const main = day.items.find((i) => i.block !== 'Warm-up' && i.sets > 1)!;
    const sets = await getSets(await startSession(day));
    const count = (pos: number) => sets.filter((s) => s.itemPosition === pos && countsForProgression(s)).length
      + sets.filter((s) => s.itemPosition === pos && s.block === 'Warm-up').length;
    expect(sets.filter((s) => s.itemPosition === warm.position)).toHaveLength(warm.sets);
    expect(count(main.position)).toBe(Math.trunc((main.sets + 1) / 2));
  });

  it('applyPlan copies supersetGroup to stored PlanItemEntity and keeps valid builder pairs', async () => {
    await applyPlan({
      name: 'Superset Plan',
      description: '',
      days: [
        {
          dayOfWeek: 1, title: 'Full body', isRest: false, note: '',
          items: [
            { name: null, kind: null, muscles: [], videoUrl: null, exerciseId: 'db_bench_press', block: 'Main', circuit: false, supersetGroup: 1, sets: 3, repsMin: 8, repsMax: 12, seconds: null, weightKg: null, restSeconds: 40, note: '' },
            { name: null, kind: null, muscles: [], videoUrl: null, exerciseId: 'db_row', block: 'Main', circuit: false, supersetGroup: 1, sets: 3, repsMin: 8, repsMax: 12, seconds: null, weightKg: null, restSeconds: 40, note: '' },
          ],
        },
      ],
    }, 'builder');
    const day = (await getActivePlan())!.days.find((d) => !d.day.isRest)!;
    expect(day.items[0].supersetGroup).toBe(1);
    expect(day.items[1].supersetGroup).toBe(1);
  });
});
