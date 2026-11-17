import {
  PushPlatform,
  SessionRow,
  SnapshotInput,
  SnapshotRow,
  UserRow,
} from "./types";

import { encodeScore, snapshotScore } from "./score";

export const MAX_PUSH_TOKENS = 5;

export interface WeekStateRow {
  user_id: string;
  week_start: number;
  state: string;
}

export interface FriendsViewData {
  /** friendsSince = friends.created_at (unix seconds). */
  friends: Array<{ user: UserRow; snapshot: SnapshotRow | null; friendsSince: number }>;
  /** week_states rows of the caller and all of their friends (shared streaks). */
  weekStates: WeekStateRow[];
  incoming: UserRow[];
  outgoing: UserRow[];
  nudges: Array<{ fromId: string; name: string; username: string | null; day: number }>;
  /** Friend ids whose workout alerts the caller muted. */
  mutedIds: string[];
  /** Only for v4 reads. */
  inbox?: {
    pings: Array<{ fromId: string; username: string | null; day: number; createdAt: number; seen: boolean }>;
    activity: Array<{ userId: string; username: string | null; at: number }>;
  };
}

export interface FriendsViewOptions {
  /** v4 clients: build the inbox and do NOT mark pings seen. */
  v4: boolean;
  /** Inbox pings from this Riyadh day on (last 7 days). */
  pingsSinceDay: number;
  /** Inbox activity from this unix second on (last 48 h). */
  activitySinceSec: number;
}

/** A workout event to record with a snapshot (decided in SQL against the stored snapshot). */
export interface WorkoutEventInput {
  day: number;
  shareWorkouts: boolean;
}

export interface ActivityTarget {
  userId: string;
  tokens: string[];
}

export interface ProfileUpdate {
  username: string;
  sharePhoto: boolean;
  sharePrs: boolean;
  alertsOn: boolean;
  shareWorkouts: boolean;
  pingsOn: boolean;
}

export type AcceptResult =
  | "accepted"
  | "limit_reached"
  | "blocked"
  | "not_found"
  | "already_friends";

export interface Store {
  getRateCount(key: string, windowStart: number): Promise<number>;
  incrementRate(key: string, windowStart: number): Promise<number>;

  upsertUserBySub(input: {
    sub: string;
    googlePhoto: string | null;
    nowSec: number;
    generateId: () => string;
    generateCode: () => string;
  }): Promise<UserRow>;
  createSession(tokenHash: string, userId: string, expiresAt: number): Promise<void>;
  getSessionWithUser(tokenHash: string): Promise<{ session: SessionRow; user: UserRow } | null>;
  extendSession(tokenHash: string, expiresAt: number): Promise<void>;
  deleteSession(tokenHash: string): Promise<void>;

  updateProfile(userId: string, input: ProfileUpdate): Promise<UserRow | null | "taken">;
  deleteUserCascade(userId: string): Promise<void>;

  /** Returns true when [event] was given and a NEW workout event row was recorded. */
  upsertSnapshot(
    userId: string,
    snapshot: SnapshotInput,
    nowSec: number,
    event?: WorkoutEventInput,
  ): Promise<boolean>;

  getSnapshot(userId: string): Promise<SnapshotRow | null>;
  getFriendsView(userId: string, opts: FriendsViewOptions): Promise<FriendsViewData>;
  markInboxSeen(userId: string): Promise<void>;
  setMute(userId: string, mutedId: string, muted: boolean): Promise<void>;
  getUserById(userId: string): Promise<UserRow | null>;
  /** Friends of [senderId] who want activity pushes from them today and have tokens (max 50). */
  getActivityTargets(senderId: string, day: number): Promise<ActivityTarget[]>;
  /** Takes the one activity push slot for (from, to, day); false when already used. */
  claimActivityPush(fromId: string, toId: string, day: number): Promise<boolean>;
  findJoinedUserByUsername(username: string): Promise<UserRow | null>;
  isBlockedEitherWay(a: string, b: string): Promise<boolean>;
  areFriends(a: string, b: string): Promise<boolean>;
  hasRequest(fromId: string, toId: string): Promise<boolean>;
  countFriends(userId: string): Promise<number>;
  createRequest(fromId: string, toId: string, nowSec: number): Promise<void>;
  acceptFriendship(params: {
    userA: string;
    userB: string;
    nowSec: number;
    cap: number;
  }): Promise<AcceptResult>;
  declineRequestsBetween(a: string, b: string): Promise<void>;
  removeFriendship(a: string, b: string): Promise<void>;
  blockUser(blocker: string, blocked: string): Promise<void>;
  unblockUser(blocker: string, blocked: string): Promise<void>;

