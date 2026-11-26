// Pure helpers and formatting for BaselineScreen ("Find your starting point").
import { t, plural, type Lang } from '../../i18n'
import type { BaselineTestRow, ExerciseRow } from '../../data/db'
import { configuredEquipment, weightUnit, type Settings } from '../../data/settings'
import type { ActivePlan } from '../../data/workouts'
import { repsTarget, holdTarget, weightedTargetInUnit, easierVariation } from '../../domain/baseline-targets'
import { expandLegacyEquipment, profileFits, profileFor, isEquipment, type Equipment } from '../../domain/exercise-swap-engine'
import type { ExerciseKind } from '../../domain/muscle'
import { isCompoundExercise } from '../../domain/plan-builder'
import { isExtraBlock } from '../../domain/pr-picker'
import { formatWeight, type WeightUnit } from '../../domain/units'

export const PLANK_ID = 'plank'
const BASELINE_PREFERENCES = [
  ['bench_press', 'db_bench_press', 'push_up'],
  ['pull_up', 'lat_pulldown', 'barbell_row', 'db_row', 'australian_pull_up'],
  ['back_squat', 'goblet_squat', 'squat'],
  ['romanian_deadlift', 'db_romanian_deadlift', 'hip_bridge'],
  ['plank'],
]

export function baselineCallerEquipment(param?: string): Set<Equipment> | undefined {
  if (!param?.startsWith('equipment=')) return undefined
  return new Set(param.slice('equipment='.length).split(',').filter(isEquipment))
}
export const DEFAULT_WEIGHTED_REPS_MIN = 8
export const BASELINE_HOLD_BENCHMARK_SECONDS = 60
export const BASELINE_REST_SECONDS = 90
export const BASELINE_REST_KEY = 'fq.baseline.restEndsAt'
export const BASELINE_DISMISSED_KEY = 'baseline_card_dismissed'

export type BaselineTargetDisplay =
  | { kind: 'REPS'; reps: number }
  | { kind: 'HOLD'; seconds: number }
  | { kind: 'WEIGHT'; weightKg: number; unit: WeightUnit }
  | { kind: 'EASIER_VARIATION'; easierExercise: ExerciseRow | null }
  | { kind: 'TOO_LIGHT' }
  | { kind: 'HOLD_TOO_SHORT' }
  | { kind: 'WEIGHT_NO_TARGET' }

export type BaselineMoveItem = {
  exercise: ExerciseRow
  latest: BaselineTestRow | null
  target: BaselineTargetDisplay | null
  unit: WeightUnit
  repsMin: number
  isSuggested: boolean
}

export type BaselineSaveResult = {
  exercise: ExerciseRow
  test: BaselineTestRow
  target: BaselineTargetDisplay
  unit: WeightUnit
}

export function shouldShowBaselineEntry(
  hasFinishedWorkout: boolean,
  hasBaseline: boolean,
  dismissed: boolean,
): boolean {
  return !hasFinishedWorkout && !hasBaseline && !dismissed
}

export function computeTargetDisplay(
  exercise: ExerciseRow,
  test: Pick<BaselineTestRow, 'reps' | 'seconds' | 'weightKg'>,
  library: readonly ExerciseRow[],
  equipment: ReadonlySet<Equipment>,
  unit: WeightUnit,
  repsMin: number = DEFAULT_WEIGHTED_REPS_MIN,
): BaselineTargetDisplay {
  switch (exercise.kind) {
    case 'REPS': {
      const target = repsTarget(test.reps ?? 0)
      if (target != null) return { kind: 'REPS', reps: target }
      const easier = easierVariation(exercise, library, equipment) as ExerciseRow | null
      return { kind: 'EASIER_VARIATION', easierExercise: easier }
    }
    case 'HOLD': {
      const target = holdTarget(test.seconds ?? 0)
      return target != null ? { kind: 'HOLD', seconds: target } : { kind: 'HOLD_TOO_SHORT' }
    }
    case 'WEIGHTED': {
      const wKg = test.weightKg ?? 0
      const r = test.reps ?? 0
      const res = weightedTargetInUnit(wKg, r, repsMin, unit)
      if (res.kind === 'TARGET') return { kind: 'WEIGHT', weightKg: res.kg, unit }
      if (res.kind === 'TOO_LIGHT') return { kind: 'TOO_LIGHT' }
      return { kind: 'WEIGHT_NO_TARGET' }
    }
  }
}

