// Port of app/.../data/WorkoutRepository.kt: library seeding, plans, the live session, history and records.
// Live workout rule: every tap is its own one-row write (putSet / putSession), never a long transaction.
import {
  db, putSession, putSet, deleteSet,
  type ExerciseRow, type PlanDayRow, type PlanItemRow, type PlanRow, type SessionRow, type SetRow,
} from './db';
import { configuredEquipment, loadSettings, weightUnit } from './settings';
import { EXERCISE_CATALOG } from './seed/exercise-catalog';
import { EXERCISE_NAMES_AR } from './seed/exercise-names-ar';
import { STARTER_PLANS_AR, type PlanDraft } from './seed/starter-plans';
import { useLive } from './live';
import { todayEpochDay } from '../domain/dates';
import { effortOf } from '../domain/effort';
import { translatePlanItem } from '../domain/exercise-swap-engine';
import { MUSCLES, type ExerciseKind, type Muscle } from '../domain/muscle';
import { personalRecords, recordScore, totalXp } from '../domain/progress';
import { harderVersion, readyForHarder } from '../domain/exercise-tiers';
import { nextPrefill, type PastSet } from '../domain/progression';
import { scoresForSessions, type WorkoutScoreResult } from '../domain/workout-score';

export type PlanDayWithItems = { day: PlanDayRow; items: PlanItemRow[] };
export type ActivePlan = { plan: PlanRow; days: PlanDayWithItems[] };
export type FinishResult = { sessionTitle: string; doneSets: number; prs: string[]; xpGained: number };

const REST_TITLE = 'Rest day';
const QUICK_TITLE = 'Quick workout';
const HOLD_CEILING_NOTE = 'Hold ceiling reached';

/** Days the plan trains on: not a rest day and at least one exercise. */
export const trainingDays = (p: ActivePlan): Set<number> =>
  new Set(p.days.filter((d) => !d.day.isRest && d.items.length > 0).map((d) => d.day.dayOfWeek));

export const planDay = (p: ActivePlan, dayOfWeek: number): PlanDayWithItems | undefined =>
  p.days.find((d) => d.day.dayOfWeek === dayOfWeek);

/** The active plan as a draft, for comparing and for applying coach changes. */
export function planToDraft(p: ActivePlan): PlanDraft {
  return {
    name: p.plan.name,
    description: p.plan.description,
    days: [...p.days].sort((a, b) => a.day.dayOfWeek - b.day.dayOfWeek).map((d) => ({
      dayOfWeek: d.day.dayOfWeek, title: d.day.title, isRest: d.day.isRest, note: d.day.note,
      items: [...d.items].sort((a, b) => a.position - b.position).map((i) => ({
        exerciseId: i.exerciseId, name: null, kind: null, muscles: [], block: i.block, circuit: i.circuit,
        sets: i.sets, repsMin: i.repsMin, repsMax: i.repsMax, seconds: i.seconds, weightKg: i.weightKg,
        restSeconds: i.restSeconds, note: i.note, videoUrl: null,
      })),
    })),
  };
}

// ---------------------------------------------------------------- Library

/** Adds missing built-in exercises, then applies the language's names and any new video links. Custom ones are left alone. */
export async function seedLibrary(lang: 'en' | 'ar' = 'en'): Promise<void> {
  const arabic = lang === 'ar';
  const existing = new Map((await db.exercises.toArray()).map((e) => [e.id, e]));
  const toPut: ExerciseRow[] = [];
  for (const b of EXERCISE_CATALOG) {
    const e = existing.get(b.id);
    if (!e) { toPut.push({ ...b, videoUrl: b.videoUrl ?? null, custom: false }); continue; }
    if (e.custom) continue;
    const ar = EXERCISE_NAMES_AR[b.id];
    const wanted = arabic && ar ? ar : b.name;
    const nameNeeds = e.name !== wanted && (e.name === ar || e.name === b.name);
    const videoNeeds = e.videoUrl == null && b.videoUrl != null;
    if (nameNeeds || videoNeeds) toPut.push({ ...e, name: nameNeeds ? wanted : e.name, videoUrl: videoNeeds ? b.videoUrl! : e.videoUrl });
  }
  if (toPut.length) await db.exercises.bulkPut(toPut);

  // Starter plan texts follow the language (only rows whose text is exactly a starter text).
  const swap = (s: string) => {
    for (const [en, a] of Object.entries(STARTER_PLANS_AR)) {
      const [from, to] = arabic ? [en, a] : [a, en];
      if (s === from) return to;
    }
    return s;
  };
  await db.transaction('rw', db.plans, db.planDays, async () => {
    await db.plans.toCollection().modify((p) => { p.name = swap(p.name); p.description = swap(p.description); });
    await db.planDays.toCollection().modify((d) => { d.title = swap(d.title); d.note = swap(d.note); });
  });
}

