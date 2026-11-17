import { hasAnyMeal } from '../../data/food'
import { hasAnyMeasurement } from '../../data/measurements'
import { hasAnyWeight } from '../../data/progress'
import { hasFinishedSession } from '../../data/workouts'
import type { FriendsView } from '../../data/friends-api'

export const GS_ELIGIBLE_KEY = 'fq_gs_eligible'
export const GS_DISMISSED_KEY = 'fq_gs_dismissed'
export const GS_FRIEND_DONE_KEY = 'fq_gs_friend_done'

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

export const isFriendDone = (store?: GsStore | null): boolean => getFlag(GS_FRIEND_DONE_KEY, store)
export const markFriendDone = (store?: GsStore | null): void => setFlag(GS_FRIEND_DONE_KEY, store)

export const shouldShowGettingStarted = (store?: GsStore | null): boolean =>
  isGettingStartedEligible(store) && !isGettingStartedDismissed(store)

export const hasFriendOrOutgoing = (
  view: Pick<FriendsView, 'friends' | 'outgoing'> | null | undefined,
): boolean => (view?.friends?.length ?? 0) >= 1 || (view?.outgoing?.length ?? 0) >= 1

export function syncFriendDone(
  view: Pick<FriendsView, 'friends' | 'outgoing'> | null | undefined,
  store?: GsStore | null,
): boolean {
  if (hasFriendOrOutgoing(view)) {
    markFriendDone(store)
    return true
  }
  return isFriendDone(store)
}

export type GettingStartedCounts = {
  workout: boolean
  food: boolean
  weight: boolean
  measure: boolean
}

export async function queryGettingStartedDone(): Promise<GettingStartedCounts> {
  const [workout, food, weight, measure] = await Promise.all([
    hasFinishedSession(),
    hasAnyMeal(),
    hasAnyWeight(),
    hasAnyMeasurement(),
  ])
  return { workout, food, weight, measure }
}

export type GettingStartedItem = {
  id: 'workout' | 'food' | 'weight' | 'measure' | 'friend'
  key: string
  href: string
  done: boolean
}

export function buildGettingStartedItems(
  counts: GettingStartedCounts,
  friendDone: boolean,
): GettingStartedItem[] {
  return [
    { id: 'workout', key: 'v42_gs_workout', href: '#/workouts', done: counts.workout },
    { id: 'food', key: 'v42_gs_food', href: '#/food', done: counts.food },
    { id: 'weight', key: 'v42_gs_weight', href: '#/progress', done: counts.weight },
    { id: 'measure', key: 'v42_gs_measure', href: '#/progress', done: counts.measure },
    { id: 'friend', key: 'v42_gs_friend', href: '#/friends', done: friendDone },
  ]
}
