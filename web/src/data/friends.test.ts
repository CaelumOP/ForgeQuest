import 'fake-indexeddb/auto'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { buildBackup, DEFAULT_BACKUP_SETTINGS, encodeBackup } from './backup'
import { db } from './db'
import {
  _resetFriendsForTest,
  FRIENDS_AUTH_KEY,
  FRIENDS_CACHE_KEY,
  FRIENDS_DEMO_KEY,
  syncFriendsQuietly,
  uploadSnapshot,
} from './friends'

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
          suggestedName: 'Tariq',
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
          suggestedName: 'Tariq',
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
})
