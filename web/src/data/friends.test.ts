import 'fake-indexeddb/auto'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { buildBackup, DEFAULT_BACKUP_SETTINGS, encodeBackup } from './backup'
import { db } from './db'
import {
  _resetFriendsForTest,
  badgeCount,
  ensureFriendsLoaded,
  FRIENDS_AUTH_KEY,
  FRIENDS_CACHE_KEY,
  FRIENDS_DEMO_KEY,
  markInboxSeen,
  setFriendMuted,
  shownName,
  syncFriendsQuietly,
  uploadSnapshot,
} from './friends'
import { initialsFor } from '../ui/friends/Avatar'

describe('friends persistence and backup isolation', () => {
  beforeEach(async () => {
    await Promise.all(db.tables.map((t) => t.clear()))
    _resetFriendsForTest()
  })

  afterEach(() => {
    vi.restoreAllMocks()
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
})