export const getLibrary = async (): Promise<ExerciseRow[]> =>
  (await db.exercises.toArray()).sort((a, b) => a.name.localeCompare(b.name));
export const useLibrary = (): ExerciseRow[] => useLive(getLibrary, [], []);

// ---------------------------------------------------------------- Plans

export async function getActivePlan(): Promise<ActivePlan | null> {
  const plan = (await db.plans.toArray()).find((p) => p.active);
  if (!plan || plan.id == null) return null;
  const days = (await db.planDays.where('planId').equals(plan.id).toArray()).sort((a, b) => a.dayOfWeek - b.dayOfWeek);
  const dayIds = new Set(days.map((d) => d.id));
  const items = (await db.planItems.toArray()).filter((i) => dayIds.has(i.dayId));
  return { plan, days: days.map((d) => ({ day: d, items: items.filter((i) => i.dayId === d.id).sort((a, b) => a.position - b.position) })) };
}
export const useActivePlan = (): ActivePlan | null => useLive(getActivePlan, [], null);

/** Every plan ever applied, newest first. */
export const getAllPlans = async (): Promise<PlanRow[]> => (await db.plans.orderBy('createdAt').reverse().toArray());
export const useAllPlans = (): PlanRow[] => useLive(getAllPlans, [], []);

/** Saves `draft` as the active plan, creating custom exercises for ids the library doesn't know. Returns the plan id. */
export function applyPlan(draft: PlanDraft, source: string): Promise<number> {
  return db.transaction('rw', [db.exercises, db.plans, db.planDays, db.planItems], async () => {
    for (const day of draft.days) {
      for (const item of day.items) {
        const ex = await db.exercises.get(item.exerciseId);
        if (!ex) {
          const kind: ExerciseKind = item.kind === 'HOLD' || item.kind === 'WEIGHTED' || item.kind === 'REPS'
            ? item.kind : item.seconds != null ? 'HOLD' : item.weightKg != null ? 'WEIGHTED' : 'REPS';
          const muscles = item.muscles.filter((m): m is Muscle => (MUSCLES as readonly string[]).includes(m));
          const fallback = item.exerciseId.replace(/_/g, ' ');
          await db.exercises.put({
            id: item.exerciseId, name: item.name ?? fallback.charAt(0).toUpperCase() + fallback.slice(1), kind,
            primary: muscles.slice(0, 2).length ? muscles.slice(0, 2) : ['ABS'], secondary: muscles.slice(2),
            videoUrl: item.videoUrl, custom: true,
          });
        } else if (item.videoUrl && item.videoUrl.trim()) {
          const builtIn = EXERCISE_CATALOG.find((e) => e.id === ex.id)?.videoUrl ?? null;
          const userEdited = ex.videoUrl != null && ex.videoUrl !== builtIn;
          if (ex.videoUrl !== item.videoUrl && !(userEdited && item.videoUrl === builtIn)) await db.exercises.put({ ...ex, videoUrl: item.videoUrl });
        }
      }
    }
    await db.plans.toCollection().modify({ active: false });
    const planId = await db.plans.add({ name: draft.name, description: draft.description, source, active: true, createdAt: Date.now() });
    for (let dow = 1; dow <= 7; dow++) {
      const day = draft.days.find((d) => d.dayOfWeek === dow);
      const dayId = await db.planDays.add({
        planId, dayOfWeek: dow, title: day?.title ?? REST_TITLE,
        isRest: !day || day.isRest || day.items.length === 0, note: day?.note ?? '',
      });
      if (day) {
        await db.planItems.bulkAdd(day.items.map((item, position): PlanItemRow => ({
          dayId, position, exerciseId: item.exerciseId, block: item.block, circuit: item.circuit,
          sets: Math.min(10, Math.max(1, item.sets)), repsMin: item.repsMin, repsMax: item.repsMax, seconds: item.seconds,
          weightKg: item.weightKg, restSeconds: item.restSeconds, note: item.note,
        })));
      }
    }
    return planId;
  });
}

