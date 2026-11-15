import { FRIENDS_BASE } from '../config'
import type { Friend, Person, Snapshot } from '../domain/forge-score'

export type { Friend, Person, Pr, Snapshot } from '../domain/forge-score'

// Shapes follow the Friends contract v2 (.claude/scratch/v4/friends-contract.md) and Android FriendsModels.kt.

export interface Me {
  id: string
  username: string | null
  name: string
  photoUrl: string | null
  sharePhoto: boolean
  sharePrs: boolean
  joined: boolean
}

export interface Nudge {
  fromId: string
  name: string
  username: string | null
  day: number
}

export interface FriendsView {
  me: Me
  today: number
  weekStart: number
  friends: Friend[]
  incoming: Person[]
  outgoing: Person[]
  nudges: Nudge[]
}

export interface AuthResponse {
  session: string
  user: Me
}

export interface InviteResult {
  status: 'sent' | 'accepted'
}

export interface NudgeResult {
  pushed: boolean
}

export interface ProfileInput {
  name: string
  sharePhoto: boolean
  sharePrs: boolean
  /** Required on first join; omit to keep the current one. */
  username?: string
}

export class FriendsError extends Error {
  readonly code: string
  readonly status: number

  constructor(code: string, status = 0) {
    super(code)
    this.name = 'FriendsError'
    this.code = code
    this.status = status
  }
}

async function request<T>(
  path: string,
  options: { method: string; token?: string; body?: unknown },
): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (options.token) {
    headers.Authorization = `Bearer ${options.token}`
  }
  let rawBody: string | undefined
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json'
    rawBody = JSON.stringify(options.body)
  }

  let res: Response
  try {
    res = await fetch(`${FRIENDS_BASE}${path}`, {
      method: options.method,
      headers,
      body: rawBody,
    })
  } catch {
    throw new FriendsError('network', 0)
  }

  const text = await res.text()
  let parsed: unknown = undefined
  if (text.length > 0) {
    try {
      parsed = JSON.parse(text)
    } catch {
      parsed = undefined
    }
  }

  if (!res.ok) {
    const errObj = parsed && typeof parsed === 'object' ? (parsed as { error?: unknown }) : null
    const code =
      errObj && typeof errObj.error === 'string' && errObj.error.length > 0
        ? errObj.error
        : `http_${res.status}`
    throw new FriendsError(code, res.status)
  }

  return parsed as T
}

const enc = encodeURIComponent

export const friendsApi = {
  authGoogle: (idToken: string) =>
    request<AuthResponse>('/v1/auth/google', { method: 'POST', body: { idToken } }),

  getMe: (token: string) => request<Me>('/v1/me', { method: 'GET', token }),

  putMe: (token: string, input: ProfileInput) =>
    request<Me>('/v1/me', { method: 'PUT', token, body: input }),

  deleteMe: (token: string) => request<void>('/v1/me', { method: 'DELETE', token }),

  putSnapshot: (token: string, s: Snapshot) =>
    request<void>('/v1/snapshot', {
      method: 'PUT',
      token,
      body: {
        weekStart: s.weekStart,
        done: s.done,
        due: s.due,
        score: s.score,
        badge: s.badge,
        streakWeeks: s.streakWeeks,
        proteinDays: s.proteinDays,
        prs: s.prs,
      },
    }),

  getFriends: (token: string) => request<FriendsView>('/v1/friends', { method: 'GET', token }),

  invite: (token: string, username: string) =>
    request<InviteResult>('/v1/invites', { method: 'POST', token, body: { username } }),

  accept: (token: string, fromId: string) =>
    request<void>(`/v1/invites/${enc(fromId)}/accept`, { method: 'POST', token }),

  decline: (token: string, id: string) =>
    request<void>(`/v1/invites/${enc(id)}/decline`, { method: 'POST', token }),

  remove: (token: string, id: string) =>
    request<void>(`/v1/friends/${enc(id)}`, { method: 'DELETE', token }),

  block: (token: string, id: string) => request<void>(`/v1/blocks/${enc(id)}`, { method: 'POST', token }),

  unblock: (token: string, id: string) => request<void>(`/v1/blocks/${enc(id)}`, { method: 'DELETE', token }),

  nudge: (token: string, id: string) =>
    request<NudgeResult>(`/v1/nudges/${enc(id)}`, { method: 'POST', token }),
}
