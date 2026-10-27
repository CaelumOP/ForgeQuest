// The Coach's chat, plan import and debrief: port of the stateful parts of Android CoachRepository.kt.
// History lives in the existing Dexie `chat` table (same fields as Android ChatEntity), so backups carry it.
// Keys come from settings through ai.ts and are never logged.
import { getLang, t } from '../i18n';
import { AiError, GROQ_FALLBACK_MODEL, aiErrorKey, geminiGenerate, groqChat, modelsFor, textPart } from './ai';
import {
  alternatingTurns, answeredHistory, applyPatch, coachSystemPrompt, debriefPayload, describeChanges,
  extractMemory, extractPatch, extractPlan, hasBrokenBlock, isReplyToIntake, languageOf, mergeMemory, parsePlanSystem,
  planHash, planSchemaInstructions, progressSummary, repairRequest, replaceUnknown, stripReply, validatePlan, wantsPlanText,
  type CoachPlan, type Turn,
} from './coach';
import { db, type ChatRow, type ExerciseRow } from './db';
import { useLive } from './live';
import { getProgressSnapshot, type ProgressSnapshot } from './progress';
import { configuredEquipment, loadSettings, updateSettings, weightUnit, type Settings } from './settings';
import { allWorkoutScores, applyPlan, doneSetsBefore, planToDraft, workoutDebrief } from './workouts';
import { debriefLines } from './debrief-text';
import { unitCode, weightNumber } from '../domain/units';
import { adaptPlanToEquipment, inferEquipmentFromText, type Equipment } from '../domain/exercise-swap-engine';
import { todayEpochDay } from '../domain/dates';

// ---------------------------------------------------------------- Reading

export const getChat = (): Promise<ChatRow[]> => db.chat.orderBy('id').toArray();
export const useChat = (): ChatRow[] => useLive(getChat, [], []);
export const clearChat = (): Promise<void> => db.chat.clear();

/** The plan card of a coach message, or null. */
export function planOfMessage(m: ChatRow): CoachPlan | null {
  if (!m.planJson) return null;
  try {
    const raw = JSON.parse(m.planJson) as CoachPlan;
    return Array.isArray(raw?.days) ? raw : null;
  } catch { return null; }
}

/** The AiError kind saved with an error bubble ("no_key", "bad_key", ...), or ''. */
export const errorKindOf = (m: ChatRow): string => (m.kind === 'error' ? m.detail.split('\n')[0] : '');

/** Who answered the last message and how long it took (ms), for the model menu. In memory only. */
let last: { who: string; ms: number } | null = null;
export const lastAnswer = () => last;

// ---------------------------------------------------------------- Sending

let queue: Promise<unknown> = Promise.resolve();
/** One message at a time, in order. */
const exclusive = <T>(fn: () => Promise<T>): Promise<T> => {
  const run = queue.then(fn, fn);
  queue = run.catch(() => undefined);
  return run;
};

const insert = (row: Partial<ChatRow> & Pick<ChatRow, 'role' | 'text'>): Promise<number> =>
  db.chat.add({ planJson: null, planApplied: false, kind: '', detail: '', createdAt: Date.now(), ...row });

/**
 * Sends the person's message and saves the coach's reply (or an error bubble) in the chat table. Never throws.
 * `aboutWorkout`: the message is the debrief button's (see debriefChatMessage). It is never read as a plan request,
 * never changes the saved equipment, and is answered in the app language.
 */
export function sendCoachMessage(text: string, aboutWorkout = false): Promise<void> {
  return exclusive(() => sendNow(text.trim(), aboutWorkout));
}