export function activatePlan(planId: number): Promise<void> {
  return db.transaction('rw', db.plans, async () => {
    if (!(await db.plans.get(planId))) return;
    await db.plans.toCollection().modify({ active: false });
    await db.plans.update(planId, { active: true });
  });
}

/** Removes an inactive plan. Workouts done under it keep their sets; only the schedule goes. */
export function deletePlan(planId: number): Promise<void> {
  return db.transaction('rw', [db.plans, db.planDays, db.planItems], async () => {
    const plan = await db.plans.get(planId);
    if (!plan || plan.active) return;
    const dayIds = (await db.planDays.where('planId').equals(planId).primaryKeys());
    await db.planItems.where('dayId').anyOf(dayIds).delete();
    await db.planDays.where('planId').equals(planId).delete();
    await db.plans.delete(planId);
  });
}

/** Moves an exercise one step up (-1) or down (+1) within its day. */
export function movePlanItem(item: PlanItemRow, delta: number): Promise<void> {
  return db.transaction('rw', db.planItems, async () => {
    const items = (await db.planItems.where('dayId').equals(item.dayId).toArray()).sort((a, b) => a.position - b.position);
    const index = items.findIndex((i) => i.id === item.id);
    const target = index + delta;
    if (index < 0 || target < 0 || target >= items.length) return;
    items.splice(target, 0, items.splice(index, 1)[0]);
    for (let i = 0; i < items.length; i++) if (items[i].position !== i) await db.planItems.update(items[i].id!, { position: i });
  });
}

/** Swaps two weekdays of a plan, e.g. to make the week start on Saturday. */
export function swapPlanDays(planId: number, dayA: number, dayB: number): Promise<void> {
  return db.transaction('rw', db.planDays, async () => {
    if (dayA === dayB) return;
    const days = await db.planDays.where('planId').equals(planId).toArray();
    const a = days.find((d) => d.dayOfWeek === dayA);
    const b = days.find((d) => d.dayOfWeek === dayB);
    if (!a || !b) return;
    await db.planDays.update(a.id!, { dayOfWeek: dayB });
    await db.planDays.update(b.id!, { dayOfWeek: dayA });
  });
}

export const updatePlanItem = async (item: PlanItemRow): Promise<void> => { await db.planItems.put(item); };
export const deletePlanItem = (id: number): Promise<void> => db.planItems.delete(id);
/** Puts a deleted item back with its original id (undo). */
export const restorePlanItem = async (item: PlanItemRow): Promise<void> => { await db.planItems.put(item); };
export const updatePlanDay = async (day: PlanDayRow): Promise<void> => { await db.planDays.put(day); };

/** Swaps an exercise on a plan day, translating its prescription to the new exercise's kind. */
export async function swapPlanItem(item: PlanItemRow, newExerciseId: string): Promise<void> {
  const ex = await db.exercises.get(newExerciseId);
  if (!ex) return;
  const translated = translatePlanItem({ ...item, id: item.id ?? 0 }, ex);
  await db.planItems.put(translated);
}

export async function addPlanItem(dayId: number, exerciseId: string): Promise<void> {
  const ex = await db.exercises.get(exerciseId);
  if (!ex) return;
  const existing = await db.planItems.where('dayId').equals(dayId).toArray();
  await db.planItems.add({
    dayId, position: existing.reduce((m, i) => Math.max(m, i.position), -1) + 1, exerciseId, block: 'Main', circuit: false,
    sets: 3, repsMin: ex.kind === 'HOLD' ? null : 10, repsMax: null, seconds: ex.kind === 'HOLD' ? 30 : null,
    weightKg: ex.kind === 'WEIGHTED' ? 0 : null, restSeconds: 90, note: '',
  });
  const day = await db.planDays.get(dayId);
  if (day?.isRest) await db.planDays.update(dayId, { isRest: false, title: day.title === REST_TITLE ? 'Workout' : day.title });
}

