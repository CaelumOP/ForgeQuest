import { beforeAll, beforeEach, describe, expect, it } from "vitest";
import { base64UrlEncode, clearJwksCache, toArrayBuffer } from "../src/auth";
import { clearFcmTokenCache } from "../src/fcm";
import { handleRequest } from "../src/index";
import { riyadhEpochDay, sundayWeekStart } from "../src/time";
import { Env, FetchFn } from "../src/types";
import { MemStore } from "./mem-store";
import { SqliteStore } from "./sqlite-store";

const USE_SQLITE = process.env.FQ_STORE === "sqlite";

const AUD = "787186946908-ctsif69lokfk6i609727696lm44avo57.apps.googleusercontent.com";
const JWKS_URL = "https://www.googleapis.com/oauth2/v3/certs";
const BASE_MS = Date.UTC(2026, 10, 15, 12, 0, 0); // Sunday 2026-11-15 15:00 Riyadh

let signingKey: CryptoKey;
let jwksPayload: { keys: Array<JsonWebKey & { kid: string; alg: string; use: string }> };
let fcmSaJson: string;

beforeAll(async () => {
  const pair = (await crypto.subtle.generateKey(
    {
      name: "RSASSA-PKCS1-v1_5",
      modulusLength: 2048,
      publicExponent: new Uint8Array([1, 0, 1]),
      hash: "SHA-256",
    },
    true,
    ["sign", "verify"],
  )) as CryptoKeyPair;
  signingKey = pair.privateKey;
  const pubJwk = (await crypto.subtle.exportKey("jwk", pair.publicKey)) as JsonWebKey;
  jwksPayload = {
    keys: [{ ...pubJwk, kid: "test-kid-1", alg: "RS256", use: "sig" }],
  };

  const saPair = (await crypto.subtle.generateKey(
    {
      name: "RSASSA-PKCS1-v1_5",
      modulusLength: 2048,
      publicExponent: new Uint8Array([1, 0, 1]),
      hash: "SHA-256",
    },
    true,
    ["sign", "verify"],
  )) as CryptoKeyPair;
  const pkcs8 = new Uint8Array((await crypto.subtle.exportKey("pkcs8", saPair.privateKey)) as ArrayBuffer);
  let bin = "";
  for (let i = 0; i < pkcs8.length; i++) bin += String.fromCharCode(pkcs8[i]);
  const pem = `-----BEGIN PRIVATE KEY-----\n${btoa(bin)}\n-----END PRIVATE KEY-----\n`;
  fcmSaJson = JSON.stringify({
    project_id: "fq-test",
    client_email: "fcm@fq-test.iam.gserviceaccount.com",
    private_key: pem,
  });
});

async function signIdToken(
  claims: Record<string, unknown>,
  headerOverrides: Record<string, unknown> = {},
): Promise<string> {
  const header = { alg: "RS256", typ: "JWT", kid: "test-kid-1", ...headerOverrides };
  const hB64 = base64UrlEncode(new TextEncoder().encode(JSON.stringify(header)));
  const pB64 = base64UrlEncode(new TextEncoder().encode(JSON.stringify(claims)));
  const input = `${hB64}.${pB64}`;
  const sig = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", signingKey, toArrayBuffer(input));
  return `${input}.${base64UrlEncode(new Uint8Array(sig))}`;
}

