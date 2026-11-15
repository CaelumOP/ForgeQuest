import {
  createSessionForUser,
  generateInviteCode,
  generateUserId,
  verifyGoogleIdToken,
} from "../auth";
import { AppContext } from "../context";
import { enforceAuthRateLimit } from "../rate";
import { HttpError, Me, toMe } from "../types";
import { readJsonObject } from "../validate";

export async function handleAuthGoogle(
  ctx: AppContext,
): Promise<{ session: string; user: Me }> {
  await enforceAuthRateLimit(ctx.store, ctx.ip, ctx.nowSec);
  const body = await readJsonObject(ctx.request);
  if (typeof body.idToken !== "string" || body.idToken.length === 0) {
    throw new HttpError(400, "bad_request");
  }
  const { sub, picture } = await verifyGoogleIdToken(
    body.idToken,
    ctx.env,
    ctx.nowMs,
    ctx.fetchFn,
  );
  const user = await ctx.store.upsertUserBySub({
    sub,
    googlePhoto: picture,
    nowSec: ctx.nowSec,
    generateId: generateUserId,
    generateCode: generateInviteCode,
  });
  const session = await createSessionForUser(ctx.store, user.id, ctx.nowSec);
  return { session, user: toMe(user) };
}