// ---------------------------------------------------------------- Exercises

export async function addCustomExercise(name: string, kind: ExerciseKind, primary: Muscle[], videoUrl: string | null): Promise<string> {
  const base = name.toLowerCase().replace(/[^a-z0-9]+/g, '_').replace(/^_+|_+$/g, '') || 'exercise';
  let id = `custom_${base}`;
  for (let n = 2; await db.exercises.get(id); n++) id = `custom_${base}_${n}`;
  await db.exercises.put({ id, name: name.trim(), kind, primary, secondary: [], videoUrl: videoUrl?.trim() || null, custom: true });
  return id;
}

export async function usage(exerciseId: string): Promise<{ sets: number; planItems: number }> {
  return {
    sets: await db.sets.where('exerciseId').equals(exerciseId).count(),
    planItems: await db.planItems.where('exerciseId').equals(exerciseId).count(),
  };
}

export const updateExercise = async (ex: ExerciseRow): Promise<void> => { await db.exercises.put(ex); };

/** Folds `fromId` into `toId`: sets and plan items move over, the duplicate goes, records are re-checked. */
export function mergeExercise(fromId: string, toId: string): Promise<void> {
  return db.transaction('rw', [db.exercises, db.sets, db.planItems, db.sessions], async () => {
    if (fromId === toId || !(await db.exercises.get(toId))) return;
    await db.sets.where('exerciseId').equals(fromId).modify({ exerciseId: toId });
    await db.planItems.where('exerciseId').equals(fromId).modify({ exerciseId: toId });
    await db.exercises.delete(fromId);
    await recomputeRecords(toId);
  });
}

/** Deletes a custom exercise nothing refers to. False if it's built in or still in use. */
export async function deleteExercise(exerciseId: string): Promise<boolean> {
  const ex = await db.exercises.get(exerciseId);
  if (!ex || !ex.custom) return false;
  const u = await usage(exerciseId);
  if (u.sets > 0 || u.planItems > 0) return false;
  await db.exercises.delete(exerciseId);
  return true;
}

