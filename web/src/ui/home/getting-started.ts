import { hasAnyMeal } from '../../data/food'
import { hasAnyWeight } from '../../data/progress'
import { hasFinishedSession } from '../../data/workouts'

export const GS_ELIGIBLE_KEY = 'fq_gs_eligible'
export const GS_DISMISSED_KEY = 'fq_gs_dismissed'

export type GsStore = Pick<Storage, 'getItem' | 'setItem'>

const defaultStore = (): GsStore | null => {
  try { return typeof localStorage !== 'undefined' ? localStorage : null } catch { return null }
}

function getFlag(key: string, store: GsStore | null = defaultStore()): boolean {
  try { return store?.getItem(key) === '1' } catch { return false }
}

function setFlag(key: string, store: GsStore | null = defaultStore()): void {
  try { store?.setItem(key, '1') } catch { /* storage blocked: ignore */ }
}

export const isGettingStartedEligible = (store?: GsStore | null): boolean => getFlag(GS_ELIGIBLE_KEY, store)
export const markGettingStartedEligible = (store?: GsStore | null): void => setFlag(GS_ELIGIBLE_KEY, store)

export const isGettingStartedDismissed = (store?: GsStore | null): boolean => getFlag(GS_DISMISSED_KEY, store)
export const markGettingStartedDismissed = (store?: GsStore | null): void => setFlag(GS_DISMISSED_KEY, store)

export const shouldShowGettingStarted = (store?: GsStore | null): boolean =>
  isGettingStartedEligible(store) && !isGettingStartedDismissed(store)

export type GettingStartedCounts = {
  workout: boolean
  food: boolean
  weight: boolean
}

export async function queryGettingStartedDone(): Promise<GettingStartedCounts> {
  const [workout, food, weight] = await Promise.all([
    hasFinishedSession(),
    hasAnyMeal(),
    hasAnyWeight(),
  ])
  return { workout, food, weight }
}

export type GettingStartedItem = {
  id: 'workout' | 'food' | 'weight'
  key: string
  href: string
  done: boolean
}

export function buildGettingStartedItems(counts: GettingStartedCounts): GettingStartedItem[] {
  return [
    { id: 'workout', key: 'v42_gs_workout', href: '#/workouts', done: counts.workout },
    { id: 'food', key: 'v42_gs_food', href: '#/food', done: counts.food },
    { id: 'weight', key: 'v42_gs_weight', href: '#/progress', done: counts.weight },
  ]
}