async function sendNow(text: string, aboutWorkout = false): Promise<void> {
  const started = Date.now();
  await insert({ role: 'user', text });
  const history = answeredHistory((await db.chat.orderBy('id').reverse().limit(14).toArray()).reverse());
  const inferred = aboutWorkout ? null : inferEquipmentFromText(text);
  let settings = await loadSettings();
  if (inferred && !settings.equipment.trim()) {
    settings = await updateSettings((s) => ({ ...s, equipment: [...inferred].join(',') }));
  }
  const configured = configuredEquipment(settings);
  const known: Set<Equipment> = configured.size ? configured : (inferEquipmentFromText(settings.coachNotes, text) ?? new Set());
  try {
    const snap = await getProgressSnapshot();
    const planNames = (snap.plan?.days ?? []).flatMap((d) => d.items).map((i) => i.exerciseId)
      .flatMap((id) => [id.replace(/_/g, ' '), snap.exercises.get(id)?.name ?? '']).filter(Boolean);
    const wants = !aboutWorkout && (wantsPlanText(text, planNames) || (isReplyToIntake(history, text) && known.size > 0));
    const needsIntake = wants && known.size === 0;
    const emitPlan = wants && !needsIntake;
    const library = [...snap.exercises.values()];
    const today = todayEpochDay();
    const readiness = await db.readinessDays.where('epochDay').aboveOrEqual(today - 14).toArray();
    const system = coachSystemPrompt({
      replyLanguage: aboutWorkout && getLang() === 'ar' ? 'Arabic' : languageOf(text), plan: emitPlan, needsEquipmentIntake: needsIntake,
      schema: emitPlan ? planSchemaInstructions(library, known, snap.plan ? planToDraft(snap.plan) : null) : undefined,
      summary: progressSummary(snap, today, readiness, known),
    });
    const turns = alternatingTurns(history);
    const choice = settings.coachModel;
    const viaGroq = !emitPlan && !!settings.groqKey.trim() && choice === 'auto';
    let provider = 'Gemini';
    let reply: string | null = null;
    if (viaGroq) {
      try {
        reply = await groqChat({
          model: settings.groqModel || GROQ_FALLBACK_MODEL,
          messages: [{ role: 'system', content: system }, ...turns.map((x) => ({ role: x.role === 'user' ? 'user' as const : 'assistant' as const, content: x.text }))],
        });
        provider = 'Groq';
      } catch { reply = null; } // Groq failed before any text: use Gemini
    }
    const gemini = (extra: Turn[] = []) => {
      const all = [...turns, ...extra];
      const lastTurn = all[all.length - 1];
      const zero = !emitPlan && choice === 'lite';
      return geminiGenerate({
        system, parts: [textPart(lastTurn.text)], history: all.slice(0, -1), models: modelsFor(choice, settings),
        fast: zero, lowThinking: !emitPlan && !zero,
      });
    };
    if (reply == null) reply = await gemini();
    last = { who: provider, ms: Date.now() - started };
    await saveReply(reply, known, snap, async (problems) =>
      gemini([{ role: 'model', text: reply! }, { role: 'user', text: repairRequest(problems) }]));
  } catch (e) {
    await saveError(e);
  }
}

async function saveError(e: unknown) {
  const err = e instanceof AiError ? e : null;
  const text = err?.kind === 'timeout' ? t('ai_err_coach_timeout') : (() => { const k = aiErrorKey(e); return t(k.key, ...k.args); })();
  const s = await loadSettings();
  const detail = [err?.kind ?? 'error', err?.detail ?? (e instanceof Error ? e.name : ''), `models ${s.liteModel} / ${s.fastModel}`].filter(Boolean).join('\n');
  await insert({ role: 'coach', text, kind: 'error', detail });
}

// ---------------------------------------------------------------- Saving a reply

type PlanCheck = { draft: CoachPlan | null; errors: string[]; unknownIds: Set<string>; broken: boolean };

function checkPlan(reply: string, library: ReadonlyMap<string, ExerciseRow>, current: CoachPlan | null): PlanCheck | null {
  const patch = extractPatch(reply);
  if (patch) {
    if (!current) return { draft: null, errors: ['There is no current plan to change; send the full plan instead.'], unknownIds: new Set(), broken: true };
    const out = applyPatch(current, patch, library);
    return { draft: out.draft, errors: out.errors, unknownIds: new Set(), broken: out.draft == null };
  }
  const plan = extractPlan(reply);
  if (plan) {
    const v = validatePlan(plan, library);
    return { draft: { ...v.draft, basedOnHash: current ? planHash(current) : null }, errors: v.errors, unknownIds: v.unknownIds, broken: false };
  }
  return hasBrokenBlock(reply)
    ? { draft: null, errors: ['The JSON block could not be parsed; check brackets, quotes and commas.'], unknownIds: new Set(), broken: true }
    : null;
}

