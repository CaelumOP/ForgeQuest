import { AcceptResult, FriendsViewData, MAX_PUSH_TOKENS, Store } from "../src/store";
import {
  PushPlatform,
  SessionRow,
  SnapshotInput,
  SnapshotRow,
  UserRow,
} from "../src/types";

export interface MemNudge {
  from_id: string;
  to_id: string;
  day: number;
  created_at: number;
  seen: number;
}

export interface MemPushToken {
  token: string;
  user_id: string;
  platform: PushPlatform;
  updated_at: number;
}

export class MemStore implements Store {
  readonly users = new Map<string, UserRow>();
  readonly sessions = new Map<string, SessionRow>();
  readonly snapshots = new Map<string, SnapshotRow>();
  readonly friends = new Map<string, Set<string>>();
  readonly requests = new Map<string, { from_id: string; to_id: string; created_at: number }>();
  readonly blocks = new Set<string>();
  readonly nudges = new Map<string, MemNudge>();
  readonly pushTokens = new Map<string, MemPushToken>();
  readonly rates = new Map<string, { count: number; windowStart: number }>();

  private reqKey(fromId: string, toId: string): string {
    return `${fromId}->${toId}`;
  }

  private blockKey(blocker: string, blocked: string): string {
    return `${blocker}->${blocked}`;
  }

  private nudgeKey(fromId: string, toId: string, day: number): string {
    return `${fromId}->${toId}@${day}`;
  }

  async getRateCount(key: string, windowStart: number): Promise<number> {
    const entry = this.rates.get(key);
    if (!entry || entry.windowStart !== windowStart) return 0;
    return entry.count;
  }

  async incrementRate(key: string, windowStart: number): Promise<number> {
    const entry = this.rates.get(key);
    if (entry && entry.windowStart === windowStart) {
      entry.count += 1;
      return entry.count;
    }
    this.rates.set(key, { count: 1, windowStart });
    return 1;
  }