export const isWebUrl = (s: string): boolean => /^https?:\/\/[^\s/$.?#].[^\s]*$/i.test(s);

/** Saves a form-video link. False (nothing changed) when it isn't a web address. */
export async function setVideoUrl(exerciseId: string, url: string | null): Promise<boolean> {
  const ex = await db.exercises.get(exerciseId);
  if (!ex) return false;
  const clean = url?.trim() || null;
  if (clean != null && !isWebUrl(clean)) return false;
  await db.exercises.put({ ...ex, videoUrl: clean });
  return true;
}

// ---------------------------------------------------------------- Live session

export async function openSessionNow(): Promise<SessionRow | undefined> {
  return (await db.sessions.filter((s) => s.finishedAt == null).toArray()).sort((a, b) => b.startedAt - a.startedAt)[0];
}
export const useOpenSession = (): SessionRow | undefined => useLive(openSessionNow, [], undefined);

export const getSets = async (sessionId: number): Promise<SetRow[]> =>
  (await db.sets.where('sessionId').equals(sessionId).toArray()).sort((a, b) => a.itemPosition - b.itemPosition || a.setIndex - b.setIndex);
export const useSets = (sessionId: number): SetRow[] => useLive(() => getSets(sessionId), [sessionId], []);

/** Done sets of one exercise from finished workouts: latest workout date first, then latest start, then set order. At most 20. */
export async function recentDoneSets(exerciseId: string): Promise<SetRow[]> {
  const all = await recentDoneSetsAll(exerciseId);
  const sess = new Map((await db.sessions.toArray()).map((s) => [s.id!, s]));
  return all.sort((a, b) => {
    const sa = sess.get(a.sessionId)!; const sb = sess.get(b.sessionId)!;
    return sb.epochDay - sa.epochDay || sb.startedAt - sa.startedAt || a.setIndex - b.setIndex;
  }).slice(0, 20);
}

/** Like recentDoneSets, but only from workouts before the one on `epochDay` that started at `startedAt`. */
export async function doneSetsBefore(exerciseId: string, epochDay: number, startedAt: number): Promise<SetRow[]> {
  const all = await recentDoneSetsAll(exerciseId);
  const sess = new Map((await db.sessions.toArray()).map((s) => [s.id!, s]));
  return all.filter((x) => { const ss = sess.get(x.sessionId)!; return ss.epochDay < epochDay || (ss.epochDay === epochDay && ss.startedAt < startedAt); })
    .sort((a, b) => {
      const sa = sess.get(a.sessionId)!; const sb = sess.get(b.sessionId)!;
      return sb.epochDay - sa.epochDay || sb.startedAt - sa.startedAt || a.setIndex - b.setIndex;
    }).slice(0, 20);
}

/** Every done set of one exercise in a finished workout. */
export async function recentDoneSetsAll(exerciseId: string): Promise<SetRow[]> {
  const finished = new Set((await db.sessions.toArray()).filter((s) => s.finishedAt != null).map((s) => s.id!));
  return (await db.sets.where('exerciseId').equals(exerciseId).toArray()).filter((s) => s.done && finished.has(s.sessionId));
}

/** Starts a workout from a plan day (or an empty quick workout) with sets prefilled from history. Returns the open one if there is one. */
export async function startSession(day: PlanDayWithItems | null, lighter = false): Promise<number> {
  const open = await openSessionNow();
  if (open) return open.id!;
  const now = Date.now();
  const id = await putSession({
    planDayId: day?.day.id ?? null, title: day?.day.title ?? QUICK_TITLE, epochDay: todayEpochDay(), startedAt: now,
    finishedAt: null, note: '', debrief: '',
  });
  const sets: SetRow[] = [];
  for (const item of day?.items ?? []) {
    const built = await buildSetsFor(id, item.position, item, await db.exercises.get(item.exerciseId));
    sets.push(...(lighter && built.length > 1 ? built.slice(0, -1) : built));
  }
  if (sets.length) await db.sets.bulkAdd(sets);
  return id;
}

/** Today's sets for one exercise from its last two finished workouts, through the Progression rules. */
async function buildSetsFor(sessionId: number, position: number, item: PlanItemRow | null, ex: ExerciseRow | undefined): Promise<SetRow[]> {
  const kind: ExerciseKind = ex?.kind ?? 'REPS';
  const recent = ex ? await recentDoneSets(ex.id) : [];
  const sessionIds = [...new Set(recent.map((s) => s.sessionId))];
  const past = (id: number | undefined): PastSet[] =>
    recent.filter((s) => s.sessionId === id).sort((a, b) => a.setIndex - b.setIndex)
      .map((s) => ({ reps: s.reps, seconds: s.seconds, weightKg: s.weightKg, effort: effortOf(s.effort, s.note) }));
  const isWarmup = item?.block.toLowerCase() === 'warm-up' || ex?.id === 'jumping_jacks';
  const result = nextPrefill(
    kind,
    item ? { sets: item.sets, repsMin: item.repsMin, repsMax: item.repsMax, seconds: item.seconds, weightKg: item.weightKg } : null,
    past(sessionIds[0]), past(sessionIds[1]), weightUnit(await loadSettings()), false, isWarmup,
  );
  const base = targetLabel(kind, item);
  const label = result.holdCeilingReached ? [base, HOLD_CEILING_NOTE].filter(Boolean).join(' · ') : base;
  return result.sets.map((p, i): SetRow => ({
    sessionId, exerciseId: ex?.id ?? item?.exerciseId ?? '', itemPosition: position, block: item?.block ?? 'Main',
    circuit: item?.circuit ?? false, setIndex: i, targetLabel: label, reps: p.reps, seconds: p.seconds, weightKg: p.weightKg,
    done: false, isPr: false, restSeconds: item?.restSeconds ?? 90, note: '', effort: '',
  }));
}

/** The plan's target as short text, e.g. "8-12 reps" or "30 s". Plain English; screens may show their own wording. */
export function targetLabel(kind: ExerciseKind, item: PlanItemRow | null): string {
  if (!item) return '';
  if (kind === 'HOLD') return `${item.seconds ?? 30} s`;
  if (item.repsMin != null && item.repsMax != null && item.repsMax !== item.repsMin) return `${item.repsMin}-${item.repsMax} reps`;
  return item.repsMin != null ? `${item.repsMin} reps` : '';
}

// Per-set saves: one row per tap.
export const updateSet = async (set: SetRow): Promise<void> => { await putSet(set); };
export const removeSet = (setId: number): Promise<void> => deleteSet(setId);

export async function addSet(sessionId: number, itemPosition: number): Promise<void> {
  const existing = (await getSets(sessionId)).filter((s) => s.itemPosition === itemPosition);
  const last = existing.reduce<SetRow | null>((m, s) => (!m || s.setIndex > m.setIndex ? s : m), null);
  if (!last) return;
  const { id: _id, ...rest } = last;
  await putSet({ ...rest, setIndex: last.setIndex + 1, done: false, isPr: false });
}

export async function addExercise(sessionId: number, exerciseId: string): Promise<void> {
  const ex = await db.exercises.get(exerciseId);
  if (!ex) return;
  const position = (await getSets(sessionId)).reduce((m, s) => Math.max(m, s.itemPosition), -1) + 1;
  const sets = await buildSetsFor(sessionId, position, null, ex);
  if (sets.length) await db.sets.bulkAdd(sets);
}

/** Replaces the exercise at `itemPosition`, keeping set count and block and translating the targets. */
export async function swapExercise(sessionId: number, itemPosition: number, newExerciseId: string): Promise<void> {
  const ex = await db.exercises.get(newExerciseId);
  if (!ex) return;
  const old = (await getSets(sessionId)).filter((s) => s.itemPosition === itemPosition);
  if (!old.length) return;
  const t = old[0];
  await db.sets.bulkDelete(old.map((s) => s.id!));
  const synthetic: PlanItemRow = {
    dayId: 0, position: itemPosition, exerciseId: newExerciseId, block: t.block, circuit: t.circuit, sets: old.length,
    repsMin: ex.kind === 'HOLD' ? null : (t.reps ?? 10), repsMax: ex.kind === 'HOLD' ? null : Math.max(t.reps ?? 10, 12),
    seconds: ex.kind === 'HOLD' ? (t.seconds ?? 30) : null, weightKg: ex.kind === 'WEIGHTED' ? 0 : null,
    restSeconds: t.restSeconds, note: '',
  };
  const fresh = await buildSetsFor(sessionId, itemPosition, synthetic, ex);
  if (fresh.length) await db.sets.bulkAdd(fresh);
}

/** A harder version replaces an easy move: today's sets at its positions, and the plan day the workout came from. */
export async function stepUp(sessionId: number, oldId: string, newId: string): Promise<void> {
  const session = await db.sessions.get(sessionId);
  const positions = [...new Set((await getSets(sessionId)).filter((s) => s.exerciseId === oldId).map((s) => s.itemPosition))];
  for (const p of positions) await swapExercise(sessionId, p, newId);
  if (session?.planDayId == null) return;
  for (const item of await db.planItems.where('dayId').equals(session.planDayId).toArray()) {
    if (item.exerciseId === oldId) await swapPlanItem(item, newId);
  }
}

/**
 * "Ready for a harder version" for one exercise of the open workout, from its last two finished sessions: undefined
 * when not ready, `{ next: null }` when there is no harder move (the screen suggests a slower tempo).
 */
export async function harderHint(ex: ExerciseRow, warmup: boolean, openSessionId: number): Promise<{ next: ExerciseRow | null } | undefined> {
  if (ex.kind !== 'REPS' || warmup || ex.id === 'jumping_jacks') return undefined;
  const recent = (await recentDoneSets(ex.id)).filter((s) => s.sessionId !== openSessionId);
  const ids = [...new Set(recent.map((s) => s.sessionId))];
  const past = (id: number | undefined): PastSet[] => recent.filter((s) => s.sessionId === id)
    .map((s) => ({ reps: s.reps, seconds: s.seconds, weightKg: s.weightKg, effort: effortOf(s.effort, s.note) }));
  if (!readyForHarder(past(ids[0]), past(ids[1]))) return undefined;
  const next = harderVersion(ex, await getLibrary(), configuredEquipment(await loadSettings()));
  return { next: (next as ExerciseRow | null) ?? null };
}

/** Throws away an unfinished workout and its sets. */
export function discardSession(sessionId: number): Promise<void> {
  return db.transaction('rw', db.sessions, db.sets, async () => {
    await db.sets.where('sessionId').equals(sessionId).delete();
    await db.sessions.delete(sessionId);
  });
}

/**
 * Closes the session: `tickAll` marks every remaining set done, otherwise unticked sets are dropped.
 * New personal records are flagged against workouts dated earlier only, so a backdated log compares with its own past.
 */
export function finishSession(sessionId: number, tickAll = false): Promise<FinishResult> {
  return db.transaction('rw', db.sessions, db.sets, db.exercises, async () => {
    const session = await db.sessions.get(sessionId);
    if (!session) return { sessionTitle: '', doneSets: 0, prs: [], xpGained: 0 };
    const mine = await db.sets.where('sessionId').equals(sessionId).toArray();
    if (tickAll) await db.sets.bulkPut(mine.map((s) => ({ ...s, done: true })));
    else await db.sets.bulkDelete(mine.filter((s) => !s.done).map((s) => s.id!));
    const done = (await getSets(sessionId)).filter((s) => s.done);

    const earlier = new Set((await finishedSessions())
      .filter((s) => s.epochDay < session.epochDay || (s.epochDay === session.epochDay && s.startedAt < session.startedAt))
      .map((s) => s.id!));
    const prs: string[] = [];
    const byExercise = new Map<string, SetRow[]>();
    for (const s of done) byExercise.set(s.exerciseId, [...(byExercise.get(s.exerciseId) ?? []), s]);
    for (const [exerciseId, sets] of byExercise) {
      const ex = await db.exercises.get(exerciseId);
      if (!ex) continue;
      const score = (s: SetRow) => recordScore(ex.kind, s.reps, s.seconds, s.weightKg, ex.id);
      const previous = (await recentDoneSetsAll(exerciseId)).filter((s) => earlier.has(s.sessionId));
      if (!previous.length) continue; // first time: nothing to beat yet
      const previousBest = Math.max(...previous.map(score));
      const best = sets.reduce((m, s) => (score(s) > score(m) ? s : m));
      if (score(best) > previousBest) { await db.sets.put({ ...best, isPr: true }); prs.push(ex.name); }
    }
    await db.sessions.put({ ...session, finishedAt: Date.now() });
    return { sessionTitle: session.title, doneSets: done.length, prs, xpGained: totalXp(1, done.length, prs.length, 0) };
  });
}

// ---------------------------------------------------------------- History

/** Finished workouts, most recently finished first. */
export async function finishedSessions(): Promise<SessionRow[]> {
  return (await db.sessions.filter((s) => s.finishedAt != null).toArray()).sort((a, b) => b.finishedAt! - a.finishedAt!);
}
export const useFinishedSessions = (): SessionRow[] => useLive(finishedSessions, [], []);

export const getSession = (id: number): Promise<SessionRow | undefined> => db.sessions.get(id);
export const useSession = (id: number): SessionRow | undefined => useLive(() => getSession(id), [id], undefined);

/** Every done set of every finished workout. */
export async function allDoneSets(): Promise<SetRow[]> {
  const finished = new Set((await finishedSessions()).map((s) => s.id!));
  return (await db.sets.filter((s) => s.done).toArray()).filter((s) => finished.has(s.sessionId));
}

/** The calculated score of every finished workout (domain/workout-score.ts): five small reads, never one query per row. */
export async function allWorkoutScores(): Promise<Map<number, WorkoutScoreResult>> {
  const [sessions, doneSets, items, readiness, library] = await Promise.all([
    finishedSessions(), allDoneSets(), db.planItems.toArray(), db.readinessDays.toArray(), getLibrary(),
  ]);
  const planned = new Map<number, number>();
  for (const i of items) planned.set(i.dayId, (planned.get(i.dayId) ?? 0) + i.sets);
  // Ease off (34-49) and recover (below 34): Android Readiness.call is under 50.
  const rough = new Set(readiness.filter((r) => r.recovery < 50).map((r) => r.epochDay));
  return scoresForSessions(sessions, doneSets, new Map(library.map((e) => [e.id, e.kind])), planned, rough);
}
export const useWorkoutScores = (): Map<number, WorkoutScoreResult> => useLive(allWorkoutScores, [], new Map());

/** Edits a set of a finished workout and re-checks which sets of that exercise are records. */
export async function updateFinishedSet(set: SetRow): Promise<void> {
  await putSet(set);
  await recomputeRecords(set.exerciseId);
}

/** Removes one set from a finished workout. A workout left with no sets is removed too. */
export async function deleteFinishedSet(set: SetRow): Promise<void> {
  await deleteSet(set.id!);
  if ((await db.sets.where('sessionId').equals(set.sessionId).count()) === 0) await db.sessions.delete(set.sessionId);
  await recomputeRecords(set.exerciseId);
}

/** Moves a finished workout to another day; its times shift with it. */
export async function moveSession(sessionId: number, epochDay: number): Promise<void> {
  const s = await db.sessions.get(sessionId);
  if (!s) return;
  const shift = (epochDay - s.epochDay) * 86_400_000;
  await db.sessions.put({ ...s, epochDay, startedAt: s.startedAt + shift, finishedAt: s.finishedAt == null ? null : s.finishedAt + shift });
  for (const id of new Set((await getSets(sessionId)).map((x) => x.exerciseId))) await recomputeRecords(id);
}

/** Puts back a workout removed by deleteSession (same ids), then re-checks the records it touched. */
export async function restoreSession(session: SessionRow, sets: SetRow[]): Promise<void> {
  await db.transaction('rw', db.sessions, db.sets, async () => {
    await db.sessions.put(session);
    await db.sets.bulkPut(sets);
  });
  for (const id of new Set(sets.map((s) => s.exerciseId))) await recomputeRecords(id);
}

export async function deleteSession(sessionId: number): Promise<void> {
  const affected = [...new Set((await getSets(sessionId)).map((s) => s.exerciseId))];
  await db.transaction('rw', db.sessions, db.sets, async () => {
    await db.sets.where('sessionId').equals(sessionId).delete();
    await db.sessions.delete(sessionId);
  });
  for (const id of affected) await recomputeRecords(id);
}

/** Replays one exercise's whole history in order so PR tags stay truthful after an edit or delete. */
export async function recomputeRecords(exerciseId: string): Promise<void> {
  const ex = await db.exercises.get(exerciseId);
  if (!ex) return;
  const all = await recentDoneSetsAll(exerciseId);
  const sess = new Map((await finishedSessions()).map((s) => [s.id!, s]));
  const groups = new Map<number, SetRow[]>();
  for (const s of all) groups.set(s.sessionId, [...(groups.get(s.sessionId) ?? []), s]);
  const ordered = [...groups.entries()].sort(([a], [b]) => {
    const x = sess.get(a); const y = sess.get(b);
    return (x?.epochDay ?? 0) - (y?.epochDay ?? 0) || (x?.startedAt ?? 0) - (y?.startedAt ?? 0) || (x?.finishedAt ?? 0) - (y?.finishedAt ?? 0);
  }).map(([, v]) => v);
  const records = personalRecords(ex.kind, ordered, (s) => s.reps, (s) => s.seconds, (s) => s.weightKg, ex.id);
  const changed = all.filter((s) => s.isPr !== records.has(s)).map((s) => ({ ...s, isPr: records.has(s) }));
  if (changed.length) await db.sets.bulkPut(changed);
}
