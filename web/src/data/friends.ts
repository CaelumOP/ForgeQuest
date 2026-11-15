import { useEffect, useState } from 'preact/hooks'
import { GOOGLE_CLIENT_ID } from '../config'
import { todayEpochDay, weekStart } from '../domain/dates'
import {
  buildLeaderboard,
  computeForgeWeek,
  normalizeUsername,
  type LeaderboardEntry,
  type Pr,
  type Snapshot,
} from '../domain/forge-score'
import { db } from './db'
import { preloadGis } from './drive'
import {
  FriendsError,
  friendsApi,
  type FriendsView,
  type InviteResult,
  type Me,
  type ProfileInput,
} from './friends-api'
import { getProgressSnapshot } from './progress'
import { trainingDays } from './workouts'

export const FRIENDS_AUTH_KEY = 'friendsAuth'
export const FRIENDS_CACHE_KEY = 'friendsCache'
export const FRIENDS_DEMO_KEY = 'friendsDemo'

export interface FriendsAuth {
  sessionToken: string
  /** Name from Google (server `user.name` at sign-in), pre-fills Join. */
  suggestedName?: string
  lastSnapshotHash?: string | null
  nudgedTodayDay?: number
  nudgedTodayIds?: string[]
}

/** What we keep in kv `friendsCache`: the last GET /friends plus my own last uploaded snapshot. */
export type CachedView = FriendsView & { mySnapshot: Snapshot | null }

export type FriendsStatus = 'signed_out' | 'needs_join' | 'joined'

export interface FriendsState {
  status: FriendsStatus
  loading: boolean
  busy: boolean
  error: string | null
  auth: FriendsAuth | null
  view: CachedView | null
  leaderboard: LeaderboardEntry[]
  nudgedToday: Set<string>
  dismissedNudgeIds: Set<string>
  demoMode: boolean
}

export type ActionResult<T = void> =
  | { ok: true; data: T }
  | { ok: false; error: string }

interface GisIdApi {
  accounts?: {
    id?: {
      initialize(cfg: {
        client_id: string
        callback: (resp: { credential?: string }) => void
        auto_select?: boolean
        cancel_on_tap_outside?: boolean
      }): void
      renderButton(
        parent: HTMLElement,
        options: {
          theme?: 'outline' | 'filled_blue' | 'filled_black'
          size?: 'large' | 'medium' | 'small'
          text?: 'signin_with' | 'signup_with' | 'continue_with' | 'signin'
          shape?: 'rectangular' | 'pill' | 'circle' | 'square'
          width?: number
          locale?: string
        },
      ): void
      disableAutoSelect?: () => void
    }
  }
}

const gisId = () => (window as unknown as { google?: GisIdApi }).google?.accounts?.id

function initialState(): FriendsState {
  return {
    status: 'signed_out',
    loading: true,
    busy: false,
    error: null,
    auth: null,
    view: null,
    leaderboard: [],
    nudgedToday: new Set(),
    dismissedNudgeIds: new Set(),
    demoMode: false,
  }
}

let state: FriendsState = initialState()
const listeners = new Set<() => void>()
let loadPromise: Promise<void> | null = null
let gisInitialized = false

function notify(): void {
  for (const l of listeners) l()
}

function setState(patch: Partial<FriendsState>): void {
  state = { ...state, ...patch }
  notify()
}

export const nudgeKey = (n: { fromId: string; day: number }) => `${n.fromId}:${n.day}`

function computeBoard(view: CachedView | null): LeaderboardEntry[] {
  if (!view) return []
  const me = { id: view.me.id, username: view.me.username, name: view.me.name, photoUrl: view.me.photoUrl }
  return buildLeaderboard(view.weekStart, me, view.mySnapshot, view.friends)
}

function withMine(view: FriendsView, mySnapshot: Snapshot | null): CachedView {
  return { ...view, mySnapshot }
}

/** Local view right after sign-in/join, before the first GET /friends. */
function placeholderView(me: Me, now = Date.now()): CachedView {
  const today = todayEpochDay(new Date(now))
  return { me, today, weekStart: weekStart(today), friends: [], incoming: [], outgoing: [], nudges: [], mySnapshot: null }
}

export const SHARE_URL = 'https://caelumop.github.io/ForgeQuest/'
export const shareText = (username: string) => `Add me on ForgeQuest: @${username} — ${SHARE_URL}`

