import { base64UrlEncode, toArrayBuffer } from "./auth";
import { Store } from "./store";
import { FetchFn } from "./types";

const FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
const OAUTH_TOKEN_URL = "https://oauth2.googleapis.com/token";

interface ServiceAccount {
  project_id: string;
  client_email: string;
  private_key: string;
}

interface TokenCacheEntry {
  accessToken: string;
  expiresAtSec: number;
}

const tokenCache = new Map<string, TokenCacheEntry>();

export function clearFcmTokenCache(): void {
  tokenCache.clear();
}

function parseServiceAccount(raw: string): ServiceAccount | null {
  try {
    const parsed: unknown = JSON.parse(raw);
    if (parsed === null || typeof parsed !== "object") return null;
    const obj = parsed as Record<string, unknown>;
    if (
      typeof obj.project_id !== "string" ||
      !obj.project_id ||
      typeof obj.client_email !== "string" ||
      !obj.client_email ||
      typeof obj.private_key !== "string" ||
      !obj.private_key
    ) {
      return null;
    }
    return {
      project_id: obj.project_id,
      client_email: obj.client_email,
      private_key: obj.private_key,
    };
  } catch {
    return null;
  }
}

function pemToArrayBuffer(pem: string): ArrayBuffer {
  const b64 = pem
    .replace(/-----BEGIN PRIVATE KEY-----/g, "")
    .replace(/-----END PRIVATE KEY-----/g, "")
    .replace(/\s+/g, "");
  const bin = atob(b64);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) {
    out[i] = bin.charCodeAt(i);
  }
  return out.buffer;
}

async function getAccessToken(
  sa: ServiceAccount,
  nowSec: number,
  fetchFn: FetchFn,
): Promise<string | null> {
  const cacheKey = `${sa.project_id}:${sa.client_email}`;
  const cached = tokenCache.get(cacheKey);
  if (cached && nowSec < cached.expiresAtSec) {
    return cached.accessToken;
  }

  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToArrayBuffer(sa.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );

  const headerB64 = base64UrlEncode(new TextEncoder().encode(JSON.stringify({ alg: "RS256", typ: "JWT" })));
  const claimsB64 = base64UrlEncode(
    new TextEncoder().encode(
      JSON.stringify({
        iss: sa.client_email,
        scope: FCM_SCOPE,
        aud: OAUTH_TOKEN_URL,
        iat: nowSec,
        exp: nowSec + 3600,
      }),
    ),
  );
  const signingInput = `${headerB64}.${claimsB64}`;
  const sig = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    toArrayBuffer(signingInput),
  );
  const assertion = `${signingInput}.${base64UrlEncode(new Uint8Array(sig))}`;

  const res = await fetchFn(OAUTH_TOKEN_URL, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: `grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=${encodeURIComponent(assertion)}`,
  });

  if (!res.ok) {
    console.error("FCM OAuth exchange failed", res.status);
    return null;
  }

  const body = (await res.json()) as { access_token?: unknown; expires_in?: unknown };
  if (typeof body.access_token !== "string" || !body.access_token) {
    return null;
  }
  const expiresIn =
    typeof body.expires_in === "number" && Number.isFinite(body.expires_in)
      ? body.expires_in
      : 3600;
  tokenCache.set(cacheKey, {
    accessToken: body.access_token,
    expiresAtSec: nowSec + expiresIn - 60,
  });
  return body.access_token;
}

/** Upper bound on FCM send requests per Worker invocation (Workers free plan allows 50 subrequests in total). */
export const MAX_FCM_SENDS = 40;

export type PushData = { type: "ping" | "activity"; fromId: string; name: string };

/**
 * Sends a DATA-ONLY message to each token (the app formats the text in the receiver's language).
 * Tokens FCM reports as gone are deleted. Returns true when at least one send succeeded.
 */
export async function sendPush(params: {
  fcmSaRaw: string | undefined;
  tokens: string[];
  data: PushData;
  store: Store;
  nowSec: number;
  fetchFn: FetchFn;
}): Promise<boolean> {
  const { fcmSaRaw, tokens, data, store, nowSec, fetchFn } = params;
  if (!fcmSaRaw || tokens.length === 0) return false;

  const sa = parseServiceAccount(fcmSaRaw);
  if (!sa) {
    console.error("FCM_SA is malformed");
    return false;
  }

  let accessToken: string | null;
  try {
    accessToken = await getAccessToken(sa, nowSec, fetchFn);
  } catch {
    console.error("FCM token acquisition error");
    return false;
  }
  if (!accessToken) return false;

  const sendUrl = `https://fcm.googleapis.com/v1/projects/${encodeURIComponent(sa.project_id)}/messages:send`;
  const deadTokens: string[] = [];
  let sentCount = 0;

  for (const token of tokens.slice(0, MAX_FCM_SENDS)) {
    try {
      const res = await fetchFn(sendUrl, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${accessToken}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          message: {
            token,
            data,
            android: { priority: "high" },
            webpush: { headers: { Urgency: "high" } },
          },
        }),
      });
      if (res.ok) {
        sentCount++;
        continue;
      }
      const errText = await res.text().catch(() => "");
      if (res.status === 404 || errText.includes("UNREGISTERED")) {
        deadTokens.push(token);
      } else {
        console.error("FCM send failed", res.status);
      }
    } catch {
      console.error("FCM send request error");
    }
  }

  if (deadTokens.length > 0) {
    await store.deletePushTokens(deadTokens);
  }

  return sentCount > 0;
}
