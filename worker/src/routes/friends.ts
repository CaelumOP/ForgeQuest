import { AppContext } from "../context";
import {
  assertNotInviteLocked,
  enforceWriteRateLimit,
  recordInviteFailure,
} from "../rate";
import { riyadhEpochDay, sundayWeekStart } from "../time";
import {
  Friend,
  HttpError,
  Me,
  NudgeView,
  Person,
  toMe,
  toPerson,
  toSnapshot,
  UserRow,
} from "../types";
import { normalizeUsername, readJsonObject } from "../validate";

const FRIEND_CAP = 50;

export async function handleGetFriends(
  ctx: AppContext,
  user: UserRow,
): Promise<{
  me: Me;
  today: number;
  weekStart: number;
  friends: Friend[];
  incoming: Person[];
  outgoing: Person[];
  nudges: NudgeView[];
}> {
  const today = riyadhEpochDay(ctx.nowMs);
  const weekStart = sundayWeekStart(today);
  const view = await ctx.store.getFriendsView(user.id);
  return {
    me: toMe(user),
    today,
    weekStart,
    friends: view.friends.map(({ user: friend, snapshot }) => ({
      ...toPerson(friend),
      snapshot: snapshot ? toSnapshot(snapshot, friend.share_prs === 1) : null,
    })),
    incoming: view.incoming.map(toPerson),
    outgoing: view.outgoing.map(toPerson),
    nudges: view.nudges,
  };
}

function assertJoined(user: UserRow): void {
  if (user.joined !== 1 || !user.username) {
    throw new HttpError(409, "not_joined");
  }
}

export async function handleSendInvite(
  ctx: AppContext,
  user: UserRow,
): Promise<{ status: "sent" | "accepted" }> {
  await enforceWriteRateLimit(ctx.store, user.id, ctx.nowSec);
  assertJoined(user);
  await assertNotInviteLocked(ctx.store, user.id, ctx.nowSec);

  const body = await readJsonObject(ctx.request);
  if (typeof body.username !== "string") {
    throw new HttpError(400, "bad_request");
  }
  // Own cap first: at 50 friends nothing can succeed, so answer before any lookup (no probing at the cap).
  if ((await ctx.store.countFriends(user.id)) >= FRIEND_CAP) {
    throw new HttpError(409, "limit_reached");
  }

  const notFound = async (): Promise<never> => {
    await recordInviteFailure(ctx.store, user.id, ctx.nowSec);
    throw new HttpError(404, "username_not_found");
  };

  const username = normalizeUsername(body.username);
  if (username === null) return notFound();
  if (username === user.username) {
    throw new HttpError(409, "self");
  }
  const target = await ctx.store.findJoinedUserByUsername(username);
  if (!target) return notFound();
  if (await ctx.store.isBlockedEitherWay(user.id, target.id)) return notFound();
  if (await ctx.store.areFriends(user.id, target.id)) {
    throw new HttpError(409, "already_friends");
  }

  if (await ctx.store.hasRequest(target.id, user.id)) {
    const result = await ctx.store.acceptFriendship({
      userA: user.id,
      userB: target.id,
      nowSec: ctx.nowSec,
      cap: FRIEND_CAP,
    });
    if (result === "accepted") {
      return { status: "accepted" };
    }
    if (result === "already_friends") {
      throw new HttpError(409, "already_friends");
    }
    if (result === "blocked") return notFound();
    if (result === "limit_reached") {
      await recordInviteFailure(ctx.store, user.id, ctx.nowSec);
      throw new HttpError(409, "limit_reached");
    }
  }

  if ((await ctx.store.countFriends(target.id)) >= FRIEND_CAP) {
    // Reveals that the name exists, so it costs a try like a miss.
    await recordInviteFailure(ctx.store, user.id, ctx.nowSec);
    throw new HttpError(409, "limit_reached");
  }

  await ctx.store.createRequest(user.id, target.id, ctx.nowSec);
  return { status: "sent" };
}

export async function handleAcceptInvite(
  ctx: AppContext,
  user: UserRow,
  fromId: string,
): Promise<{ ok: true }> {
  await enforceWriteRateLimit(ctx.store, user.id, ctx.nowSec);
  assertJoined(user);
  if (fromId === user.id) {
    throw new HttpError(409, "self");
  }
  const result = await ctx.store.acceptFriendship({
    userA: user.id,
    userB: fromId,
    nowSec: ctx.nowSec,
    cap: FRIEND_CAP,
  });
  if (result === "accepted") {
    return { ok: true };
  }
  if (result === "already_friends") {
    throw new HttpError(409, "already_friends");
  }
  if (result === "limit_reached") {
    throw new HttpError(409, "limit_reached");
  }
  throw new HttpError(404, "not_found");
}

export async function handleDeclineInvite(
  ctx: AppContext,
  user: UserRow,
  otherId: string,
): Promise<{ ok: true }> {
  await enforceWriteRateLimit(ctx.store, user.id, ctx.nowSec);
  await ctx.store.declineRequestsBetween(user.id, otherId);
  return { ok: true };
}

export async function handleRemoveFriend(
  ctx: AppContext,
  user: UserRow,
  friendId: string,
): Promise<{ ok: true }> {
  await enforceWriteRateLimit(ctx.store, user.id, ctx.nowSec);
  await ctx.store.removeFriendship(user.id, friendId);
  return { ok: true };
}

export async function handleBlockUser(
  ctx: AppContext,
  user: UserRow,
  targetId: string,
): Promise<{ ok: true }> {
  await enforceWriteRateLimit(ctx.store, user.id, ctx.nowSec);
  if (targetId === user.id) {
    throw new HttpError(409, "self");
  }
  await ctx.store.blockUser(user.id, targetId);
  return { ok: true };
}

export async function handleUnblockUser(
  ctx: AppContext,
  user: UserRow,
  targetId: string,
): Promise<{ ok: true }> {
  await enforceWriteRateLimit(ctx.store, user.id, ctx.nowSec);
  await ctx.store.unblockUser(user.id, targetId);
  return { ok: true };
}
