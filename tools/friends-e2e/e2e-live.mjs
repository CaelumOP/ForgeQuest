#!/usr/bin/env node
// Live end-to-end suite for the ForgeQuest Friends Worker, run against the STAGING Worker
// (forgequest-friends-staging, own D1, test-only aud/issuer + inline public test key; see worker/wrangler.toml env.staging).
// Production is never touched and has no bypass: auth.ts only accepts an inline JWKS with the e2e aud + issuer.
//
//   node tools/friends-e2e/e2e-live.mjs                 # full suite, prints a pass/fail table, exit 1 on any failure
//   node tools/friends-e2e/e2e-live.mjs mint <sub> <username> <name>   # print a session for client checks (joins the user)
//
// Needs the private test key at D:\Claude\secrets\fq-e2e-key.json (or FQ_E2E_KEY) and wrangler (worker/node_modules)
// for SQL checks/seeding on the staging D1.
import { createPrivateKey, createSign, randomBytes } from "node:crypto";
import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { dirname, join as joinPath } from "node:path";
import { fileURLToPath } from "node:url";

const BASE = process.env.FQ_E2E_BASE ?? "https://forgequest-friends-staging.forgequest-worker.workers.dev";
const KEY_PATH = process.env.FQ_E2E_KEY ?? "D:/Claude/secrets/fq-e2e-key.json";
const AUD = "forgequest-e2e";
const ISS = "https://e2e.forgequest.invalid";
const WORKER_DIR = joinPath(dirname(fileURLToPath(import.meta.url)), "..", "..", "worker");
const ALLOWED_ORIGIN = "https://caelumop.github.io";

const keyFile = JSON.parse(readFileSync(KEY_PATH, "utf8"));
const privateKey = createPrivateKey({ key: keyFile.private, format: "jwk" });

const b64u = (buf) => Buffer.from(buf).toString("base64url");

function signToken(claims, { kid = keyFile.kid, key = privateKey } = {}) {
  const header = b64u(JSON.stringify({ alg: "RS256", typ: "JWT", kid }));
  const payload = b64u(JSON.stringify(claims));
  const signer = createSign("RSA-SHA256");
  signer.update(`${header}.${payload}`);
  return `${header}.${payload}.${b64u(signer.sign(key))}`;
}

function idToken(sub, extra = {}) {
  const now = Math.floor(Date.now() / 1000);
  return signToken({ sub, iss: ISS, aud: AUD, iat: now, exp: now + 600, ...extra });
}

async function call(method, path, { token, body, origin } = {}) {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (origin) headers.Origin = origin;
  if (body !== undefined) headers["Content-Type"] = "application/json";
  const init = {
    method,
    headers,
    body: body === undefined ? undefined : typeof body === "string" ? body : JSON.stringify(body),
  };
  let res;
  try {
    res = await fetch(`${BASE}${path}`, init);
  } catch (e) {
    // One retry on a transport error (no HTTP response at all), e.g. a "fetch failed" blip in the 120-write loop.
    // HTTP errors are never retried, so counts and rate limits stay exact.
    await new Promise((r) => setTimeout(r, 1500));
    res = await fetch(`${BASE}${path}`, init);
  }
  const text = await res.text();
  let json = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch {
    json = text;
  }
  return { status: res.status, json, headers: res.headers };
}

function sql(command) {
  // wrangler on Windows can hit a libuv assertion on exit AFTER printing valid JSON: trust stdout, not the exit code.
  let out;
  try {
    out = execFileSync(
    process.execPath,
    [joinPath(WORKER_DIR, "node_modules", "wrangler", "bin", "wrangler.js"), "d1", "execute", "forgequest-staging", "--env", "staging", "--remote", "--json", "--command", command],
    { cwd: WORKER_DIR, encoding: "utf8", stdio: ["ignore", "pipe", "pipe"], maxBuffer: 16 * 1024 * 1024 },
  );
  } catch (e) {
    out = e.stdout ?? "";
    if (!out.trim().startsWith("[")) throw new Error(`wrangler failed: ${e.stderr ?? e}`);
  }
  const parsed = JSON.parse(out);
  return parsed.flatMap((r) => r.results ?? []);
}
const clearRates = () => sql("DELETE FROM rate");

