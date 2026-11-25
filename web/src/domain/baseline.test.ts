import { describe, expect, it } from 'vitest'
import { EXERCISE_BY_ID as library } from '../data/seed/exercise-catalog'
import type { ExerciseEntity, SetEntity } from './entities'
import { bucketOf, calibratedE1rm, coldStart, e1rmOf, equipmentRatio, transfer } from './baseline'
import { fromKg } from './units'

const ex = (id: string) => library.get(id)!
const set = (overrides: Partial<SetEntity> = {}): SetEntity => ({
  id: 1, sessionId: 10, exerciseId: 'bench_press', itemPosition: 0, block: 'Main', circuit: false,
  setIndex: 0, targetLabel: '8-12', reps: 8, seconds: null, weightKg: 100, done: true,
  isPr: false, restSeconds: 90, note: '', effort: '', setType: 'WORKING', ...overrides,
})
const donor = (id = 'bench_press', weightKg = 100, sessionId = 10) => set({ exerciseId: id, weightKg, sessionId, reps: 1, effort: 'FAILURE' })
const history = (...sets: SetEntity[]) => {
  const result = new Map<string, SetEntity[]>()
  for (const s of sets) result.set(s.exerciseId, [...(result.get(s.exerciseId) ?? []), s])
  return result
}
const estimate = (target: string, sets: SetEntity[] = [donor()], reps = 8) => coldStart(ex(target), reps, 'KG', history(...sets), library)