function nudgedSetForToday(auth: FriendsAuth | null, now = Date.now()): Set<string> {
  if (!auth) return new Set()
  const today = todayEpochDay(new Date(now))
  if (auth.nudgedTodayDay !== today || !Array.isArray(auth.nudgedTodayIds)) return new Set()
  return new Set(auth.nudgedTodayIds)
}

function buildDemoView(now = Date.now()): CachedView {
  const today = todayEpochDay(new Date(now))
  const ws = weekStart(today)
  const snap = (
    score: number,
    badge: Snapshot['badge'],
    streakWeeks: number,
    proteinDays: number,
    prs: Pr[] | null,
    w = ws,
  ): Snapshot => ({ weekStart: w, done: Math.round(score / 25), due: 4, score, badge, streakWeeks, proteinDays, prs })
  const person = (id: string, name: string) => ({ id: `demo_${id}`, username: id, name, photoUrl: null })
  return {
    me: { id: 'demo_me', username: 'tariq', name: 'Tariq', photoUrl: null, sharePhoto: true, sharePrs: true, joined: true },
    today,
    weekStart: ws,
    mySnapshot: snap(86, 'trained', 6, 5, [
      { exercise: 'Barbell Bench Press', kg: 90, reps: 5 },
      { exercise: 'Back Squat', kg: 120, reps: 5 },
    ]),
    friends: [
      {
        ...person('omar', 'Omar'),
        snapshot: snap(100, 'trained', 9, 6, [
          { exercise: 'Barbell Bench Press', kg: 100, reps: 5 },
          { exercise: 'Deadlift', kg: 160, reps: 3 },
        ]),
      },
      { ...person('lina', 'Lina'), snapshot: snap(92, 'on_track', 4, 5, [{ exercise: 'Romanian Deadlift', kg: 85, reps: 8 }]) },
      { ...person('zaid', 'Zaid'), snapshot: snap(75, 'rest', 3, 4, [{ exercise: 'Overhead Press', kg: 55, reps: 6 }]) },
      { ...person('sami', 'Sami'), snapshot: snap(45, 'slacking', 1, 2, null) },
      { ...person('noor', 'Noor'), snapshot: snap(100, 'trained', 5, 3, null, ws - 7) },
    ],
    incoming: [person('karim', 'Karim')],
    outgoing: [person('maya', 'Maya')],
    nudges: [{ fromId: 'demo_omar', name: 'Omar', username: 'omar', day: today }],
  }
}

async function saveAuth(auth: FriendsAuth | null): Promise<void> {
  if (auth) {
    await db.kv.put({ key: FRIENDS_AUTH_KEY, value: auth })
  } else {
    await db.kv.delete(FRIENDS_AUTH_KEY)
  }
}

async function saveCache(view: CachedView | null): Promise<void> {
  if (view) {
    await db.kv.put({ key: FRIENDS_CACHE_KEY, value: view })
  } else {
    await db.kv.delete(FRIENDS_CACHE_KEY)
  }
}

async function clearLocalSession(): Promise<void> {
  await Promise.all([saveAuth(null), saveCache(null)])
  setState({
    status: 'signed_out',
    auth: null,
    view: null,
    leaderboard: [],
    nudgedToday: new Set(),
    dismissedNudgeIds: new Set(),
  })
}

function errorCode(err: unknown): string {
  return err instanceof FriendsError ? err.code : 'network'
}

async function handleApiError(err: unknown): Promise<string> {
  const code = errorCode(err)
  if (err instanceof FriendsError && (err.status === 401 || err.code === 'unauthorized')) {
    await clearLocalSession()
  }
  setState({ error: code })
  return code
}

