import { dayOfWeek, weekStart } from './dates'

export type ForgeBadge = 'trained' | 'on_track' | 'slacking' | 'rest'
export type FriendBadge = ForgeBadge | 'not_synced'

export interface ForgeWeek {
  weekStart: number
  done: number
  due: number
  score: number
  badge: ForgeBadge
  proteinDays: number
}

/** Public profile of a friend or invite counterpart (contract v2 `Person`). */
export interface Person {
  id: string
  username: string | null
  name: string
  photoUrl: string | null
}

export interface Pr {
  exercise: string
  kg: number
  reps: number
}

/** Weekly snapshot exactly as PUT /v1/snapshot takes it (plus updatedAt on reads). */
export interface Snapshot {
  weekStart: number
  done: number
  due: number
  score: number
  badge: ForgeBadge
  streakWeeks: number
  proteinDays: number
  prs: Pr[] | null
  /** v3: this week's state ("open" while in progress). On reads: the friend's current-week state or null. */
  weekState?: 'hit' | 'free' | 'open' | null
  /** v3, upload only: last week's final state; omitted for a brand-new user. */
  lastWeekState?: 'hit' | 'free' | 'miss' | null
  updatedAt?: number
}

/** sharedStreak (v3): consecutive weeks you and this friend both hit, computed by the server. */
export type Friend = Person & { snapshot: Snapshot | null; sharedStreak?: number; muted?: boolean }

export interface LeaderboardEntry {
  person: Person
  isMe: boolean
  score: number
  badge: FriendBadge
  streakWeeks: number
  proteinDays: number
  prs: Pr[]
  syncedThisWeek: boolean
  /** Workout days this week (0 when not synced). */
  done: number
  /** This week's state for the shared-streak line; null when unknown. */
  weekState: 'hit' | 'free' | 'open' | null
  /** Weeks you and this friend both hit (0 for me). */
  sharedStreak: number
}

const RESERVED_USERNAMES = new Set([
  'admin', 'administrator', 'root', 'system', 'support', 'help', 'forgequest', 'forge', 'official',
  'staff', 'moderator', 'mod', 'team', 'null', 'undefined', 'me', 'you', 'friends', 'api', 'www',
])

/**
 * Client copy of the Worker's normalizeUsername (worker/src/validate.ts): trim, drop one leading "@", ASCII check
 * BEFORE lowercasing, no edge/double dots, not reserved. Returns the lowercase name or null.
 */
export function normalizeUsername(raw: string): string | null {
  let v = raw.trim()
  if (v.startsWith('@')) v = v.slice(1)
  if (!/^[A-Za-z0-9_.]{3,20}$/.test(v)) return null
  v = v.toLowerCase()
  if (v.startsWith('.') || v.endsWith('.') || v.includes('..')) return null
  if (RESERVED_USERNAMES.has(v) || v.startsWith('forgequest')) return null
  return v
}

/**
 * Computes the user's weekly Forge Score from a Sunday-first week boundary.
 * Mirrors ForgeScore.kt and shared forge-score.vectors.json.
 */
export function computeForgeWeek(
  today: number,
  scheduledDays: Set<number>,
  trainedDays: Set<number>,
  proteinDays: Set<number> = new Set(),
): ForgeWeek {
  const ws = weekStart(today)
  const trainedToday = trainedDays.has(today)

  let done = 0
  let dueBeforeToday = 0
  let protein = 0

  for (let d = ws; d <= today; d++) {
    if (trainedDays.has(d)) done++
    if (proteinDays.has(d)) protein++
    if (d < today && scheduledDays.has(dayOfWeek(d))) dueBeforeToday++
  }

  const due = Math.max(dueBeforeToday + (trainedToday ? 1 : 0), done)
  const score = due === 0 ? 100 : Math.round((100 * done) / due)

  let badge: ForgeBadge
  if (trainedToday) {
    badge = 'trained'
  } else if (!scheduledDays.has(dayOfWeek(today))) {
    badge = 'rest'
  } else if (score >= 60) {
    badge = 'on_track'
  } else {
    badge = 'slacking'
  }

  return {
    weekStart: ws,
    done,
    due,
    score,
    badge,
    proteinDays: protein,
  }
}

function toLeaderboardEntry(
  person: Person,
  isMe: boolean,
  snapshot: Snapshot | null,
  serverWeekStart: number,
  sharedStreak = 0,
): LeaderboardEntry {
  // Contract: a snapshot from an older week shows as neutral "not synced this week", score 0.
  if (snapshot === null || snapshot.weekStart < serverWeekStart) {
    return { person, isMe, score: 0, badge: 'not_synced', streakWeeks: 0, proteinDays: 0, prs: [], syncedThisWeek: false, done: 0, weekState: null, sharedStreak }
  }
  return {
    person,
    isMe,
    score: snapshot.score,
    badge: snapshot.badge,
    streakWeeks: snapshot.streakWeeks,
    proteinDays: snapshot.proteinDays,
    prs: snapshot.prs ?? [],
    syncedThisWeek: true,
    done: snapshot.done,
    weekState: snapshot.weekState ?? null,
    sharedStreak,
  }
}

/**
 * Builds the weekly leaderboard from me + friends (server weekStart from GET /friends). Sorted by score desc,
 * then name asc; the user is included and flagged isMe.
 */
export function buildLeaderboard(
  serverWeekStart: number,
  me: Person | null,
  mySnapshot: Snapshot | null,
  friends: Friend[],
): LeaderboardEntry[] {
  const entries: LeaderboardEntry[] = []
  if (me) entries.push(toLeaderboardEntry(me, true, mySnapshot, serverWeekStart))
  for (const f of friends) {
    const person: Person = { id: f.id, username: f.username, name: f.name, photoUrl: f.photoUrl }
    entries.push(toLeaderboardEntry(person, false, f.snapshot, serverWeekStart, f.sharedStreak ?? 0))
  }
  entries.sort((a, b) => {
    if (b.score !== a.score) return b.score - a.score
    const an = a.person.name.toLowerCase()
    const bn = b.person.name.toLowerCase()
    return an < bn ? -1 : an > bn ? 1 : 0
  })
  return entries
}
