import { describe, expect, it } from 'vitest';
import { volumeChangePercent, volumeChangeVsLastTime } from './volume-change';
import type { SessionEntity, SetEntity } from './entities';

const mkSet = (sessionId: number, weightKg: number, reps: number, block = 'Main'): SetEntity => ({
  id: 1, sessionId, exerciseId: 'bench_press', itemPosition: 0, block, circuit: false, setIndex: 0,
  reps, seconds: null, weightKg, done: true, isPr: false, note: '', effort: '', doneAt: null, targetLabel: '', restSeconds: 60,
});

describe('volumeChange', () => {
  it('computes percent change excluding warm-up sets', () => {
    const prev = [mkSet(1, 50, 10, 'Warm-up'), mkSet(1, 100, 10)];
    const now = [mkSet(2, 110, 10)];
    expect(volumeChangePercent(now, prev)).toBe(10);
  });

  it('compares against the previous session of the same plan day', () => {
    const sessions: SessionEntity[] = [
      { id: 1, title: 'Push', epochDay: 100, startedAt: 1, finishedAt: 2, planDayId: 10, note: '', debrief: '' },
      { id: 2, title: 'Push', epochDay: 103, startedAt: 1, finishedAt: 2, planDayId: 10, note: '', debrief: '' },
    ];
    const sets = [mkSet(1, 100, 10), mkSet(2, 90, 10)];
    expect(volumeChangeVsLastTime(2, sessions, sets)).toBe(-10);
    expect(volumeChangeVsLastTime(1, sessions, sets)).toBeNull();
  });
});