export async function ensureFriendsLoaded(): Promise<void> {
  if (!state.loading) return
  loadPromise ??= (async () => {
    try {
      const [authRow, cacheRow, demoRow] = await Promise.all([
        db.kv.get(FRIENDS_AUTH_KEY),
        db.kv.get(FRIENDS_CACHE_KEY),
        import.meta.env.DEV ? db.kv.get(FRIENDS_DEMO_KEY) : Promise.resolve(undefined),
      ])

      if (import.meta.env.DEV && demoRow?.value === true) {
        const view = buildDemoView()
        setState({
          loading: false,
          demoMode: true,
          status: 'joined',
          view,
          leaderboard: computeBoard(view),
          auth: { sessionToken: 'demo', suggestedName: view.me.name },
          nudgedToday: new Set(),
        })
        return
      }

      const auth = (authRow?.value as FriendsAuth | undefined) ?? null
      const cached = (cacheRow?.value as CachedView | undefined) ?? null
      // Drop caches written by the pre-contract build (no weekStart / username fields).
      const view = cached && typeof cached.weekStart === 'number' && 'username' in (cached.me ?? {}) ? cached : null

      if (auth?.sessionToken && view?.me?.joined) {
        setState({
          loading: false,
          status: 'joined',
          auth,
          view,
          leaderboard: computeBoard(view),
          nudgedToday: nudgedSetForToday(auth),
        })
      } else if (auth?.sessionToken) {
        setState({
          loading: false,
          status: 'needs_join',
          auth,
          view: null,
          leaderboard: [],
          nudgedToday: nudgedSetForToday(auth),
        })
        // Session but no usable cache (cleared, or written by an older build): ask the server whether we already joined.
        void resumeFromServer(auth)
      } else {
        setState({ loading: false, status: 'signed_out' })
      }
    } finally {
      loadPromise = null
    }
  })()
  await loadPromise
}

async function resumeFromServer(auth: FriendsAuth): Promise<void> {
  try {
    const me = await friendsApi.getMe(auth.sessionToken)
    if (state.auth?.sessionToken !== auth.sessionToken || state.status !== 'needs_join') return
    if (me.joined && me.username) {
      const view = placeholderView(me)
      await saveCache(view)
      setState({ status: 'joined', view, leaderboard: computeBoard(view) })
      await refreshFriends(true)
    } else if (!auth.suggestedName && me.name) {
      const next = { ...auth, suggestedName: me.name }
      await saveAuth(next)
      setState({ auth: next })
    }
  } catch (err) {
    // Offline: stay on Join; an expired session drops back to signed out.
    if (err instanceof FriendsError && err.status === 401) await clearLocalSession()
  }
}

export function useFriends(): FriendsState {
  const [, setTick] = useState(0)
  useEffect(() => {
    const fn = () => setTick((n) => n + 1)
    listeners.add(fn)
    void ensureFriendsLoaded()
    return () => {
      listeners.delete(fn)
    }
  }, [])
  return state
}

export function clearFriendsError(): void {
  if (state.error !== null) setState({ error: null })
}

export function dismissFriendsNudge(id: string): void {
  const next = new Set(state.dismissedNudgeIds)
  next.add(id)
  setState({ dismissedNudgeIds: next })
}

/**
 * Computes this week's anonymized snapshot from local Dexie tables.
 * Mirrors Android FriendsRepository.buildSnapshot().
 */
export async function buildMySnapshot(sharePrs: boolean, now = Date.now()): Promise<Snapshot> {
  const today = todayEpochDay(new Date(now))
  const ws = weekStart(today)
  const progress = await getProgressSnapshot(now, true)

  const scheduledDays = progress.plan ? trainingDays(progress.plan) : new Set<number>()
  const trainedDays = new Set(
    progress.sessions.filter((s) => s.planDayId != null).map((s) => s.epochDay),
  )

  const proteinGoal = progress.settings.proteinGoal
  const proteinDaysSet =
    proteinGoal > 0
      ? new Set(
          progress.nutritionByDay.filter((d) => d.protein >= proteinGoal).map((d) => d.epochDay),
        )
      : new Set<number>()

  const week = computeForgeWeek(today, scheduledDays, trainedDays, proteinDaysSet)

  let prs: Pr[] | null = null
  if (sharePrs) {
    const weekSessions = new Map(
      progress.sessions
        .filter((s) => s.epochDay >= ws && s.epochDay <= today && s.finishedAt != null && s.id != null)
        .map((s) => [s.id!, s]),
    )
    const prSets = progress.doneSets
      .filter((s) => s.isPr && weekSessions.has(s.sessionId))
      .sort((a, b) => {
        const sa = weekSessions.get(a.sessionId)?.finishedAt ?? 0
        const sb = weekSessions.get(b.sessionId)?.finishedAt ?? 0
        if (sb !== sa) return sb - sa
        return (b.id ?? 0) - (a.id ?? 0)
      })
      .slice(0, 10)

    prs = prSets.map((s) => {
      const rawName = progress.exercises.get(s.exerciseId)?.name ?? s.exerciseId
      const trimmed = rawName.trim()
      const exercise = (trimmed.length > 0 ? trimmed : s.exerciseId).slice(0, 60)
      const kg = Math.round(Math.min(1000, Math.max(0, s.weightKg ?? 0)) * 100) / 100
      const reps = Math.min(100, Math.max(1, Math.round(s.reps ?? s.seconds ?? 1)))
      return { exercise, kg, reps }
    })
  }

  return {
    weekStart: week.weekStart,
    done: week.done,
    due: week.due,
    score: week.score,
    badge: week.badge,
    streakWeeks: Math.min(520, Math.max(0, progress.streak.weeks)),
    proteinDays: week.proteinDays,
    prs,
  }
}

