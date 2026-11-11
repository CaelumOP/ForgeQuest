// First-run coach-mark state and placement math (Android CoachMark.kt + AppTour.kt).
// Flags live in localStorage under the Android keys; `fq_tour_seen` is kept so anyone who finished the old slide tour
// never gets the tips again. Every storage access is guarded for private-browsing / blocked-storage contexts.

export const TOUR_SEEN_KEY = 'fq_tour_seen'
export const COACH_STEPS = ['coach_home', 'coach_plan', 'coach_food', 'coach_ask', 'coach_tick'] as const
export type CoachStep = (typeof COACH_STEPS)[number]

export type CoachStore = Pick<Storage, 'getItem' | 'setItem'>

const defaultStore = (): CoachStore | null => {
  try { return typeof localStorage !== 'undefined' ? localStorage : null } catch { return null }
}

function getFlag(key: string, store: CoachStore | null = defaultStore()): boolean {
  try { return store?.getItem(key) === '1' } catch { return false }
}

function setFlag(key: string, store: CoachStore | null = defaultStore()): void {
  try { store?.setItem(key, '1') } catch { /* storage blocked: shown again next visit */ }
}

export function tourSeen(store?: CoachStore | null): boolean {
  return getFlag(TOUR_SEEN_KEY, store)
}

export function markTourSeen(store?: CoachStore | null): void {
  setFlag(TOUR_SEEN_KEY, store)
}

export function seen(id: CoachStep, store?: CoachStore | null): boolean {
  return getFlag(id, store)
}

export function markSeen(id: CoachStep, store?: CoachStore | null): void {
  setFlag(id, store)
}

export function shouldShow(id: CoachStep, enabled = true, store?: CoachStore | null): boolean {
  return Boolean(enabled) && !tourSeen(store) && !seen(id, store)
}

export function allSeen(store?: CoachStore | null): boolean {
  return COACH_STEPS.every((s) => seen(s, store))
}

/** Marks [id] seen when shown; once every step in the tour has been seen, marks the tour finished for good. */
export function stepSeen(id: CoachStep, store?: CoachStore | null): void {
  markSeen(id, store)
  if (allSeen(store)) markTourSeen(store)
}

/** "Skip tips" on any tip: ends the tour for good. */
export function skipTour(store?: CoachStore | null): void {
  markTourSeen(store)
}
export const skip = skipTour

// ---------------------------------------------------------------- Single active tip queue

let activeToken: symbol | null = null
const waiters = new Set<() => void>()

/** Claims the single app-wide tip slot for [token] if no other tip owns it. */
export function acquireTip(token: symbol): boolean {
  if (activeToken !== null && activeToken !== token) return false
  activeToken = token
  return true
}

/** Registers [fn] to run when the active tip closes; returns an unsubscribe callback. */
export function waitForTipSlot(fn: () => void): () => void {
  waiters.add(fn)
  return () => { waiters.delete(fn) }
}

/** Releases [token]'s slot and wakes waiting tips in order until one claims the slot. */
export function releaseTip(token: symbol): void {
  if (activeToken !== token) return
  activeToken = null
  for (const fn of [...waiters]) {
    if (activeToken !== null) break
    fn()
  }
}

/** Clears the active slot and waiters (for tests). */
export function resetTipSlot(): void {
  activeToken = null
  waiters.clear()
}

// ---------------------------------------------------------------- Viewport placement

export type BoxRect = { top: number; bottom: number; left: number; right: number; width: number; height: number }

export type PlaceTipInput = {
  anchor: BoxRect
  tip: { width: number; height: number }
  viewport: { width: number; height: number }
  below?: boolean
  rtl?: boolean
  topBarBottom?: number
  tabBar?: BoxRect | null
}

export type TipPlacement = {
  blockStart: number
  inlineStart: number
}

const GUTTER = 16
const GAP = 8

/**
 * Places the tip next to [anchor] (below when [below] is true, flipping to the other side if it would run into the top
 * bar, tab bar, or viewport edge), kept inside 16 px gutters. Returns logical `inset-block-start` and
 * `inset-inline-start` coordinates so RTL mirrors naturally.
 */
export function placeTip(input: PlaceTipInput): TipPlacement {
  const { anchor, tip, viewport, below = false, rtl = false, topBarBottom = 0, tabBar = null } = input
  const hasTab = Boolean(tabBar && tabBar.width > 0 && tabBar.height > 0)
  const bottomTab = hasTab && tabBar!.width >= tabBar!.height
  const railTab = hasTab && tabBar!.height > tabBar!.width

  const safeTop = Math.max(GUTTER, topBarBottom > 0 ? Math.round(topBarBottom + GAP) : GUTTER)
  const safeBottom = bottomTab
    ? Math.min(viewport.height - GUTTER, Math.round(tabBar!.top - GAP))
    : viewport.height - GUTTER

  const minInline = railTab
    ? Math.max(GUTTER, Math.round((rtl ? viewport.width - tabBar!.left : tabBar!.right) + GAP))
    : GUTTER
  const maxInline = Math.max(minInline, viewport.width - GUTTER - tip.width)
  const rawInline = rtl ? viewport.width - anchor.right : anchor.left
  const inlineStart = Math.min(maxInline, Math.max(minInline, Math.round(rawInline)))

  const belowTop = anchor.bottom + GAP
  const aboveTop = anchor.top - GAP - tip.height
  const fitsBelow = belowTop + tip.height <= safeBottom
  const fitsAbove = aboveTop >= safeTop
  const roomBelow = safeBottom - anchor.bottom
  const roomAbove = anchor.top - safeTop

  const rawBlock = below
    ? (fitsBelow || (!fitsAbove && roomBelow >= roomAbove) ? belowTop : aboveTop)
    : (fitsAbove || (!fitsBelow && roomAbove >= roomBelow) ? aboveTop : belowTop)
  const maxBlock = Math.max(safeTop, safeBottom - tip.height)
  const blockStart = Math.min(maxBlock, Math.max(safeTop, Math.round(rawBlock)))

  return { blockStart, inlineStart }
}
