import { Store } from "./store";
import { Env, FetchFn, HttpError, UserRow } from "./types";

const INVITE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
const SESSION_TTL_SEC = 180 * 86400;
const SESSION_SLIDE_THRESHOLD_SEC = 30 * 86400;

interface JwksCacheEntry {
  keys: Map<string, CryptoKey>;
  expiresAtMs: number;
  fetchedAtMs: number;
}

const jwksCache = new Map<string, JwksCacheEntry>();

export function clearJwksCache(): void {
  jwksCache.clear();
}

export function toArrayBuffer(input: string): ArrayBuffer {
  const encoded = new TextEncoder().encode(input);
  const copy = new Uint8Array(encoded.length);
  copy.set(encoded);
  return copy.buffer;
}

export function base64UrlEncode(bytes: Uint8Array): string {
  let bin = "";
  for (let i = 0; i < bytes.length; i++) {
    bin += String.fromCharCode(bytes[i]);
  }
  return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

export function base64UrlDecode(str: string): ArrayBuffer {
  if (!str || !/^[A-Za-z0-9\-_]+$/.test(str)) {
    throw new HttpError(401, "invalid_token");
  }
  const padded = str.replace(/-/g, "+").replace(/_/g, "/") + "=".repeat((4 - (str.length % 4)) % 4);
  let bin: string;
  try {
    bin = atob(padded);
  } catch {
    throw new HttpError(401, "invalid_token");
  }
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) {
    out[i] = bin.charCodeAt(i);
  }
  return out.buffer;
}

export async function sha256Hex(input: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", toArrayBuffer(input));
  const bytes = new Uint8Array(digest);
  let hex = "";
  for (let i = 0; i < bytes.length; i++) {
    hex += bytes[i].toString(16).padStart(2, "0");
  }
  return hex;
}

export function generateUserId(): string {
  const bytes = new Uint8Array(8);
  crypto.getRandomValues(bytes);
  let hex = "";
  for (let i = 0; i < bytes.length; i++) {
    hex += bytes[i].toString(16).padStart(2, "0");
  }
  return hex;
}

export function generateSessionToken(): string {
  const bytes = new Uint8Array(32);
  crypto.getRandomValues(bytes);
  return base64UrlEncode(bytes);
}

export function generateInviteCode(): string {
  const limit = Math.floor(256 / INVITE_ALPHABET.length) * INVITE_ALPHABET.length;
  let code = "";
  const buf = new Uint8Array(16);
  while (code.length < 6) {
    crypto.getRandomValues(buf);
    for (let i = 0; i < buf.length && code.length < 6; i++) {
      if (buf[i] < limit) {
        code += INVITE_ALPHABET[buf[i] % INVITE_ALPHABET.length];
      }
    }
  }
  return code;
}

function parseMaxAge(cacheControl: string | null): number {
  if (!cacheControl) return 300;
  if (/no-store|no-cache/i.test(cacheControl)) return 0;
  const match = cacheControl.match(/max-age\s*=\s*(\d+)/i);
  return match ? Number(match[1]) : 300;
}

