import { describe, expect, it } from 'vitest';
import { EXERCISE_CATALOG } from '../data/seed/exercise-catalog';
import type { PlanDraft, PlanItemEntity } from './entities';
import {
  PLAN_SHARE_FORMAT,
  canPairWithNext,
  exportSharedPlan,
  isPairedWithNext,
  isPairedWithPrev,
  normalizeDaySupersets,
  parseAndValidateSharedPlan,
  safeImportName,
  setPairWithNext,
  weeklyMuscleVolume,
} from './plan-share';

const exercises = new Map(EXERCISE_CATALOG.map((e) => [e.id, e]));

function sampleItem(over: Partial<PlanItemEntity> = {}): PlanItemEntity {
  return {
    id: 1,
    dayId: 1,
    position: 0,
    exerciseId: 'bench_press',
    block: 'Main',
    circuit: false,
    sets: 3,
    repsMin: 8,
    repsMax: 12,
    seconds: null,
    weightKg: 60,
    restSeconds: 90,
    note: '',
    supersetGroup: null,
    autoWarmup: true,
    progressionMode: 'LINEAR',
    ...over,
  };
}

describe('PlanShare', () => {
  it('rejects unsupported share formats', () => {
    const raw = JSON.stringify({ format: 'future-v2', name: 'Plan', days: [{ dayOfWeek: 1, title: 'Rest', isRest: true }] });
    expect(parseAndValidateSharedPlan(raw, exercises).ok).toBe(false);
  });
  it('exports and re-imports a plan preserving scheduleMode, supersets, autoWarmup, and progressionMode', () => {
    const json = exportSharedPlan(
      {
        plan: { name: 'Upper / Lower', description: 'Balanced split', scheduleMode: 'ROTATION' },
        days: [
          {
            day: { dayOfWeek: 1, title: 'Upper', isRest: false, note: '' },
            items: [
              sampleItem({ position: 0, exerciseId: 'bench_press', supersetGroup: 1, autoWarmup: true, progressionMode: 'LINEAR' }),
              sampleItem({ position: 1, exerciseId: 'barbell_row', supersetGroup: 1, autoWarmup: false, progressionMode: 'DOUBLE' }),
            ],
          },
          {
            day: { dayOfWeek: 2, title: 'Rest', isRest: true, note: '' },
            items: [],
          },
        ],
      },
      exercises,
    );

    expect(json).toContain(PLAN_SHARE_FORMAT);
    const res = parseAndValidateSharedPlan(json, exercises);
    expect(res.ok).toBe(true);
    if (!res.ok) return;
    expect(res.plan.scheduleMode).toBe('ROTATION');
    expect(res.plan.days[0].items[0].supersetGroup).toBe(1);
    expect(res.plan.days[0].items[1].supersetGroup).toBe(1);
    expect(res.plan.days[0].items[0].autoWarmup).toBe(true);
    expect(res.plan.days[0].items[0].progressionMode).toBe('LINEAR');
    expect(res.volume.find((v) => v.muscle === 'CHEST')?.sets).toBe(3);
  });

  it('accepts fenced markdown JSON, clamps out-of-bounds numbers, and strips mismatched supersets', () => {
    const fenced = [
      'Here is your plan:',
      '```json',
      JSON.stringify({
        format: PLAN_SHARE_FORMAT,
        name: 'Custom Split',
        days: [
          {
            dayOfWeek: 1,
            title: 'Day 1',
            isRest: false,
            items: [
              { name: null, kind: null, muscles: [], videoUrl: null, exerciseId: 'back_squat', sets: 25, repsMin: 5, repsMax: 3, restSeconds: 9999, supersetGroup: 4 },
              { exerciseId: 'Leg Curl', name: 'Seated Leg Curl', kind: 'WEIGHTED', muscles: ['HAMSTRINGS'], sets: 3, supersetGroup: 4 },
            ],
          },
        ],
      }),
      '```',
    ].join('\n');

    const res = parseAndValidateSharedPlan(fenced, exercises);
    expect(res.ok).toBe(true);
    if (!res.ok) return;
    const items = res.plan.days[0].items;
    expect(items[0].sets).toBe(10);
    expect(items[0].repsMax).toBe(5);
    expect(items[0].restSeconds).toBe(600);
    // Mismatched set counts (10 vs 3) strip the superset pairing
    expect(items[0].supersetGroup).toBeNull();
    expect(items[1].supersetGroup).toBeNull();
  });

  it('rejects wrong format, missing days, and unknown exercises without names', () => {
    expect(parseAndValidateSharedPlan('not json', exercises).ok).toBe(false);
    expect(parseAndValidateSharedPlan(JSON.stringify({ format: 'wrong', days: [] }), exercises).ok).toBe(false);
    expect(parseAndValidateSharedPlan(JSON.stringify({
      format: PLAN_SHARE_FORMAT,
      name: 'Bad',
      days: [{ dayOfWeek: 1, title: 'D1', isRest: false, items: [{ name: null, kind: null, muscles: [], videoUrl: null, exerciseId: 'totally_unknown_id', sets: 3 }] }],
    }), exercises).ok).toBe(false);
  });

  it('generates collision-free safeImportName without overwriting existing plans', () => {
    expect(safeImportName('PPL', ['Full Body'])).toBe('PPL');
    expect(safeImportName('PPL', ['ppl'])).toBe('PPL (2)');
    expect(safeImportName('PPL', ['PPL', 'PPL (2)', 'PPL (3)'])).toBe('PPL (4)');
  });

  it('computes weeklyMuscleVolume excluding Warm-up/Cool-down blocks', () => {
    const draft: Pick<PlanDraft, 'days'> = {
      days: [
        {
          dayOfWeek: 1,
          title: 'Push',
          isRest: false,
          note: '',
          items: [
            { name: null, kind: null, muscles: [], videoUrl: null, exerciseId: 'jumping_jacks', block: 'Warm-up', circuit: false, sets: 5, repsMin: 20, repsMax: null, seconds: null, weightKg: null, restSeconds: 20, note: '' },
            { name: null, kind: null, muscles: [], videoUrl: null, exerciseId: 'bench_press', block: 'Main', circuit: false, sets: 4, repsMin: 6, repsMax: 10, seconds: null, weightKg: 80, restSeconds: 120, note: '' },
          ],
        },
      ],
    };
    const vol = weeklyMuscleVolume(draft, exercises);
    expect(vol.find((v) => v.muscle === 'CALVES')).toBeUndefined();
    expect(vol.find((v) => v.muscle === 'CHEST')?.sets).toBe(4);
  });
});