  createNudgeIfAbsent(fromId: string, toId: string, day: number, nowSec: number): Promise<boolean>;
  upsertPushToken(token: string, userId: string, platform: PushPlatform, nowSec: number): Promise<void>;
  deletePushTokenForUser(token: string, userId: string): Promise<void>;
  getPushTokens(userId: string): Promise<string[]>;
  deletePushTokens(tokens: string[]): Promise<void>;
}

const USER_COLS =
  "id, sub, code, username, google_photo, share_photo, share_prs, joined, created_at, alerts_on, share_workouts, pings_on";
const U_COLS = USER_COLS.split(", ")
  .map((c) => `u.${c}`)
  .join(", ");

function isUniqueError(err: unknown): boolean {
  return err instanceof Error && err.message.toLowerCase().includes("unique");
}

export class D1Store implements Store {
  constructor(private readonly db: D1Database) {}

  async getRateCount(key: string, windowStart: number): Promise<number> {
    const row = await this.db
      .prepare("SELECT count, window_start FROM rate WHERE key = ?1")
      .bind(key)
      .first<{ count: number; window_start: number }>();
    if (!row || row.window_start !== windowStart) return 0;
    return row.count;
  }

  async incrementRate(key: string, windowStart: number): Promise<number> {
    const row = await this.db
      .prepare(
        `INSERT INTO rate (key, count, window_start) VALUES (?1, 1, ?2)
         ON CONFLICT(key) DO UPDATE SET
           count = CASE WHEN rate.window_start = excluded.window_start THEN rate.count + 1 ELSE 1 END,
           window_start = excluded.window_start
         RETURNING count`,
      )
      .bind(key, windowStart)
      .first<{ count: number }>();
    return row?.count ?? 1;
  }

  async upsertUserBySub(input: {
    sub: string;
    googlePhoto: string | null;
    nowSec: number;
    generateId: () => string;
    generateCode: () => string;
  }): Promise<UserRow> {
    const existing = await this.db
      .prepare(`SELECT ${USER_COLS} FROM users WHERE sub = ?1`)
      .bind(input.sub)
      .first<UserRow>();
    if (existing) {
      if (existing.google_photo !== input.googlePhoto) {
        await this.db
          .prepare("UPDATE users SET google_photo = ?1 WHERE id = ?2")
          .bind(input.googlePhoto, existing.id)
          .run();
        return { ...existing, google_photo: input.googlePhoto };
      }
      return existing;
    }

    for (let attempt = 0; attempt < 8; attempt++) {
      const id = input.generateId();
      const code = input.generateCode();
      try {
        await this.db
          .prepare(
            `INSERT INTO users (id, sub, code, google_photo, share_photo, share_prs, joined, created_at)
             VALUES (?1, ?2, ?3, ?4, 1, 1, 0, ?5)`,
          )
          .bind(id, input.sub, code, input.googlePhoto, input.nowSec)
          .run();
        return {
          id,
          sub: input.sub,
          code,
          username: null,
          google_photo: input.googlePhoto,
          share_photo: 1,
          share_prs: 1,
          joined: 0,
          created_at: input.nowSec,
          alerts_on: 1,
          share_workouts: 1,
          pings_on: 1,
        };
      } catch (err) {
        if (!isUniqueError(err)) throw err;
        const raced = await this.db
          .prepare(`SELECT ${USER_COLS} FROM users WHERE sub = ?1`)
          .bind(input.sub)
          .first<UserRow>();
        if (raced) {
          if (raced.google_photo !== input.googlePhoto) {
            await this.db
              .prepare("UPDATE users SET google_photo = ?1 WHERE id = ?2")
              .bind(input.googlePhoto, raced.id)
              .run();
            return { ...raced, google_photo: input.googlePhoto };
          }
          return raced;
        }
      }
    }
    throw new Error("failed to generate unique user id/code");
  }