// ---- tiny runner ----
const results = [];
let current = "setup";
function check(name, cond, detail = "") {
  results.push({ group: current, name, ok: !!cond, detail: cond ? "" : detail });
  if (!cond) console.error(`  FAIL ${current} :: ${name} ${detail}`);
}
function expectRes(name, res, status, error) {
  const ok = res.status === status && (error === undefined || res.json?.error === error);
  check(name, ok, `got ${res.status} ${JSON.stringify(res.json)}; want ${status}${error ? " " + error : ""}`);
  return res;
}
async function group(name, fn) {
  current = name;
  console.log(`# ${name}`);
  try {
    await fn();
  } catch (e) {
    check("no exception", false, String(e?.stack ?? e));
  }
}

const RID = randomBytes(3).toString("hex");
const users = {};

async function login(key, { picture } = {}) {
  const sub = `e2e-${RID}-${key}`;
  const res = await call("POST", "/v1/auth/google", { body: { idToken: idToken(sub, picture ? { picture } : {}) } });
  if (res.status !== 200) throw new Error(`login ${key}: ${res.status} ${JSON.stringify(res.json)}`);
  users[key] = { sub, session: res.json.session, id: res.json.user.id, username: `e2e_${RID}_${key}` };
  return users[key];
}
async function join(key, name, extra = {}) {
  const u = users[key];
  const res = await call("PUT", "/v1/me", {
    token: u.session,
    body: { name, username: u.username, sharePhoto: true, sharePrs: true, ...extra },
  });
  if (res.status !== 200) throw new Error(`join ${key}: ${res.status} ${JSON.stringify(res.json)}`);
  return res.json;
}

function riyadhToday() {
  return Math.floor((Date.now() + 3 * 3600 * 1000) / 86400000);
}
const sundayOf = (d) => d - ((d - 3 + 7000) % 7); // 1970-01-01 was a Thursday; epochDay % 7 == 3 is a Sunday
function snap(weekStart, extra = {}) {
  return {
    weekStart, done: 2, due: 3, score: 67, badge: "on_track", streakWeeks: 4, proteinDays: 2,
    prs: [{ exercise: "Bench Press", kg: 80, reps: 5 }], ...extra,
  };
}

