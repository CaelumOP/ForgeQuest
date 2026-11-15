import {
  PushPlatform,
  SessionRow,
  SnapshotInput,
  SnapshotRow,
  UserRow,
} from "./types";

export interface FriendsViewData {
  friends: Array<{ user: UserRow; snapshot: SnapshotRow | null }>;
  incoming: UserRow[];
  outgoing: UserRow[];
  nudges: Array<{ fromId: string; name: string; day: number }>;
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

  updateProfile(
    userId: string,
    input: { name: string; sharePhoto: boolean; sharePrs: boolean },
  ): Promise<UserRow | null>;
  rotateInviteCode(userId: string, generateCode: () => string): Promise<UserRow | null>;
  deleteUserCascade(userId: string): Promise<void>;

  upsertSnapshot(userId: string, snapshot: SnapshotInput, nowSec: number): Promise<void>;

  getFriendsView(userId: string): Promise<FriendsViewData>;
  findUserByCode(code: string): Promise<UserRow | null>;
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
  "id, sub, code, name, google_photo, share_photo, share_prs, joined, created_at";

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
            `INSERT INTO users (id, sub, code, name, google_photo, share_photo, share_prs, joined, created_at)
             VALUES (?1, ?2, ?3, '', ?4, 1, 1, 0, ?5)`,
          )
          .bind(id, input.sub, code, input.googlePhoto, input.nowSec)
          .run();
        return {
          id,
          sub: input.sub,
          code,
          name: "",
          google_photo: input.googlePhoto,
          share_photo: 1,
          share_prs: 1,
          joined: 0,
          created_at: input.nowSec,
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
        `SELECT s.token_hash, s.user_id, s.expires_at,
                u.id AS u_id, u.sub AS u_sub, u.code AS u_code, u.name AS u_name,
                u.google_photo AS u_google_photo, u.share_photo AS u_share_photo,
                u.share_prs AS u_share_prs, u.joined AS u_joined, u.created_at AS u_created_at
         FROM sessions s
         JOIN users u ON u.id = s.user_id
         WHERE s.token_hash = ?1`,
      )
      .bind(tokenHash)
      .first<{
        token_hash: string;
        user_id: string;
        expires_at: number;
        u_id: string;
        u_sub: string;
        u_code: string;
        u_name: string;
        u_google_photo: string | null;
        u_share_photo: number;
        u_share_prs: number;
        u_joined: number;
        u_created_at: number;
      }>();
    if (!row) return null;
    return {
      session: {
        token_hash: row.token_hash,
        user_id: row.user_id,
        expires_at: row.expires_at,
      },
      user: {
        id: row.u_id,
        sub: row.u_sub,
        code: row.u_code,
        name: row.u_name,
        google_photo: row.u_google_photo,
        share_photo: row.u_share_photo,
        share_prs: row.u_share_prs,
        joined: row.u_joined,
        created_at: row.u_created_at,
      },
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

  async updateProfile(
    userId: string,
    input: { name: string; sharePhoto: boolean; sharePrs: boolean },
  ): Promise<UserRow | null> {
    const sp = input.sharePhoto ? 1 : 0;
    const sprs = input.sharePrs ? 1 : 0;
    const updateStmt = this.db
      .prepare(
        "UPDATE users SET name = ?1, share_photo = ?2, share_prs = ?3, joined = 1 WHERE id = ?4",
      )
      .bind(input.name, sp, sprs, userId);

    if (!input.sharePrs) {
      await this.db.batch([
        updateStmt,
        this.db.prepare("UPDATE snapshots SET prs = NULL WHERE user_id = ?1").bind(userId),
      ]);
    } else {
      await updateStmt.run();
    }

    return await this.db
      .prepare(`SELECT ${USER_COLS} FROM users WHERE id = ?1`)
      .bind(userId)
      .first<UserRow>();
  }

  async rotateInviteCode(userId: string, generateCode: () => string): Promise<UserRow | null> {
    for (let attempt = 0; attempt < 8; attempt++) {
      const code = generateCode();
      try {
        await this.db
          .prepare("UPDATE users SET code = ?1 WHERE id = ?2")
          .bind(code, userId)
          .run();
        return await this.db
          .prepare(`SELECT ${USER_COLS} FROM users WHERE id = ?1`)
          .bind(userId)
          .first<UserRow>();
      } catch (err) {
        if (!isUniqueError(err)) throw err;
      }
    }
    throw new Error("failed to rotate invite code");
  }

  async deleteUserCascade(userId: string): Promise<void> {
    await this.db.batch([
      this.db.prepare("DELETE FROM sessions WHERE user_id = ?1").bind(userId),
      this.db.prepare("DELETE FROM snapshots WHERE user_id = ?1").bind(userId),
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
      this.db.prepare("DELETE FROM users WHERE id = ?1").bind(userId),
      this.db
        .prepare("DELETE FROM rate WHERE key = ?1 OR key = ?2")
        .bind(`write:${userId}`, `invite_fail:${userId}`),
    ]);
  }

  async upsertSnapshot(userId: string, s: SnapshotInput, nowSec: number): Promise<void> {
    const prsJson = s.prs === null ? null : JSON.stringify(s.prs);
    await this.db
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
           prs = excluded.prs,
           updated_at = excluded.updated_at`,
      )
      .bind(
        userId,
        s.weekStart,
        s.done,
        s.due,
        s.score,
        s.badge,
        s.streakWeeks,
        s.proteinDays,
        prsJson,
        nowSec,
      )
      .run();
  }

  async getFriendsView(userId: string): Promise<FriendsViewData> {
    const results = await this.db.batch([
      this.db
        .prepare(
          `SELECT u.id, u.sub, u.code, u.name, u.google_photo, u.share_photo, u.share_prs, u.joined, u.created_at,
                  s.week_start AS s_week_start, s.done AS s_done, s.due AS s_due, s.score AS s_score,
                  s.badge AS s_badge, s.streak_weeks AS s_streak_weeks, s.protein_days AS s_protein_days,
                  s.prs AS s_prs, s.updated_at AS s_updated_at
           FROM friends f
           JOIN users u ON u.id = f.friend_id
           LEFT JOIN snapshots s ON s.user_id = f.friend_id
           WHERE f.user_id = ?1`,
        )
        .bind(userId),
      this.db
        .prepare(
          `SELECT u.id, u.sub, u.code, u.name, u.google_photo, u.share_photo, u.share_prs, u.joined, u.created_at
           FROM requests r
           JOIN users u ON u.id = r.from_id
           WHERE r.to_id = ?1
           ORDER BY r.created_at DESC`,
        )
        .bind(userId),
      this.db
        .prepare(
          `SELECT u.id, u.sub, u.code, u.name, u.google_photo, u.share_photo, u.share_prs, u.joined, u.created_at
           FROM requests r
           JOIN users u ON u.id = r.to_id
           WHERE r.from_id = ?1
           ORDER BY r.created_at DESC`,
        )
        .bind(userId),
      this.db
        .prepare(
          `SELECT n.from_id, u.name, n.day
           FROM nudges n
           JOIN users u ON u.id = n.from_id
           WHERE n.to_id = ?1 AND n.seen = 0
           ORDER BY n.day DESC, n.created_at DESC`,
        )
        .bind(userId),
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
    };

    const friendRows = (results[0].results ?? []) as FriendJoinRow[];
    const friends = friendRows.map((r) => {
      const user: UserRow = {
        id: r.id,
        sub: r.sub,
        code: r.code,
        name: r.name,
        google_photo: r.google_photo,
        share_photo: r.share_photo,
        share_prs: r.share_prs,
        joined: r.joined,
        created_at: r.created_at,
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
      return { user, snapshot };
    });

    const incoming = (results[1].results ?? []) as UserRow[];
    const outgoing = (results[2].results ?? []) as UserRow[];
    const nudgeRows = (results[3].results ?? []) as Array<{
      from_id: string;
      name: string;
      day: number;
    }>;

    if (nudgeRows.length > 0) {
      await this.db.batch(
        nudgeRows.map((n) =>
          this.db
            .prepare("UPDATE nudges SET seen = 1 WHERE from_id = ?1 AND to_id = ?2 AND day = ?3")
            .bind(n.from_id, userId, n.day),
        ),
      );
    }

    return {
      friends,
      incoming,
      outgoing,
      nudges: nudgeRows.map((n) => ({ fromId: n.from_id, name: n.name, day: n.day })),
    };
  }

  async findUserByCode(code: string): Promise<UserRow | null> {
    return await this.db
      .prepare(`SELECT ${USER_COLS} FROM users WHERE code = ?1`)
      .bind(code)
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
    await this.db
      .prepare(
        "DELETE FROM friends WHERE (user_id = ?1 AND friend_id = ?2) OR (user_id = ?2 AND friend_id = ?1)",
      )
      .bind(a, b)
      .run();
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
}
