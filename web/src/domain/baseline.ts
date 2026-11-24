// Port of Android Baseline.kt. History maps retain newest-workout-first insertion order.
import type { ExerciseEntity, SetEntity } from './entities'
import { effortOf, effortRir } from './effort'
import { profileFor, type Equipment, type MovementPattern } from './exercise-swap-engine'
import { countsForProgression } from './set-types'
import { fromKg, loadStep, toKg, type WeightUnit } from './units'

export type BaselineStatus = 'CALIBRATED' | 'ESTIMATED' | 'NONE'
export type PatternBucket = 'PUSH' | 'PULL' | 'SQUAT' | 'HINGE' | 'CORE'
export type BaselineEstimate = { weightKg: number | null; status: BaselineStatus; donorId?: string }
type BaselineSet = Omit<SetEntity, 'id'>
const f = Math.fround

const LOAD_IMPLEMENT_PRIORITY: readonly Equipment[] = [
  'BARBELL', 'SMITH_MACHINE', 'CHEST_PRESS_MACHINE', 'SHOULDER_PRESS_MACHINE', 'PEC_DECK',
  'PREACHER_CURL_MACHINE', 'MACHINE', 'LEG_PRESS', 'HACK_SQUAT', 'LEG_EXTENSION', 'LEG_CURL',
  'CALF_MACHINE', 'CABLE_MACHINE', 'LAT_PULLDOWN', 'SEATED_ROW_MACHINE', 'DUMBBELLS', 'KETTLEBELL',
]

export function bucketOf(pattern: MovementPattern): PatternBucket | null {
  switch (pattern) {
    case 'HORIZONTAL_PUSH': case 'DIP_PUSH': case 'VERTICAL_PUSH': return 'PUSH'
    case 'VERTICAL_PULL': case 'HORIZONTAL_PULL': case 'UPPER_BACK_PULL': return 'PULL'
    case 'SQUAT_BILATERAL': case 'SQUAT_UNILATERAL': return 'SQUAT'
    case 'HIP_HINGE': return 'HINGE'
    case 'CORE_ANTI_EXTENSION': case 'CORE_FLEXION_ROTATION': return 'CORE'
    default: return null
  }
}

export function e1rmOf(set: BaselineSet): number {
  const weight = set.weightKg ?? 0
  const reps = set.reps ?? 0
  if (!Number.isFinite(weight) || weight <= 0 || reps < 1 || reps > 12) return 0
  const effort = !set.effort.trim() && set.setType === 'FAILURE' ? 'FAILURE' : effortOf(set.effort, set.note)
  if (reps === 1 && effort === 'FAILURE') return weight
  return f(f(weight) * f(1 + f(Math.min(reps + effortRir(effort), 12) / 30)))
}

/** Maximum across the last two eligible sessions, in the caller's workout-date order. */
export function calibratedE1rm(sets: readonly BaselineSet[]): number | null {
  const eligible = sets.filter((s) => countsForProgression(s) && s.done && (s.weightKg ?? 0) > 0)
  const sessions = new Set([...new Set(eligible.map((s) => s.sessionId))].slice(0, 2))
  const values = eligible.filter((s) => sessions.has(s.sessionId)).map(e1rmOf).filter((v) => v > 0)
  return values.length ? Math.max(...values) : null
}

function ratioForEquipment(eq: Equipment): number | null {
  switch (eq) {
    case 'BARBELL': return 1
    case 'SMITH_MACHINE': return f(.9)
    case 'DUMBBELLS': case 'KETTLEBELL': return f(.4)
    case 'CABLE_MACHINE': case 'LAT_PULLDOWN': case 'SEATED_ROW_MACHINE': return f(.6)
    case 'CHEST_PRESS_MACHINE': case 'SHOULDER_PRESS_MACHINE': case 'PEC_DECK':
    case 'PREACHER_CURL_MACHINE': case 'MACHINE': case 'LEG_PRESS': case 'HACK_SQUAT':
    case 'LEG_EXTENSION': case 'LEG_CURL': case 'CALF_MACHINE': return 1
    default: return null
  }
}

export function equipmentRatio(ex: ExerciseEntity): number | null {
  const profile = profileFor(ex)
  const direct = ratioForEquipment(profile.equipment)
  if (direct != null) return direct
  const primary = LOAD_IMPLEMENT_PRIORITY.find((eq) => profile.requires?.includes(eq))
  return primary ? ratioForEquipment(primary) : null
}

export function coldStart(
  ex: ExerciseEntity, repsMin: number, unit: WeightUnit,
  historyByExercise: ReadonlyMap<string, readonly BaselineSet[]>, library: ReadonlyMap<string, ExerciseEntity>,
): BaselineEstimate {
  if (historyByExercise.get(ex.id)?.some((s) => countsForProgression(s) && s.done))
    return { weightKg: null, status: 'CALIBRATED' }
  const none: BaselineEstimate = { weightKg: null, status: 'NONE' }
  if (ex.kind !== 'WEIGHTED') return none
  const ratio = equipmentRatio(ex)
  if (ratio == null) return none
  const pattern = profileFor(ex).pattern
  const bucket = bucketOf(pattern)
  let best: { id: string; e1rm: number; ratio: number; samePattern: boolean } | null = null
  for (const [id, sets] of historyByExercise) {
    if (id === ex.id) continue
    const donor = library.get(id)
    if (!donor || donor.kind !== 'WEIGHTED') continue
    const donorRatio = equipmentRatio(donor)
    if (donorRatio == null) continue
    const donorPattern = profileFor(donor).pattern
    const samePattern = donorPattern === pattern
    if (!samePattern && (bucket == null || bucketOf(donorPattern) !== bucket)) continue
    const e1rm = calibratedE1rm(sets)
    if (e1rm == null) continue
    // First in each preference class is the newest workout, regardless of session id.
    if (!best || (samePattern && !best.samePattern)) best = { id, e1rm, ratio: donorRatio, samePattern }
  }
  if (!best) return none
  const discount = best.samePattern ? f(.9) : f(f(.9) * f(.8))
  const targetE1rm = f(f(f(best.e1rm / best.ratio) * ratio) * discount)
  const rawKg = f(targetE1rm / f(1 + f((Math.max(1, repsMin) + 3) / 30)))
  const step = loadStep(unit)
  const display = f(Math.floor(f(fromKg(unit, rawKg) / step) + 1e-4) * step)
  if (display < step - 1e-4) return none
  return { weightKg: toKg(unit, display), status: 'ESTIMATED', donorId: best.id }
}
