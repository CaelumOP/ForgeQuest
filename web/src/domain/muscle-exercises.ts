// "Tap a muscle, see its exercises" (docs/plans/muscle-exercises-plan.md). Android: MuscleExercises.kt.
import type { ExerciseEntity } from './entities'
import { EQUIPMENT, profileFor, type Equipment } from './exercise-swap-engine'
import { tierOf, TIERS, type Tier } from './exercise-tiers'
import type { Muscle } from './muscle'

/** One row: `main` when the muscle is a primary one, `tier` null for custom exercises. */
export type MuscleExercise = { exercise: ExerciseEntity; main: boolean; tier: Tier | null }
/** The plan's exercises first, then the rest; `hidden` = how many the equipment filter left out. */
export type MuscleExerciseList = { inPlan: MuscleExercise[]; others: MuscleExercise[]; hidden: number }

/** Muscles with one built-in exercise show with a bigger neighbour, so no list opens nearly empty. */
export const COMPANIONS: Partial<Record<Muscle, Muscle[]>> = {
  OBLIQUES: ['ABS'],
  ADDUCTORS: ['QUADS', 'GLUTES'],
  TIBIALIS: ['CALVES'],
}

const byName = (a: string, b: string) => (a < b ? -1 : a > b ? 1 : 0)

/**
 * Exercises that work `muscle` (or its companions) as a primary or secondary muscle. Plan exercises come first; the
 * rest are main-muscle first, then Foundation → Advanced, then by name. Unless `allEquipment`, the rest only include
 * exercises the person's `equipment` allows (empty = not set, so everything).
 */
export function muscleExercises(
  muscle: Muscle, library: ExerciseEntity[], planExerciseIds: Set<string>, equipment: Set<Equipment>, allEquipment = false,
): MuscleExerciseList {
  const targets = new Set<Muscle>([muscle, ...(COMPANIONS[muscle] ?? [])])
  const allowed = new Set<Equipment>(equipment.size ? [...equipment, 'BODYWEIGHT'] : EQUIPMENT)
  const tierRank = (t: Tier | null) => (t ? TIERS.indexOf(t) : TIERS.length)
  const rows = library
    .filter((ex) => ex.primary.some((m) => targets.has(m)) || ex.secondary.some((m) => targets.has(m)))
    .map((ex): MuscleExercise => ({ exercise: ex, main: ex.primary.some((m) => targets.has(m)), tier: tierOf(ex) }))
    .sort((a, b) => Number(!a.main) - Number(!b.main) || tierRank(a.tier) - tierRank(b.tier) || byName(a.exercise.name, b.exercise.name))
  const inPlan = rows.filter((r) => planExerciseIds.has(r.exercise.id))
  const rest = rows.filter((r) => !planExerciseIds.has(r.exercise.id))
  const shown = allEquipment ? rest : rest.filter((r) => allowed.has(profileFor(r.exercise).equipment))
  return { inPlan, others: shown, hidden: rest.length - shown.length }
}

export const listSize = (l: MuscleExerciseList) => l.inPlan.length + l.others.length

/**
 * The plan exercise `ex` could replace: the only one in the plan with the same movement pattern. Null when `ex` is
 * already in the plan or there are none or several (then the plan editor is the place to choose).
 */
export function planSwapTarget(ex: ExerciseEntity, planExercises: ExerciseEntity[]): ExerciseEntity | null {
  if (planExercises.some((e) => e.id === ex.id)) return null
  const pattern = profileFor(ex).pattern
  const matches = [...new Map(planExercises.map((e) => [e.id, e])).values()].filter((e) => profileFor(e).pattern === pattern)
  return matches.length === 1 ? matches[0] : null
}