async function hashSnapshot(s: Snapshot): Promise<string> {
  const canonical = JSON.stringify({
    weekStart: s.weekStart,
    done: s.done,
    due: s.due,
    score: s.score,
    badge: s.badge,
    streakWeeks: s.streakWeeks,
    proteinDays: s.proteinDays,
    prs: s.prs,
  })
  if (typeof crypto !== 'undefined' && crypto.subtle) {
    const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(canonical))
    return Array.from(new Uint8Array(digest), (b) => b.toString(16).padStart(2, '0')).join('')
  }
  return canonical
}

export async function renderGoogleSignInButton(
  container: HTMLElement | null,
  options?: { theme?: 'outline' | 'filled_blue' | 'filled_black'; width?: number },
): Promise<boolean> {
  if (!container) return false
  try {
    await preloadGis()
  } catch {
    setState({ error: 'network' })
    return false
  }
  // Wait briefly in case accounts.oauth2 settled while accounts.id initializes on the same script tick.
  let idApi = gisId()
  if (!idApi) {
    await new Promise((r) => setTimeout(r, 50))
    idApi = gisId()
  }
  if (!idApi) return false

  if (!gisInitialized) {
    idApi.initialize({
      client_id: GOOGLE_CLIENT_ID,
      callback: (resp) => {
        if (resp.credential) {
          void signInWithIdToken(resp.credential)
        }
      },
      auto_select: false,
      cancel_on_tap_outside: true,
    })
    gisInitialized = true
  }

  container.innerHTML = ''
  idApi.renderButton(container, {
    theme: options?.theme ?? 'outline',
    size: 'large',
    shape: 'pill',
    text: 'signin_with',
    width: options?.width,
  })
  return true
}

export async function signInWithIdToken(idToken: string): Promise<ActionResult> {
  await ensureFriendsLoaded()
  setState({ busy: true, error: null })
  try {
    const res = await friendsApi.authGoogle(idToken)
    const auth: FriendsAuth = { sessionToken: res.session, suggestedName: res.user.name, lastSnapshotHash: null }
    await saveAuth(auth)

    if (res.user.joined && res.user.username) {
      const view = placeholderView(res.user)
      await saveCache(view)
      setState({ auth, status: 'joined', view, leaderboard: computeBoard(view), nudgedToday: new Set(), busy: false })
      await refreshFriends(true)
    } else {
      await saveCache(null)
      setState({ auth, status: 'needs_join', view: null, leaderboard: [], nudgedToday: new Set(), busy: false })
    }
    return { ok: true, data: undefined }
  } catch (err) {
    const code = await handleApiError(err)
    setState({ busy: false })
    return { ok: false, error: code }
  }
}

export async function joinFriends(input: {
  name: string
  username: string
  sharePhoto: boolean
  sharePrs: boolean
}): Promise<ActionResult<Me>> {
  await ensureFriendsLoaded()
  const token = state.auth?.sessionToken
  if (!token) return { ok: false, error: 'unauthorized' }

  const name = input.name.trim()
  if (name.length < 1 || name.length > 40) return fail('bad_profile')
  const username = normalizeUsername(input.username)
  if (!username) return fail('bad_username')

  setState({ busy: true, error: null })
  try {
    const me = await friendsApi.putMe(token, { name, username, sharePhoto: input.sharePhoto, sharePrs: input.sharePrs })
    const view = placeholderView(me)
    await saveCache(view)
    setState({ status: 'joined', view, leaderboard: computeBoard(view) })
    await refreshFriends(true) // uploads the first snapshot, then fetches the board
    setState({ busy: false })
    return { ok: true, data: me }
  } catch (err) {
    const code = await handleApiError(err)
    setState({ busy: false })
    return { ok: false, error: code }
  }
}

function fail<T>(code: string): ActionResult<T> {
  setState({ error: code })
  return { ok: false, error: code }
}

