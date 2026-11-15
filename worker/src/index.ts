import { authenticateRequest } from "./auth";
import { AppContext } from "./context";
import { handleAuthGoogle } from "./routes/auth";
import {
  handleAcceptInvite,
  handleBlockUser,
  handleDeclineInvite,
  handleGetFriends,
  handleRemoveFriend,
  handleSendInvite,
  handleUnblockUser,
} from "./routes/friends";
import {
  handleDeleteMe,
  handleGetMe,
  handlePutMe,
  handleRotateCode,
} from "./routes/me";
import { handleNudge } from "./routes/nudges";
import { handleDeletePush, handlePutPush } from "./routes/push";
import { handlePutSnapshot } from "./routes/snapshot";
import { D1Store, Store } from "./store";
import { Env, FetchFn, HttpError } from "./types";

export interface HandlerDeps {
  store?: Store;
  nowMs?: () => number;
  fetchFn?: FetchFn;
}

function buildCorsHeaders(
  origin: string | null,
  allowedOriginsRaw: string,
): Record<string, string> {
  if (!origin) return {};
  const allowed = allowedOriginsRaw
    .split(",")
    .map((s) => s.trim())
    .filter(Boolean);
  if (!allowed.includes(origin)) return {};
  return {
    "Access-Control-Allow-Origin": origin,
    "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE",
    "Access-Control-Allow-Headers": "Authorization, Content-Type",
    "Access-Control-Max-Age": "86400",
    Vary: "Origin",
  };
}

function jsonResponse(
  status: number,
  body: unknown,
  cors: Record<string, string>,
): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      ...cors,
    },
  });
}

function decodeSegment(raw: string): string {
  try {
    const decoded = decodeURIComponent(raw);
    if (!decoded) throw new Error("empty");
    return decoded;
  } catch {
    throw new HttpError(400, "bad_request");
  }
}

function getClientIp(request: Request): string {
  const cfIp = request.headers.get("CF-Connecting-IP");
  if (cfIp) return cfIp.trim();
  const xff = request.headers.get("X-Forwarded-For");
  if (xff) return xff.split(",")[0].trim() || "unknown";
  return "unknown";
}

async function dispatch(ctx: AppContext): Promise<unknown> {
  const { request } = ctx;
  const method = request.method.toUpperCase();
  const path = new URL(request.url).pathname;

  if (method === "POST" && path === "/v1/auth/google") {
    return await handleAuthGoogle(ctx);
  }

  if (!path.startsWith("/v1/")) {
    throw new HttpError(404, "not_found");
  }

  const user = await authenticateRequest(request, ctx.store, ctx.nowSec);

  if (method === "GET" && path === "/v1/me") {
    return handleGetMe(user);
  }
  if (method === "PUT" && path === "/v1/me") {
    return await handlePutMe(ctx, user);
  }
  if (method === "DELETE" && path === "/v1/me") {
    return await handleDeleteMe(ctx, user);
  }
  if (method === "POST" && path === "/v1/me/code") {
    return await handleRotateCode(ctx, user);
  }
  if (method === "PUT" && path === "/v1/snapshot") {
    return await handlePutSnapshot(ctx, user);
  }
  if (method === "GET" && path === "/v1/friends") {
    return await handleGetFriends(ctx, user);
  }
  if (method === "POST" && path === "/v1/invites") {
    return await handleSendInvite(ctx, user);
  }
  if (method === "PUT" && path === "/v1/push") {
    return await handlePutPush(ctx, user);
  }
  if (method === "DELETE" && path === "/v1/push") {
    return await handleDeletePush(ctx, user);
  }

  let m = path.match(/^\/v1\/invites\/([^/]+)\/accept$/);
  if (m && method === "POST") {
    return await handleAcceptInvite(ctx, user, decodeSegment(m[1]));
  }

  m = path.match(/^\/v1\/invites\/([^/]+)\/decline$/);
  if (m && method === "POST") {
    return await handleDeclineInvite(ctx, user, decodeSegment(m[1]));
  }

  m = path.match(/^\/v1\/friends\/([^/]+)$/);
  if (m && method === "DELETE") {
    return await handleRemoveFriend(ctx, user, decodeSegment(m[1]));
  }

  m = path.match(/^\/v1\/blocks\/([^/]+)$/);
  if (m && method === "POST") {
    return await handleBlockUser(ctx, user, decodeSegment(m[1]));
  }
  if (m && method === "DELETE") {
    return await handleUnblockUser(ctx, user, decodeSegment(m[1]));
  }

  m = path.match(/^\/v1\/nudges\/([^/]+)$/);
  if (m && method === "POST") {
    return await handleNudge(ctx, user, decodeSegment(m[1]));
  }

  throw new HttpError(404, "not_found");
}

export async function handleRequest(
  request: Request,
  env: Env,
  deps: HandlerDeps = {},
): Promise<Response> {
  const cors = buildCorsHeaders(request.headers.get("Origin"), env.ALLOWED_ORIGINS);
  if (request.method.toUpperCase() === "OPTIONS") {
    return new Response(null, { status: 204, headers: cors });
  }

  try {
    const nowMs = (deps.nowMs ?? Date.now)();
    const ctx: AppContext = {
      request,
      env,
      store: deps.store ?? new D1Store(env.DB),
      nowMs,
      nowSec: Math.floor(nowMs / 1000),
      fetchFn: deps.fetchFn ?? ((input, init) => fetch(input, init)),
      ip: getClientIp(request),
    };
    const result = await dispatch(ctx);
    return jsonResponse(200, result, cors);
  } catch (err) {
    if (err instanceof HttpError) {
      return jsonResponse(err.status, { error: err.code }, cors);
    }
    console.error("Unhandled worker error", err);
    return jsonResponse(500, { error: "server_error" }, cors);
  }
}

export default {
  async fetch(request, env): Promise<Response> {
    return await handleRequest(request, env);
  },
} satisfies ExportedHandler<Env>;