describe("ForgeQuest Friends Worker", () => {
  let store: MemStore;
  let nowMs: number;
  let jwksFetches: number;
  let env: Env;
  let customFetch: FetchFn;

  beforeEach(() => {
    clearJwksCache();
    clearFcmTokenCache();
    store = (USE_SQLITE ? new SqliteStore() : new MemStore()) as unknown as MemStore;
    nowMs = BASE_MS;
    jwksFetches = 0;
    env = {
      DB: {} as D1Database,
      GOOGLE_AUD: AUD,
      GOOGLE_JWKS_URL: JWKS_URL,
      GOOGLE_ISSUERS: "accounts.google.com,https://accounts.google.com",
      ALLOWED_ORIGINS: "https://caelumop.github.io,http://localhost:5173",
    };
    customFetch = async (url) => {
      if (url === JWKS_URL) {
        jwksFetches++;
        return new Response(JSON.stringify(jwksPayload), {
          status: 200,
          headers: {
            "Content-Type": "application/json",
            "Cache-Control": "public, max-age=300",
          },
        });
      }
      return new Response("not found", { status: 404 });
    };
  });

  async function req(
    method: string,
    path: string,
    opts: {
      body?: unknown;
      token?: string;
      origin?: string;
      ip?: string;
    } = {},
  ): Promise<{ status: number; json: any; headers: Headers }> {
    const headers: Record<string, string> = {};
    if (opts.token) headers["Authorization"] = `Bearer ${opts.token}`;
    if (opts.origin) headers["Origin"] = opts.origin;
    if (opts.ip) headers["CF-Connecting-IP"] = opts.ip;
    let bodyStr: string | undefined;
    if (opts.body !== undefined) {
      headers["Content-Type"] = "application/json";
      bodyStr = JSON.stringify(opts.body);
    }
    const request = new Request(`https://forgequest-friends.test${path}`, {
      method,
      headers,
      body: bodyStr,
    });
    const res = await handleRequest(request, env, {
      store,
      nowMs: () => nowMs,
      fetchFn: customFetch,
    });
    const text = await res.text();
    const json = text ? JSON.parse(text) : null;
    return { status: res.status, json, headers: res.headers };
  }

  async function loginUser(sub: string, name?: string, picture?: string) {
    const nowSec = Math.floor(nowMs / 1000);
    const idToken = await signIdToken({
      sub,
      iss: "https://accounts.google.com",
      aud: AUD,
      exp: nowSec + 3600,
      ...(picture ? { picture } : {}),
    });
    const auth = await req("POST", "/v1/auth/google", { body: { idToken }, ip: sub });
    expect(auth.status).toBe(200);
    let me = auth.json.user;
    if (name) {
      const put = await req("PUT", "/v1/me", {
        token: auth.json.session,
        body: { name, sharePhoto: true, sharePrs: true },
      });
      expect(put.status).toBe(200);
      me = put.json;
    }
    return { session: auth.json.session as string, user: me };
  }

  describe("Google ID token verification", () => {
    it("verifies good token, caches JWKS, and upserts user", async () => {
      const nowSec = Math.floor(nowMs / 1000);
      const idToken = await signIdToken({
        sub: "google-sub-1",
        iss: "accounts.google.com",
        aud: AUD,
        exp: nowSec + 600,
        picture: "https://example.com/pic.jpg",
      });

      const r1 = await req("POST", "/v1/auth/google", { body: { idToken } });
      expect(r1.status).toBe(200);
      expect(r1.json.session).toMatch(/^[A-Za-z0-9\-_]{43}$/);
      expect(r1.json.user).toMatchObject({
        name: "",
        photoUrl: "https://example.com/pic.jpg",
        sharePhoto: true,
        sharePrs: true,
        joined: false,
      });
      expect(r1.json.user.code).toMatch(/^[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{6}$/);
      expect(jwksFetches).toBe(1);

      // Second auth within Cache-Control max-age reuses cached JWKS
      const r2 = await req("POST", "/v1/auth/google", { body: { idToken } });
      expect(r2.status).toBe(200);
      expect(r2.json.user.id).toBe(r1.json.user.id);
      expect(jwksFetches).toBe(1);
    });

    it("rejects wrong aud, wrong iss, expired token, and unknown kid", async () => {
      const nowSec = Math.floor(nowMs / 1000);

      const wrongAud = await signIdToken({
        sub: "s1",
        iss: "https://accounts.google.com",
        aud: "wrong-aud",
        exp: nowSec + 600,
      });
      expect((await req("POST", "/v1/auth/google", { body: { idToken: wrongAud } })).status).toBe(
        401,
      );

      const wrongIss = await signIdToken({
        sub: "s1",
        iss: "https://evil.example",
        aud: AUD,
        exp: nowSec + 600,
      });
      expect((await req("POST", "/v1/auth/google", { body: { idToken: wrongIss } })).status).toBe(
        401,
      );

      const expired = await signIdToken({
        sub: "s1",
        iss: "https://accounts.google.com",
        aud: AUD,
        exp: nowSec - 120,
      });
      expect((await req("POST", "/v1/auth/google", { body: { idToken: expired } })).status).toBe(
        401,
      );

      const unknownKid = await signIdToken(
        {
          sub: "s1",
          iss: "https://accounts.google.com",
          aud: AUD,
          exp: nowSec + 600,
        },
        { kid: "unknown-kid" },
      );
      expect(
        (await req("POST", "/v1/auth/google", { body: { idToken: unknownKid } })).status,
      ).toBe(401);
    });

    it("rate limits auth at 20 per hour per IP", async () => {
      const nowSec = Math.floor(nowMs / 1000);
      const idToken = await signIdToken({
        sub: "rate-sub",
        iss: "https://accounts.google.com",
        aud: AUD,
        exp: nowSec + 600,
      });
      for (let i = 0; i < 20; i++) {
        const res = await req("POST", "/v1/auth/google", {
          body: { idToken },
          ip: "1.2.3.4",
        });
        expect(res.status).toBe(200);
      }
      const blocked = await req("POST", "/v1/auth/google", {
        body: { idToken },
        ip: "1.2.3.4",
      });
      expect(blocked.status).toBe(429);
      expect(blocked.json).toEqual({ error: "rate_limited" });
    });
  });

  describe("Session expiry and sliding renewal", () => {
    it("rejects missing/expired session and slides when < 30 days left", async () => {
      const noAuth = await req("GET", "/v1/me");
      expect(noAuth.status).toBe(401);
      expect(noAuth.json).toEqual({ error: "unauthorized" });

      const alice = await loginUser("alice", "Alice");

      // Advance 160 days (20 days left < 30 days -> slides +180 days from day 160)
      nowMs += 160 * 86400 * 1000;
      const slideRes = await req("GET", "/v1/me", { token: alice.session });
      expect(slideRes.status).toBe(200);

      // Advance another 30 days (day 190 total: past original 180, alive due to slide)
      nowMs += 30 * 86400 * 1000;
      const aliveRes = await req("GET", "/v1/me", { token: alice.session });
      expect(aliveRes.status).toBe(200);

      // Advance 181 days past last activity -> expired
      nowMs += 181 * 86400 * 1000;
      const expiredRes = await req("GET", "/v1/me", { token: alice.session });
      expect(expiredRes.status).toBe(401);
      expect(expiredRes.json).toEqual({ error: "unauthorized" });
    });
  });

  describe("Profile, code rotation, and snapshot validation", () => {
    it("validates profile names, sharePhoto, sharePrs=false nulls stored prs, and rotates code", async () => {
      const alice = await loginUser("alice", undefined, "https://example.com/a.png");

      // Snapshot before joining -> 409 not_joined
      const today = riyadhEpochDay(nowMs);
      const weekStart = sundayWeekStart(today);
      const notJoined = await req("PUT", "/v1/snapshot", {
        token: alice.session,
        body: {
          weekStart,
          done: 1,
          due: 1,
          score: 100,
          badge: "trained",
          streakWeeks: 1,
          proteinDays: 1,
          prs: null,
        },
      });
      expect(notJoined.status).toBe(409);
      expect(notJoined.json).toEqual({ error: "not_joined" });

      // Bad names rejected
      for (const badName of [
        "",
        "   ",
        "x".repeat(41),
        "visit https://spam.com",
        "http://evil.io",
        "check www.foo.bar",
      ]) {
        const bad = await req("PUT", "/v1/me", {
          token: alice.session,
          body: { name: badName, sharePhoto: true, sharePrs: true },
        });
        expect(bad.status).toBe(400);
      }

      // Join with valid profile and upload snapshot with PRs
      const joined = await req("PUT", "/v1/me", {
        token: alice.session,
        body: { name: "  Alice  ", sharePhoto: true, sharePrs: true },
      });
      expect(joined.status).toBe(200);
      expect(joined.json.name).toBe("Alice");
      expect(joined.json.joined).toBe(true);

      const snapRes = await req("PUT", "/v1/snapshot", {
        token: alice.session,
        body: {
          weekStart,
          done: 3,
          due: 3,
          score: 100,
          badge: "trained",
          streakWeeks: 4,
          proteinDays: 3,
          prs: [{ exercise: "Bench Press", kg: 85.5, reps: 5 }],
        },
      });
      expect(snapRes.status).toBe(200);
      expect(store.snapshots.get(alice.user.id)?.prs).not.toBeNull();

      // Turning sharePrs=false nulls stored prs immediately; sharePhoto=false nulls photoUrl
      const off = await req("PUT", "/v1/me", {
        token: alice.session,
        body: { name: "Alice", sharePhoto: false, sharePrs: false },
      });
      expect(off.status).toBe(200);
      expect(off.json.photoUrl).toBeNull();
      expect(off.json.sharePrs).toBe(false);
      expect(store.snapshots.get(alice.user.id)?.prs).toBeNull();

      // Uploading new snapshot while sharePrs=false forces prs=null
      const snapWhileOff = await req("PUT", "/v1/snapshot", {
        token: alice.session,
        body: {
          weekStart,
          done: 4,
          due: 4,
          score: 100,
          badge: "trained",
          streakWeeks: 4,
          proteinDays: 4,
          prs: [{ exercise: "Squat", kg: 120, reps: 5 }],
        },
      });
      expect(snapWhileOff.status).toBe(200);
      expect(store.snapshots.get(alice.user.id)?.prs).toBeNull();

      // Rotate code invalidates old code
      const oldCode = alice.user.code;
      const rotated = await req("POST", "/v1/me/code", { token: alice.session });
      expect(rotated.status).toBe(200);
      expect(rotated.json.code).not.toBe(oldCode);

      const bob = await loginUser("bob", "Bob");
      const oldTry = await req("POST", "/v1/invites", {
        token: bob.session,
        body: { code: oldCode },
      });
      expect(oldTry.status).toBe(404);
      expect(oldTry.json).toEqual({ error: "code_not_found" });

      const newTry = await req("POST", "/v1/invites", {
        token: bob.session,
        body: { code: rotated.json.code },
      });
      expect(newTry.status).toBe(200);
      expect(newTry.json).toEqual({ status: "sent" });
    });

    it("rejects invalid snapshots (non-Sunday weekStart, out of range score, bad prs)", async () => {
      const alice = await loginUser("alice", "Alice");
      const today = riyadhEpochDay(nowMs);
      const weekStart = sundayWeekStart(today);

      const nonSunday = await req("PUT", "/v1/snapshot", {
        token: alice.session,
        body: {
          weekStart: weekStart + 1,
          done: 1,
          due: 1,
          score: 100,
          badge: "trained",
          streakWeeks: 0,
          proteinDays: 0,
          prs: null,
        },
      });
      expect(nonSunday.status).toBe(400);
      expect(nonSunday.json).toEqual({ error: "bad_snapshot" });

      const tooOldWeek = await req("PUT", "/v1/snapshot", {
        token: alice.session,
        body: {
          weekStart: weekStart - 14,
          done: 1,
          due: 1,
          score: 100,
          badge: "trained",
          streakWeeks: 0,
          proteinDays: 0,
          prs: null,
        },
      });
      expect(tooOldWeek.status).toBe(400);
      expect(tooOldWeek.json).toEqual({ error: "bad_snapshot" });

      const badScore = await req("PUT", "/v1/snapshot", {
        token: alice.session,
        body: {
          weekStart,
          done: 1,
          due: 1,
          score: 101,
          badge: "trained",
          streakWeeks: 0,
          proteinDays: 0,
          prs: null,
        },
      });
      expect(badScore.status).toBe(400);
      expect(badScore.json).toEqual({ error: "bad_snapshot" });
    });
  });

  describe("Invites, friends, blocks, and caps", () => {
    it("supports send, mutual auto-accept, accept, decline, remove, and block/unblock", async () => {
      const alice = await loginUser("alice", "Alice");
      const bob = await loginUser("bob", "Bob");
      const carol = await loginUser("carol", "Carol");

      // Self invite -> 409 self
      const selfRes = await req("POST", "/v1/invites", {
        token: alice.session,
        body: { code: alice.user.code },
      });
      expect(selfRes.status).toBe(409);
      expect(selfRes.json).toEqual({ error: "self" });

      // Mutual auto-accept: Alice invites Bob, then Bob invites Alice
      const inv1 = await req("POST", "/v1/invites", {
        token: alice.session,
        body: { code: bob.user.code },
      });
      expect(inv1.status).toBe(200);
      expect(inv1.json).toEqual({ status: "sent" });

      const inv2 = await req("POST", "/v1/invites", {
        token: bob.session,
        body: { code: alice.user.code },
      });
      expect(inv2.status).toBe(200);
      expect(inv2.json).toEqual({ status: "accepted" });

      const aliceFriends = await req("GET", "/v1/friends", { token: alice.session });
      expect(aliceFriends.json.friends.map((f: any) => f.id)).toEqual([bob.user.id]);
      expect(aliceFriends.json.incoming).toEqual([]);
      expect(aliceFriends.json.outgoing).toEqual([]);

      // Already friends -> 409 already_friends
      const dupInv = await req("POST", "/v1/invites", {
        token: alice.session,
        body: { code: bob.user.code },
      });
      expect(dupInv.status).toBe(409);
      expect(dupInv.json).toEqual({ error: "already_friends" });

      // Accept flow: Carol invites Alice, Alice accepts
      await req("POST", "/v1/invites", {
        token: carol.session,
        body: { code: alice.user.code },
      });
      const accept = await req("POST", `/v1/invites/${carol.user.id}/accept`, {
        token: alice.session,
      });
      expect(accept.status).toBe(200);
      expect(await store.areFriends(alice.user.id, carol.user.id)).toBe(true);

      // Remove Carol
      const rem = await req("DELETE", `/v1/friends/${carol.user.id}`, {
        token: alice.session,
      });
      expect(rem.status).toBe(200);
      expect(await store.areFriends(alice.user.id, carol.user.id)).toBe(false);

      // Decline flow
      await req("POST", "/v1/invites", {
        token: carol.session,
        body: { code: alice.user.code },
      });
      const dec = await req("POST", `/v1/invites/${carol.user.id}/decline`, {
        token: alice.session,
      });
      expect(dec.status).toBe(200);
      expect(await store.hasRequest(carol.user.id, alice.user.id)).toBe(false);

      // Block removes friendship and disguises invite lookup as code_not_found
      const blk = await req("POST", `/v1/blocks/${bob.user.id}`, {
        token: alice.session,
      });
      expect(blk.status).toBe(200);
      expect(await store.areFriends(alice.user.id, bob.user.id)).toBe(false);

      const blockedInvite = await req("POST", "/v1/invites", {
        token: bob.session,
        body: { code: alice.user.code },
      });
      expect(blockedInvite.status).toBe(404);
      expect(blockedInvite.json).toEqual({ error: "code_not_found" });

      // Unblock restores ability to invite
      const unblk = await req("DELETE", `/v1/blocks/${bob.user.id}`, {
        token: alice.session,
      });
      expect(unblk.status).toBe(200);
      const afterUnblock = await req("POST", "/v1/invites", {
        token: bob.session,
        body: { code: alice.user.code },
      });
      expect(afterUnblock.status).toBe(200);
      expect(afterUnblock.json).toEqual({ status: "sent" });
    });

    it("rate limits failed invite lookups after 5 failures", async () => {
      const alice = await loginUser("alice", "Alice");
      const bob = await loginUser("bob", "Bob");

      for (let i = 0; i < 5; i++) {
        const res = await req("POST", "/v1/invites", {
          token: alice.session,
          body: { code: "NOPE22" },
        });
        expect(res.status).toBe(404);
        expect(res.json).toEqual({ error: "code_not_found" });
      }

      // 6th attempt even with a valid code is blocked by too_many_tries
      const locked = await req("POST", "/v1/invites", {
        token: alice.session,
        body: { code: bob.user.code },
      });
      expect(locked.status).toBe(429);
      expect(locked.json).toEqual({ error: "too_many_tries" });

      // Next 15-min window resets the limit
      nowMs += 15 * 60 * 1000;
      const ok = await req("POST", "/v1/invites", {
        token: alice.session,
        body: { code: bob.user.code },
      });
      expect(ok.status).toBe(200);
      expect(ok.json).toEqual({ status: "sent" });
    });

    it("enforces the 50 friend cap on either side", async () => {
      const alice = await loginUser("alice", "Alice");
      const bob = await loginUser("bob", "Bob");
      const carol = await loginUser("carol", "Carol");

      // Carol invites Alice before Alice hits cap
      await req("POST", "/v1/invites", {
        token: carol.session,
        body: { code: alice.user.code },
      });

      // Fill Alice to 50 friends
      store.friends.set(alice.user.id, new Set());
      for (let i = 0; i < 50; i++) {
        const fid = `dummy-${i}`;
        store.friends.get(alice.user.id)!.add(fid);
        store.friends.set(fid, new Set([alice.user.id]));
      }

      // Alice cannot send invite -> limit_reached
      const sendCap = await req("POST", "/v1/invites", {
        token: alice.session,
        body: { code: bob.user.code },
      });
      expect(sendCap.status).toBe(409);
      expect(sendCap.json).toEqual({ error: "limit_reached" });

      // Bob cannot invite Alice -> limit_reached
      const recvCap = await req("POST", "/v1/invites", {
        token: bob.session,
        body: { code: alice.user.code },
      });
      expect(recvCap.status).toBe(409);
      expect(recvCap.json).toEqual({ error: "limit_reached" });

      // Alice cannot accept Carol's pending invite -> limit_reached
      const accCap = await req("POST", `/v1/invites/${carol.user.id}/accept`, {
        token: alice.session,
      });
      expect(accCap.status).toBe(409);
      expect(accCap.json).toEqual({ error: "limit_reached" });
    });
  });

  describe("Nudges, push tokens, and FCM", () => {
    it("allows one nudge per Riyadh day, marks seen on GET /friends, and handles FCM", async () => {
      const alice = await loginUser("alice", "Alice");
      const bob = await loginUser("bob", "Bob");
      const carol = await loginUser("carol", "Carol");

      // Not friends -> 403 not_friends
      const nonFriend = await req("POST", `/v1/nudges/${bob.user.id}`, {
        token: alice.session,
      });
      expect(nonFriend.status).toBe(403);
      expect(nonFriend.json).toEqual({ error: "not_friends" });

      // Befriend Alice & Bob, Alice & Carol
      await req("POST", "/v1/invites", { token: alice.session, body: { code: bob.user.code } });
      await req("POST", `/v1/invites/${alice.user.id}/accept`, { token: bob.session });
      await req("POST", "/v1/invites", { token: alice.session, body: { code: carol.user.code } });
      await req("POST", `/v1/invites/${alice.user.id}/accept`, { token: carol.session });

      // Without FCM_SA -> stored only, pushed: false
      const n1 = await req("POST", `/v1/nudges/${bob.user.id}`, { token: alice.session });
      expect(n1.status).toBe(200);
      expect(n1.json).toEqual({ pushed: false });

      // Same Riyadh day -> 409 already_nudged
      const nDup = await req("POST", `/v1/nudges/${bob.user.id}`, { token: alice.session });
      expect(nDup.status).toBe(409);
      expect(nDup.json).toEqual({ error: "already_nudged" });

      // Bob sees unseen nudge once, then marked seen
      const f1 = await req("GET", "/v1/friends", { token: bob.session });
      expect(f1.status).toBe(200);
      expect(f1.json.nudges).toEqual([
        { fromId: alice.user.id, name: "Alice", day: riyadhEpochDay(nowMs) },
      ]);
      const f2 = await req("GET", "/v1/friends", { token: bob.session });
      expect(f2.json.nudges).toEqual([]);

      // Enable FCM_SA and register tokens (1 live, 1 dead)
      env.FCM_SA = fcmSaJson;
      let oauthCalls = 0;
      const fcmBodies: any[] = [];
      customFetch = async (url, init) => {
        if (url === "https://oauth2.googleapis.com/token") {
          oauthCalls++;
          return new Response(
            JSON.stringify({ access_token: "fcm-access-1", expires_in: 3600 }),
            { status: 200, headers: { "Content-Type": "application/json" } },
          );
        }
        if (url === "https://fcm.googleapis.com/v1/projects/fq-test/messages:send") {
          const payload = JSON.parse(init?.body ?? "{}");
          fcmBodies.push(payload);
          if (payload.message?.token === "dead-token") {
            return new Response(
              JSON.stringify({ error: { status: "NOT_FOUND" } }),
              { status: 404 },
            );
          }
          return new Response(JSON.stringify({ name: "msg/1" }), { status: 200 });
        }
        return new Response("not found", { status: 404 });
      };

      await req("PUT", "/v1/push", {
        token: carol.session,
        body: { token: "live-token", platform: "android" },
      });
      await req("PUT", "/v1/push", {
        token: carol.session,
        body: { token: "dead-token", platform: "web" },
      });

      const nFcm = await req("POST", `/v1/nudges/${carol.user.id}`, { token: alice.session });
      expect(nFcm.status).toBe(200);
      expect(nFcm.json).toEqual({ pushed: true });
      expect(oauthCalls).toBe(1);
      expect(fcmBodies.length).toBe(2);
      expect(fcmBodies[0].message.data).toEqual({ type: "nudge", fromName: "Alice" });
      // Dead token pruned, live token kept
      expect(await store.getPushTokens(carol.user.id)).toEqual(["live-token"]);

      // Next Riyadh day (advance 12 hours past midnight Riyadh) -> nudge allowed again
      nowMs += 12 * 3600 * 1000;
      const nNextDay = await req("POST", `/v1/nudges/${carol.user.id}`, { token: alice.session });
      expect(nNextDay.status).toBe(200);
      expect(nNextDay.json).toEqual({ pushed: true });
      // 12 h later the cached 1 h OAuth token has expired -> exactly one refresh
      expect(oauthCalls).toBe(2);

      // DELETE /push removes token
      const delPush = await req("DELETE", "/v1/push", {
        token: carol.session,
        body: { token: "live-token" },
      });
      expect(delPush.status).toBe(200);
      expect(await store.getPushTokens(carol.user.id)).toEqual([]);

      // At most 5 tokens per user: the oldest are dropped as new devices register
      for (let i = 0; i < 7; i++) {
        nowMs += 1000;
        await req("PUT", "/v1/push", { token: carol.session, body: { token: `t${i}`, platform: "android" } });
      }
      expect((await store.getPushTokens(carol.user.id)).sort()).toEqual(["t2", "t3", "t4", "t5", "t6"]);
    });
  });

  describe("DELETE /v1/me cascade wipe", () => {
    it("wipes user and every row referencing them across all tables", async () => {
      const alice = await loginUser("alice", "Alice");
      const bob = await loginUser("bob", "Bob");
      const carol = await loginUser("carol", "Carol");
      const dave = await loginUser("dave", "Dave");
      const today = riyadhEpochDay(nowMs);

      await req("PUT", "/v1/snapshot", {
        token: alice.session,
        body: {
          weekStart: sundayWeekStart(today),
          done: 2,
          due: 2,
          score: 100,
          badge: "trained",
          streakWeeks: 1,
          proteinDays: 2,
          prs: null,
        },
      });
      await req("PUT", "/v1/push", {
        token: alice.session,
        body: { token: "alice-tok", platform: "web" },
      });

      // Friend with Bob + nudges both ways
      await req("POST", "/v1/invites", { token: alice.session, body: { code: bob.user.code } });
      await req("POST", `/v1/invites/${alice.user.id}/accept`, { token: bob.session });
      await req("POST", `/v1/nudges/${bob.user.id}`, { token: alice.session });
      await req("POST", `/v1/nudges/${alice.user.id}`, { token: bob.session });

      // Outgoing request to Carol + incoming request from Dave + block
      await req("POST", "/v1/invites", { token: alice.session, body: { code: carol.user.code } });
      await req("POST", "/v1/invites", { token: dave.session, body: { code: alice.user.code } });
      await req("POST", `/v1/blocks/${dave.user.id}`, { token: alice.session });

      const del = await req("DELETE", "/v1/me", { token: alice.session });
      expect(del.status).toBe(200);

      const aid = alice.user.id;
      if (USE_SQLITE) {
        expect((store as unknown as SqliteStore).rowsReferencing(aid)).toBe(0);
      } else {
        expect(store.users.has(aid)).toBe(false);
        expect(store.snapshots.has(aid)).toBe(false);
        expect(Array.from(store.sessions.values()).some((s) => s.user_id === aid)).toBe(false);
        expect(store.friends.has(aid)).toBe(false);
        expect(store.friends.get(bob.user.id)?.has(aid) ?? false).toBe(false);
        expect(
          Array.from(store.requests.values()).some((r) => r.from_id === aid || r.to_id === aid),
        ).toBe(false);
        expect(Array.from(store.blocks).some((b) => b.includes(aid))).toBe(false);
        expect(
          Array.from(store.nudges.values()).some((n) => n.from_id === aid || n.to_id === aid),
        ).toBe(false);
        expect(Array.from(store.pushTokens.values()).some((p) => p.user_id === aid)).toBe(false);
      }

      // Session is immediately invalid
      const after = await req("GET", "/v1/me", { token: alice.session });
      expect(after.status).toBe(401);
    });
  });

  describe("CORS", () => {
    it("echoes allowed origin, omits foreign origin, and serves requests with no origin", async () => {
      const allowedPreflight = await req("OPTIONS", "/v1/friends", {
        origin: "https://caelumop.github.io",
      });
      expect(allowedPreflight.status).toBe(204);
      expect(allowedPreflight.headers.get("Access-Control-Allow-Origin")).toBe(
        "https://caelumop.github.io",
      );
      expect(allowedPreflight.headers.get("Access-Control-Allow-Methods")).toBe(
        "GET, POST, PUT, DELETE",
      );
      expect(allowedPreflight.headers.get("Access-Control-Allow-Headers")).toBe(
        "Authorization, Content-Type",
      );
      expect(allowedPreflight.headers.get("Access-Control-Max-Age")).toBe("86400");

      const alice = await loginUser("alice", "Alice");

      const allowedGet = await req("GET", "/v1/me", {
        token: alice.session,
        origin: "http://localhost:5173",
      });
      expect(allowedGet.status).toBe(200);
      expect(allowedGet.headers.get("Access-Control-Allow-Origin")).toBe(
        "http://localhost:5173",
      );

      const foreignPreflight = await req("OPTIONS", "/v1/me", {
        origin: "https://evil.example",
      });
      expect(foreignPreflight.status).toBe(204);
      expect(foreignPreflight.headers.get("Access-Control-Allow-Origin")).toBeNull();

      const foreignGet = await req("GET", "/v1/me", {
        token: alice.session,
        origin: "https://evil.example",
      });
      expect(foreignGet.status).toBe(200);
      expect(foreignGet.headers.get("Access-Control-Allow-Origin")).toBeNull();

      const noOriginGet = await req("GET", "/v1/me", { token: alice.session });
      expect(noOriginGet.status).toBe(200);
      expect(noOriginGet.headers.get("Access-Control-Allow-Origin")).toBeNull();
    });
  });
});