export async function uploadSnapshot(force = false): Promise<boolean> {
  if (state.demoMode) return true
  const auth = state.auth ?? ((await db.kv.get(FRIENDS_AUTH_KEY))?.value as FriendsAuth | undefined) ?? null
  const view = state.view ?? ((await db.kv.get(FRIENDS_CACHE_KEY))?.value as CachedView | undefined) ?? null
  if (!auth?.sessionToken || !view?.me?.joined) return false

  const snap = await buildMySnapshot(view.me.sharePrs)
  const hash = await hashSnapshot(snap)
  if (!force && auth.lastSnapshotHash === hash) return false

  await friendsApi.putSnapshot(auth.sessionToken, snap)
  const nextAuth: FriendsAuth = { ...auth, lastSnapshotHash: hash }
  await saveAuth(nextAuth)
  const nextView: CachedView = { ...view, mySnapshot: snap }
  await saveCache(nextView)
  if (state.status === 'joined') {
    setState({
      auth: nextAuth,
      view: nextView,
      leaderboard: computeBoard(nextView),
    })
  }
  return true
}

export async function syncFriendsQuietly(): Promise<void> {
  try {
    await ensureFriendsLoaded()
    if (state.demoMode) return
    await uploadSnapshot(false)
  } catch {
    // Quiet background sync after workouts must never surface errors or break finish flow.
  }
}

export async function refreshFriends(silent = false): Promise<ActionResult> {
  await ensureFriendsLoaded()
  if (state.demoMode) return { ok: true, data: undefined }
  const token = state.auth?.sessionToken
  if (!token) return { ok: false, error: 'unauthorized' }

  if (!silent) setState({ busy: true, error: null })
  try {
    try {
      await uploadSnapshot(false)
    } catch {
      // Proceed to fetch latest friends board even if snapshot upload didn't run.
    }
    const view = withMine(await friendsApi.getFriends(token), state.view?.mySnapshot ?? null)
    await saveCache(view)
    setState({
      status: 'joined',
      view,
      leaderboard: computeBoard(view),
      dismissedNudgeIds: new Set(),
      busy: false,
      error: null,
    })
    return { ok: true, data: undefined }
  } catch (err) {
    const code = await handleApiError(err)
    if (!silent) setState({ busy: false })
    return { ok: false, error: code }
  }
}

/** PUT /me needs the whole profile, so every edit merges into the current Me. */
async function putProfile(patch: Partial<ProfileInput>): Promise<ActionResult<Me>> {
  await ensureFriendsLoaded()
  const current = state.view?.me
  if (!current) return { ok: false, error: 'not_joined' }
  if (state.demoMode && state.view) {
    const me: Me = { ...current, ...patch, username: patch.username ?? current.username }
    const view: CachedView = { ...state.view, me }
    setState({ view, leaderboard: computeBoard(view) })
    return { ok: true, data: me }
  }
  const token = state.auth?.sessionToken
  if (!token) return { ok: false, error: 'unauthorized' }

  setState({ busy: true, error: null })
  try {
    const me = await friendsApi.putMe(token, {
      name: patch.name ?? current.name,
      sharePhoto: patch.sharePhoto ?? current.sharePhoto,
      sharePrs: patch.sharePrs ?? current.sharePrs,
      ...(patch.username !== undefined ? { username: patch.username } : {}),
    })
    if (state.view) {
      const nextView: CachedView = { ...state.view, me }
      await saveCache(nextView)
      setState({ view: nextView, leaderboard: computeBoard(nextView) })
    }
    if (patch.sharePrs !== undefined) await uploadSnapshot(true)
    setState({ busy: false })
    return { ok: true, data: me }
  } catch (err) {
    const code = await handleApiError(err)
    setState({ busy: false })
    return { ok: false, error: code }
  }
}

export const updateFriendsPreferences = (patch: { sharePhoto?: boolean; sharePrs?: boolean }) => putProfile(patch)

export async function changeUsername(raw: string): Promise<ActionResult<Me>> {
  const username = normalizeUsername(raw)
  if (!username) return fail('bad_username')
  if (state.view && username === state.view.me.username) return { ok: true, data: state.view.me }
  return putProfile({ username })
}