  async createSession(tokenHash: string, userId: string, expiresAt: number): Promise<void> {
    await this.db
      .prepare("INSERT INTO sessions (token_hash, user_id, expires_at) VALUES (?1, ?2, ?3)")
      .bind(tokenHash, userId, expiresAt)
      .run();
  }

  async getSessionWithUser(
    tokenHash: string,
  ): Promise<{ session: SessionRow; user: UserRow } | null> {
    const row = await this.db
      .prepare(
        `SELECT s.token_hash AS s_token_hash, s.user_id AS s_user_id, s.expires_at AS s_expires_at, ${U_COLS}
         FROM sessions s
         JOIN users u ON u.id = s.user_id
         WHERE s.token_hash = ?1`,
      )
      .bind(tokenHash)
      .first<UserRow & { s_token_hash: string; s_user_id: string; s_expires_at: number }>();
    if (!row) return null;
    const { s_token_hash, s_user_id, s_expires_at, ...user } = row;
    return {
      session: { token_hash: s_token_hash, user_id: s_user_id, expires_at: s_expires_at },
      user,
    };
  }

  async extendSession(tokenHash: string, expiresAt: number): Promise<void> {
    await this.db
      .prepare("UPDATE sessions SET expires_at = ?1 WHERE token_hash = ?2")
      .bind(expiresAt, tokenHash)
      .run();
  }

  async deleteSession(tokenHash: string): Promise<void> {
    await this.db
      .prepare("DELETE FROM sessions WHERE token_hash = ?1")
      .bind(tokenHash)
      .run();
  }

  async updateProfile(userId: string, input: ProfileUpdate): Promise<UserRow | null | "taken"> {
    const b = (v: boolean) => (v ? 1 : 0);
    const stmts = [
      this.db
        .prepare(
          `UPDATE users SET username = ?1, share_photo = ?2, share_prs = ?3, alerts_on = ?4, share_workouts = ?5,
             pings_on = ?6, joined = 1 WHERE id = ?7`,
        )
        .bind(
          input.username,
          b(input.sharePhoto),
          b(input.sharePrs),
          b(input.alertsOn),
          b(input.shareWorkouts),
          b(input.pingsOn),
          userId,
        ),
    ];
    if (!input.sharePrs) {
      stmts.push(this.db.prepare(`UPDATE snapshots SET prs = CASE WHEN json_valid(prs) AND json_type(prs) = 'object'
        THEN json_set(prs, '$.prs', NULL) ELSE NULL END WHERE user_id = ?1`).bind(userId));
    }
    if (!input.shareWorkouts) {
      stmts.push(this.db.prepare("DELETE FROM workout_events WHERE user_id = ?1").bind(userId));
    }

    try {
      await this.db.batch(stmts);
    } catch (err) {
      if (isUniqueError(err)) return "taken";
      throw err;
    }

    return await this.db
      .prepare(`SELECT ${USER_COLS} FROM users WHERE id = ?1`)
      .bind(userId)
      .first<UserRow>();
  }

  async deleteUserCascade(userId: string): Promise<void> {
    await this.db.batch([
      this.db.prepare("DELETE FROM sessions WHERE user_id = ?1").bind(userId),
      this.db.prepare("DELETE FROM snapshots WHERE user_id = ?1").bind(userId),
      this.db.prepare("DELETE FROM week_states WHERE user_id = ?1").bind(userId),
      this.db
        .prepare("DELETE FROM friends WHERE user_id = ?1 OR friend_id = ?1")
        .bind(userId),
      this.db
        .prepare("DELETE FROM requests WHERE from_id = ?1 OR to_id = ?1")
        .bind(userId),
      this.db
        .prepare("DELETE FROM blocks WHERE blocker = ?1 OR blocked = ?1")
        .bind(userId),
      this.db
        .prepare("DELETE FROM nudges WHERE from_id = ?1 OR to_id = ?1")
        .bind(userId),
      this.db.prepare("DELETE FROM push_tokens WHERE user_id = ?1").bind(userId),
      this.db.prepare("DELETE FROM mutes WHERE user_id = ?1 OR muted_id = ?1").bind(userId),
      this.db.prepare("DELETE FROM workout_events WHERE user_id = ?1").bind(userId),
      this.db.prepare("DELETE FROM activity_push WHERE from_id = ?1 OR to_id = ?1").bind(userId),
      this.db.prepare("DELETE FROM users WHERE id = ?1").bind(userId),
      this.db
        .prepare("DELETE FROM rate WHERE key = ?1 OR key = ?2")
        .bind(`write:${userId}`, `invite_fail:${userId}`),
    ]);
  }