describe('BaselineTest (Android parity)', () => {
  it('e1rmCapsAtTwelveRepsAndIgnoresHigher', () => {
    expect(e1rmOf(set({ reps: 10, effort: 'EASY' }))).toBeCloseTo(140)
    expect(e1rmOf(set({ reps: 12, effort: 'OK' }))).toBeCloseTo(140)
    for (const reps of [13, 15, 0]) expect(e1rmOf(set({ reps }))).toBe(0)
  })
  it('e1rmUsesRirFromEffort', () => {
    for (const [effort, expected] of [['EASY', 130], ['OK', 126.6667], ['HARD', 123.3333], ['FAILURE', 120]] as const)
      expect(e1rmOf(set({ reps: 6, effort }))).toBeCloseTo(expected, 2)
  })
  it('blankEffortCountsTwoInReserve', () => {
    expect(e1rmOf(set({ reps: 6 }))).toBe(e1rmOf(set({ reps: 6, effort: 'OK' })))
    expect(calibratedE1rm([set({ reps: 6 })])).toBeCloseTo(126.6667, 2)
  })
  it('singleToFailureIsTheWeight', () => {
    expect(e1rmOf(set({ weightKg: 120, reps: 1, effort: 'FAILURE' }))).toBe(120)
    expect(e1rmOf(set({ weightKg: 120, reps: 1, effort: 'OK' }))).toBeCloseTo(132)
  })
  it('zeroOrNegativeWeightGivesZero', () => {
    for (const weightKg of [0, -10, NaN, Infinity, null]) expect(e1rmOf(set({ weightKg }))).toBe(0)
  })
  it('exerciseE1rmIgnoresWarmupsAndUsesLastTwoSessions', () => {
    expect(calibratedE1rm([
      set({ sessionId: 30, weightKg: 150, setType: 'WARMUP' }),
      set({ sessionId: 30, weightKg: 200, reps: 5, done: false }),
      set({ sessionId: 30, weightKg: 80, reps: 5, effort: 'OK' }),
      set({ sessionId: 20, weightKg: 90, reps: 5, effort: 'HARD' }),
      set({ sessionId: 10, weightKg: 200, reps: 5, effort: 'FAILURE' }),
    ])).toBeCloseTo(108)
    expect(calibratedE1rm([])).toBeNull()
  })
  it('coldStartFromSamePatternBarbellToDumbbell', () => {
    expect(estimate('db_bench_press')).toEqual({ status: 'ESTIMATED', weightKg: 25, donorId: 'bench_press' })
  })
  it('coldStartPressToOverheadUsesSmallerTransfer', () => {
    // Horizontal -> vertical push transfers 0.9 x 0.6 (audit S6): 100 * 0.54 = 54 -> 54 / (1 + 11/30) = 39.5 -> 37.5
    expect(estimate('overhead_press')).toEqual({ status: 'ESTIMATED', weightKg: 37.5, donorId: 'bench_press' })
  })
  it('transferRatesAndFlyRejection', () => {
    expect(transfer('HORIZONTAL_PUSH', 'HORIZONTAL_PUSH')).toBeCloseTo(0.9, 5)
    expect(transfer('HORIZONTAL_PUSH', 'VERTICAL_PUSH')).toBeCloseTo(0.54, 5)
    expect(transfer('SQUAT_BILATERAL', 'SQUAT_UNILATERAL')).toBeCloseTo(0.54, 5)
    expect(transfer('VERTICAL_PULL', 'HORIZONTAL_PULL')).toBeCloseTo(0.72, 5)
    // Flyes never donate or receive (S6, Gate 4).
    expect(estimate('db_bench_press', [donor('db_fly', 20)])).toEqual({ status: 'NONE', weightKg: null })
    expect(estimate('db_fly', [donor('bench_press', 100)])).toEqual({ status: 'NONE', weightKg: null })
    // Two-leg squat -> one-leg squat uses 0.9 x 0.6 transfer.
    expect(estimate('db_split_squat', [donor('back_squat', 100)])).toEqual({ status: 'ESTIMATED', weightKg: 15, donorId: 'back_squat' })
  })
  it('backDatedWorkoutDoesNotWinRecency', () => {
    expect(estimate('db_bench_press', [donor(), donor('chest_press_machine', 200, 99)]).donorId).toBe('bench_press')
  })
  it('donorsOnlyInOtherPatternsGiveNoEstimate', () => {
    expect(estimate('db_bench_press', [donor('back_squat', 140)])).toEqual({ status: 'NONE', weightKg: null })
  })
  it('newBodyweightExerciseNeverGetsAGuessedLoad', () => {
    expect(estimate('push_up')).toEqual({ status: 'NONE', weightKg: null })
    expect(estimate('plank')).toEqual({ status: 'NONE', weightKg: null })
  })
  it('coldStartWithNoDonorIsBlank', () => {
    for (const sets of [[], [donor('back_squat', 140)], [donor('bench_press', 0)]])
      expect(estimate('db_bench_press', sets)).toEqual({ status: 'NONE', weightKg: null })
  })
  it('coldStartBelowOneLoadStepIsBlank', () => {
    expect(estimate('bicep_curl', [donor('barbell_curl', 5)], 10)).toEqual({ status: 'NONE', weightKg: null })
  })
  it('coldStartInPoundsFloorsToFivePounds', () => {
    const result = coldStart(ex('incline_bench_press'), 8, 'LB', history(donor()), library)
    expect(result.status).toBe('ESTIMATED')
    expect(fromKg('LB', result.weightKg!)).toBeCloseTo(145)
  })
  it('calibratedExerciseReturnsCalibratedStatus', () => {
    expect(estimate('bench_press', [set({ weightKg: 80 }), donor('incline_bench_press', 90, 11)]))
      .toEqual({ status: 'CALIBRATED', weightKg: null })
  })
  it('bucketMapping', () => {
    for (const pattern of ['HORIZONTAL_PUSH', 'DIP_PUSH', 'VERTICAL_PUSH'] as const) expect(bucketOf(pattern)).toBe('PUSH')
    for (const pattern of ['VERTICAL_PULL', 'HORIZONTAL_PULL', 'UPPER_BACK_PULL'] as const) expect(bucketOf(pattern)).toBe('PULL')
    for (const pattern of ['SQUAT_BILATERAL', 'SQUAT_UNILATERAL'] as const) expect(bucketOf(pattern)).toBe('SQUAT')
    expect(bucketOf('HIP_HINGE')).toBe('HINGE')
    for (const pattern of ['CORE_ANTI_EXTENSION', 'CORE_FLEXION_ROTATION'] as const) expect(bucketOf(pattern)).toBe('CORE')
    for (const pattern of ['MOBILITY', 'LATERAL_RAISE', 'ELBOW_FLEXION', 'ELBOW_EXTENSION'] as const) expect(bucketOf(pattern)).toBeNull()
  })
  it('equipmentRatioCoversRealEquipmentAndMultiEquipmentCustom', () => {
    for (const [id, ratio] of [['bench_press', 1], ['smith_squat', .9], ['db_bench_press', .4], ['kb_swing', .4], ['cable_fly', .6], ['lat_pulldown', .6], ['seated_cable_row', .6], ['leg_press', 1]] as const)
      expect(equipmentRatio(ex(id))).toBeCloseTo(ratio)
    for (const id of ['push_up', 'band_row']) expect(equipmentRatio(ex(id))).toBeNull()
    const custom: ExerciseEntity = { id: 'custom_bench_bar', name: 'Custom Floor Bench', kind: 'WEIGHTED', primary: ['CHEST'], secondary: [], custom: true, equipment: 'FLAT_BENCH,BARBELL', pattern: 'HORIZONTAL_PUSH' }
    expect(equipmentRatio(custom)).toBe(1)
  })
  it('blank failure effort overrides legacy notes and excluded sets do not calibrate', () => {
    expect(e1rmOf(set({ reps: 1, setType: 'FAILURE', note: 'easy' }))).toBe(100)
    expect(calibratedE1rm([set({ block: 'Warm-up' }), set({ setType: 'DROPSET' }), set({ setType: 'RESTPAUSE' })])).toBeNull()
  })
  it('same pattern wins over a newer donor from the same bucket', () => {
    expect(estimate('db_bench_press', [donor('overhead_press'), donor()]).donorId).toBe('bench_press')
  })
})