export async function inviteFriend(rawUsername: string): Promise<ActionResult<InviteResult>> {
  await ensureFriendsLoaded()
  // Bad syntax never reaches the server, so typos don't count toward the 5-per-15-min lookup lock.
  const username = normalizeUsername(rawUsername)
  if (!username) return fail('bad_username')
  if (state.demoMode) return { ok: true, data: { status: 'sent' } }
  const token = state.auth?.sessionToken
  if (!token) return { ok: false, error: 'unauthorized' }

  setState({ busy: true, error: null })
  try {
    const result = await friendsApi.invite(token, username)
    await refreshFriends(true)
    setState({ busy: false })
    return { ok: true, data: result }
  } catch (err) {
    const code = await handleApiError(err)
    setState({ busy: false })
    return { ok: false, error: code }
  }
}

async function runFriendMutation(fn: (token: string) => Promise<unknown>): Promise<ActionResult> {
  await ensureFriendsLoaded()
  if (state.demoMode) return { ok: true, data: undefined }
  const token = state.auth?.sessionToken
  if (!token) return { ok: false, error: 'unauthorized' }

  setState({ busy: true, error: null })
  try {
    await fn(token)
    await refreshFriends(true)
    setState({ busy: false })
    return { ok: true, data: undefined }
  } catch (err) {
    const code = await handleApiError(err)
    setState({ busy: false })
    return { ok: false, error: code }
  }
}

export const acceptFriend = (fromId: string) =>
  runFriendMutation((t) => friendsApi.accept(t, fromId))

export const declineFriend = (fromId: string) =>
  runFriendMutation((t) => friendsApi.decline(t, fromId))

export const removeFriend = (id: string) =>
  runFriendMutation((t) => friendsApi.remove(t, id))

export const blockFriend = (id: string) =>
  runFriendMutation((t) => friendsApi.block(t, id))

export const unblockFriend = (id: string) =>
  runFriendMutation((t) => friendsApi.unblock(t, id))

export async function nudgeFriend(toId: string): Promise<ActionResult> {
  await ensureFriendsLoaded()
  if (state.nudgedToday.has(toId)) {
    return { ok: false, error: 'already_nudged' }
  }
  const markNudged = async () => {
    const today = todayEpochDay()
    const nextSet = new Set(state.nudgedToday)
    nextSet.add(toId)
    if (state.auth) {
      const nextAuth: FriendsAuth = {
        ...state.auth,
        nudgedTodayDay: today,
        nudgedTodayIds: [...nextSet],
      }
      await saveAuth(nextAuth)
      setState({ auth: nextAuth, nudgedToday: nextSet })
    } else {
      setState({ nudgedToday: nextSet })
    }
  }

  if (state.demoMode) {
    await markNudged()
    return { ok: true, data: undefined }
  }

  const token = state.auth?.sessionToken
  if (!token) return { ok: false, error: 'unauthorized' }

  setState({ busy: true, error: null })
  try {
    await friendsApi.nudge(token, toId)
    await markNudged()
    setState({ busy: false })
    return { ok: true, data: undefined }
  } catch (err) {
    if (err instanceof FriendsError && (err.code === 'already_nudged')) {
      await markNudged()
    }
    const code = await handleApiError(err)
    setState({ busy: false })
    return { ok: false, error: code }
  }
}

export async function signOutFriends(): Promise<void> {
  gisId()?.disableAutoSelect?.()
  if (state.demoMode && import.meta.env.DEV) {
    await db.kv.delete(FRIENDS_DEMO_KEY)
    setState({ demoMode: false })
  }
  await clearLocalSession()
  setState({ error: null })
}

export async function deleteFriendsAccount(): Promise<ActionResult> {
  await ensureFriendsLoaded()
  if (state.demoMode) {
    await signOutFriends()
    return { ok: true, data: undefined }
  }
  const token = state.auth?.sessionToken
  if (!token) {
    await clearLocalSession()
    return { ok: true, data: undefined }
  }

  setState({ busy: true, error: null })
  try {
    try {
      await friendsApi.deleteMe(token)
    } catch (err) {
      if (!(err instanceof FriendsError && err.status === 401)) throw err
    }
    gisId()?.disableAutoSelect?.()
    await clearLocalSession()
    setState({ busy: false, error: null })
    return { ok: true, data: undefined }
  } catch (err) {
    const code = await handleApiError(err)
    setState({ busy: false })
    return { ok: false, error: code }
  }
}

/** Test-only reset of module state. */
export function _resetFriendsForTest(): void {
  state = initialState()
  loadPromise = null
  gisInitialized = false
}