  async upsertSnapshot(
    userId: string,
    s: SnapshotInput,
    nowSec: number,
    event?: WorkoutEventInput,
  ): Promise<boolean> {
    const prsJson = encodeScore(s, nowSec);
    const snapshotStmt = this.db
      .prepare(
        `INSERT INTO snapshots (user_id, week_start, done, due, score, badge, streak_weeks, protein_days, prs, updated_at)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10)
         ON CONFLICT(user_id) DO UPDATE SET
           week_start = excluded.week_start,
           done = excluded.done,
           due = excluded.due,
           score = excluded.score,
           badge = excluded.badge,
           streak_weeks = excluded.streak_weeks,
           protein_days = excluded.protein_days,
           prs = CASE WHEN snapshots.week_start = excluded.week_start AND
             (CASE WHEN json_valid(snapshots.prs) AND json_type(snapshots.prs) = 'object' THEN snapshots.score
               WHEN snapshots.due = 0 THEN 0 ELSE round(100.0 * min(snapshots.done, snapshots.due) / snapshots.due) END) = excluded.score
             THEN json_set(excluded.prs, '$.reachedAt', coalesce(CASE WHEN json_valid(snapshots.prs)
               THEN json_extract(snapshots.prs, '$.reachedAt') END, snapshots.updated_at)) ELSE excluded.prs END,
           updated_at = excluded.updated_at`,
      )
      .bind(
        userId,
        s.weekStart,
        s.done,
        s.due,
        snapshotScore(s).score,
        s.badge,
        s.streakWeeks,
        s.proteinDays,
        prsJson,
        nowSec,
      );
    const weekStmt = (weekStart: number, state: string) =>
      this.db
        .prepare(
          `INSERT INTO week_states (user_id, week_start, state, updated_at) VALUES (?1, ?2, ?3, ?4)
           ON CONFLICT(user_id, week_start) DO UPDATE SET state = excluded.state, updated_at = excluded.updated_at`,
        )
        .bind(userId, weekStart, state, nowSec);
    const stmts: D1PreparedStatement[] = [];
    if (event) {
      // FIRST in the batch: it compares against the snapshot stored BEFORE this upsert. Never on the first snapshot
      // or an older week; (user, day, done_count) is the key, so the same count is never recorded twice.
      stmts.push(
        this.db
          .prepare(
            `INSERT OR IGNORE INTO workout_events (user_id, day, done_count, created_at)
             SELECT ?1, ?2, ?6, ?3 FROM snapshots s
             WHERE s.user_id = ?1 AND ?4 = 1
               AND ((s.week_start = ?5 AND ?6 > s.done) OR (?5 > s.week_start AND ?6 >= 1))`,
          )
          .bind(userId, event.day, nowSec, event.shareWorkouts ? 1 : 0, s.weekStart, s.done),
      );
    }
    stmts.push(snapshotStmt);
    if (s.weekState) stmts.push(weekStmt(s.weekStart, s.weekState));
    if (s.lastWeekState) stmts.push(weekStmt(s.weekStart - 7, s.lastWeekState));
    const res = await this.db.batch(stmts);
    return event !== undefined && (res[0].meta?.changes ?? 0) > 0;
  }

  async getSnapshot(userId: string): Promise<SnapshotRow | null> {
    return this.db.prepare("SELECT * FROM snapshots WHERE user_id = ?1").bind(userId).first<SnapshotRow>();
  }

