import 'fake-indexeddb/auto';
import { beforeEach, describe, expect, it } from 'vitest';
import { db } from './db';
import {
  getBaselineTests, hasAnyBaselineTest, latestAllBaselines, latestBaselineTest,
  migratePlanBuilderBaselines, planInputWithBaselines, PULL_UP_ID, PUSH_UP_ID, saveBaselineTest,
} from './baseline-tests';
import { getProgressSnapshot } from './progress';
import { EXERCISE_BY_ID } from './seed/exercise-catalog';
import { loadSettings, updateSettings } from './settings';
import { defaultPlanAnswers, decodePlanAnswers, encodePlanAnswers } from '../domain/plan-answers';

beforeEach(async () => {
  await Promise.all(db.tables.map((t) => t.clear()));
});

describe('baseline-tests data module', () => {
  it('verifies standard push-up and pull-up IDs exist in the catalog', () => {
    expect(EXERCISE_BY_ID.get(PUSH_UP_ID)?.kind).toBe('REPS');
    expect(EXERCISE_BY_ID.get(PULL_UP_ID)?.kind).toBe('REPS');
  });

  it('saves rows, returns latest per exerciseId, and preserves history', async () => {
    const id1 = await saveBaselineTest({ exerciseId: PUSH_UP_ID, kind: 'REPS', reps: 12, createdAt: 1000 });
    const id2 = await saveBaselineTest({ exerciseId: PUSH_UP_ID, kind: 'REPS', reps: 18, createdAt: 2000 });
    await saveBaselineTest({ exerciseId: 'plank', kind: 'HOLD', seconds: 45, createdAt: 1500 });
    await saveBaselineTest({ exerciseId: 'bench_press', kind: 'WEIGHTED', weightKg: 60, reps: 8, createdAt: 2500 });

    expect(id2).toBeGreaterThan(id1);
    expect(await hasAnyBaselineTest()).toBe(true);

    const all = await getBaselineTests();
    expect(all).toHaveLength(4);
    expect(all.map((r) => r.exerciseId)).toEqual(['bench_press', PUSH_UP_ID, 'plank', PUSH_UP_ID]);

    const latestPush = await latestBaselineTest(PUSH_UP_ID);
    expect(latestPush?.reps).toBe(18);
    expect(latestPush?.seconds).toBeNull();
    expect(latestPush?.weightKg).toBeNull();

    const latestMap = await latestAllBaselines();
    expect(latestMap.size).toBe(3);
    expect(latestMap.get(PUSH_UP_ID)?.reps).toBe(18);
    expect(latestMap.get('plank')?.seconds).toBe(45);
    expect(latestMap.get('bench_press')?.weightKg).toBe(60);
    expect(await latestBaselineTest('squat')).toBeNull();
  });

  it('breaks ties by newest id when createdAt timestamps match', async () => {
    await saveBaselineTest({ exerciseId: PUSH_UP_ID, kind: 'REPS', reps: 10, createdAt: 5000 });
    await saveBaselineTest({ exerciseId: PUSH_UP_ID, kind: 'REPS', reps: 15, createdAt: 5000 });
    expect((await latestBaselineTest(PUSH_UP_ID))?.reps).toBe(15);
  });

  it('migrates pushUpMax and pullUpMax once only when no row exists and keeps answers readable', async () => {
    const answers = { ...defaultPlanAnswers(), pushUpMax: 14, pullUpMax: 0 };
    const encoded = encodePlanAnswers(answers);
    await updateSettings((s) => ({ ...s, planBuilderAnswers: encoded }));

    // Existing row for pull_up should NOT be overwritten; missing push_up SHOULD be migrated.
    await saveBaselineTest({ exerciseId: PULL_UP_ID, kind: 'REPS', reps: 6, createdAt: 100 });

    expect(await migratePlanBuilderBaselines()).toBe(true);
    expect(await migratePlanBuilderBaselines()).toBe(false);

    const pushRow = await latestBaselineTest(PUSH_UP_ID);
    const pullRow = await latestBaselineTest(PULL_UP_ID);
    expect(pushRow?.reps).toBe(14);
    expect(pushRow?.kind).toBe('REPS');
    expect(pullRow?.reps).toBe(6);
    expect((await getBaselineTests()).filter((r) => r.exerciseId === PULL_UP_ID)).toHaveLength(1);

    // Saved plan-builder answers remain intact and readable.
    const reloaded = decodePlanAnswers((await loadSettings()).planBuilderAnswers);
    expect(reloaded?.pushUpMax).toBe(14);
    expect(reloaded?.pullUpMax).toBe(0);
  });

  it('migrates zero pushUpMax into a baseline row', async () => {
    const encoded = encodePlanAnswers({ ...defaultPlanAnswers(), pushUpMax: 0, pullUpMax: null });
    await updateSettings((s) => ({ ...s, planBuilderAnswers: encoded }));

    expect(await migratePlanBuilderBaselines()).toBe(true);
    expect((await latestBaselineTest(PUSH_UP_ID))?.reps).toBe(0);
    expect(await latestBaselineTest(PULL_UP_ID)).toBeNull();
  });

  it('builds PlanInput from latest baseline rows with fallback to answers', async () => {
    await saveBaselineTest({ exerciseId: PUSH_UP_ID, kind: 'REPS', reps: 22, createdAt: 1000 });
    const input = await planInputWithBaselines({ ...defaultPlanAnswers(), pushUpMax: 5, pullUpMax: 4 });
    expect(input.pushUpMax).toBe(22);
    expect(input.pullUpMax).toBe(4);
  });

  it('never leaks baseline tests into streak, XP, PRs, volume or workout history', async () => {
    await saveBaselineTest({ exerciseId: PUSH_UP_ID, kind: 'REPS', reps: 30, createdAt: Date.now() });
    await saveBaselineTest({ exerciseId: 'bench_press', kind: 'WEIGHTED', weightKg: 100, reps: 5, createdAt: Date.now() });

    expect(await db.sessions.count()).toBe(0);
    expect(await db.sets.count()).toBe(0);

    const snap = await getProgressSnapshot();
    expect(snap.sessions).toHaveLength(0);
    expect(snap.doneSets).toHaveLength(0);
    expect(snap.level.totalXp).toBe(0);
    expect(snap.streak.weeks).toBe(0);
    expect(snap.stats.workouts).toBe(0);
  });
});