async function main() {
  if (process.argv[2] === "mint") {
    const [, , , sub, username, name] = process.argv;
    const res = await call("POST", "/v1/auth/google", { body: { idToken: idToken(sub) } });
    if (res.status !== 200) throw new Error(JSON.stringify(res.json));
    if (!res.json.user.joined) {
      const j = await call("PUT", "/v1/me", {
        token: res.json.session,
        body: { name, username, sharePhoto: true, sharePrs: true },
      });
      if (j.status !== 200) throw new Error(JSON.stringify(j.json));
    }
    console.log(JSON.stringify({ session: res.json.session, id: res.json.user.id }));
    return;
  }

  console.log(`Friends live e2e against ${BASE} (run ${RID})`);
  clearRates();

  await group("auth", async () => {
    const now = Math.floor(Date.now() / 1000);
    const sub = `e2e-${RID}-auth`;
    expectRes("wrong aud", await call("POST", "/v1/auth/google", { body: { idToken: idToken(sub, { aud: "other" }) } }), 401, "invalid_token");
    expectRes("wrong iss", await call("POST", "/v1/auth/google", { body: { idToken: idToken(sub, { iss: "https://accounts.google.com" }) } }), 401, "invalid_token");
    expectRes("expired", await call("POST", "/v1/auth/google", { body: { idToken: idToken(sub, { exp: now - 3600 }) } }), 401, "invalid_token");
    expectRes("unknown kid", await call("POST", "/v1/auth/google", { body: { idToken: signToken({ sub, iss: ISS, aud: AUD, exp: now + 600 }, { kid: "nope" }) } }), 401, "invalid_token");
    const tok = idToken(sub);
    const forged = tok.slice(0, tok.lastIndexOf(".") + 1) + b64u(randomBytes(256));
    expectRes("bad signature", await call("POST", "/v1/auth/google", { body: { idToken: forged } }), 401, "invalid_token");
    expectRes("garbage token", await call("POST", "/v1/auth/google", { body: { idToken: "a.b.c" } }), 401);
    expectRes("no session -> 401", await call("GET", "/v1/me"), 401, "unauthorized");
    expectRes("bogus session -> 401", await call("GET", "/v1/me", { token: b64u(randomBytes(32)) }), 401, "unauthorized");
    const ok = await call("POST", "/v1/auth/google", { body: { idToken: tok } });
    expectRes("good token", ok, 200);
    check("session is 43-char base64url", /^[A-Za-z0-9_-]{43}$/.test(ok.json?.session ?? ""));
    check("new user not joined, no username, no code", ok.json?.user?.joined === false && ok.json?.user?.username === null && !("code" in (ok.json?.user ?? {})));
    const again = await call("POST", "/v1/auth/google", { body: { idToken: idToken(sub) } });
    check("same sub -> same user id", again.json?.user?.id === ok.json?.user?.id);
    await call("DELETE", "/v1/me", { token: ok.json.session });
  });

  await group("join + username rules", async () => {
    const a = await login("a", { picture: "https://lh3.googleusercontent.com/a/e2e-photo" });
    await login("b");
    await login("c");
    const put = (body) => call("PUT", "/v1/me", { token: a.session, body: { name: "Ali E2E", sharePhoto: true, sharePrs: true, ...body } });
    expectRes("snapshot before join -> not_joined", await call("PUT", "/v1/snapshot", { token: a.session, body: snap(sundayOf(riyadhToday())) }), 409, "not_joined");
    expectRes("invite before join -> not_joined", await call("POST", "/v1/invites", { token: a.session, body: { username: "whoever" } }), 409, "not_joined");
    expectRes("join without username", await put({}), 400, "bad_username");
    for (const bad of ["ab", "x".repeat(21), "has space", "dash-name", "دايت", ".dot", "dot.", "a..b", "admin", "Support", "forgequest_x", "\u212Aelvin"]) {
      expectRes(`bad username ${JSON.stringify(bad)}`, await put({ username: bad }), 400, "bad_username");
    }
    expectRes("bad name (URL)", await put({ name: "visit https://x.io", username: a.username }), 400, "bad_profile");
    const ok = await put({ username: `  @${a.username.toUpperCase()} ` });
    expectRes("join with @UPPERCASE username", ok, 200);
    check("stored lowercase", ok.json?.username === a.username, JSON.stringify(ok.json));
    check("joined + photo shown", ok.json?.joined === true && ok.json?.photoUrl === "https://lh3.googleusercontent.com/a/e2e-photo");
    const rows = sql(`SELECT username, joined FROM users WHERE id = '${a.id}'`);
    check("D1 row has lowercase username", rows[0]?.username === a.username && rows[0]?.joined === 1, JSON.stringify(rows));
    await join("b", "Badr E2E");
    expectRes("taken (case-insensitive)", await call("PUT", "/v1/me", { token: users.c.session, body: { name: "Carl", username: a.username.toUpperCase(), sharePhoto: true, sharePrs: true } }), 409, "username_taken");
    await join("c", "Carl E2E");
    const me = await call("GET", "/v1/me", { token: a.session });
    check("GET /me returns username", me.json?.username === a.username);
  });

  await group("invites by username", async () => {
    const { a, b, c } = users;
    clearRates();
    expectRes("self", await call("POST", "/v1/invites", { token: a.session, body: { username: a.username } }), 409, "self");
    expectRes("old {code} body", await call("POST", "/v1/invites", { token: a.session, body: { code: "ABC234" } }), 400, "bad_request");
    expectRes("unknown name", await call("POST", "/v1/invites", { token: a.session, body: { username: `zz_${RID}_none` } }), 404, "username_not_found");
    expectRes("reserved name is a miss", await call("POST", "/v1/invites", { token: a.session, body: { username: "admin" } }), 404, "username_not_found");
    const sent = await call("POST", "/v1/invites", { token: a.session, body: { username: `@${b.username.toUpperCase()}` } });
    expectRes("a -> b sent (case-insensitive, @)", sent, 200);
    check("status sent", sent.json?.status === "sent");
    const fb = await call("GET", "/v1/friends", { token: b.session });
    check("b sees incoming a with @username", fb.json?.incoming?.some((p) => p.id === a.id && p.username === a.username && p.name === "Ali E2E"), JSON.stringify(fb.json?.incoming));
    const fa = await call("GET", "/v1/friends", { token: a.session });
    check("a sees outgoing b", fa.json?.outgoing?.some((p) => p.id === b.id));
    check("not friends until accepted", !fa.json?.friends?.some((f) => f.id === b.id));
    expectRes("b declines", await call("POST", `/v1/invites/${a.id}/decline`, { token: b.session }), 200);
    const fb2 = await call("GET", "/v1/friends", { token: b.session });
    check("decline removes request", !fb2.json?.incoming?.some((p) => p.id === a.id));
    await call("POST", "/v1/invites", { token: a.session, body: { username: b.username } });
    expectRes("b accepts", await call("POST", `/v1/invites/${a.id}/accept`, { token: b.session }), 200);
    const fa2 = await call("GET", "/v1/friends", { token: a.session });
    check("a and b are friends", fa2.json?.friends?.some((f) => f.id === b.id && f.username === b.username));
    expectRes("already friends", await call("POST", "/v1/invites", { token: a.session, body: { username: b.username } }), 409, "already_friends");
    expectRes("accept with no request", await call("POST", `/v1/invites/${c.id}/accept`, { token: a.session }), 404);
    // mutual auto-accept: c -> a, then a -> c
    await call("POST", "/v1/invites", { token: c.session, body: { username: a.username } });
    const mutual = await call("POST", "/v1/invites", { token: a.session, body: { username: c.username } });
    check("mutual invite auto-accepts", mutual.status === 200 && mutual.json?.status === "accepted", JSON.stringify(mutual.json));
    const rows = sql(`SELECT COUNT(*) AS n FROM friends WHERE (user_id = '${a.id}' AND friend_id = '${c.id}') OR (user_id = '${c.id}' AND friend_id = '${a.id}')`);
    check("both friend rows stored", rows[0]?.n === 2, JSON.stringify(rows));
  });

  await group("failed-lookup lock (5 per 15 min)", async () => {
    const { b, c } = users;
    clearRates();
    for (let i = 0; i < 5; i++) {
      expectRes(`miss ${i + 1}`, await call("POST", "/v1/invites", { token: b.session, body: { username: `nope_${RID}_${i}` } }), 404, "username_not_found");
    }
    expectRes("6th try locked even for a real name", await call("POST", "/v1/invites", { token: b.session, body: { username: c.username } }), 429, "too_many_tries");
    expectRes("username change locked too", await call("PUT", "/v1/me", { token: b.session, body: { name: "Badr E2E", username: `new_${RID}_b`, sharePhoto: true, sharePrs: true } }), 429, "too_many_tries");
    expectRes("profile edit keeping username still works", await call("PUT", "/v1/me", { token: b.session, body: { name: "Badr Two", sharePhoto: true, sharePrs: true } }), 200);
    clearRates();
    // username_taken answers count as misses
    for (let i = 0; i < 5; i++) {
      await call("PUT", "/v1/me", { token: b.session, body: { name: "Badr", username: c.username, sharePhoto: true, sharePrs: true } });
    }
    expectRes("5 taken answers -> locked", await call("POST", "/v1/invites", { token: b.session, body: { username: c.username } }), 429, "too_many_tries");
    clearRates();
  });

  await group("block / unblock / remove", async () => {
    const { a, b, c } = users;
    expectRes("c blocks b", await call("POST", `/v1/blocks/${b.id}`, { token: c.session }), 200);
    expectRes("b -> c is username_not_found while blocked", await call("POST", "/v1/invites", { token: b.session, body: { username: c.username } }), 404, "username_not_found");
    expectRes("c -> b also not found (either way)", await call("POST", "/v1/invites", { token: c.session, body: { username: b.username } }), 404, "username_not_found");
    expectRes("block self", await call("POST", `/v1/blocks/${c.id}`, { token: c.session }), 409, "self");
    expectRes("c unblocks b", await call("DELETE", `/v1/blocks/${b.id}`, { token: c.session }), 200);
    clearRates();
    expectRes("after unblock b -> c works", await call("POST", "/v1/invites", { token: b.session, body: { username: c.username } }), 200);
    await call("POST", `/v1/invites/${b.id}/accept`, { token: c.session });
    // block removes friendship + requests
    expectRes("c blocks friend b", await call("POST", `/v1/blocks/${b.id}`, { token: c.session }), 200);
    const fc = await call("GET", "/v1/friends", { token: c.session });
    check("block removed friendship", !fc.json?.friends?.some((f) => f.id === b.id));
    await call("DELETE", `/v1/blocks/${b.id}`, { token: c.session });
    // remove is silent and symmetric
    expectRes("a removes c", await call("DELETE", `/v1/friends/${c.id}`, { token: a.session }), 200);
    const fc2 = await call("GET", "/v1/friends", { token: c.session });
    check("removal is symmetric", !fc2.json?.friends?.some((f) => f.id === a.id));
    check("removal leaves no request/notice", !fc2.json?.incoming?.some((p) => p.id === a.id));
    clearRates();
  });

  await group("snapshots + stale-week leaderboard", async () => {
    const { a, b } = users;
    const today = riyadhToday();
    const ws = sundayOf(today);
    expectRes("valid snapshot", await call("PUT", "/v1/snapshot", { token: a.session, body: snap(ws) }), 200);
    const bad = [
      ["non-Sunday weekStart", snap(ws + 1)],
      ["weekStart too old", snap(ws - 14)],
      ["score 101", snap(ws, { score: 101 })],
      ["done 8", snap(ws, { done: 8 })],
      ["bad badge", snap(ws, { badge: "legend" })],
      ["11 PRs", snap(ws, { prs: Array.from({ length: 11 }, () => ({ exercise: "Squat", kg: 100, reps: 5 })) })],
      ["PR kg 1001", snap(ws, { prs: [{ exercise: "Squat", kg: 1001, reps: 5 }] })],
      ["PR reps 0", snap(ws, { prs: [{ exercise: "Squat", kg: 100, reps: 0 }] })],
      ["float done", snap(ws, { done: 1.5 })],
    ];
    for (const [name, body] of bad) expectRes(name, await call("PUT", "/v1/snapshot", { token: a.session, body }), 400, "bad_snapshot");
    // b uploads LAST week's snapshot -> stale
    expectRes("last-week snapshot accepted", await call("PUT", "/v1/snapshot", { token: b.session, body: snap(ws - 7, { score: 100, done: 3, due: 3 }) }), 200);
    const fa = await call("GET", "/v1/friends", { token: a.session });
    check("server today/weekStart are Riyadh", fa.json?.today === today && fa.json?.weekStart === ws, `today ${fa.json?.today} ws ${fa.json?.weekStart} want ${today}/${ws}`);
    const fbRow = fa.json?.friends?.find((f) => f.id === b.id);
    check("stale snapshot is returned with its old weekStart (client zeroes it)", fbRow?.snapshot?.weekStart === ws - 7, JSON.stringify(fbRow));
    const fb = await call("GET", "/v1/friends", { token: b.session });
    const faRow = fb.json?.friends?.find((f) => f.id === a.id);
    check("friend sees fresh snapshot fields", faRow?.snapshot?.score === 67 && faRow?.snapshot?.weekStart === ws && faRow?.snapshot?.prs?.[0]?.exercise === "Bench Press", JSON.stringify(faRow));
    check("snapshot has updatedAt", typeof faRow?.snapshot?.updatedAt === "number");
  });

  await group("sharePrs / sharePhoto off null server data", async () => {
    const { a, b } = users;
    expectRes("a turns PRs + photo off", await call("PUT", "/v1/me", { token: a.session, body: { name: "Ali E2E", sharePhoto: false, sharePrs: false } }), 200);
    const rows = sql(`SELECT prs FROM snapshots WHERE user_id = '${a.id}'`);
    check("D1 prs nulled immediately", rows[0] && rows[0].prs === null, JSON.stringify(rows));
    const fb = await call("GET", "/v1/friends", { token: b.session });
    const row = fb.json?.friends?.find((f) => f.id === a.id);
    check("friend sees prs null", row?.snapshot && row.snapshot.prs === null, JSON.stringify(row));
    check("friend sees photoUrl null", row && row.photoUrl === null);
    const ws = sundayOf(riyadhToday());
    await call("PUT", "/v1/snapshot", { token: a.session, body: snap(ws) });
    const rows2 = sql(`SELECT prs FROM snapshots WHERE user_id = '${a.id}'`);
    check("new upload with PRs stored as NULL while off", rows2[0]?.prs === null, JSON.stringify(rows2));
    await call("PUT", "/v1/me", { token: a.session, body: { name: "Ali E2E", sharePhoto: true, sharePrs: true } });
    await call("PUT", "/v1/snapshot", { token: a.session, body: snap(ws) });
    const fb2 = await call("GET", "/v1/friends", { token: b.session });
    const row2 = fb2.json?.friends?.find((f) => f.id === a.id);
    check("back on: photo + PRs visible again", row2?.photoUrl?.startsWith("https://") && row2?.snapshot?.prs?.length === 1, JSON.stringify(row2));
  });

  await group("nudges (one per pair per day)", async () => {
    const { a, b, c } = users;
    const n1 = await call("POST", `/v1/nudges/${b.id}`, { token: a.session });
    expectRes("a nudges b", n1, 200);
    check("pushed=false without FCM on staging", n1.json?.pushed === false);
    expectRes("second nudge same day", await call("POST", `/v1/nudges/${b.id}`, { token: a.session }), 409, "already_nudged");
    expectRes("nudge non-friend", await call("POST", `/v1/nudges/${c.id}`, { token: a.session }), 403, "not_friends");
    expectRes("nudge self", await call("POST", `/v1/nudges/${a.id}`, { token: a.session }), 409, "self");
    const fb = await call("GET", "/v1/friends", { token: b.session });
    check("b sees unseen nudge from a with username", fb.json?.nudges?.some((n) => n.fromId === a.id && n.username === a.username), JSON.stringify(fb.json?.nudges));
    const fb2 = await call("GET", "/v1/friends", { token: b.session });
    check("nudge marked seen after first GET", !fb2.json?.nudges?.some((n) => n.fromId === a.id));
    expectRes("b can nudge a back", await call("POST", `/v1/nudges/${a.id}`, { token: b.session }), 200);
  });

  await group("push tokens", async () => {
    const { a } = users;
    expectRes("bad platform", await call("PUT", "/v1/push", { token: a.session, body: { token: "t", platform: "ios" } }), 400, "bad_request");
    expectRes("token with spaces", await call("PUT", "/v1/push", { token: a.session, body: { token: "a b", platform: "android" } }), 400, "bad_request");
    for (let i = 0; i < 6; i++) {
      expectRes(`register token ${i + 1}`, await call("PUT", "/v1/push", { token: a.session, body: { token: `e2e-${RID}-tok-${i}`, platform: i % 2 ? "web" : "android" } }), 200);
    }
    const rows = sql(`SELECT COUNT(*) AS n FROM push_tokens WHERE user_id = '${a.id}'`);
    check("capped at 5 tokens per user", rows[0]?.n === 5, JSON.stringify(rows));
    expectRes("delete token", await call("DELETE", "/v1/push", { token: a.session, body: { token: `e2e-${RID}-tok-5` } }), 200);
    const rows2 = sql(`SELECT COUNT(*) AS n FROM push_tokens WHERE token = 'e2e-${RID}-tok-5'`);
    check("token deleted", rows2[0]?.n === 0);
  });

  await group("50 friend cap", async () => {
    const { a, b } = users;
    clearRates();
    // a has friend b; seed 49 dummy users + friend rows so a is at 50
    const now = Math.floor(Date.now() / 1000);
    const vals = [];
    const fr = [];
    for (let i = 0; i < 49; i++) {
      const id = `e2edummy${RID}${String(i).padStart(2, "0")}`;
      vals.push(`('${id}','sub-${id}','C${RID}${String(i).padStart(2, "0")}','d_${RID}_${i}','Dummy',NULL,1,1,1,${now})`);
      fr.push(`('${a.id}','${id}',${now})`, `('${id}','${a.id}',${now})`);
    }
    sql(`INSERT INTO users (id, sub, code, username, name, google_photo, share_photo, share_prs, joined, created_at) VALUES ${vals.join(",")}`);
    sql(`INSERT INTO friends (user_id, friend_id, created_at) VALUES ${fr.join(",")}`);
    const fa = await call("GET", "/v1/friends", { token: a.session });
    check("a now has 50 friends", fa.json?.friends?.length === 50, String(fa.json?.friends?.length));
    expectRes("a at cap cannot invite (even unknown name -> no probe)", await call("POST", "/v1/invites", { token: a.session, body: { username: `nobody_${RID}` } }), 409, "limit_reached");
    expectRes("c cannot invite a (target at cap)", await call("POST", "/v1/invites", { token: users.c.session, body: { username: a.username } }), 409, "limit_reached");
    // pending request to a cannot be accepted at cap
    sql(`INSERT INTO requests (from_id, to_id, created_at) VALUES ('${users.c.id}','${a.id}',${now})`);
    expectRes("a cannot accept at cap", await call("POST", `/v1/invites/${users.c.id}/accept`, { token: a.session }), 409, "limit_reached");
    sql(`DELETE FROM friends WHERE user_id LIKE 'e2edummy${RID}%' OR friend_id LIKE 'e2edummy${RID}%'`);
    sql(`DELETE FROM users WHERE id LIKE 'e2edummy${RID}%'`);
    check("b still a friend", (await call("GET", "/v1/friends", { token: b.session })).json?.friends?.some((f) => f.id === a.id));
    clearRates();
  });

  await group("write rate limit (120/hour)", async () => {
    const t = await login("rl");
    let last;
    for (let i = 0; i < 121; i++) {
      last = await call("PUT", "/v1/push", { token: t.session, body: { token: `e2e-${RID}-rl`, platform: "android" } });
      if (last.status !== 200) break;
    }
    expectRes("121st write -> rate_limited", last, 429, "rate_limited");
    clearRates();
    await call("DELETE", "/v1/me", { token: t.session });
  });

  await group("auth rate limit (20/hour per IP)", async () => {
    clearRates();
    let last;
    // Dual-stack hosts may alternate IPv4/IPv6 (two buckets), so allow up to 2x20+1 tries.
    for (let i = 0; i < 41; i++) {
      last = await call("POST", "/v1/auth/google", { body: { idToken: "x.y.z" } });
      if (last.status === 429) break;
    }
    expectRes("auth beyond 20/hour per IP -> rate_limited", last, 429, "rate_limited");
    clearRates();
  });

  await group("CORS", async () => {
    const allowed = await call("GET", "/v1/me", { token: users.a.session, origin: ALLOWED_ORIGIN });
    check("allowed origin echoed", allowed.headers.get("access-control-allow-origin") === ALLOWED_ORIGIN);
    check("Vary: Origin", (allowed.headers.get("vary") ?? "").includes("Origin"));
    const dev = await call("GET", "/v1/me", { token: users.a.session, origin: "http://localhost:5173" });
    check("dev origin echoed", dev.headers.get("access-control-allow-origin") === "http://localhost:5173");
    const foreign = await call("GET", "/v1/me", { token: users.a.session, origin: "https://evil.example" });
    check("foreign origin gets no ACAO", !foreign.headers.get("access-control-allow-origin"));
    const pre = await fetch(`${BASE}/v1/friends`, { method: "OPTIONS", headers: { Origin: ALLOWED_ORIGIN, "Access-Control-Request-Method": "GET", "Access-Control-Request-Headers": "authorization" } });
    check("preflight ok", pre.status === 204 || pre.status === 200, String(pre.status));
    check("preflight allows Authorization", (pre.headers.get("access-control-allow-headers") ?? "").toLowerCase().includes("authorization"));
    check("preflight max-age 86400", pre.headers.get("access-control-max-age") === "86400");
    const none = await call("GET", "/v1/me", { token: users.a.session });
    check("no Origin (Android) works", none.status === 200);
  });

  await group("username change + DELETE /me wipe", async () => {
    const { a, b, c } = users;
    const newName = `e2e_${RID}_a2`;
    const ch = await call("PUT", "/v1/me", { token: a.session, body: { name: "Ali E2E", username: newName, sharePhoto: true, sharePrs: true } });
    check("username changed", ch.json?.username === newName, JSON.stringify(ch.json));
    clearRates();
    expectRes("old username no longer found", await call("POST", "/v1/invites", { token: c.session, body: { username: a.username } }), 404, "username_not_found");
    const fb = await call("GET", "/v1/friends", { token: b.session });
    check("friends see new username", fb.json?.friends?.some((f) => f.id === a.id && f.username === newName));
    // give a rows in every table, then wipe
    await call("POST", "/v1/invites", { token: a.session, body: { username: c.username } });
    await call("POST", `/v1/blocks/e2e-ghost-${RID}`, { token: a.session });
    await call("PUT", "/v1/push", { token: a.session, body: { token: `e2e-${RID}-wipe`, platform: "web" } });
    const before = rowsFor(a.id);
    check("a has rows before wipe", before > 3, String(before));
    expectRes("DELETE /me", await call("DELETE", "/v1/me", { token: a.session }), 200);
    const after = rowsFor(a.id);
    check("every row referencing a is gone", after === 0, String(after));
    expectRes("old session dead", await call("GET", "/v1/me", { token: a.session }), 401, "unauthorized");
    const fb2 = await call("GET", "/v1/friends", { token: b.session });
    check("a vanished from b's list", !fb2.json?.friends?.some((f) => f.id === a.id));
    expectRes("a's username is free again", await call("PUT", "/v1/me", { token: c.session, body: { name: "Carl", username: newName, sharePhoto: true, sharePrs: true } }), 200);
  });

  current = "cleanup";
  for (const k of ["b", "c"]) await call("DELETE", "/v1/me", { token: users[k].session });
  const left = sql(`SELECT COUNT(*) AS n FROM users WHERE sub LIKE 'e2e-${RID}-%'`);
  check("all run users deleted", left[0]?.n === 0, JSON.stringify(left));
  clearRates();

  // ---- report ----
  const byGroup = new Map();
  for (const r of results) {
    const g = byGroup.get(r.group) ?? { pass: 0, fail: 0 };
    r.ok ? g.pass++ : g.fail++;
    byGroup.set(r.group, g);
  }
  console.log("\n| Group | Pass | Fail |\n|---|---|---|");
  for (const [g, v] of byGroup) console.log(`| ${g} | ${v.pass} | ${v.fail} |`);
  const failed = results.filter((r) => !r.ok);
  console.log(`\nTOTAL ${results.length - failed.length}/${results.length} passed`);
  for (const f of failed) console.log(`FAIL ${f.group} :: ${f.name} ${f.detail}`);
  process.exit(failed.length ? 1 : 0);
}

function rowsFor(id) {
  const q = [
    `SELECT COUNT(*) AS n FROM users WHERE id = '${id}'`,
    `SELECT COUNT(*) AS n FROM sessions WHERE user_id = '${id}'`,
    `SELECT COUNT(*) AS n FROM snapshots WHERE user_id = '${id}'`,
    `SELECT COUNT(*) AS n FROM friends WHERE user_id = '${id}' OR friend_id = '${id}'`,
    `SELECT COUNT(*) AS n FROM requests WHERE from_id = '${id}' OR to_id = '${id}'`,
    `SELECT COUNT(*) AS n FROM blocks WHERE blocker = '${id}' OR blocked = '${id}'`,
    `SELECT COUNT(*) AS n FROM nudges WHERE from_id = '${id}' OR to_id = '${id}'`,
    `SELECT COUNT(*) AS n FROM push_tokens WHERE user_id = '${id}'`,
  ];
  return q.reduce((sum, one) => sum + (sql(one)[0]?.n ?? 0), 0);
}

main().catch((e) => {
  console.error(e);
  process.exit(2);
});