describe('SupersetRules', () => {
  it('only pairs adjacent non-extra items with equal working sets and prevents 3-way chains', () => {
    const items = [
      sampleItem({ position: 0, exerciseId: 'bench_press', sets: 4 }),
      sampleItem({ position: 1, exerciseId: 'barbell_row', sets: 4 }),
      sampleItem({ position: 2, exerciseId: 'back_squat', sets: 4 }),
      sampleItem({ position: 3, exerciseId: 'deadlift', sets: 3 }),
    ];

    expect(canPairWithNext(items, 0)).toBe(true);
    expect(canPairWithNext(items, 2)).toBe(false); // 4 sets vs 3 sets

    const paired = setPairWithNext(items, 0, true);
    expect(isPairedWithNext(paired, 0)).toBe(true);
    expect(isPairedWithPrev(paired, 1)).toBe(true);
    // Middle item already paired with previous -> cannot pair with next (no 3-way superset)
    expect(canPairWithNext(paired, 1)).toBe(false);

    // Changing sets breaks the invalid superset on normalize
    const modified = paired.map((it, idx) => (idx === 0 ? { ...it, sets: 5 } : it));
    const normalized = normalizeDaySupersets(modified);
    expect(normalized[0].supersetGroup).toBeNull();
    expect(normalized[1].supersetGroup).toBeNull();
  });
});
