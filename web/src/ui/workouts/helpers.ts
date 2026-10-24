import { plural, t } from '../../i18n'
import type { ExerciseRow, PlanItemRow } from '../../data/db'
import { isWebUrl } from '../../data/workouts'
import { formatWeight, type WeightUnit } from '../../domain/units'
import { profileFor, type Equipment } from '../../domain/exercise-swap-engine'
import type { ExerciseKind, Muscle } from '../../domain/muscle'

/** Weekday name for 1 = Monday .. 7 = Sunday, in the app language. */
export function dayName(dow: number, lang: string, style: 'long' | 'short' = 'long'): string {
  // 1970-01-05 was a Monday.
  return new Intl.DateTimeFormat(lang, { weekday: style, timeZone: 'UTC' }).format(new Date(Date.UTC(1970, 0, 4 + dow)))
}

/** The exercise's own tutorial link, or a YouTube search for its form. */
export function formVideoUrl(ex: { name?: string; videoUrl?: string | null } | undefined): string {
  const own = ex?.videoUrl?.trim()
  if (own && isWebUrl(own)) return own
  return 'https://www.youtube.com/results?search_query=' + encodeURIComponent(`${ex?.name ?? 'exercise'} proper form`)
}

export function openFormVideo(ex: ExerciseRow | undefined) {
  window.open(formVideoUrl(ex), '_blank', 'noopener,noreferrer')
}

export const kindLabel = (k: ExerciseKind): string => t(k === 'REPS' ? 'stepper_reps' : k === 'HOLD' ? 'kind_hold' : 'kind_weighted')
export const muscleLabel = (m: Muscle): string => t('muscle_' + m.toLowerCase())
export const musclesLabel = (ex: Pick<ExerciseRow, 'primary' | 'secondary'>): string =>
  [...new Set([...ex.primary, ...ex.secondary])].slice(0, 3).map(muscleLabel).join(t('list_separator'))

const BLOCKS: Record<string, string> = { 'Warm-up': 'block_warm_up', Core: 'block_core', Main: 'block_main', Legs: 'block_legs' }
export const blockLabel = (b: string): string => (BLOCKS[b] ? t(BLOCKS[b]) : b)

/** "3 × 10 reps", "3 × 30 s", "4 × 6–8 reps"; a circuit item drops the sets. */
export function prescription(item: PlanItemRow, kind: ExerciseKind | undefined, unit: WeightUnit, arabic: boolean): string {
  const { repsMin, repsMax, seconds } = item
  const per = kind === 'HOLD' || (seconds != null && repsMin == null)
    ? t('rx_seconds', seconds ?? 30)
    : repsMin != null && repsMax != null && repsMax !== repsMin ? t('rx_rep_range', repsMin, repsMax)
    : repsMin != null ? plural('rx_reps', repsMin, repsMin)
    : t('rx_reps_only')
  const load = kind === 'WEIGHTED' && (item.weightKg ?? 0) > 0 ? ` · ${formatWeight(unit, item.weightKg ?? 0, arabic)}` : ''
  return item.circuit ? per + load : t('rx_sets_times', item.sets, per + load)
}

/** The "Rest day" title the data layer stores in English. */
export const REST_TITLES = ['Rest day']
export const isRestTitle = (title: string): boolean => REST_TITLES.includes(title) || title === t('today_rest_day')

export type PickerFilter = { query: string; muscle: Muscle | null; equipment: ReadonlySet<Equipment>; excludeIds?: ReadonlySet<string>; focusMuscle?: Muscle }

/** Library rows for the picker: filtered, then primary-muscle matches first, then by name. */
export function filterExercises(library: readonly ExerciseRow[], f: PickerFilter): ExerciseRow[] {
  const q = f.query.trim().toLowerCase()
  return library
    .filter((ex) =>
      f.equipment.has(profileFor(ex).equipment) &&
      (!q || ex.name.toLowerCase().includes(q)) &&
      (f.muscle == null || ex.primary.includes(f.muscle) || ex.secondary.includes(f.muscle)) &&
      !f.excludeIds?.has(ex.id))
    .sort((a, b) =>
      Number(f.muscle != null && !a.primary.includes(f.muscle)) - Number(f.muscle != null && !b.primary.includes(f.muscle)) ||
      Number(f.focusMuscle != null && !a.primary.includes(f.focusMuscle)) - Number(f.focusMuscle != null && !b.primary.includes(f.focusMuscle)) ||
      a.name.localeCompare(b.name))
}
