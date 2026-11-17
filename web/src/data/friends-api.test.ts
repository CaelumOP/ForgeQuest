import { afterEach, describe, expect, it, vi } from 'vitest'
import { FRIENDS_BASE } from '../config'
import { FriendsError, friendsApi } from './friends-api'

// A Response body can be read once, so every mock builds a fresh one per call.
const json = (body: unknown, status = 200) => () =>
  Promise.resolve(new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } }))
const empty = (status = 200) => () => Promise.resolve(new Response(null, { status }))

describe('friendsApi (contract v2 routes)', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('POST /v1/auth/google without Bearer, returns {session, user}', async () => {
    const user = { id: 'u1', username: null, name: 'Tariq', photoUrl: null, sharePhoto: true, sharePrs: true, joined: false }
    const mockFetch = vi.fn().mockImplementation(json({ session: 'tok_1', user }))
    vi.stubGlobal('fetch', mockFetch)

    const out = await friendsApi.authGoogle('id_tok')
    expect(out.session).toBe('tok_1')
    expect(out.user.joined).toBe(false)

    const [url, init] = mockFetch.mock.calls[0]
    expect(url).toBe(`${FRIENDS_BASE}/v1/auth/google`)
    expect(init.method).toBe('POST')
    expect(init.headers.Authorization).toBeUndefined()
    expect(init.body).toBe(JSON.stringify({ idToken: 'id_tok' }))
  })

  it('PUT /v1/me sends the full profile with username and optional alert flags', async () => {
    const mockFetch = vi.fn().mockImplementation(json({}))
    vi.stubGlobal('fetch', mockFetch)
    await friendsApi.putMe('s', { sharePhoto: false, sharePrs: true, username: 'tariq', alertsOn: false })
    const [url, init] = mockFetch.mock.calls[0]
    expect(url).toBe(`${FRIENDS_BASE}/v1/me`)
    expect(init.method).toBe('PUT')
    expect(JSON.parse(init.body)).toEqual({ sharePhoto: false, sharePrs: true, username: 'tariq', alertsOn: false })
  })

  it('PUT /v1/snapshot sends contract fields only (no updatedAt)', async () => {
    const mockFetch = vi.fn().mockImplementation(empty(200))
    vi.stubGlobal('fetch', mockFetch)

    await friendsApi.putSnapshot('sess_1', {
      weekStart: 20772,
      done: 2,
      due: 3,
      score: 67,
      badge: 'on_track',
      streakWeeks: 2,
      proteinDays: 3,
      prs: [{ exercise: 'Squat', kg: 100, reps: 5 }],
      updatedAt: 999999,
    })

    const [url, init] = mockFetch.mock.calls[0]
    expect(url).toBe(`${FRIENDS_BASE}/v1/snapshot`)
    expect(init.method).toBe('PUT')
    expect(init.headers.Authorization).toBe('Bearer sess_1')
    expect(JSON.parse(init.body)).toEqual({
      weekStart: 20772,
      done: 2,
      due: 3,
      score: 67,
      badge: 'on_track',
      streakWeeks: 2,
      proteinDays: 3,
      prs: [{ exercise: 'Squat', kg: 100, reps: 5 }],
    })
  })

  it('PUT /v1/snapshot sends v3 week states only when present', async () => {
    const mockFetch = vi.fn().mockImplementation(empty(200))
    vi.stubGlobal('fetch', mockFetch)
    const base = { weekStart: 20772, done: 3, due: 3, score: 100, badge: 'trained' as const, streakWeeks: 4, proteinDays: 0, prs: null }
    await friendsApi.putSnapshot('s', { ...base, weekState: 'hit', lastWeekState: 'miss' })
    await friendsApi.putSnapshot('s', { ...base, weekState: 'open' })
    expect(JSON.parse(mockFetch.mock.calls[0][1].body)).toEqual({ ...base, weekState: 'hit', lastWeekState: 'miss' })
    expect(JSON.parse(mockFetch.mock.calls[1][1].body)).toEqual({ ...base, weekState: 'open' })
  })

  it('PUT /v1/snapshot sends target/prCount/reachedAt only when all three are numbers, and sends trainDays when present', async () => {
    const mockFetch = vi.fn().mockImplementation(empty(200))
    vi.stubGlobal('fetch', mockFetch)
    const base = { weekStart: 20772, done: 2, due: 3, score: 77, badge: 'on_track' as const, streakWeeks: 1, proteinDays: 1, prs: null }
    await friendsApi.putSnapshot('s', {
      ...base,
      target: 3,
      prCount: 2,
      reachedAt: 1794700000,
      trainDays: [1, 3, 5],
    })
    await friendsApi.putSnapshot('s', {
      ...base,
      target: 3,
      prCount: 2,
    })

    expect(JSON.parse(mockFetch.mock.calls[0][1].body)).toEqual({
      ...base,
      target: 3,
      prCount: 2,
      reachedAt: 1794700000,
      trainDays: [1, 3, 5],
    })
    expect(JSON.parse(mockFetch.mock.calls[1][1].body)).toEqual(base)
  })

  it('POST /v1/nudges/{id} returns { pushed, nextAt }', async () => {
    vi.stubGlobal('fetch', vi.fn().mockImplementation(json({ pushed: true, nextAt: 1794714400 })))
    await expect(friendsApi.nudge('s', 'u2')).resolves.toEqual({ pushed: true, nextAt: 1794714400 })
  })

  it('POST /v1/invites {username}; error field becomes FriendsError.code', async () => {
    const mockFetch = vi.fn().mockImplementation(json({ error: 'username_not_found' }, 404))
    vi.stubGlobal('fetch', mockFetch)

    await expect(friendsApi.invite('sess_1', 'omar')).rejects.toMatchObject({
      name: 'FriendsError',
      code: 'username_not_found',
      status: 404,
    })
    const [url, init] = mockFetch.mock.calls[0]
    expect(url).toBe(`${FRIENDS_BASE}/v1/invites`)
    expect(JSON.parse(init.body)).toEqual({ username: 'omar' })
  })

  it('maps non-JSON or missing error field to http_<status>', async () => {
    vi.stubGlobal('fetch', vi.fn().mockImplementation(() => Promise.resolve(new Response('Bad Gateway', { status: 502 }))))
    try {
      await friendsApi.getFriends('sess_1')
      expect.fail('Expected FriendsError')
    } catch (err) {
      expect(err).toBeInstanceOf(FriendsError)
      expect((err as FriendsError).code).toBe('http_502')
      expect((err as FriendsError).status).toBe(502)
    }

    vi.stubGlobal('fetch', vi.fn().mockImplementation(json({ message: 'nope' }, 429)))
    await expect(friendsApi.nudge('sess_1', 'u2')).rejects.toMatchObject({ code: 'http_429', status: 429 })
  })

  it('maps fetch rejection to FriendsError("network", 0)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))
    await expect(friendsApi.getMe('sess_1')).rejects.toMatchObject({ name: 'FriendsError', code: 'network', status: 0 })
  })

  it('uses the contract paths and URL-encodes ids', async () => {
    const mockFetch = vi.fn().mockImplementation(json({ pushed: false }))
    vi.stubGlobal('fetch', mockFetch)

    await friendsApi.remove('s', 'a/b?c')
    await friendsApi.block('s', 'a/b')
    await friendsApi.unblock('s', 'x')
    await friendsApi.accept('s', 'f1')
    await friendsApi.decline('s', 'f2')
    await friendsApi.nudge('s', 'n1')
    await friendsApi.getFriends('s')
    await friendsApi.inboxSeen('s')
    await friendsApi.setMuted('s', 'f1', true)
    await friendsApi.setMuted('s', 'f1', false)
    await friendsApi.putPush('s', 'fcm_tok')
    await friendsApi.deletePush('s', 'fcm_tok')
    const calls = mockFetch.mock.calls.map(([u, i]) => `${i.method} ${String(u).slice(FRIENDS_BASE.length)}`)
    expect(calls).toEqual([
      'DELETE /v1/friends/a%2Fb%3Fc',
      'POST /v1/blocks/a%2Fb',
      'DELETE /v1/blocks/x',
      'POST /v1/invites/f1/accept',
      'POST /v1/invites/f2/decline',
      'POST /v1/nudges/n1',
      'GET /v1/friends?v=4',
      'POST /v1/inbox/seen',
      'PUT /v1/mutes/f1',
      'DELETE /v1/mutes/f1',
      'PUT /v1/push',
      'DELETE /v1/push',
    ])
    expect(JSON.parse(mockFetch.mock.calls[10][1].body)).toEqual({ token: 'fcm_tok', platform: 'web' })
    expect(JSON.parse(mockFetch.mock.calls[11][1].body)).toEqual({ token: 'fcm_tok' })
  })
})
