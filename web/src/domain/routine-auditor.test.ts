// All 12 Android RoutineAuditorTest cases.
import { describe, expect, it } from 'vitest'
import type { ExerciseEntity } from './entities'
import type { MovementPattern } from './exercise-swap-engine'
import type { Muscle } from './muscle'
import { audit, type AuditDay, type AuditItem } from './routine-auditor'

const ex = (id: string, primary: Muscle[], secondary: Muscle[], pattern: MovementPattern): ExerciseEntity =>
  ({ id, name: id, kind: 'WEIGHTED', primary, secondary, pattern })
const bench = ex('test_bench', ['CHEST'], ['SHOULDERS', 'TRICEPS'], 'HORIZONTAL_PUSH')
const ohp = ex('test_ohp', ['SHOULDERS'], ['TRICEPS'], 'VERTICAL_PUSH')
const latRaise = ex('test_lat_raise', ['SHOULDERS'], [], 'LATERAL_RAISE')
const row = ex('test_row', ['UPPER_BACK'], ['BICEPS'], 'HORIZONTAL_PULL')
const pulldown = ex('test_pulldown', ['UPPER_BACK'], ['BICEPS'], 'VERTICAL_PULL')
const curl = ex('test_curl', ['BICEPS'], [], 'ELBOW_FLEXION')
const squat = ex('test_squat', ['QUADS', 'GLUTES'], [], 'SQUAT_BILATERAL')
const rdl = ex('test_rdl', ['HAMSTRINGS', 'GLUTES'], [], 'HIP_HINGE')
const calf = ex('test_calf', ['CALVES'], [], 'ANKLE_FLEXION')
const exercises = new Map([bench, ohp, latRaise, row, pulldown, curl, squat, rdl, calf].map((e) => [e.id, e]))
const item = (e: ExerciseEntity, sets: number, position = 0, block = 'Main'): AuditItem =>
  ({ exerciseId: e.id, sets, position, block })
const day = (dayOfWeek: number, items: AuditItem[]): AuditDay => ({ dayOfWeek, items })

describe('RoutineAuditorTest', () => {
  it('belowMevForMajorMuscleOnly', () => {
    const days = (chest: number) => [day(1, [item(bench, chest), item(row, 10), item(ohp, 8), item(squat, 8), item(rdl, 6)])]
    expect(audit(days(8), exercises).filter((f) => f.kind === 'BELOW_MEV')).toEqual([])
    expect(audit(days(4), exercises).filter((f) => f.kind === 'BELOW_MEV').map((f) => f.muscle)).toEqual(['CHEST'])
  })
  it('secondaryCountsHalf', () => {
    const findings = audit([
      day(1, [item(bench, 8), item(latRaise, 4)]), day(3, [item(row, 10)]), day(5, [item(squat, 8), item(rdl, 6)]),
    ], exercises)
    expect(findings.some((f) => f.kind === 'BELOW_MEV' && f.muscle === 'SHOULDERS')).toBe(false)
  })
  it('aboveMrvFlagged', () => {
    const findings = audit([day(1, [item(bench, 8)]), day(3, [item(bench, 8)]), day(5, [item(bench, 7)])], exercises)
    expect(findings.filter((f) => f.kind === 'ABOVE_MRV')).toEqual([{ kind: 'ABOVE_MRV', muscle: 'CHEST', value: 23, limit: 22 }])
  })
  it('pushPullImbalanceCountsExerciseOnce', () => {
    const findings = audit([day(1, [item(bench, 6), item(row, 4)])], exercises)
    expect(findings.filter((f) => f.kind === 'PUSH_PULL')).toEqual([{ kind: 'PUSH_PULL', value: 6, limit: 4 }])
  })
  it('balancedPushPullIsQuiet', () => {
    expect(audit([day(1, [item(bench, 6), item(row, 5)])], exercises).some((f) => f.kind === 'PUSH_PULL')).toBe(false)
  })
  it('isolationBeforeSameMuscleCompound', () => {
    const iso = audit([day(2, [item(latRaise, 3, 0), item(ohp, 4, 1)])], exercises).filter((f) => f.kind === 'ISOLATION_FIRST')
    expect(iso).toHaveLength(1)
    expect(iso[0]).toMatchObject({ muscle: 'SHOULDERS', dayIndex: 0 })
  })
  it('junkVolumeOverTenInOneDay', () => {
    const junk = audit([day(1, [item(bench, 11)])], exercises).filter((f) => f.kind === 'JUNK_VOLUME')
    expect(junk).toHaveLength(1)
    expect(junk[0]).toMatchObject({ muscle: 'CHEST', value: 11, limit: 10 })
  })
  it('recoveryOverlapOnConsecutiveWeekdays', () => {
    const overlap = audit([day(1, [item(bench, 6)]), day(2, [item(bench, 6)])], exercises, 'WEEKDAY')
      .filter((f) => f.kind === 'RECOVERY_OVERLAP')
    expect(overlap).toHaveLength(1)
    expect(overlap[0]).toMatchObject({ muscle: 'CHEST', dayIndex: 1 })
  })
  it('sundayMondayWrapCounts', () => {
    const overlap = audit([day(7, [item(row, 6)]), day(1, [item(pulldown, 6)])], exercises, 'WEEKDAY')
      .filter((f) => f.kind === 'RECOVERY_OVERLAP')
    expect(overlap).toHaveLength(1)
    expect(overlap[0]).toMatchObject({ muscle: 'UPPER_BACK', dayIndex: 1 })
  })
  it('rotationPlanSkipsOverlap', () => {
    expect(audit([day(7, [item(bench, 6)]), day(1, [item(bench, 6)])], exercises, 'ROTATION')
      .some((f) => f.kind === 'RECOVERY_OVERLAP')).toBe(false)
  })
  it('warmupAndCooldownIgnored', () => {
    const findings = audit([day(1, [
      item(latRaise, 12, 0, 'Warm-up'), item(bench, 12, 1, 'Cool-down'), item(ohp, 8, 2),
      item(row, 10, 3), item(squat, 8, 4), item(rdl, 6, 5),
    ])], exercises)
    expect(findings.some((f) => f.kind === 'ISOLATION_FIRST')).toBe(false)
    expect(findings.some((f) => f.kind === 'JUNK_VOLUME' && ['SHOULDERS', 'CHEST'].includes(f.muscle!))).toBe(false)
    expect(findings.some((f) => f.kind === 'ABOVE_MRV')).toBe(false)
    expect(findings.filter((f) => f.kind === 'BELOW_MEV').map((f) => f.muscle)).toEqual(['CHEST'])
  })
  it('emptyPlanGivesNoCrash', () => {
    expect(audit([], exercises)).toEqual([])
    expect(audit([day(1, [])], exercises)).toEqual([])
  })
})