export function buildBaselineMoves(
  library: readonly ExerciseRow[],
  settings: Settings,
  activePlan: ActivePlan | null,
  latestById: ReadonlyMap<string, BaselineTestRow>,
  callerEquipment?: ReadonlySet<Equipment>,
): BaselineMoveItem[] {
  if (library.length === 0) return []
  const byId = new Map(library.map((e) => [e.id, e]))
  const equipment = expandLegacyEquipment(callerEquipment ?? configuredEquipment(settings))
  equipment.add('BODYWEIGHT')
  const fits = (ex: ExerciseRow) => profileFits(profileFor(ex), equipment)
  const planRepsMin = new Map<string, number>()
  const planWeightedMainIds: string[] = []

  if (activePlan) {
    const orderedItems = [...activePlan.days]
      .sort((a, b) => a.day.dayOfWeek - b.day.dayOfWeek)
      .flatMap((d) => [...d.items].sort((a, b) => a.position - b.position))
    for (const item of orderedItems) {
      if (!planRepsMin.has(item.exerciseId) && item.repsMin != null) {
        planRepsMin.set(item.exerciseId, item.repsMin)
      }
      if (
        !isExtraBlock(item.block) &&
        item.block.toLowerCase() !== 'finisher'
      ) {
        const ex = byId.get(item.exerciseId)
        if (ex && ex.kind === 'WEIGHTED' && [...equipment].some((eq) => eq !== 'BODYWEIGHT') &&
            fits(ex) && isCompoundExercise(ex) &&
            ex.id !== 'around_the_worlds' && !planWeightedMainIds.includes(item.exerciseId)) {
          planWeightedMainIds.push(item.exerciseId)
        }
      }
    }
  }

  const suggestedIds = BASELINE_PREFERENCES.flatMap((ids) => {
    const candidates = ids.flatMap((id) => { const ex = byId.get(id); return ex ? [ex] : [] })
    const patterns = new Set(candidates.map((ex) => profileFor(ex).pattern))
    // Around-the-worlds is catalogued as a push but is a single-joint isolation.
    const planId = planWeightedMainIds.find((id) => patterns.has(profileFor(byId.get(id)!).pattern))
    const id = planId ?? candidates.find(fits)?.id
    return id ? [id] : []
  })

  const suggestedSet = new Set(suggestedIds)
  const extraTestedIds = [...latestById.values()]
    .filter((row) => !suggestedSet.has(row.exerciseId) && byId.has(row.exerciseId))
    .filter((row) => fits(byId.get(row.exerciseId)!))
    .sort((a, b) => b.createdAt - a.createdAt)
    .map((row) => row.exerciseId)

  const toItem = (id: string, isSuggested: boolean): BaselineMoveItem | null => {
    const ex = byId.get(id)
    if (!ex) return null
    const unit = weightUnit(settings, ex.unitOverride)
    const repsMin = planRepsMin.get(id) ?? DEFAULT_WEIGHTED_REPS_MIN
    const rawLatest = latestById.get(id) ?? null
    const latest = rawLatest && rawLatest.kind === ex.kind ? rawLatest : null
    const target = latest ? computeTargetDisplay(ex, latest, library, equipment, unit, repsMin) : null
    return { exercise: ex, latest, target, unit, repsMin, isSuggested }
  }

  const out: BaselineMoveItem[] = []
  for (const id of suggestedIds) {
    const item = toItem(id, true)
    if (item) out.push(item)
  }
  for (const id of extraTestedIds) {
    const item = toItem(id, false)
    if (item) out.push(item)
  }
  return out
}

export function formatLastResult(
  test: Pick<BaselineTestRow, 'reps' | 'seconds' | 'weightKg'> | null,
  kind: ExerciseKind,
  unit: WeightUnit,
  lang: Lang,
): string | null {
  if (!test) return null
  const arabic = lang === 'ar'
  switch (kind) {
    case 'REPS': {
      if (test.reps == null) return null
      return plural('baseline_last_reps', test.reps, test.reps)
    }
    case 'HOLD': {
      if (test.seconds == null) return null
      return t('baseline_last_seconds', test.seconds)
    }
    case 'WEIGHTED': {
      if (test.weightKg == null || test.reps == null) return null
      return t('baseline_last_weight', formatWeight(unit, test.weightKg, arabic), test.reps)
    }
  }
}

export function formatTargetText(target: BaselineTargetDisplay | null, lang: Lang): string | null {
  if (!target) return null
  const arabic = lang === 'ar'
  switch (target.kind) {
    case 'REPS': return plural('baseline_start_reps', target.reps, target.reps)
    case 'HOLD': return t('baseline_start_seconds', target.seconds)
    case 'WEIGHT': return t('baseline_start_weight', formatWeight(target.unit, target.weightKg, arabic))
    case 'EASIER_VARIATION':
      return target.easierExercise
        ? t('baseline_try_easier', target.easierExercise.name)
        : t('baseline_try_easier_generic')
    case 'TOO_LIGHT':
    case 'WEIGHT_NO_TARGET':
      return t('baseline_too_light')
    case 'HOLD_TOO_SHORT':
      return t('baseline_hold_zero_hint')
  }
}

export type RestStore = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>

export function saveBaselineRest(store: RestStore | null, endsAt: number | null): void {
  try {
    if (!store) return
    if (endsAt == null) store.removeItem(BASELINE_REST_KEY)
    else store.setItem(BASELINE_REST_KEY, String(endsAt))
  } catch { /* storage blocked */ }
}

export function loadBaselineRest(store: RestStore | null, now: number): number | null {
  try {
    const raw = store?.getItem(BASELINE_REST_KEY)
    if (!raw) return null
    const endsAt = Number(raw)
    if (!Number.isFinite(endsAt) || endsAt <= now) {
      store?.removeItem(BASELINE_REST_KEY)
      return null
    }
    return endsAt
  } catch {
    return null
  }
}