  async getFriendsView(userId: string, opts: FriendsViewOptions): Promise<FriendsViewData> {
    const results = await this.db.batch([
      this.db
        .prepare(
          `SELECT ${U_COLS},
                  s.week_start AS s_week_start, s.done AS s_done, s.due AS s_due, s.score AS s_score,
                  s.badge AS s_badge, s.streak_weeks AS s_streak_weeks, s.protein_days AS s_protein_days,
                  s.prs AS s_prs, s.updated_at AS s_updated_at, f.created_at AS f_created_at
           FROM friends f
           JOIN users u ON u.id = f.friend_id
           LEFT JOIN snapshots s ON s.user_id = f.friend_id
           WHERE f.user_id = ?1`,
        )
        .bind(userId),
      this.db
        .prepare(
          `SELECT ${U_COLS}
           FROM requests r
           JOIN users u ON u.id = r.from_id
           WHERE r.to_id = ?1
           ORDER BY r.created_at DESC`,
        )
        .bind(userId),
      this.db
        .prepare(
          `SELECT ${U_COLS}
           FROM requests r
           JOIN users u ON u.id = r.to_id
           WHERE r.from_id = ?1
           ORDER BY r.created_at DESC`,
        )
        .bind(userId),
      this.db
        .prepare(
          `SELECT n.from_id, u.username, n.day
           FROM nudges n
           JOIN users u ON u.id = n.from_id
           WHERE n.to_id = ?1 AND n.seen = 0
           ORDER BY n.day DESC, n.created_at DESC`,
        )
        .bind(userId),
      this.db
        .prepare(
          `SELECT user_id, week_start, state FROM week_states
           WHERE user_id = ?1 OR user_id IN (SELECT friend_id FROM friends WHERE user_id = ?1)`,
        )
        .bind(userId),
      this.db.prepare("SELECT muted_id FROM mutes WHERE user_id = ?1").bind(userId),
      // v4 inbox, limited to CURRENT friends.
      this.db
        .prepare(
          `SELECT n.from_id, u.username, n.day, n.created_at, n.seen_v4
           FROM nudges n
           JOIN users u ON u.id = n.from_id
           JOIN friends f ON f.user_id = ?1 AND f.friend_id = n.from_id
           WHERE n.to_id = ?1 AND n.day >= ?2
           ORDER BY n.created_at DESC
           LIMIT 100`,
        )
        .bind(userId, opts.pingsSinceDay),
      this.db
        .prepare(
          `SELECT w.user_id, u.username, w.created_at
           FROM workout_events w
           JOIN users u ON u.id = w.user_id
           JOIN friends f ON f.user_id = ?1 AND f.friend_id = w.user_id
           WHERE w.created_at >= ?2 AND u.share_workouts = 1
           ORDER BY w.created_at DESC
           LIMIT 100`,
        )
        .bind(userId, opts.activitySinceSec),
    ]);

    type FriendJoinRow = UserRow & {
      s_week_start: number | null;
      s_done: number | null;
      s_due: number | null;
      s_score: number | null;
      s_badge: string | null;
      s_streak_weeks: number | null;
      s_protein_days: number | null;
      s_prs: string | null;
      s_updated_at: number | null;
      f_created_at: number;
    };

    const friendRows = (results[0].results ?? []) as FriendJoinRow[];
    const friends = friendRows.map((r) => {
      const user: UserRow = {
        id: r.id,
        sub: r.sub,
        code: r.code,
        username: r.username,
        google_photo: r.google_photo,
        share_photo: r.share_photo,
        share_prs: r.share_prs,
        joined: r.joined,
        created_at: r.created_at,
        alerts_on: r.alerts_on,
        share_workouts: r.share_workouts,
        pings_on: r.pings_on,
      };
      const snapshot: SnapshotRow | null =
        r.s_week_start !== null
          ? {
              user_id: r.id,
              week_start: r.s_week_start,
              done: r.s_done!,
              due: r.s_due!,
              score: r.s_score!,
              badge: r.s_badge!,
              streak_weeks: r.s_streak_weeks!,
              protein_days: r.s_protein_days!,
              prs: r.s_prs,
              updated_at: r.s_updated_at!,
            }
          : null;
      return { user, snapshot, friendsSince: r.f_created_at };
    });

    const incoming = (results[1].results ?? []) as UserRow[];
    const outgoing = (results[2].results ?? []) as UserRow[];
    const nudgeRows = (results[3].results ?? []) as Array<{
      from_id: string;
      username: string | null;
      day: number;
    }>;

    // Legacy (4.0) clients: unseen nudges are shown once, then marked seen for both client generations.
    if (!opts.v4 && nudgeRows.length > 0) {
      await this.db.batch(
        nudgeRows.map((n) =>
          this.db
            .prepare(
              "UPDATE nudges SET seen = 1, seen_v4 = 1 WHERE from_id = ?1 AND to_id = ?2 AND day = ?3",
            )
            .bind(n.from_id, userId, n.day),
        ),
      );
    }

    const pingRows = (results[6].results ?? []) as Array<{
      from_id: string;
      username: string | null;
      day: number;
      created_at: number;
      seen_v4: number;
    }>;
    const activityRows = (results[7].results ?? []) as Array<{
      user_id: string;
      username: string | null;
      created_at: number;
    }>;

    return {
      friends,
      weekStates: (results[4].results ?? []) as WeekStateRow[],
      incoming,
      outgoing,
      nudges: nudgeRows.map((n) => ({
        fromId: n.from_id,
        name: n.username ?? "",
        username: n.username,
        day: n.day,
      })),
      mutedIds: ((results[5].results ?? []) as Array<{ muted_id: string }>).map((r) => r.muted_id),
      inbox: opts.v4
        ? {
            pings: pingRows.map((n) => ({
              fromId: n.from_id,
              username: n.username,
              day: n.day,
              createdAt: n.created_at,
              seen: n.seen_v4 === 1,
            })),
            activity: activityRows.map((a) => ({ userId: a.user_id, username: a.username, at: a.created_at })),
          }
        : undefined,
    };
  }