async function getJwksKey(
  jwksUrl: string,
  kid: string,
  nowMs: number,
  fetchFn: FetchFn,
): Promise<CryptoKey | null> {
  const cached = jwksCache.get(jwksUrl);
  if (cached && nowMs < cached.expiresAtMs) {
    const hit = cached.keys.get(kid);
    if (hit) return hit;
    // Key not in fresh cache; avoid repeated refetch storms for bogus kids.
    if (nowMs - cached.fetchedAtMs < 10_000) {
      return null;
    }
  }

  const res = await fetchFn(jwksUrl);
  if (!res.ok) {
    throw new HttpError(502, "jwks_fetch_failed");
  }
  let body: unknown;
  try {
    body = await res.json();
  } catch {
    throw new HttpError(502, "jwks_fetch_failed");
  }

  const keysList =
    body !== null && typeof body === "object" && Array.isArray((body as { keys?: unknown }).keys)
      ? ((body as { keys: unknown[] }).keys as Array<
          JsonWebKey & { kid?: string; alg?: string; use?: string; kty?: string }
        >)
      : [];

  const keys = new Map<string, CryptoKey>();
  for (const jwk of keysList) {
    if (
      jwk &&
      typeof jwk.kid === "string" &&
      jwk.kid.length > 0 &&
      jwk.kty === "RSA" &&
      (!jwk.alg || jwk.alg === "RS256") &&
      (!jwk.use || jwk.use === "sig")
    ) {
      try {
        const key = await crypto.subtle.importKey(
          "jwk",
          jwk,
          { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
          false,
          ["verify"],
        );
        keys.set(jwk.kid, key);
      } catch {
        // Skip malformed individual JWKs.
      }
    }
  }

  const maxAgeSec = parseMaxAge(res.headers.get("cache-control"));
  jwksCache.set(jwksUrl, {
    keys,
    expiresAtMs: nowMs + maxAgeSec * 1000,
    fetchedAtMs: nowMs,
  });

  return keys.get(kid) ?? null;
}

function decodeJwtPart(b64: string): Record<string, unknown> {
  const buf = base64UrlDecode(b64);
  try {
    const text = new TextDecoder().decode(buf);
    const parsed: unknown = JSON.parse(text);
    if (parsed === null || typeof parsed !== "object" || Array.isArray(parsed)) {
      throw new Error("not object");
    }
    return parsed as Record<string, unknown>;
  } catch {
    throw new HttpError(401, "invalid_token");
  }
}

export async function verifyGoogleIdToken(
  token: string,
  env: Env,
  nowMs: number,
  fetchFn: FetchFn = fetch,
): Promise<{ sub: string; picture: string | null }> {
  const parts = token.split(".");
  if (parts.length !== 3 || !parts[0] || !parts[1] || !parts[2]) {
    throw new HttpError(401, "invalid_token");
  }

  const header = decodeJwtPart(parts[0]);
  if (header.alg !== "RS256" || typeof header.kid !== "string" || header.kid.length === 0) {
    throw new HttpError(401, "invalid_token");
  }

  const payload = decodeJwtPart(parts[1]);
  const nowSec = Math.floor(nowMs / 1000);
  const allowedIssuers = env.GOOGLE_ISSUERS.split(",")
    .map((s) => s.trim())
    .filter(Boolean);

  if (
    typeof payload.sub !== "string" ||
    payload.sub.length === 0 ||
    typeof payload.iss !== "string" ||
    !allowedIssuers.includes(payload.iss) ||
    payload.aud !== env.GOOGLE_AUD ||
    typeof payload.exp !== "number" ||
    !Number.isFinite(payload.exp) ||
    payload.exp <= nowSec - 60
  ) {
    throw new HttpError(401, "invalid_token");
  }
  if (
    payload.nbf !== undefined &&
    (typeof payload.nbf !== "number" || !Number.isFinite(payload.nbf) || payload.nbf > nowSec + 60)
  ) {
    throw new HttpError(401, "invalid_token");
  }

  const sigBuf = base64UrlDecode(parts[2]);
  const key = await getJwksKey(env.GOOGLE_JWKS_URL, header.kid, nowMs, fetchFn);
  if (!key) {
    throw new HttpError(401, "invalid_token");
  }

  const valid = await crypto.subtle.verify(
    "RSASSA-PKCS1-v1_5",
    key,
    sigBuf,
    toArrayBuffer(`${parts[0]}.${parts[1]}`),
  );
  if (!valid) {
    throw new HttpError(401, "invalid_token");
  }

  const picture =
    typeof payload.picture === "string" &&
    payload.picture.length > 0 &&
    payload.picture.length <= 1024
      ? payload.picture
      : null;

  return { sub: payload.sub, picture };
}

export async function createSessionForUser(
  store: Store,
  userId: string,
  nowSec: number,
): Promise<string> {
  const token = generateSessionToken();
  const tokenHash = await sha256Hex(token);
  await store.createSession(tokenHash, userId, nowSec + SESSION_TTL_SEC);
  return token;
}

export async function authenticateRequest(
  request: Request,
  store: Store,
  nowSec: number,
): Promise<UserRow> {
  const auth = request.headers.get("Authorization");
  if (!auth) {
    throw new HttpError(401, "unauthorized");
  }
  const match = auth.match(/^Bearer\s+(\S+)$/i);
  if (!match) {
    throw new HttpError(401, "unauthorized");
  }
  const tokenHash = await sha256Hex(match[1]);
  const found = await store.getSessionWithUser(tokenHash);
  if (!found) {
    throw new HttpError(401, "unauthorized");
  }
  if (found.session.expires_at <= nowSec) {
    await store.deleteSession(tokenHash);
    throw new HttpError(401, "unauthorized");
  }
  if (found.session.expires_at - nowSec < SESSION_SLIDE_THRESHOLD_SEC) {
    await store.extendSession(tokenHash, nowSec + SESSION_TTL_SEC);
  }
  return found.user;
}
