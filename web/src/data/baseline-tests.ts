// Baseline tests ("Find your starting point"): stores max reps, max holds and submax weighted tests
// in Dexie `baselineTests` without touching workout sessions, sets, streaks, XP, PRs or volume.
import { db, putBaselineTest, type BaselineTestRow } from './db';
import { useLive } from './live';
import { loadSettings } from './settings';
import type { ExerciseKind } from '../domain/muscle';
import { decodePlanAnswers, planAnswersToInput, type PlanAnswers } from '../domain/plan-answers';
import type { PlanInput } from '../domain/plan-builder';

export const PUSH_UP_ID = 'push_up';
export const PULL_UP_ID = 'pull_up';

export type SaveBaselineTestInput = {
  exerciseId: string;
  kind: ExerciseKind;
  reps?: number | null;
  seconds?: number | null;
  weightKg?: number | null;
  createdAt?: number;
};

const newestFirst = (a: BaselineTestRow, b: BaselineTestRow): number =>
  b.createdAt - a.createdAt || (b.id ?? 0) - (a.id ?? 0);

/** Saves a baseline test row. Never touches workout sessions or sets. */
export function saveBaselineTest(input: SaveBaselineTestInput): Promise<number> {
  const row: BaselineTestRow = {
    exerciseId: input.exerciseId,
    kind: input.kind,
    reps: input.kind === 'HOLD' ? null : (input.reps != null ? Math.trunc(input.reps) : null),
    seconds: input.kind === 'HOLD' ? (input.seconds != null ? Math.trunc(input.seconds) : null) : null,
    weightKg: input.kind === 'WEIGHTED' ? (input.weightKg ?? null) : null,
    createdAt: input.createdAt ?? Date.now(),
  };
  return putBaselineTest(row);
}

/** All baseline test rows, newest first. */
export async function getBaselineTests(): Promise<BaselineTestRow[]> {
  return (await db.baselineTests.toArray()).sort(newestFirst);
}
export const useBaselineTests = (): BaselineTestRow[] => useLive(getBaselineTests, [], []);

/** Newest baseline test row for `exerciseId`, or null when never tested. */
export async function latestBaselineTest(exerciseId: string): Promise<BaselineTestRow | null> {
  const rows = await db.baselineTests.where('exerciseId').equals(exerciseId).toArray();
  return rows.sort(newestFirst)[0] ?? null;
}

/** Map of exerciseId -> newest baseline test row across all tested exercises. */
export async function latestAllBaselines(): Promise<Map<string, BaselineTestRow>> {
  const all = await getBaselineTests();
  const latest = new Map<string, BaselineTestRow>();
  for (const row of all) {
    if (!latest.has(row.exerciseId)) latest.set(row.exerciseId, row);
  }
  return latest;
}
export const useLatestBaselines = (): Map<string, BaselineTestRow> =>
  useLive(latestAllBaselines, [], new Map<string, BaselineTestRow>());

/** Whether at least one baseline test row exists. */
export const hasAnyBaselineTest = async (): Promise<boolean> => (await db.baselineTests.count()) > 0;

/**
 * Idempotent migration of existing `pushUpMax` / `pullUpMax` values from saved plan-builder answers
 * into `baselineTests` (kind REPS), only if no baseline row already exists for that exerciseId.
 * Keeps `planBuilderAnswers` intact and readable.
 */
export async function migratePlanBuilderBaselines(): Promise<boolean> {
  const settings = await loadSettings();
  const decoded = decodePlanAnswers(settings.planBuilderAnswers);
  const pushUpMax = decoded?.pushUpMax ?? null;
  const pullUpMax = decoded?.pullUpMax ?? null;
  if (pushUpMax == null && pullUpMax == null) return false;

  // No persisted "done" flag (same as Android): insert-if-no-row keeps it idempotent and lets a restored old backup re-import its maxima.
  return db.transaction('rw', [db.baselineTests], async () => {
    const now = Date.now();
    let inserted = false;
    const maybeInsert = async (exerciseId: string, max: number | null) => {
      if (max == null || !Number.isFinite(max) || max < 0) return;
      if ((await db.baselineTests.where('exerciseId').equals(exerciseId).count()) > 0) return;
      await putBaselineTest({
        exerciseId,
        kind: 'REPS',
        reps: Math.trunc(max),
        seconds: null,
        weightKg: null,
        createdAt: now,
      });
      inserted = true;
    };
    await maybeInsert(PUSH_UP_ID, pushUpMax);
    await maybeInsert(PULL_UP_ID, pullUpMax);
    return inserted;
  });
}

/** Converts plan-builder answers into `PlanInput`, reading `pushUpMax` / `pullUpMax` from the latest baseline rows with fallback to `answers`. */
export async function planInputWithBaselines(
  answers: Partial<PlanAnswers> = {},
  offset = 0,
): Promise<Required<PlanInput>> {
  const latest = await latestAllBaselines();
  return planAnswersToInput(answers, offset, latest);
}
