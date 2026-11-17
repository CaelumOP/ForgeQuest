import { describe, expect, it } from 'vitest';
import { EXERCISE_CATALOG } from '../data/seed/exercise-catalog';
import { nextVariation, recentlyCycledIds, recordCycle } from './variation-cycle';

describe('variationCycle', () => {
  it('suggests a variation for a stalled core lift and respects cooldowns', () => {
    const stalled = { exerciseId: 'bench_press', exerciseName: 'Bench press', bestScore: 100, workoutsFlat: 3, sessionsCount: 3, spanDays: 14 };
    const planIds = new Set(['bench_press']);
    const eq = new Set(['BARBELL', 'DUMBBELLS', 'FLAT_BENCH'] as const);
    const sug = nextVariation(stalled, EXERCISE_CATALOG, eq, planIds, new Set());
    expect(sug).not.toBeNull();
    expect(sug!.to.id).not.toBe('bench_press');
    expect(sug!.to.kind).toBe('WEIGHTED');

    const hist = recordCycle('', sug!.to.id, 100);
    expect(recentlyCycledIds(hist, 120).has(sug!.to.id)).toBe(true);
    expect(recentlyCycledIds(hist, 160).has(sug!.to.id)).toBe(false);
    const nextSug = nextVariation(stalled, EXERCISE_CATALOG, eq, planIds, recentlyCycledIds(hist, 120));
    expect(nextSug?.to.id).not.toBe(sug!.to.id);
  });
});
