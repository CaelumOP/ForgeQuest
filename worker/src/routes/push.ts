import { AppContext } from "../context";
import { enforceWriteRateLimit } from "../rate";
import { UserRow } from "../types";
import { readJsonObject, validatePushDelete, validatePushPut } from "../validate";

export async function handlePutPush(
  ctx: AppContext,
  user: UserRow,
): Promise<{ ok: true }> {
  await enforceWriteRateLimit(ctx.store, user.id, ctx.nowSec);
  const body = await readJsonObject(ctx.request);
  const { token, platform } = validatePushPut(body);
  await ctx.store.upsertPushToken(token, user.id, platform, ctx.nowSec);
  return { ok: true };
}

export async function handleDeletePush(
  ctx: AppContext,
  user: UserRow,
): Promise<{ ok: true }> {
  await enforceWriteRateLimit(ctx.store, user.id, ctx.nowSec);
  const body = await readJsonObject(ctx.request);
  const { token } = validatePushDelete(body);
  await ctx.store.deletePushTokenForUser(token, user.id);
  return { ok: true };
}