/**
 * Saves the coach's reply. A plan or change block is checked first; with problems the coach is asked once to fix them.
 * Ids still unknown become the closest library exercise (noted under the reply); an unreadable block says so.
 */
async function saveReply(reply: string, equipment: ReadonlySet<Equipment>, snap: ProgressSnapshot, repair?: (problems: string[]) => Promise<string>) {
  const current = snap.plan ? planToDraft(snap.plan) : null;
  const first = checkPlan(reply, snap.exercises, current);
  let retried: PlanCheck | null = null;
  if (first && first.errors.length && repair) {
    try { retried = checkPlan(await repair(first.errors), snap.exercises, current); } catch { retried = null; }
  }
  const check = retried && first && (retried.draft != null || first.draft == null) && retried.errors.length <= first.errors.length ? retried : first;
  const extra: string[] = [];
  let checked = check?.draft ?? null;
  if (checked && check && check.unknownIds.size) {
    const [replaced, swaps] = replaceUnknown(checked, check.unknownIds, [...snap.exercises.values()], equipment);
    checked = replaced;
    for (const [old, now] of swaps) extra.push(now ? t('coach_plan_replaced', old, now) : t('coach_plan_dropped', old));
  }
  if (check && !checked) extra.push(t('coach_plan_unreadable'));
  if (checked?.patch && current) extra.push(...describeChanges(checked.patch, snap.exercises, getLang()));
  const notes = extractMemory(reply);
  if (notes.length) {
    const eq = inferEquipmentFromText(...notes);
    await updateSettings((s) => ({
      ...s, coachNotes: mergeMemory(s.coachNotes, notes),
      equipment: !s.equipment.trim() && eq ? [...eq].join(',') : s.equipment,
    }));
  }
  const adapted = checked && equipment.size ? adaptPlanToEquipment(checked, snap.exercises, equipment)[0] : checked;
  const prose = stripReply(reply) || t('coach_plan_fallback');
  await insert({
    role: 'coach',
    text: extra.length ? `${prose}\n\n${extra.map((x) => `• ${x}`).join('\n')}` : prose,
    planJson: adapted ? JSON.stringify(adapted) : null,
  });
}

// ---------------------------------------------------------------- Applying a plan card

export type ApplyResult = 'APPLIED' | 'STALE' | 'FAILED';

/**
 * Applies a coach plan card. If the plan was edited since the card was made, a change-only card is re-run on the
 * current plan; a full plan returns STALE so the screen can ask first (`force` skips that).
 */
export async function applyChatPlan(messageId: number, force = false): Promise<ApplyResult> {
  const msg = await db.chat.get(messageId);
  let plan = msg ? planOfMessage(msg) : null;
  if (!msg || !plan) return 'FAILED';
  const snap = await getProgressSnapshot();
  const current = snap.plan ? planToDraft(snap.plan) : null;
  if (!force && plan.basedOnHash && current && planHash(current) !== plan.basedOnHash) {
    if (!plan.patch) return 'STALE';
    const redone = applyPatch(current, plan.patch, snap.exercises).draft;
    if (!redone) return 'STALE';
    plan = redone;
  }
  const eq = configuredEquipment(await loadSettings());
  const final = eq.size ? adaptPlanToEquipment(plan, snap.exercises, eq)[0] : plan;
  await applyPlan({ name: final.name, description: final.description, days: final.days }, 'coach');
  await db.chat.update(messageId, { planApplied: true });
  return 'APPLIED';
}

// ---------------------------------------------------------------- Pasted plan text

