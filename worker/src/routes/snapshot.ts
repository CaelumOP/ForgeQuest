import { AppContext } from "../context";
import { enforceWriteRateLimit } from "../rate";
import { riyadhEpochDay } from "../time";
import { HttpError, UserRow } from "../types";
import { readJsonObject, validateSnapshot } from "../validate";

export async function handlePutSnapshot(
  ctx: AppContext,
  user: UserRow,
): Promise<{ ok: true }> {
  await enforceWriteRateLimit(ctx.store, user.id, ctx.nowSec);
  if (user.joined !== 1) {
    throw new HttpError(409, "not_joined");
  }
  const body = await readJsonObject(ctx.request);
  const today = riyadhEpochDay(ctx.nowMs);
  const snapshot = validateSnapshot(body, today);
  if (user.share_prs !== 1) {
    snapshot.prs = null;
  }
  await ctx.store.upsertSnapshot(user.id, snapshot, ctx.nowSec);
  return { ok: true };
}