  async findJoinedUserByUsername(username: string): Promise<UserRow | null> {
    return await this.db
      .prepare(`SELECT ${USER_COLS} FROM users WHERE username = ?1 AND joined = 1`)
      .bind(username)
      .first<UserRow>();
  }

  async isBlockedEitherWay(a: string, b: string): Promise<boolean> {
    const row = await this.db
      .prepare(
        "SELECT 1 AS ok FROM blocks WHERE (blocker = ?1 AND blocked = ?2) OR (blocker = ?2 AND blocked = ?1) LIMIT 1",
      )
      .bind(a, b)
      .first<{ ok: number }>();
    return row !== null;
  }

  async areFriends(a: string, b: string): Promise<boolean> {
    const row = await this.db
      .prepare("SELECT 1 AS ok FROM friends WHERE user_id = ?1 AND friend_id = ?2 LIMIT 1")
      .bind(a, b)
      .first<{ ok: number }>();
    return row !== null;
  }

  async hasRequest(fromId: string, toId: string): Promise<boolean> {
    const row = await this.db
      .prepare("SELECT 1 AS ok FROM requests WHERE from_id = ?1 AND to_id = ?2 LIMIT 1")
      .bind(fromId, toId)
      .first<{ ok: number }>();
    return row !== null;
  }

  async countFriends(userId: string): Promise<number> {
    const row = await this.db
      .prepare("SELECT COUNT(*) AS c FROM friends WHERE user_id = ?1")
      .bind(userId)
      .first<{ c: number }>();
    return row?.c ?? 0;
  }

  async createRequest(fromId: string, toId: string, nowSec: number): Promise<void> {
    await this.db
      .prepare(
        `INSERT OR IGNORE INTO requests (from_id, to_id, created_at)
         SELECT ?1, ?2, ?3
         WHERE NOT EXISTS (
           SELECT 1 FROM blocks
           WHERE (blocker = ?1 AND blocked = ?2) OR (blocker = ?2 AND blocked = ?1)
         )`,
      )
      .bind(fromId, toId, nowSec)
      .run();
  }