/** Turns free text (notes, a message, a Google Doc) into a plan. Throws AiError (kind 'unreadable' when it is no plan). */
export async function parsePlanText(text: string): Promise<CoachPlan> {
  const s = await loadSettings();
  const snap = await getProgressSnapshot();
  const raw = await geminiGenerate({
    system: parsePlanSystem(planSchemaInstructions([...snap.exercises.values()], configuredEquipment(s))),
    parts: [textPart(text.slice(0, 20_000))], json: true, lowThinking: true, models: modelsFor(s.coachModel, s),
  });
  const plan = extractPlan('```json\n' + raw + '\n```');
  if (!plan) throw new AiError('unreadable', 'plan text not readable');
  return plan;
}

// ---------------------------------------------------------------- Debrief

/** The debrief's "Ask the coach" button shows: a Groq or Gemini key is saved. */
export const canDebrief = (s: Pick<Settings, 'groqKey' | 'apiKey'>): boolean => !!(s.groqKey.trim() || s.apiKey.trim());

let pending: string | null = null;
/** The Coach screen takes the message the debrief button left for it (and clears it). */
export function takePendingCoachMessage(): string | null {
  const text = pending;
  pending = null;
  return text;
}

/**
 * "Ask the coach about this workout": leaves the workout's message for the Coach screen, which sends it as it opens.
 * Nothing is left when the workout is gone.
 */
export async function askCoachAboutWorkout(sessionId: number): Promise<boolean> {
  const text = await debriefChatMessage(sessionId);
  pending = text || null;
  return !!text;
}

/**
 * The message of "Ask the coach about this workout": the workout's facts (each exercise against its previous time,
 * effort chips, notes, that day's readiness), the debrief the app calculated in plain English and the instruction to
 * treat the numbers as fact. Blank when the workout is gone or has no done sets.
 */
export async function debriefChatMessage(sessionId: number): Promise<string> {
  const s = await loadSettings();
  const session = await db.sessions.get(sessionId);
  if (!session) return '';
  const sets = (await db.sets.where('sessionId').equals(sessionId).toArray()).filter((x) => x.done);
  if (!sets.length) return '';
  const unit = weightUnit(s);
  const library = new Map((await db.exercises.toArray()).map((e) => [e.id, e]));
  const name = (id: string) => library.get(id)?.name ?? id;
  const readiness = await db.readinessDays.get(session.epochDay);
  const ids = [...new Set(sets.map((x) => x.exerciseId))];
  const exercises = [];
  for (const id of ids) {
    // Only workouts before this one, so a message asked for later from the journal compares the right way.
    const before = await doneSetsBefore(id, session.epochDay, session.startedAt);
    const lastId = before[0]?.sessionId;
    exercises.push({ name: name(id), done: sets.filter((x) => x.exerciseId === id), lastTime: lastId == null ? [] : before.filter((x) => x.sessionId === lastId) });
  }
  const facts = debriefPayload({
    title: session.title, epochDay: session.epochDay, goal: s.nutritionGoal, note: session.note,
    minutes: Math.trunc(((session.finishedAt ?? Date.now()) - session.startedAt) / 60_000),
    readiness: readiness ? { recovery: readiness.recovery, sleepMin: readiness.sleepMin } : null,
    exercises, score: (await allWorkoutScores(sessionId)).get(sessionId) ?? null, unit,
  });
  const d = await workoutDebrief(sessionId);
  let calculated = '';
  if (d) {
    const lines = debriefLines(d, 'en', name, (kg) => `${weightNumber(unit, kg)} ${unitCode(unit)}`);
    calculated = ['Debrief calculated by the app:', lines.headline, ...[...lines.wins, ...lines.watch, ...lines.next, ...(lines.sameRest ? [lines.sameRest] : [])].map((x) => `- ${x}`)].join('\n') + '\n';
  }
  const language = getLang() === 'ar' ? 'formal Modern Standard Arabic (Western digits)' : 'English';
  return [
    facts + (calculated ? `\n${calculated}` : ''),
    "Treat these numbers and next-time targets as fact; don't recalculate or contradict them.",
    `Give me your take in a few short lines: what went well, one thing to watch, and one tip. Reply in ${language}.`,
  ].join('\n');
}
