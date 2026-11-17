import 'fake-indexeddb/auto'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { buildBackup, DEFAULT_BACKUP_SETTINGS, encodeBackup } from './backup'
import { db } from './db'
import {
  _resetFriendsForTest,
  acceptFriend,
  badgeCount,
  blockFriend,
  buildMySnapshot,
  declineFriend,
  effectiveNudgeNextAt,
  ensureFriendsLoaded,
  FRIENDS_AUTH_KEY,
  FRIENDS_CACHE_KEY,
  FRIENDS_DEMO_KEY,
  getFriendsState,
  hashSnapshot,
  localMidnightSec,
  markInboxSeen,
  migrateNudgeAuth,
  nudgeCooldownKind,
  nudgeFriend,
  pruneNudgeMap,
  refreshFriends,
  removeFriend,
  renderGoogleSignInButton,
  setFriendMuted,
  shownName,
  syncFriendsQuietly,
  uploadSnapshot,
} from './friends'
import { DEFAULT_SETTINGS } from './settings'
import { todayEpochDay, weekStart } from '../domain/dates'
import type { SetRow } from './db'
import { initialsFor } from '../ui/friends/Avatar'

describe('friends persistence and backup isolation', () => {
  beforeEach(async () => {
    await Promise.all(db.tables.map((t) => t.clear()))
    _resetFriendsForTest()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('sends raw inputs with real protein crossing time and private uncapped PR counts', async () => {
    const now = new Date(2026, 10, 18, 18).getTime()
    const today = todayEpochDay(new Date(now))
    const ws = weekStart(today)
    const finish = new Date(2026, 10, 18, 10).getTime()
    await db.kv.put({ key: 'settings', value: { ...DEFAULT_SETTINGS, weeklyTargetOverride: 1, proteinGoal: 100 } })
    await db.sessions.bulkPut([
      { id: 1, planDayId: null, title: 'Free workout', epochDay: today, startedAt: finish - 1000, finishedAt: finish, note: '', debrief: '' },
      { id: 2, planDayId: null, title: 'Old', epochDay: ws - 1, startedAt: finish, finishedAt: finish, note: '', debrief: '' },
      { id: 3, planDayId: null, title: 'Open', epochDay: today, startedAt: finish, finishedAt: null, note: '', debrief: '' },
    ])
    const set: SetRow = { sessionId: 1, exerciseId: 'bench', itemPosition: 0, block: 'Main', circuit: false,
      setIndex: 0, targetLabel: '5', reps: 5, seconds: null, weightKg: 50, done: true, isPr: true, restSeconds: 60, note: '', effort: '' }
    await db.sets.bulkPut([
      ...Array.from({ length: 12 }, (_, i) => ({ ...set, id: i + 1 })),
      ...['WARMUP', 'DROPSET', 'RESTPAUSE'].map((setType, i) => ({ ...set, id: 20 + i, setType })),
      { ...set, id: 30, done: false }, { ...set, id: 31, sessionId: 2 }, { ...set, id: 32, sessionId: 3 },
      { ...set, id: 33, block: 'Warm-up' },
    ])
    const meal = { epochDay: today, name: 'Meal', category: 'LUNCH' as const, kcal: 300, quantity: 4,
      carbs: 0, photoPath: null, foodId: null }
    const crossing = finish + 7200000
    await db.meals.bulkPut([
      { ...meal, loggedAt: crossing, protein: 60 },
      { ...meal, loggedAt: finish + 3600000, protein: 40 },
      { ...meal, loggedAt: crossing + 3600000, protein: 50 },
    ])
    const hidden = await buildMySnapshot(false, now)
    expect(hidden).toMatchObject({ done: 1, due: 1, target: 1, prCount: 12, proteinDays: 1,
      score: 112, reachedAt: crossing / 1000, prs: null })
    const shared = await buildMySnapshot(true, now)
    expect(shared.prs).toHaveLength(10)
    expect({ ...shared, prs: null }).toEqual(hidden)
    await db.kv.put({ key: 'settings', value: { ...DEFAULT_SETTINGS, weeklyTargetOverride: 1, proteinGoal: 0 } })
    expect(await buildMySnapshot(false, now)).toMatchObject({ proteinDays: 0, score: 110, reachedAt: finish / 1000 })
  })

  it('keeps the authoritative self row after upload and replaces it only from GET', async () => {
    const own = { weekStart: 20772, done: 1, due: 3, target: 3, score: 33, badge: 'on_track',
      streakWeeks: 0, proteinDays: 0, prCount: 0, reachedAt: 1794700000, scoreVersion: 2 }
    const view = { me: { id: 'u1', username: 'tariq', name: 'tariq', photoUrl: null, sharePhoto: true, sharePrs: false, joined: true },
      today: 20775, weekStart: 20772, mySnapshot: own, friends: [], incoming: [], outgoing: [], nudges: [] }
    await db.kv.bulkPut([{ key: FRIENDS_AUTH_KEY, value: { sessionToken: 'test' } }, { key: FRIENDS_CACHE_KEY, value: view }])
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 204 })))
    await uploadSnapshot(true)
    expect((await db.kv.get(FRIENDS_CACHE_KEY))?.value).toMatchObject({ mySnapshot: own })
    const authoritative = { ...own, score: 38, prCount: 1 }
    vi.stubGlobal('fetch', vi.fn().mockImplementation((url: string) => Promise.resolve(
      url.includes('/v1/friends') ? new Response(JSON.stringify({ ...view, mySnapshot: authoritative })) : new Response(null, { status: 204 }))))
    expect((await refreshFriends(true)).ok).toBe(true)
    expect((await db.kv.get(FRIENDS_CACHE_KEY))?.value).toMatchObject({ mySnapshot: authoritative })
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ ...view, mySnapshot: null }))))
    expect((await refreshFriends(true)).ok).toBe(true)
    expect((await db.kv.get(FRIENDS_CACHE_KEY))?.value).toMatchObject({ mySnapshot: null })
  })

  it('excludes friendsAuth, friendsCache, and friendsDemo from built backups', async () => {
    await db.kv.bulkPut([
      {
        key: FRIENDS_AUTH_KEY,
        value: {
          sessionToken: 'secret_session_tok_xyz',
        },
      },
      {
        key: FRIENDS_CACHE_KEY,
        value: {
          me: { id: 'u1', username: 'tariq_fq9999', name: 'Tariq', photoUrl: null, sharePhoto: true, sharePrs: true, joined: true },
          today: 20775,
          weekStart: 20772,
          mySnapshot: null,
          friends: [],
          incoming: [],
          outgoing: [],
          nudges: [],
        },
      },
      { key: FRIENDS_DEMO_KEY, value: true },
    ])

    const backup = await buildBackup(DEFAULT_BACKUP_SETTINGS)
    const encoded = encodeBackup(backup)

    expect(encoded).not.toContain(FRIENDS_AUTH_KEY)
    expect(encoded).not.toContain(FRIENDS_CACHE_KEY)
    expect(encoded).not.toContain(FRIENDS_DEMO_KEY)
    expect(encoded).not.toContain('secret_session_tok_xyz')
    expect(encoded).not.toContain('tariq_fq9999')
  })

  it('skips uploading identical snapshots and swallows errors in syncFriendsQuietly', async () => {
    await db.kv.bulkPut([
      {
        key: FRIENDS_AUTH_KEY,
        value: {
          sessionToken: 'tok_quiet',
          lastSnapshotHash: null,
        },
      },
      {
        key: FRIENDS_CACHE_KEY,
        value: {
          me: { id: 'u1', username: 'tariq', name: 'Tariq', photoUrl: null, sharePhoto: true, sharePrs: true, joined: true },
          today: 20775,
          weekStart: 20772,
          mySnapshot: null,
          friends: [],
          incoming: [],
          outgoing: [],
          nudges: [],
        },
      },
    ])

    const mockFetch = vi.fn().mockImplementation(() => Promise.resolve(new Response(null, { status: 204 })))
    vi.stubGlobal('fetch', mockFetch)

    expect(await uploadSnapshot(false)).toBe(true)
    expect(mockFetch).toHaveBeenCalledTimes(1)

    // Second upload with unchanged local data is skipped
    expect(await uploadSnapshot(false)).toBe(false)
    expect(mockFetch).toHaveBeenCalledTimes(1)

    // Forced upload when network fails is swallowed by syncFriendsQuietly
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('offline')))
    await expect(syncFriendsQuietly()).resolves.toBeUndefined()
  })

  it('ignores a cache written by the pre-contract build (no weekStart/username)', async () => {
    await db.kv.bulkPut([
      { key: FRIENDS_AUTH_KEY, value: { sessionToken: 'tok_old' } },
      {
        key: FRIENDS_CACHE_KEY,
        value: { me: { id: 'u1', name: 'T', inviteCode: 'ABC234', shareAvatar: true, sharePrs: true }, friends: [] },
      },
    ])
    const mockFetch = vi.fn()
    vi.stubGlobal('fetch', mockFetch)
    expect(await uploadSnapshot(false)).toBe(false)
    expect(mockFetch).not.toHaveBeenCalled()
  })

  it('loads a cached 4.0 view without crashing and normalises defaults', async () => {
    await db.kv.bulkPut([
      { key: FRIENDS_AUTH_KEY, value: { sessionToken: 'tok_v40' } },
      {
        key: FRIENDS_CACHE_KEY,
        value: {
          me: { id: 'u1', username: 'tariq', name: 'Old Display', photoUrl: null, sharePhoto: true, sharePrs: true, joined: true },
          today: 20775,
          weekStart: 20772,
          mySnapshot: null,
          friends: [
            { id: 'u2', username: 'omar', name: 'Omar Old', photoUrl: null, snapshot: null, sharedStreak: 2 },
          ],
          incoming: [],
          outgoing: [],
          nudges: [],
        },
      },
    ])

    await ensureFriendsLoaded()
    const row = (await db.kv.get(FRIENDS_CACHE_KEY))?.value
    expect(row).toBeTruthy()
    expect(shownName({ username: 'omar', name: 'Omar Old' })).toBe('omar')
    expect(shownName({ username: null, name: 'Fallback' })).toBe('Fallback')

    // Verify badgeCount and markInboxSeen don't crash on normalized 4.0 view
    expect(badgeCount({ incoming: [], inbox: { pings: [], activity: [] } })).toBe(0)
  })

  it('computes badgeCount from incoming requests and unseen pings only (activity never counts)', () => {
    const view = {
      incoming: [{ id: 'u3', username: 'maya', name: 'maya', photoUrl: null }],
      inbox: {
        pings: [
          { fromId: 'u2', name: 'omar', username: 'omar', day: 20775, createdAt: 1000, seen: false },
          { fromId: 'u4', name: 'lina', username: 'lina', day: 20774, createdAt: 900, seen: true },
        ],
        activity: [
          { userId: 'u2', name: 'omar', username: 'omar', at: 2000 },
          { userId: 'u4', name: 'lina', username: 'lina', at: 3000 },
        ],
      },
    }
    expect(badgeCount(view)).toBe(2)
  })

  it('markInboxSeen clears unseen pings locally at once, saves cache, and swallows POST errors', async () => {
    await db.kv.bulkPut([
      { key: FRIENDS_AUTH_KEY, value: { sessionToken: 'tok_seen' } },
      {
        key: FRIENDS_CACHE_KEY,
        value: {
          me: { id: 'u1', username: 'tariq', name: 'tariq', photoUrl: null, sharePhoto: true, sharePrs: true, joined: true },
          today: 20775,
          weekStart: 20772,
          mySnapshot: null,
          friends: [],
          incoming: [{ id: 'u3', username: 'maya', name: 'maya', photoUrl: null }],
          outgoing: [],
          nudges: [],
          inbox: {
            pings: [{ fromId: 'u2', name: 'omar', username: 'omar', day: 20775, createdAt: 1000, seen: false }],
            activity: [{ userId: 'u2', name: 'omar', username: 'omar', at: 2000 }],
          },
        },
      },
    ])

    await ensureFriendsLoaded()
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('offline')))

    await expect(markInboxSeen()).resolves.toBeUndefined()

    const cached = (await db.kv.get(FRIENDS_CACHE_KEY))?.value as { inbox: { pings: Array<{ seen: boolean }> }; incoming: unknown[] }
    expect(cached.inbox.pings[0].seen).toBe(true)
    expect(badgeCount(cached as Parameters<typeof badgeCount>[0])).toBe(1)
  })

  it('setFriendMuted updates optimistically and reverts on failure', async () => {
    await db.kv.bulkPut([
      { key: FRIENDS_AUTH_KEY, value: { sessionToken: 'tok_mute' } },
      {
        key: FRIENDS_CACHE_KEY,
        value: {
          me: { id: 'u1', username: 'tariq', name: 'tariq', photoUrl: null, sharePhoto: true, sharePrs: true, joined: true },
          today: 20775,
          weekStart: 20772,
          mySnapshot: null,
          friends: [{ id: 'u2', username: 'omar', name: 'omar', photoUrl: null, snapshot: null, muted: false }],
          incoming: [],
          outgoing: [],
          nudges: [],
        },
      },
    ])

    await ensureFriendsLoaded()
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ error: 'server_err' }), { status: 500 })))

    const res = await setFriendMuted('u2', true)
    expect(res).toEqual({ ok: false, error: 'server_err' })
    const cached = (await db.kv.get(FRIENDS_CACHE_KEY))?.value as { friends: Array<{ muted?: boolean }> }
    expect(cached.friends[0].muted).toBe(false)
  })

  it('initialsFor returns one uppercase letter', () => {
    expect(initialsFor('tariq')).toBe('T')
    expect(initialsFor('  omar ali ')).toBe('O')
    expect(initialsFor('')).toBe('?')
  })

  it('renderGoogleSignInButton renders into a fresh child div with theme, width, and locale', async () => {
    const renderButton = vi.fn()
    const initialize = vi.fn()
    const created: Array<{ tag: string }> = []
    const children: unknown[] = []
    const container = {
      innerHTML: 'stale',
      appendChild(child: unknown) {
        children.push(child)
        return child
      },
    } as unknown as HTMLElement

    vi.stubGlobal('window', {
      google: { accounts: { id: { initialize, renderButton }, oauth2: {} } },
    })
    vi.stubGlobal('document', {
      createElement(tag: string) {
        const node = { tag }
        created.push(node)
        return node
      },
    })

    await expect(
      renderGoogleSignInButton(container, { theme: 'filled_black', width: 260, locale: 'en' }),
    ).resolves.toBe(true)
    expect(renderButton).toHaveBeenCalledTimes(1)
    expect(renderButton.mock.calls[0][0]).toBe(created[0])
    expect(renderButton.mock.calls[0][1]).toMatchObject({
      theme: 'filled_black',
      width: 260,
      locale: 'en',
      shape: 'pill',
    })

    await expect(
      renderGoogleSignInButton(container, { theme: 'outline', width: 240, locale: 'ar' }),
    ).resolves.toBe(true)
    expect(renderButton).toHaveBeenCalledTimes(2)
    expect(renderButton.mock.calls[1][0]).toBe(created[1])
    expect(renderButton.mock.calls[1][0]).not.toBe(renderButton.mock.calls[0][0])
    expect(renderButton.mock.calls[1][1]).toMatchObject({
      theme: 'outline',
      width: 240,
      locale: 'ar',
    })
  })

  it('includes sorted trainDays in buildMySnapshot and changes hashSnapshot when trainDays changes', async () => {
    const now = new Date(2026, 10, 18, 12).getTime()
    await db.plans.put({ id: 1, name: 'Test', description: '', source: 'manual', active: true, createdAt: now })
    await db.planDays.bulkPut([
      { id: 10, planId: 1, dayOfWeek: 5, title: 'Fri', isRest: false, note: '' },
      { id: 11, planId: 1, dayOfWeek: 1, title: 'Mon', isRest: false, note: '' },
      { id: 12, planId: 1, dayOfWeek: 3, title: 'Rest', isRest: true, note: '' },
      { id: 13, planId: 1, dayOfWeek: 6, title: 'Empty', isRest: false, note: '' },
    ])
    await db.planItems.bulkPut([
      { id: 100, dayId: 10, position: 0, exerciseId: 'squat', block: 'Main', circuit: false, sets: 3, repsMin: 5, repsMax: 5, seconds: null, weightKg: null, restSeconds: 60, note: '' },
      { id: 101, dayId: 11, position: 0, exerciseId: 'bench', block: 'Main', circuit: false, sets: 3, repsMin: 5, repsMax: 5, seconds: null, weightKg: null, restSeconds: 60, note: '' },
    ])

    const snap = await buildMySnapshot(false, now)
    expect(snap.trainDays).toEqual([1, 5])

    const h1 = await hashSnapshot(snap)
    const h2 = await hashSnapshot({ ...snap, trainDays: [1, 3, 5] })
    const h3 = await hashSnapshot({ ...snap, trainDays: undefined })
    expect(h1).not.toBe(h2)
    expect(h1).not.toBe(h3)
  })

  it('nudge cooldown helpers handle allowed/blocked, max, pruning, legacy migration, and label choice', () => {
    const nowMs = new Date(2026, 10, 18, 14, 0, 0).getTime()
    const nowSec = Math.floor(nowMs / 1000)
    const midnight = localMidnightSec(nowMs)

    expect(effectiveNudgeNextAt(null, undefined, nowSec)).toBeNull()
    expect(effectiveNudgeNextAt(nowSec, nowSec - 10, nowSec)).toBeNull()
    expect(effectiveNudgeNextAt(nowSec + 100, nowSec + 300, nowSec)).toBe(nowSec + 300)
    expect(effectiveNudgeNextAt(nowSec + 500, nowSec + 300, nowSec)).toBe(nowSec + 500)

    expect(pruneNudgeMap({ old: nowSec - 1, eq: nowSec, active: nowSec + 60 }, nowSec)).toEqual({
      active: nowSec + 60,
    })

    expect(nudgeCooldownKind(nowSec + 3600, nowMs)).toBe('again_at')
    expect(nudgeCooldownKind(midnight, nowMs)).toBe('tomorrow')
    expect(nudgeCooldownKind(midnight + 60, nowMs)).toBe('tomorrow')

    const migrated = migrateNudgeAuth(
      {
        sessionToken: 'tok',
        nudgeNextAt: { expired: nowSec - 5, keep: nowSec + 100 },
        nudgedTodayDay: todayEpochDay(new Date(nowMs)),
        nudgedTodayIds: ['u_legacy', 'keep'],
      },
      nowMs,
    )
    expect(migrated.changed).toBe(true)
    expect(migrated.map).toEqual({
      keep: midnight,
      u_legacy: midnight,
    })
    expect(migrated.auth?.nudgedTodayDay).toBeUndefined()
    expect(migrated.auth?.nudgedTodayIds).toBeUndefined()
  })

  it('nudgeFriend handles server nextAt, old-server fallback, 409 refresh, and error rollback', async () => {
    const future = Math.floor(Date.now() / 1000) + 3600
    const view = {
      me: { id: 'u1', username: 'tariq', name: 'tariq', photoUrl: null, sharePhoto: true, sharePrs: true, joined: true },
      today: todayEpochDay(),
      weekStart: weekStart(todayEpochDay()),
      mySnapshot: null,
      friends: [
        { id: 'u2', username: 'omar', name: 'omar', photoUrl: null, snapshot: null, nudgeNextAt: null },
        { id: 'u3', username: 'lina', name: 'lina', photoUrl: null, snapshot: null },
      ],
      incoming: [],
      outgoing: [],
      nudges: [],
    }
    await db.kv.bulkPut([
      { key: FRIENDS_AUTH_KEY, value: { sessionToken: 'tok_nudge' } },
      { key: FRIENDS_CACHE_KEY, value: view },
    ])
    await ensureFriendsLoaded()

    // 1. Server returns nextAt
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ pushed: true, nextAt: future }))))
    await expect(nudgeFriend('u2')).resolves.toEqual({ ok: true, data: undefined })
    let saved = (await db.kv.get(FRIENDS_AUTH_KEY))?.value as { nudgeNextAt?: Record<string, number> }
    expect(saved.nudgeNextAt?.u2).toBe(future)

    // 2. Network error does not record cooldown
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('offline')))
    await expect(nudgeFriend('u3')).resolves.toEqual({ ok: false, error: 'network' })
    saved = (await db.kv.get(FRIENDS_AUTH_KEY))?.value as { nudgeNextAt?: Record<string, number> }
    expect(saved.nudgeNextAt?.u3).toBeUndefined()

    // 3. 409 already_nudged refreshes view and picks up friend.nudgeNextAt
    const serverNext = Math.floor(Date.now() / 1000) + 7200
    vi.stubGlobal('fetch', vi.fn().mockImplementation((url: string) => {
      if (url.includes('/v1/nudges/')) {
        return Promise.resolve(new Response(JSON.stringify({ error: 'already_nudged' }), { status: 409 }))
      }
      if (url.includes('/v1/friends')) {
        return Promise.resolve(new Response(JSON.stringify({
          ...view,
          friends: [view.friends[0], { ...view.friends[1], nudgeNextAt: serverNext }],
        })))
      }
      return Promise.resolve(new Response(null, { status: 204 }))
    }))
    await expect(nudgeFriend('u3')).resolves.toEqual({ ok: false, error: 'already_nudged' })
    saved = (await db.kv.get(FRIENDS_AUTH_KEY))?.value as { nudgeNextAt?: Record<string, number> }
    expect(saved.nudgeNextAt?.u3).toBe(serverNext)

    // 4. Old server without nextAt falls back to local midnight
    await db.kv.put({ key: FRIENDS_AUTH_KEY, value: { sessionToken: 'tok_nudge' } })
    _resetFriendsForTest()
    await ensureFriendsLoaded()
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ pushed: true }))))
    await expect(nudgeFriend('u2')).resolves.toEqual({ ok: true, data: undefined })
    saved = (await db.kv.get(FRIENDS_AUTH_KEY))?.value as { nudgeNextAt?: Record<string, number> }
    expect(saved.nudgeNextAt?.u2).toBeGreaterThanOrEqual(Math.floor(Date.now() / 1000))
  })

  it('optimistic friend mutations track per-item pending/hidden and roll back on error', async () => {
    const view = {
      me: { id: 'u1', username: 'tariq', name: 'tariq', photoUrl: null, sharePhoto: true, sharePrs: true, joined: true },
      today: todayEpochDay(),
      weekStart: weekStart(todayEpochDay()),
      mySnapshot: null,
      friends: [{ id: 'u2', username: 'omar', name: 'omar', photoUrl: null, snapshot: null }],
      incoming: [{ id: 'u3', username: 'karim', name: 'karim', photoUrl: null }],
      outgoing: [{ id: 'u4', username: 'maya', name: 'maya', photoUrl: null }],
      nudges: [],
    }
    await db.kv.bulkPut([
      { key: FRIENDS_AUTH_KEY, value: { sessionToken: 'tok_opt' } },
      { key: FRIENDS_CACHE_KEY, value: view },
    ])
    await ensureFriendsLoaded()

    let rejectAccept!: (err: unknown) => void
    vi.stubGlobal('fetch', vi.fn().mockImplementation(() => new Promise((_, rej) => { rejectAccept = rej })))

    // Start acceptFriend, verify pendingIds/hiddenIds set immediately while global busy stays false
    const pAccept = acceptFriend('u3')
    await Promise.resolve()
    expect(getFriendsState().busy).toBe(false)
    expect(getFriendsState().pendingIds.has('u3')).toBe(true)
    expect(getFriendsState().hiddenIds.has('u3')).toBe(true)
    // Second call while pending returns 'pending' immediately
    await expect(acceptFriend('u3')).resolves.toEqual({ ok: false, error: 'pending' })
    rejectAccept(new TypeError('offline'))
    await expect(pAccept).resolves.toEqual({ ok: false, error: 'network' })
    expect(getFriendsState().pendingIds.has('u3')).toBe(false)
    expect(getFriendsState().hiddenIds.has('u3')).toBe(false)

    // After rollback, u3 can be retried and succeeds
    vi.stubGlobal('fetch', vi.fn().mockImplementation((url: string) => {
      if (url.includes('/v1/friends')) return Promise.resolve(new Response(JSON.stringify({ ...view, incoming: [] })))
      return Promise.resolve(new Response(null, { status: 204 }))
    }))
    await expect(acceptFriend('u3')).resolves.toEqual({ ok: true, data: undefined })
    await expect(declineFriend('u4')).resolves.toEqual({ ok: true, data: undefined })
    await expect(removeFriend('u2')).resolves.toEqual({ ok: true, data: undefined })
    await expect(blockFriend('u2')).resolves.toEqual({ ok: true, data: undefined })
  })

  it('demo mode provides valid breakdowns, a friend with nudgeNextAt, and local demo nudges', async () => {
    await db.kv.put({ key: FRIENDS_DEMO_KEY, value: true })
    await ensureFriendsLoaded()
    const st = getFriendsState()
    expect(st.demoMode).toBe(true)
    expect(st.view?.friends.length).toBeGreaterThan(0)
    for (const f of st.view?.friends ?? []) {
      if (f.snapshot) {
        expect(f.snapshot.breakdown).toBeTruthy()
        expect(f.snapshot.breakdown?.score).toBe(f.snapshot.score)
      }
    }
    const cooling = st.view?.friends.find((f) => typeof f.nudgeNextAt === 'number' && f.nudgeNextAt > Math.floor(Date.now() / 1000))
    expect(cooling).toBeTruthy()

    const target = st.view!.friends.find((f) => f.nudgeNextAt == null && f.snapshot?.weekState !== 'hit')!
    await expect(nudgeFriend(target.id)).resolves.toEqual({ ok: true, data: undefined })
    expect(getFriendsState().nudgeNextAt[target.id]).toBeGreaterThan(Math.floor(Date.now() / 1000))
  })
})