  async acceptFriendship(params: {
    userA: string;
    userB: string;
    nowSec: number;
    cap: number;
  }): Promise<AcceptResult> {
    const { userA, userB, nowSec, cap } = params;
    const res = await this.db.batch([
      this.db
        .prepare(
          `INSERT OR IGNORE INTO friends (user_id, friend_id, created_at)
           SELECT ?1, ?2, ?3
           WHERE (SELECT COUNT(*) FROM friends WHERE user_id = ?1) < ?4
             AND (SELECT COUNT(*) FROM friends WHERE user_id = ?2) < ?4
             AND NOT EXISTS (
               SELECT 1 FROM blocks
               WHERE (blocker = ?1 AND blocked = ?2) OR (blocker = ?2 AND blocked = ?1)
             )
             AND EXISTS (
               SELECT 1 FROM requests WHERE from_id = ?2 AND to_id = ?1
             )`,
        )
        .bind(userA, userB, nowSec, cap),
      this.db
        .prepare(
          `INSERT OR IGNORE INTO friends (user_id, friend_id, created_at)
           SELECT ?2, ?1, ?3
           WHERE EXISTS (
             SELECT 1 FROM friends WHERE user_id = ?1 AND friend_id = ?2
           )`,
        )
        .bind(userA, userB, nowSec),
      this.db
        .prepare(
          `DELETE FROM requests
           WHERE ((from_id = ?1 AND to_id = ?2) OR (from_id = ?2 AND to_id = ?1))
             AND EXISTS (SELECT 1 FROM friends WHERE user_id = ?1 AND friend_id = ?2)
             AND EXISTS (SELECT 1 FROM friends WHERE user_id = ?2 AND friend_id = ?1)`,
        )
        .bind(userA, userB),
    ]);

    if ((res[0].meta?.changes ?? 0) === 1) {
      return "accepted";
    }
    if (await this.areFriends(userA, userB)) {
      await this.declineRequestsBetween(userA, userB);
      return "already_friends";
    }
    if (await this.isBlockedEitherWay(userA, userB)) {
      return "blocked";
    }
    if (!(await this.hasRequest(userB, userA))) {
      return "not_found";
    }
    return "limit_reached";
  }

  async declineRequestsBetween(a: string, b: string): Promise<void> {
    await this.db
      .prepare(
        "DELETE FROM requests WHERE (from_id = ?1 AND to_id = ?2) OR (from_id = ?2 AND to_id = ?1)",
      )
      .bind(a, b)
      .run();
  }

  async removeFriendship(a: string, b: string): Promise<void> {
    await this.db.batch([
      this.db
        .prepare(
          "DELETE FROM friends WHERE (user_id = ?1 AND friend_id = ?2) OR (user_id = ?2 AND friend_id = ?1)",
        )
        .bind(a, b),
      this.mutesBetween(a, b),
    ]);
  }

  private mutesBetween(a: string, b: string): D1PreparedStatement {
    return this.db
      .prepare("DELETE FROM mutes WHERE (user_id = ?1 AND muted_id = ?2) OR (user_id = ?2 AND muted_id = ?1)")
      .bind(a, b);
  }

  async blockUser(blocker: string, blocked: string): Promise<void> {
    await this.db.batch([
      this.db
        .prepare(
          "DELETE FROM friends WHERE (user_id = ?1 AND friend_id = ?2) OR (user_id = ?2 AND friend_id = ?1)",
        )
        .bind(blocker, blocked),
      this.db
        .prepare(
          "DELETE FROM requests WHERE (from_id = ?1 AND to_id = ?2) OR (from_id = ?2 AND to_id = ?1)",
        )
        .bind(blocker, blocked),
      this.db
        .prepare("INSERT OR IGNORE INTO blocks (blocker, blocked) VALUES (?1, ?2)")
        .bind(blocker, blocked),
      this.mutesBetween(blocker, blocked),
    ]);
  }

  async unblockUser(blocker: string, blocked: string): Promise<void> {
    await this.db
      .prepare("DELETE FROM blocks WHERE blocker = ?1 AND blocked = ?2")
      .bind(blocker, blocked)
      .run();
  }

  async createNudgeIfAbsent(
    fromId: string,
    toId: string,
    day: number,
    nowSec: number,
  ): Promise<boolean> {
    const res = await this.db
      .prepare(
        "INSERT OR IGNORE INTO nudges (from_id, to_id, day, created_at, seen) VALUES (?1, ?2, ?3, ?4, 0)",
      )
      .bind(fromId, toId, day, nowSec)
      .run();
    return (res.meta?.changes ?? 0) > 0;
  }

