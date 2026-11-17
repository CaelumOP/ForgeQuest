import { describe, expect, it } from 'vitest';
import { pickInsights, recoverySplit } from './insights';
import type { ExerciseEntity, SessionEntity, SetEntity } from './entities';

const ex = new Map<string, ExerciseEntity>([
  ['push_up', { id: 'push_up', name: 'Push-up', kind: 'REPS', primary: ['CHEST'], secondary: ['TRICEPS'], custom: false, videoUrl: null }],
]);

describe('insights & recoverySplit', () => {
  it('splits fresh and recovering muscles', () => {
    const map = new Map([
      ['CHEST' as const, { fatigue: 0.1, hoursUntilReady: 0 }],
      ['QUADS' as const, { fatigue: 0.8, hoursUntilReady: 18 }],
      ['ABS' as const, { fatigue: 0.3, hoursUntilReady: 6 }],
    ]);
    expect(recoverySplit(map)).toEqual({ fresh: ['CHEST'], recovering: ['ABS', 'QUADS'] });
  });

  it('picks round-number rep milestones and respects cooldowns', () => {
    const sessions: SessionEntity[] = [
      { id: 1, title: 'A', epochDay: 100, startedAt: 1, finishedAt: 2, planDayId: null, note: '', debrief: '' },
      { id: 2, title: 'B', epochDay: 102, startedAt: 1, finishedAt: 2, planDayId: null, note: '', debrief: '' },
    ];
    const sets: SetEntity[] = [
      { id: 1, sessionId: 1, exerciseId: 'push_up', itemPosition: 0, block: 'Main', circuit: false, setIndex: 0, reps: 18, seconds: null, weightKg: null, done: true, isPr: false, note: '', effort: '', doneAt: null, targetLabel: '', restSeconds: 60 },
      { id: 2, sessionId: 2, exerciseId: 'push_up', itemPosition: 0, block: 'Main', circuit: false, setIndex: 0, reps: 20, seconds: null, weightKg: null, done: true, isPr: true, note: '', effort: '', doneAt: null, targetLabel: '', restSeconds: 60 },
    ];
    const picked = pickInsights({ sessions, sets, exercises: ex }, '', 102, 2);
    expect(picked[0]?.kind).toBe('FIRST_ROUND_REPS');
    const cooled = pickInsights({ sessions, sets, exercises: ex }, 'FIRST_ROUND_REPS:0:102', 103, 2);
    expect(cooled.some((i) => i.kind === 'FIRST_ROUND_REPS')).toBe(false);
  });
});