  async upsertUserBySub(input: {
    sub: string;
    googlePhoto: string | null;
    nowSec: number;
    generateId: () => string;
    generateCode: () => string;
  }): Promise<UserRow> {
    for (const u of this.users.values()) {
      if (u.sub === input.sub) {
        if (u.google_photo !== input.googlePhoto) {
          u.google_photo = input.googlePhoto;
        }
        return { ...u };
      }
    }

    let id = input.generateId();
    while (this.users.has(id)) {
      id = input.generateId();
    }

    const usedCodes = new Set(Array.from(this.users.values(), (u) => u.code));
    let code = input.generateCode();
    while (usedCodes.has(code)) {
      code = input.generateCode();
    }

    const row: UserRow = {
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
    this.users.set(id, row);
    return { ...row };
  }

  async createSession(tokenHash: string, userId: string, expiresAt: number): Promise<void> {
    this.sessions.set(tokenHash, {
      token_hash: tokenHash,
      user_id: userId,
      expires_at: expiresAt,
    });
  }

  async getSessionWithUser(
    tokenHash: string,
  ): Promise<{ session: SessionRow; user: UserRow } | null> {
    const session = this.sessions.get(tokenHash);
    if (!session) return null;
    const user = this.users.get(session.user_id);
    if (!user) return null;
    return { session: { ...session }, user: { ...user } };
  }

  async extendSession(tokenHash: string, expiresAt: number): Promise<void> {
    const session = this.sessions.get(tokenHash);
    if (session) session.expires_at = expiresAt;
  }

  async deleteSession(tokenHash: string): Promise<void> {
    this.sessions.delete(tokenHash);
  }

  async updateProfile(
    userId: string,
    input: { name: string; sharePhoto: boolean; sharePrs: boolean },
  ): Promise<UserRow | null> {
    const user = this.users.get(userId);
    if (!user) return null;
    user.name = input.name;
    user.share_photo = input.sharePhoto ? 1 : 0;
    user.share_prs = input.sharePrs ? 1 : 0;
    user.joined = 1;
    if (!input.sharePrs) {
      const snap = this.snapshots.get(userId);
      if (snap) snap.prs = null;
    }
    return { ...user };
  }

  async rotateInviteCode(userId: string, generateCode: () => string): Promise<UserRow | null> {
    const user = this.users.get(userId);
    if (!user) return null;
    const usedCodes = new Set(Array.from(this.users.values(), (u) => u.code));
    let code = generateCode();
    while (usedCodes.has(code)) {
      code = generateCode();
    }
    user.code = code;
    return { ...user };
  }

  async deleteUserCascade(userId: string): Promise<void> {
    for (const [k, s] of this.sessions) {
      if (s.user_id === userId) this.sessions.delete(k);
    }
    this.snapshots.delete(userId);
    this.friends.delete(userId);
    for (const set of this.friends.values()) {
      set.delete(userId);
    }
    for (const [k, r] of this.requests) {
      if (r.from_id === userId || r.to_id === userId) this.requests.delete(k);
    }
    for (const k of this.blocks) {
      const [a, b] = k.split("->");
      if (a === userId || b === userId) this.blocks.delete(k);
    }
    for (const [k, n] of this.nudges) {
      if (n.from_id === userId || n.to_id === userId) this.nudges.delete(k);
    }
    for (const [k, p] of this.pushTokens) {
      if (p.user_id === userId) this.pushTokens.delete(k);
    }
    this.users.delete(userId);
    this.rates.delete(`write:${userId}`);
    this.rates.delete(`invite_fail:${userId}`);
  }

  async upsertSnapshot(userId: string, s: SnapshotInput, nowSec: number): Promise<void> {
    this.snapshots.set(userId, {
      user_id: userId,
      week_start: s.weekStart,
      done: s.done,
      due: s.due,
      score: s.score,
      badge: s.badge,
      streak_weeks: s.streakWeeks,
      protein_days: s.proteinDays,
      prs: s.prs === null ? null : JSON.stringify(s.prs),
      updated_at: nowSec,
    });
  }

  async getFriendsView(userId: string): Promise<FriendsViewData> {
    const friendIds = this.friends.get(userId) ?? new Set<string>();
    const friends: FriendsViewData["friends"] = [];
    for (const fid of friendIds) {
      const user = this.users.get(fid);
      if (!user) continue;
      const snap = this.snapshots.get(fid) ?? null;
      friends.push({
        user: { ...user },
        snapshot: snap ? { ...snap } : null,
      });
    }

    const incomingReqs = Array.from(this.requests.values())
      .filter((r) => r.to_id === userId)
      .sort((a, b) => b.created_at - a.created_at);
    const incoming: UserRow[] = [];
    for (const r of incomingReqs) {
      const u = this.users.get(r.from_id);
      if (u) incoming.push({ ...u });
    }

    const outgoingReqs = Array.from(this.requests.values())
      .filter((r) => r.from_id === userId)
      .sort((a, b) => b.created_at - a.created_at);
    const outgoing: UserRow[] = [];
    for (const r of outgoingReqs) {
      const u = this.users.get(r.to_id);
      if (u) outgoing.push({ ...u });
    }

    const unseenNudges = Array.from(this.nudges.values())
      .filter((n) => n.to_id === userId && n.seen === 0)
      .sort((a, b) => b.day - a.day || b.created_at - a.created_at);
    const nudges: FriendsViewData["nudges"] = [];
    for (const n of unseenNudges) {
      const from = this.users.get(n.from_id);
      if (!from) continue;
      nudges.push({ fromId: n.from_id, name: from.name, day: n.day });
      n.seen = 1;
    }

    return { friends, incoming, outgoing, nudges };
  }

  async findUserByCode(code: string): Promise<UserRow | null> {
    for (const u of this.users.values()) {
      if (u.code === code) return { ...u };
    }
    return null;
  }

  async isBlockedEitherWay(a: string, b: string): Promise<boolean> {
    return this.blocks.has(this.blockKey(a, b)) || this.blocks.has(this.blockKey(b, a));
  }

  async areFriends(a: string, b: string): Promise<boolean> {
    return this.friends.get(a)?.has(b) ?? false;
  }

  async hasRequest(fromId: string, toId: string): Promise<boolean> {
    return this.requests.has(this.reqKey(fromId, toId));
  }

  async countFriends(userId: string): Promise<number> {
    return this.friends.get(userId)?.size ?? 0;
  }

  async createRequest(fromId: string, toId: string, nowSec: number): Promise<void> {
    if (await this.isBlockedEitherWay(fromId, toId)) return;
    const k = this.reqKey(fromId, toId);
    if (!this.requests.has(k)) {
      this.requests.set(k, { from_id: fromId, to_id: toId, created_at: nowSec });
    }
  }

  async acceptFriendship(params: {
    userA: string;
    userB: string;
    nowSec: number;
    cap: number;
  }): Promise<AcceptResult> {
    const { userA, userB, cap } = params;
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
    if ((await this.countFriends(userA)) >= cap || (await this.countFriends(userB)) >= cap) {
      return "limit_reached";
    }
    if (!this.friends.has(userA)) this.friends.set(userA, new Set());
    if (!this.friends.has(userB)) this.friends.set(userB, new Set());
    this.friends.get(userA)!.add(userB);
    this.friends.get(userB)!.add(userA);
    this.requests.delete(this.reqKey(userA, userB));
    this.requests.delete(this.reqKey(userB, userA));
    return "accepted";
  }

  async declineRequestsBetween(a: string, b: string): Promise<void> {
    this.requests.delete(this.reqKey(a, b));
    this.requests.delete(this.reqKey(b, a));
  }

  async removeFriendship(a: string, b: string): Promise<void> {
    this.friends.get(a)?.delete(b);
    this.friends.get(b)?.delete(a);
  }

  async blockUser(blocker: string, blocked: string): Promise<void> {
    await this.removeFriendship(blocker, blocked);
    await this.declineRequestsBetween(blocker, blocked);
    this.blocks.add(this.blockKey(blocker, blocked));
  }

  async unblockUser(blocker: string, blocked: string): Promise<void> {
    this.blocks.delete(this.blockKey(blocker, blocked));
  }

  async createNudgeIfAbsent(
    fromId: string,
    toId: string,
    day: number,
    nowSec: number,
  ): Promise<boolean> {
    const k = this.nudgeKey(fromId, toId, day);
    if (this.nudges.has(k)) return false;
    this.nudges.set(k, { from_id: fromId, to_id: toId, day, created_at: nowSec, seen: 0 });
    return true;
  }

  async upsertPushToken(
    token: string,
    userId: string,
    platform: PushPlatform,
    nowSec: number,
  ): Promise<void> {
    this.pushTokens.set(token, {
      token,
      user_id: userId,
      platform,
      updated_at: nowSec,
    });
    const mine = Array.from(this.pushTokens.values())
      .filter((p) => p.user_id === userId)
      .sort((a, b) => b.updated_at - a.updated_at || a.token.localeCompare(b.token));
    for (const old of mine.slice(MAX_PUSH_TOKENS)) this.pushTokens.delete(old.token);
  }

  async deletePushTokenForUser(token: string, userId: string): Promise<void> {
    const existing = this.pushTokens.get(token);
    if (existing && existing.user_id === userId) {
      this.pushTokens.delete(token);
    }
  }

  async getPushTokens(userId: string): Promise<string[]> {
    const tokens: string[] = [];
    for (const p of this.pushTokens.values()) {
      if (p.user_id === userId) tokens.push(p.token);
    }
    return tokens;
  }

  async deletePushTokens(tokens: string[]): Promise<void> {
    for (const t of tokens) {
      this.pushTokens.delete(t);
    }
  }
}