  async upsertPushToken(
    token: string,
    userId: string,
    platform: PushPlatform,
    nowSec: number,
  ): Promise<void> {
    await this.db
      .prepare(
        `INSERT INTO push_tokens (token, user_id, platform, updated_at) VALUES (?1, ?2, ?3, ?4)
         ON CONFLICT(token) DO UPDATE SET
           user_id = excluded.user_id,
           platform = excluded.platform,
           updated_at = excluded.updated_at`,
      )
      .bind(token, userId, platform, nowSec)
      .run();
    // Each nudge sends one FCM request per token: keep only the newest MAX_PUSH_TOKENS per user.
    await this.db
      .prepare(
        `DELETE FROM push_tokens WHERE user_id = ?1 AND token NOT IN (
           SELECT token FROM push_tokens WHERE user_id = ?1 ORDER BY updated_at DESC, token LIMIT ?2)`,
      )
      .bind(userId, MAX_PUSH_TOKENS)
      .run();
  }

  async deletePushTokenForUser(token: string, userId: string): Promise<void> {
    await this.db
      .prepare("DELETE FROM push_tokens WHERE token = ?1 AND user_id = ?2")
      .bind(token, userId)
      .run();
  }

  async getPushTokens(userId: string): Promise<string[]> {
    const res = await this.db
      .prepare("SELECT token FROM push_tokens WHERE user_id = ?1")
      .bind(userId)
      .all<{ token: string }>();
    return (res.results ?? []).map((r) => r.token);
  }

  async deletePushTokens(tokens: string[]): Promise<void> {
    if (tokens.length === 0) return;
    await this.db.batch(
      tokens.map((t) =>
        this.db.prepare("DELETE FROM push_tokens WHERE token = ?1").bind(t),
      ),
    );
  }

  async markInboxSeen(userId: string): Promise<void> {
    await this.db
      .prepare("UPDATE nudges SET seen = 1, seen_v4 = 1 WHERE to_id = ?1 AND seen_v4 = 0")
      .bind(userId)
      .run();
  }

  async setMute(userId: string, mutedId: string, muted: boolean): Promise<void> {
    await this.db
      .prepare(
        muted
          ? "INSERT OR IGNORE INTO mutes (user_id, muted_id) VALUES (?1, ?2)"
          : "DELETE FROM mutes WHERE user_id = ?1 AND muted_id = ?2",
      )
      .bind(userId, mutedId)
      .run();
  }

  async getUserById(userId: string): Promise<UserRow | null> {
    return await this.db
      .prepare(`SELECT ${USER_COLS} FROM users WHERE id = ?1`)
      .bind(userId)
      .first<UserRow>();
  }

  async getActivityTargets(senderId: string, day: number): Promise<ActivityTarget[]> {
    const res = await this.db
      .prepare(
        `SELECT f.friend_id AS user_id, p.token
         FROM friends f
         JOIN users u ON u.id = f.friend_id
         JOIN push_tokens p ON p.user_id = f.friend_id
         WHERE f.user_id = ?1
           AND u.alerts_on = 1
           AND NOT EXISTS (SELECT 1 FROM mutes m WHERE m.user_id = f.friend_id AND m.muted_id = ?1)
           AND NOT EXISTS (
             SELECT 1 FROM blocks b
             WHERE (b.blocker = ?1 AND b.blocked = f.friend_id) OR (b.blocker = f.friend_id AND b.blocked = ?1)
           )
           AND NOT EXISTS (
             SELECT 1 FROM activity_push a WHERE a.from_id = ?1 AND a.to_id = f.friend_id AND a.day = ?2
           )
         ORDER BY f.created_at, f.friend_id, p.updated_at DESC`,
      )
      .bind(senderId, day)
      .all<{ user_id: string; token: string }>();
    const byUser = new Map<string, string[]>();
    for (const r of res.results ?? []) {
      let list = byUser.get(r.user_id);
      if (!list) byUser.set(r.user_id, (list = []));
      list.push(r.token);
    }
    return Array.from(byUser, ([userId, tokens]) => ({ userId, tokens })).slice(0, 50);
  }

  async claimActivityPush(fromId: string, toId: string, day: number): Promise<boolean> {
    const res = await this.db
      .prepare("INSERT OR IGNORE INTO activity_push (from_id, to_id, day) VALUES (?1, ?2, ?3)")
      .bind(fromId, toId, day)
      .run();
    return (res.meta?.changes ?? 0) > 0;
  }
}
