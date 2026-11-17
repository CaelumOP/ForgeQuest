import { describe, expect, it } from 'vitest'
import { weekStart } from '../../domain/dates'
import type { ExerciseEntity, SessionEntity, SetEntity } from '../../domain/entities'
import { epley1Rm, epleyPercentages, strengthToBodyweight, weeklyMuscleVolume } from './metrics'

describe('progress metrics', () => {
  it('computes weekly muscle volume using WP1a set-type rules', () => {
    const today = 20050
    const ws = weekStart(today)
    const bench: ExerciseEntity = {
      id: 'bench_press',
      name: 'Bench press',
      kind: 'WEIGHTED',
      primary: ['CHEST'],
      secondary: ['TRICEPS', 'SHOULDERS'],
    }
    const stretch: ExerciseEntity = {
      id: 'triceps_overhead_stretch',
      name: 'Stretch',
      kind: 'HOLD',
      primary: ['TRICEPS'],
      secondary: [],
    }
    const exercises = new Map<string, ExerciseEntity>([
      [bench.id, bench],
      [stretch.id, stretch],
    ])
    const sessions: SessionEntity[] = [
      { id: 1, planDayId: null, title: 'Push', epochDay: ws, startedAt: 1, finishedAt: 2, note: '', debrief: '' },
      { id: 2, planDayId: null, title: 'Old', epochDay: ws - 1, startedAt: 1, finishedAt: 2, note: '', debrief: '' },
      { id: 3, planDayId: null, title: 'Open', epochDay: ws, startedAt: 1, finishedAt: null, note: '', debrief: '' },
    ]
    const sets: SetEntity[] = [
      { id: 1, sessionId: 1, exerciseId: 'bench_press', itemPosition: 0, block: 'Main', circuit: false, setIndex: 0, targetLabel: '8', reps: 8, seconds: null, weightKg: 80, done: true, isPr: false, restSeconds: 60, note: '', effort: '', doneAt: 1, setType: 'WORKING' },
      { id: 2, sessionId: 1, exerciseId: 'bench_press', itemPosition: 0, block: 'Main', circuit: false, setIndex: 1, targetLabel: '8', reps: 7, seconds: null, weightKg: 80, done: true, isPr: false, restSeconds: 60, note: '', effort: '', doneAt: 1, setType: 'FAILURE' },
      { id: 3, sessionId: 1, exerciseId: 'bench_press', itemPosition: 0, block: 'Main', circuit: false, setIndex: 2, targetLabel: '10', reps: 10, seconds: null, weightKg: 40, done: true, isPr: false, restSeconds: 60, note: '', effort: '', doneAt: 1, setType: 'WARMUP' },
      { id: 4, sessionId: 1, exerciseId: 'bench_press', itemPosition: 0, block: 'Main', circuit: false, setIndex: 3, targetLabel: '10', reps: 10, seconds: null, weightKg: 50, done: true, isPr: false, restSeconds: 60, note: '', effort: '', doneAt: 1, setType: 'DROPSET' },
      { id: 5, sessionId: 1, exerciseId: 'bench_press', itemPosition: 0, block: 'Warm-up', circuit: false, setIndex: 4, targetLabel: '10', reps: 10, seconds: null, weightKg: 30, done: true, isPr: false, restSeconds: 60, note: '', effort: '', doneAt: 1, setType: 'WORKING' },
      { id: 6, sessionId: 1, exerciseId: 'triceps_overhead_stretch', itemPosition: 1, block: 'Cool-down', circuit: false, setIndex: 0, targetLabel: '30', reps: null, seconds: 30, weightKg: null, done: true, isPr: false, restSeconds: 60, note: '', effort: '', doneAt: 1, setType: 'WORKING' },
      { id: 7, sessionId: 2, exerciseId: 'bench_press', itemPosition: 0, block: 'Main', circuit: false, setIndex: 0, targetLabel: '8', reps: 8, seconds: null, weightKg: 80, done: true, isPr: false, restSeconds: 60, note: '', effort: '', doneAt: 1, setType: 'WORKING' },
      { id: 8, sessionId: 3, exerciseId: 'bench_press', itemPosition: 0, block: 'Main', circuit: false, setIndex: 0, targetLabel: '8', reps: 8, seconds: null, weightKg: 80, done: true, isPr: false, restSeconds: 60, note: '', effort: '', doneAt: 1, setType: 'WORKING' },
    ]
    const vol = weeklyMuscleVolume(sessions, sets, exercises, today)
    expect(vol.get('CHEST')).toBe(2)
    expect(vol.get('TRICEPS')).toBe(1)
    expect(vol.get('SHOULDERS')).toBe(1)
    expect(vol.get('QUADS')).toBeUndefined()
  })

  it('computes Epley 1RM, strength-to-BW, and percentage table', () => {
    expect(epley1Rm(0, 5)).toBe(0)
    expect(epley1Rm(100, 1)).toBe(100)
    expect(epley1Rm(100, 5)).toBeCloseTo(116.67, 2)
    expect(strengthToBodyweight(120, 80)).toBeCloseTo(1.5, 4)
    expect(strengthToBodyweight(120, null)).toBeNull()
    const pcts = epleyPercentages(100)
    expect(pcts[0]).toEqual({ pct: 95, value: 95 })
    expect(pcts[pcts.length - 1]).toEqual({ pct: 70, value: 70 })
  })
})
