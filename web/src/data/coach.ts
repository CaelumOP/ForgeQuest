// Coach logic that needs no network or database: prompts, the training-context summary, plan/patch/debrief parsing.
// Port of Android data/CoachRepository.kt (prompting, PlanIntent, checkPlan helpers) and data/plan/{PlanJson,PlanSafety,Debrief}.kt.
// The chat itself (sending, saving, applying) lives in coach-chat.ts.
import { t } from '../i18n';
import type { ReadinessDayRow } from './db';
import type { ProgressSnapshot } from './progress';
import type { PlanDraft } from './seed/starter-plans';
import type { PlanDraftDay, PlanDraftItem, ExerciseEntity, SessionEntity } from '../domain/entities';
import { EQUIPMENT, EQUIPMENT_LABEL, profileFor, rankSubstitutes, translateDraftItem, type Equipment } from '../domain/exercise-swap-engine';
import { isExerciseKind, isMuscle, MUSCLES, MUSCLE_INFO, type ExerciseKind } from '../domain/muscle';
import { MIN_SAFE_KCAL, weightTrendNote } from '../domain/nutrition';
import { recordScore } from '../domain/progress';
import { analyzeFatigueAndPlateaus } from '../domain/trends';
import { BADGES, BADGES_BY_ID } from '../domain/badges';

// ---------------------------------------------------------------- Types

/** A small edit the coach proposes instead of rewriting the week (Android PlanChange). */
export type PlanChange = {
  type: string; dayOfWeek: number; exerciseId?: string | null; newExerciseId?: string | null
  sets?: number | null; repsMin?: number | null; repsMax?: number | null; seconds?: number | null
  weightKg?: number | null; restSeconds?: number | null; note?: string | null
};
export type PlanPatch = { changes: PlanChange[] };
/** A plan as saved on a chat message: the Android PlanDraft with its two extra fields. */
export type CoachPlan = PlanDraft & { basedOnHash?: string | null; patch?: PlanPatch | null };

/** The coach's short take on a finished workout (Android WorkoutDebrief). */
export type WorkoutDebrief = { score: number; summary: string; highlights: string[]; watch: string[]; nextTime: string[] };

// ---------------------------------------------------------------- Lenient JSON reading (Kotlin: isLenient, coerceInputValues)

const isObj = (x: unknown): x is Record<string, unknown> => typeof x === 'object' && x !== null && !Array.isArray(x);
const intOrNull = (x: unknown): number | null => (typeof x === 'number' && Number.isFinite(x) ? Math.round(x) : null);
const numOrNull = (x: unknown): number | null => (typeof x === 'number' && Number.isFinite(x) ? x : null);
const strOr = (x: unknown, d: string): string => (typeof x === 'string' ? x : d);
const strList = (x: unknown): string[] => (Array.isArray(x) ? x.filter((v): v is string => typeof v === 'string') : []);

function readItem(raw: unknown): PlanDraftItem | null {
  if (!isObj(raw) || typeof raw.exerciseId !== 'string' || !raw.exerciseId.trim()) return null;
  return {
    exerciseId: raw.exerciseId,
    name: typeof raw.name === 'string' ? raw.name : null,
    kind: typeof raw.kind === 'string' ? raw.kind : null,
    muscles: strList(raw.muscles),
    block: strOr(raw.block, 'Main'),
    circuit: raw.circuit === true,
    sets: intOrNull(raw.sets) ?? 3,
    repsMin: intOrNull(raw.repsMin),
    repsMax: intOrNull(raw.repsMax),
    seconds: intOrNull(raw.seconds),
    weightKg: numOrNull(raw.weightKg),
    restSeconds: intOrNull(raw.restSeconds) ?? 90,
    note: strOr(raw.note, ''),
    videoUrl: typeof raw.videoUrl === 'string' ? raw.videoUrl : null,
  };
}

function readDay(raw: unknown): PlanDraftDay | null {
  if (!isObj(raw)) return null;
  const dow = intOrNull(raw.dayOfWeek);
  if (dow == null) return null;
  const items = Array.isArray(raw.items) ? raw.items.map(readItem).filter((i): i is PlanDraftItem => i != null) : [];
  return { dayOfWeek: dow, title: strOr(raw.title, `Day ${dow}`), isRest: raw.isRest === true, note: strOr(raw.note, ''), items };
}

/** A plan object from parsed JSON; null when it has no days. Days outside 1..7 and repeated days are dropped. */
export function readPlan(raw: unknown): CoachPlan | null {
  if (!isObj(raw) || !Array.isArray(raw.days)) return null;
  const seen = new Set<number>();
  const days = raw.days.map(readDay).filter((d): d is PlanDraftDay => d != null && d.dayOfWeek >= 1 && d.dayOfWeek <= 7)
    .filter((d) => (seen.has(d.dayOfWeek) ? false : (seen.add(d.dayOfWeek), true)));
  if (!days.length) return null;
  const plan: CoachPlan = { name: strOr(raw.name, 'Plan'), description: strOr(raw.description, ''), days };
  if (typeof raw.basedOnHash === 'string') plan.basedOnHash = raw.basedOnHash;
  const patch = readPatch(raw.patch);
  if (patch) plan.patch = patch;
  return plan;
}

export function readPatch(raw: unknown): PlanPatch | null {
  if (!isObj(raw) || !Array.isArray(raw.changes)) return null;
  const changes: PlanChange[] = [];
  for (const c of raw.changes) {
    if (!isObj(c) || typeof c.type !== 'string') continue;
    const dow = intOrNull(c.dayOfWeek);
    if (dow == null) continue;
    changes.push({
      type: c.type, dayOfWeek: dow,
      exerciseId: typeof c.exerciseId === 'string' ? c.exerciseId : null,
      newExerciseId: typeof c.newExerciseId === 'string' ? c.newExerciseId : null,
      sets: intOrNull(c.sets), repsMin: intOrNull(c.repsMin), repsMax: intOrNull(c.repsMax), seconds: intOrNull(c.seconds),
      weightKg: numOrNull(c.weightKg), restSeconds: intOrNull(c.restSeconds), note: typeof c.note === 'string' ? c.note : null,
    });
  }
  return changes.length ? { changes } : null;
}

const parse = (s: string): unknown => { try { return JSON.parse(s); } catch { return undefined; } };

// ---------------------------------------------------------------- PlanJson

const FENCED = /```(?:json)?\s*(\{[\s\S]*?\})\s*```/g;
const fencedBlocks = (text: string): string[] => [...text.matchAll(FENCED)].map((m) => m[1]);
const lastWith = (text: string, needle: string): string | undefined => fencedBlocks(text).filter((b) => b.includes(needle)).pop();

/** The last ```json block with "days" as a plan, or null. */
export function extractPlan(text: string): CoachPlan | null {
  const block = lastWith(text, '"days"');
  return block ? readPlan(parse(block)) : null;
}

/** A small-edit block: ```json {"changes": [...]} ```, or null. */
export function extractPatch(text: string): PlanPatch | null {
  const block = lastWith(text, '"changes"');
  return block ? readPatch(parse(block)) : null;
}

/** True when the reply carries a plan or change block that could not be read: worth one repair round. */
export function hasBrokenBlock(text: string): boolean {
  const blocks = [...text.matchAll(/```(?:json)?\s*([\s\S]*?)```/g)].map((m) => m[1]);
  return blocks.some((b) => b.includes('"days"') || b.includes('"changes"')) && !extractPlan(text) && !extractPatch(text);
}

/** A pasted plan that is already JSON (with or without a ```json fence), so it needs no AI round trip. */
export function parsePastedPlan(text: string): CoachPlan | null {
  const trimmed = text.trim();
  const fenced = [...trimmed.matchAll(FENCED)][0]?.[1];
  const body = fenced ?? (trimmed.startsWith('{') && trimmed.endsWith('}') ? trimmed : null);
  return body ? readPlan(parse(body)) : null;
}

// ---------------------------------------------------------------- Memory ("MEMORY: <fact>" lines)

const MEMORY_LINE = /^[ \t]*\*{0,2}MEMORY:\*{0,2}[ \t]*(.+?)[ \t]*$/gim;
export const MAX_NOTES = 15;

export const extractMemory = (text: string): string[] =>
  [...text.matchAll(MEMORY_LINE)].map((m) => m[1].trim().replace(/\.+$/, '')).filter(Boolean);
export const stripMemory = (text: string): string => text.replace(MEMORY_LINE, '').trimEnd();

/** Adds `added` to `existing` (one note per line), skipping duplicates and keeping the newest MAX_NOTES. */
export function mergeMemory(existing: string, added: string[]): string {
  const notes = existing.split('\n').map((n) => n.trim()).filter(Boolean);
  for (const n of added) if (!notes.some((x) => x.toLowerCase() === n.toLowerCase())) notes.push(n);
  return notes.slice(-MAX_NOTES).join('\n');
}

/** The reply without its plan block and MEMORY line: what the person reads. */
export const stripReply = (text: string): string => stripMemory(text.replace(FENCED, '')).trim();

/** The text to show while a reply is still arriving: an unfinished ``` block and any MEMORY line are hidden. */
export function stripPartial(text: string): string {
  const cleaned = stripReply(text);
  const fences = (cleaned.match(/```/g) ?? []).length;
  const cut = fences % 2 === 1 ? cleaned.slice(0, cleaned.lastIndexOf('```')) : cleaned;
  return stripMemory(cut).trimEnd();
}

// ---------------------------------------------------------------- PlanIntent

const PLAN_WORD = /\b(plan|program|programme|split|routine|schedule)s?\b/;
const ACTION = /\b(make|build|create|write|design|give|new|change|update|adjust|modify|redo|swap|replace|generate|set up|turn|convert)\b/;
const AR_PLAN = /جدول|برنامج|خطة|خطه|روتين|سبليت/;
const AR_ACTION = /اعمل|سوي|سوّي|ابني|اكتب|صمم|غير|غيّر|عدل|عدّل|بدل|جديد|اعطني|عطني|ابغى|أبغى|بدي|عايز|حضر|جهز/;
const EDIT_ACTION = /\b(swap|replace|remove|drop|add|switch|substitute|more|less|fewer|extra)\b/;
const AR_EDIT = /بدل|بدّل|استبدل|احذف|أضف|اضف|زد|قلل|أنقص|غير|غيّر/;
const DAY_WORD = /\b(mon|tue|wed|thu|fri|sat|sun)(day|s|sday|nesday|rsday|urday)?\b/;
const AR_DAY = /الاثنين|الإثنين|الثلاثاء|الأربعاء|الاربعاء|الخميس|الجمعة|السبت|الأحد|الاحد/;
const ASKED_EQUIPMENT = /(equipment|gym|dumbbell|pull-?up|bodyweight|معدات|نادي|دمبل|عقلة|وزن الجسم)/i;

/** True for "make me a new plan" and for edits like "swap dips on Tuesday": an edit verb plus a day or a plan exercise. */
export function wantsPlanText(text: string, planExercises: readonly string[] = []): boolean {
  const s = text.toLowerCase();
  if ((PLAN_WORD.test(s) || AR_PLAN.test(s)) && (ACTION.test(s) || AR_ACTION.test(s))) return true;
  if (!(EDIT_ACTION.test(s) || AR_EDIT.test(s))) return false;
  return DAY_WORD.test(s) || AR_DAY.test(s) || planExercises.some((n) => n.length >= 3 && s.includes(n.toLowerCase()));
}

export type ChatLike = { role: string; text: string; kind: string; planJson: string | null };

/** True when the earlier user turn asked for a plan, the coach asked an intake question, and the person just answered it. */
export function isReplyToIntake(history: readonly ChatLike[], latestUserText: string): boolean {
  if (history.length < 3) return false;
  const prevCoach = history[history.length - 2];
  const prevUser = history[history.length - 3];
  if (prevCoach.role !== 'coach' || prevCoach.planJson != null) return false;
  return wantsPlanText(prevUser.text) && ASKED_EQUIPMENT.test(prevCoach.text) && latestUserText.trim() !== '';
}

/** Script of the person's latest message, so the reply never drifts into another language. */
export function languageOf(text: string): string {
  if (/[؀-ۿݐ-ݿ]/.test(text)) return 'Arabic';
  if (/\p{L}/u.test(text) && [...text].some((c) => /\p{L}/u.test(c) && c.charCodeAt(0) < 0x250)) return 'English';
  return "the language of the user's latest message";
}

// ---------------------------------------------------------------- History for the model

/**
 * Drops error bubbles, background check-ins and unanswered questions so the model only sees clean user->coach
 * exchanges. The newest message always stays.
 */
export function answeredHistory<T extends ChatLike>(messages: readonly T[]): T[] {
  const conv = messages.filter((m) => m.kind !== 'checkin' && m.kind !== 'gemini');
  return conv.filter((m, i) => {
    if (m.kind === 'error') return false;
    if (i === conv.length - 1) return true;
    if (m.role === 'user') return conv[i + 1].role === 'coach' && conv[i + 1].kind !== 'error';
    return true;
  });
}

export type Turn = { role: 'user' | 'model'; text: string };

/** Turns that strictly alternate user/model and end with a user turn. */
export function alternatingTurns(history: readonly ChatLike[]): Turn[] {
  const out: Turn[] = [];
  for (const m of history) {
    const role = m.role === 'user' ? 'user' : 'model';
    const last = out[out.length - 1];
    if (last && last.role === role) last.text += `\n\n${m.text}`;
    else out.push({ role, text: m.text });
  }
  while (out.length && out[out.length - 1].role !== 'user') out.pop();
  return out;
}

// ---------------------------------------------------------------- PlanHash, validator, patches

const f1 = (x: number | null): string => (x == null ? 'null' : Number.isInteger(x) ? x.toFixed(1) : String(x));
const n = (x: number | null): string => (x == null ? 'null' : String(x));

/** A 64-bit FNV-1a fingerprint of a plan's training content, so a stale proposal can be told apart. */
export function planHash(plan: { days: PlanDraftDay[] }): string {
  let s = '';
  for (const d of [...plan.days].sort((a, b) => a.dayOfWeek - b.dayOfWeek)) {
    s += `${d.dayOfWeek}${d.isRest ? 'R' : 'T'}[`;
    for (const i of d.items) s += `${i.exerciseId},${i.block},${i.circuit},${i.sets},${n(i.repsMin)},${n(i.repsMax)},${n(i.seconds)},${f1(i.weightKg)},${i.restSeconds};`;
    s += ']';
  }
  let h = BigInt('0xcbf29ce484222325');
  const prime = BigInt('0x100000001b3');
  for (const b of new TextEncoder().encode(s)) h = BigInt.asUintN(64, (h ^ BigInt(b)) * prime);
  return h.toString(16).padStart(16, '0');
}

type Known = ReadonlyMap<string, ExerciseEntity>;
const norm = (raw: string): string => raw.trim().toLowerCase().replace(/[\s-]+/g, '_').replace(/[^a-z0-9_]/g, '');

/** The library id `raw` most likely means, or null. Tries exact, normalised, singular/plural and name matches. */
export function canonicalId(raw: string, name: string | null | undefined, known: Known): string | null {
  if (known.has(raw)) return raw;
  const s = norm(raw);
  if (known.has(s)) return s;
  const variants = [s.replace(/es$/, ''), s.replace(/s$/, ''), `${s}s`, `${s}es`];
  const hit = variants.find((v) => known.has(v));
  if (hit) return hit;
  const byName = new Map([...known.values()].map((e) => [norm(e.name), e.id]));
  return byName.get(s) ?? (name ? byName.get(norm(name)) : undefined) ?? null;
}

const clampN = (x: number, lo: number, hi: number) => Math.min(hi, Math.max(lo, x));
function clampItem(i: PlanDraftItem): PlanDraftItem {
  const repsMin = i.repsMin == null ? null : clampN(i.repsMin, 1, 100);
  const repsMax = i.repsMax == null ? null : (repsMin != null ? Math.max(repsMin, clampN(i.repsMax, 1, 100)) : clampN(i.repsMax, 1, 100));
  return {
    ...i, sets: clampN(i.sets, 1, 10), repsMin, repsMax,
    seconds: i.seconds == null ? null : clampN(i.seconds, 5, 3600),
    weightKg: i.weightKg == null ? null : clampN(i.weightKg, 0, 500),
    restSeconds: clampN(i.restSeconds, 0, 600),
  };
}

export type ValidationResult = { draft: CoachPlan; errors: string[]; unknownIds: Set<string> };

/** Matches ids to the library (near misses are fixed), keeps numbers sane, and lists what is still wrong. */
export function validatePlan(draft: CoachPlan, known: Known): ValidationResult {
  const errors: string[] = [];
  const unknown = new Set<string>();
  const days = draft.days.map((day) => {
    const items = day.items.map((item) => {
      const id = canonicalId(item.exerciseId, item.name, known);
      const described = !!item.name?.trim() && item.muscles.some((m) => (MUSCLES as readonly string[]).includes(m));
      if (id == null && !described) {
        unknown.add(item.exerciseId);
        errors.push(`Day ${day.dayOfWeek}: unknown exerciseId "${item.exerciseId}". Use an id from the list, or give a new exercise a name, kind and muscles.`);
      }
      return clampItem({ ...item, exerciseId: id ?? (norm(item.exerciseId) || item.exerciseId) });
    });
    const byId = new Map<string, PlanDraftItem[]>();
    for (const i of items) byId.set(i.exerciseId, [...(byId.get(i.exerciseId) ?? []), i]);
    for (const [dup, list] of byId) {
      if (list.length > 1 && !list.some((i) => i.circuit)) errors.push(`Day ${day.dayOfWeek}: "${dup}" appears twice; pick a different exercise or merge the sets.`);
    }
    return { ...day, items };
  });
  return { draft: { ...draft, days }, errors, unknownIds: unknown };
}

/**
 * Last resort after the repair round: each unknown id becomes the closest library exercise (same movement and muscles,
 * available equipment). Returns the plan and the swaps made as [old id, new exercise name ('' when dropped)].
 */
export function replaceUnknown(
  draft: CoachPlan, unknownIds: ReadonlySet<string>, library: readonly ExerciseEntity[], equipment: ReadonlySet<Equipment>,
): [CoachPlan, [string, string][]] {
  const swaps: [string, string][] = [];
  const unknownNorm = new Set([...unknownIds].map(norm));
  const days = draft.days.map((day) => {
    const used = new Set(day.items.map((i) => i.exerciseId));
    const items: PlanDraftItem[] = [];
    for (const item of day.items) {
      if (!unknownIds.has(item.exerciseId) && !unknownNorm.has(norm(item.exerciseId))) { items.push(item); continue; }
      const kind: ExerciseKind = item.kind != null && isExerciseKind(item.kind) ? item.kind : item.seconds != null ? 'HOLD' : item.weightKg != null ? 'WEIGHTED' : 'REPS';
      const stand: ExerciseEntity = {
        id: item.exerciseId, name: item.name ?? item.exerciseId.replace(/_/g, ' '), kind,
        primary: item.muscles.filter(isMuscle), secondary: [],
      };
      const pick = rankSubstitutes(stand, library, equipment).find((c) => c.matchesUserEquipment && !used.has(c.exercise.id) && c.score >= 20)?.exercise;
      if (!pick) { swaps.push([item.exerciseId, '']); continue; }
      used.add(pick.id);
      swaps.push([item.exerciseId, pick.name]);
      items.push(translateDraftItem(item, pick));
    }
    return { ...day, items };
  });
  return [{ ...draft, days }, swaps];
}

export type PatchOutcome = { draft: CoachPlan | null; errors: string[] };

/** Applies a coach patch to a plan; every change must find its day and exercise, or nothing is applied. */
export function applyPatch(base: CoachPlan, patch: PlanPatch, known: Known): PatchOutcome {
  if (!patch.changes.length) return { draft: null, errors: ['The changes list is empty.'] };
  const errors: string[] = [];
  const days = new Map(base.days.map((d) => [d.dayOfWeek, d]));
  for (const c of patch.changes) {
    const day = days.get(c.dayOfWeek) ?? (c.type === 'add' ? { dayOfWeek: c.dayOfWeek, title: `Day ${c.dayOfWeek}`, isRest: true, note: '', items: [] } : undefined);
    if (!day || c.dayOfWeek < 1 || c.dayOfWeek > 7) { errors.push(`No day ${c.dayOfWeek} in the plan.`); continue; }
    const exId = c.exerciseId ? canonicalId(c.exerciseId, null, known) ?? c.exerciseId : null;
    const at = day.items.findIndex((i) => i.exerciseId === exId);
    const withFields = (i: PlanDraftItem): PlanDraftItem => ({
      ...i, sets: c.sets ?? i.sets, repsMin: c.repsMin ?? i.repsMin, repsMax: c.repsMax ?? i.repsMax, seconds: c.seconds ?? i.seconds,
      weightKg: c.weightKg ?? i.weightKg, restSeconds: c.restSeconds ?? i.restSeconds, note: c.note ?? i.note,
    });
    const newId = c.newExerciseId ? canonicalId(c.newExerciseId, null, known) : null;
    switch (c.type) {
      case 'swap':
        if (at < 0) errors.push(`Day ${c.dayOfWeek}: "${c.exerciseId}" is not in that day.`);
        else if (!newId) errors.push(`Day ${c.dayOfWeek}: unknown newExerciseId "${c.newExerciseId}".`);
        else if (day.items.some((i) => i.exerciseId === newId)) errors.push(`Day ${c.dayOfWeek}: "${newId}" is already in that day.`);
        else {
          const items = [...day.items];
          items[at] = withFields(translateDraftItem(day.items[at], known.get(newId)!));
          days.set(c.dayOfWeek, { ...day, items });
        }
        break;
      case 'add':
        if (!newId) errors.push(`Day ${c.dayOfWeek}: unknown newExerciseId "${c.newExerciseId}".`);
        else if (day.items.some((i) => i.exerciseId === newId)) errors.push(`Day ${c.dayOfWeek}: "${newId}" is already in that day.`);
        else {
          const hold = known.get(newId)!.kind === 'HOLD';
          const fresh: PlanDraftItem = {
            exerciseId: newId, name: null, kind: null, muscles: [], block: 'Main', circuit: false, sets: c.sets ?? 3,
            repsMin: hold ? null : c.repsMin ?? 8, repsMax: hold ? null : c.repsMax ?? 12, seconds: hold ? c.seconds ?? 30 : null,
            weightKg: c.weightKg ?? null, restSeconds: c.restSeconds ?? 90, note: c.note ?? '', videoUrl: null,
          };
          days.set(c.dayOfWeek, { ...day, isRest: false, items: [...day.items, fresh] });
        }
        break;
      case 'remove':
        if (at < 0) errors.push(`Day ${c.dayOfWeek}: "${c.exerciseId}" is not in that day.`);
        else days.set(c.dayOfWeek, { ...day, items: day.items.filter((_, i) => i !== at) });
        break;
      case 'edit':
        if (at < 0) errors.push(`Day ${c.dayOfWeek}: "${c.exerciseId}" is not in that day.`);
        else { const items = [...day.items]; items[at] = withFields(items[at]); days.set(c.dayOfWeek, { ...day, items }); }
        break;
      default:
        errors.push(`Unknown change type "${c.type}"; use swap, add, remove or edit.`);
    }
  }
  if (errors.length) return { draft: null, errors };
  return { draft: { ...base, days: [...days.values()].sort((a, b) => a.dayOfWeek - b.dayOfWeek), basedOnHash: planHash(base), patch }, errors: [] };
}

/** "Tuesday: Dips -> Push-up" lines for a change-only card, in the app language. */
export function describeChanges(patch: PlanPatch, library: Known, lang: string): string[] {
  const nameOf = (id?: string | null) => {
    if (!id) return '';
    const c = canonicalId(id, null, library) ?? id;
    return library.get(c)?.name ?? c;
  };
  return patch.changes.map((c) => {
    const dow = Math.min(7, Math.max(1, c.dayOfWeek));
    const day = new Intl.DateTimeFormat(lang, { weekday: 'long', timeZone: 'UTC' }).format(new Date(Date.UTC(1970, 0, 4 + dow)));
    switch (c.type) {
      case 'swap': return t('coach_change_swap', day, nameOf(c.exerciseId), nameOf(c.newExerciseId));
      case 'add': return t('coach_change_add', day, nameOf(c.newExerciseId));
      case 'remove': return t('coach_change_remove', day, nameOf(c.exerciseId));
      default: return t('coach_change_edit', day, nameOf(c.exerciseId));
    }
  });
}

// ---------------------------------------------------------------- Debrief

export const MIN_SCORE = 4;

/** Reads the model's JSON (bare or fenced, with chatter around it); null when it isn't usable. */
export function parseDebrief(text: string): WorkoutDebrief | null {
  const start = text.indexOf('{');
  const end = text.lastIndexOf('}');
  if (start < 0 || end <= start) return null;
  const raw = parse(text.slice(start, end + 1));
  if (!isObj(raw)) return null;
  const summary = strOr(raw.summary, '').trim();
  if (!summary) return null;
  const list = (x: unknown, max: number) => strList(x).map((s) => s.trim()).filter(Boolean).slice(0, max);
  return {
    score: clampN(Math.round(Number(raw.score) || 0), MIN_SCORE, 10),
    summary: summary.slice(0, 240),
    highlights: list(raw.highlights, 2), watch: list(raw.watch, 1), nextTime: list(raw.nextTime, 1),
  };
}

/** The text stored on the session row (Android WorkoutDebrief.encode). */
export const encodeDebrief = (d: WorkoutDebrief): string => JSON.stringify(d);

/** Android WorkoutDebrief.decode, with all four lists. */
export function decodeDebriefFull(text: string): WorkoutDebrief | null {
  if (!text.trim()) return null;
  const raw = parse(text);
  if (!isObj(raw)) return null;
  const summary = strOr(raw.summary, '');
  if (!summary.trim()) return null;
  return {
    score: Math.round(Number(raw.score) || 0), summary,
    highlights: strList(raw.highlights), watch: strList(raw.watch), nextTime: strList(raw.nextTime),
  };
}

export type DebriefSet = { reps: number | null; seconds: number | null; weightKg: number | null; effort: string; note: string; isPr: boolean; setIndex: number };
export type DebriefInput = {
  title: string; epochDay: number; minutes: number; goal: string; note: string
  readiness?: { recovery: number; sleepMin: number } | null
  exercises: { name: string; done: DebriefSet[]; lastTime: DebriefSet[] }[]
};

function debriefLine(list: readonly DebriefSet[]): string {
  return [...list].sort((a, b) => a.setIndex - b.setIndex).map((st) => [
    st.reps != null ? `${st.reps} reps` : null,
    st.seconds != null ? `${st.seconds}s` : null,
    st.weightKg != null && st.weightKg > 0 ? `${st.weightKg}kg` : null,
    st.effort.trim() && st.effort !== 'OK' ? st.effort.toLowerCase() : null,
    st.note.trim() ? `"${st.note}"` : null,
    st.isPr ? 'PR' : null,
  ].filter((x): x is string => x != null).join(' ')).join(', ');
}

/** The user message of the debrief request (Android generateDebrief payload). */
export function debriefPayload(i: DebriefInput): string {
  const day = new Date(i.epochDay * 86_400_000).toISOString().slice(0, 10);
  let s = `Workout: ${i.title}, ${day}, ${i.minutes} min\nBody goal: ${i.goal}\n`;
  if (i.readiness) s += `Readiness that day: recovery ${i.readiness.recovery}%, sleep ${i.readiness.sleepMin} min\n`;
  if (i.note.trim()) s += `Their note: ${i.note}\n`;
  for (const e of i.exercises) {
    s += `- ${e.name}: ${debriefLine(e.done)}\n`;
    if (e.lastTime.length) s += `  last time: ${debriefLine(e.lastTime)}\n`;
  }
  return s;
}

export function debriefSystem(lang: 'en' | 'ar'): string {
  const language = lang === 'ar' ? 'formal Modern Standard Arabic (Western digits)' : 'English';
  return [
    'You are a friendly strength coach writing a 10-second debrief of a workout that was just finished.',
    'Reply with ONLY a JSON object: {"score": 4-10, "summary": one short sentence, "highlights": [1-2 short items citing real numbers],',
    '"watch": [0-1 short item: a stall, a pain/hard note, or low readiness], "nextTime": [1 short, concrete tip]}.',
    'Finishing counts: never score below 4. Be specific, warm and brief. Write in ' + language + '.',
  ].join('\n');
}

// ---------------------------------------------------------------- Prompts

export const CORE_RULES = `You are the head performance and nutrition coach inside ForgeQuest (acting as a high-precision Sports Science, Calisthenics & Gym Coach).
- Synthesize, do not just parrot stats back: connect the dots between Fitbit Readiness (recovery %, HRV vs baseline, resting HR, sleep debt, strain target), muscle recovery hours, training load / deload status, recent set performance, and daily protein/calorie intake.
- Give concrete, actionable prescriptions: when asked about today's session or progression, specify exact set/rep/hold/weight adjustments based on recovery state and their last logged sets. If a deload or lift plateau is flagged in the summary, proactively prescribe a structured deload (cutting sets by 35-40% for 1 week) or a biomechanical exercise variation.
- Strict sports nutrition guardrails: NEVER prescribe a daily calorie target below ${MIN_SAFE_KCAL} kcal/day or a deficit exceeding 22% of TDEE. Always maintain a protein floor of 1.6-2.2 g/kg and an essential dietary fat floor of at least 0.6-0.8 g/kg before allocating carbohydrates.
- Keep normal replies focused and punchy (120-250 words), using **bold** and clean bullet points, and go deeper when the user asks for analysis or meal/workout breakdowns.
- Never shame food choices; going over calories is fuel, not failure.`;

export const MEMORY_RULE = `If the user tells you something lasting about themselves (an injury, their available equipment, their schedule, a food preference, a goal), finish your reply with one final line exactly like: MEMORY: <one short sentence>
Only do this for new facts not already in "Things to remember" below. Never mention this mechanism.`;

const equipmentLabel = (e: Equipment) => EQUIPMENT_LABEL[e].en;

/** The plan JSON shape, exercise ids the person can do, and (with a current plan) the small-changes format. */
export function planSchemaInstructions(library: readonly ExerciseEntity[], available: ReadonlySet<Equipment> = new Set(), current: CoachPlan | null = null): string {
  const allowed = new Set<Equipment>(available.size ? available : EQUIPMENT);
  allowed.add('BODYWEIGHT');
  const ids = library.filter((e) => !e.custom && allowed.has(profileFor(e).equipment)).map((e) => e.id).join(', ');
  const limited = available.size > 0 && !EQUIPMENT.every((e) => allowed.has(e));
  const equipmentRule = limited
    ? `CRITICAL EQUIPMENT CONSTRAINT: The user ONLY has access to: ${[...allowed].map(equipmentLabel).join(', ')}. You MUST ONLY program exercises that use this equipment. NEVER program unavailable machines, cables, or barbells.`
    : "Match exercise selection to the user's available equipment.";
  let out = `Plan JSON shape: {"name": string, "description": string, "days": [{"dayOfWeek": 1-7 (1 = Monday), "title": string, "isRest": boolean, "note": string,
"items": [{"exerciseId": string, "name": string, "kind": "REPS"|"HOLD"|"WEIGHTED", "muscles": [string], "block": string, "circuit": boolean,
"sets": int, "repsMin": int|null, "repsMax": int|null, "seconds": int|null, "weightKg": number|null, "restSeconds": int, "note": string}]}]}
Include all 7 days. ${equipmentRule}
Prefer these equipment-compatible exercise ids: ${ids}.
For an exercise not in that list, invent a snake_case id that strictly uses their available equipment and set name, kind and muscles (from: ${MUSCLES.join(', ')}).
Keep the JSON short: for ids from the list leave out name, kind and muscles; omit "note" unless needed; one-sentence description.
Use "seconds" for holds, "repsMin"/"repsMax" for reps. Items with the same block and circuit=true are done round by round.`;
  if (current) {
    const lines = [...current.days].sort((a, b) => a.dayOfWeek - b.dayOfWeek).filter((d) => d.items.length).map((d) =>
      `  ${d.dayOfWeek}: ` + d.items.map((i) => `${i.exerciseId} ${i.sets}x${i.seconds != null ? `${i.seconds}s` : [...new Set([i.repsMin, i.repsMax].filter((x) => x != null))].join('-')}`).join(', ')).join('\n');
    out += `\n\nSMALL CHANGES: when the user wants to adjust the current plan (swap, add or remove a few exercises, or change sets/reps/seconds/weight/rest) and not a whole new week, send ONLY the changes instead of the full plan, as a \`\`\`json block at the very end:
{"changes": [{"type": "swap"|"add"|"remove"|"edit", "dayOfWeek": 1-7, "exerciseId": current id (swap/remove/edit), "newExerciseId": id (swap/add), "sets": int, "repsMin": int, "repsMax": int, "seconds": int, "weightKg": number, "restSeconds": int}]}
Only include the fields that change. Use ids from the list above. Current plan (dayOfWeek: exerciseId setsxreps):\n${lines}`;
  }
  return out;
}

/** System prompt of the free-text-to-plan conversion (Android parsePlanText). */
export const parsePlanSystem = (schema: string): string =>
  `Convert the user's workout plan text into JSON. Keep their exercises, sets, reps and days.\n${schema}\nReply ONLY with the JSON object, no other text.`;

export type CoachPromptOptions = {
  replyLanguage?: string; plan?: boolean; needsEquipmentIntake?: boolean
  /** planSchemaInstructions() text, needed only when plan is true. */
  schema?: string
  /** progressSummary() text. */
  summary: string
};

/** The chat system prompt: rules, plan mode, memory rule, the person's data and the reply language. */
export function coachSystemPrompt(o: CoachPromptOptions): string {
  const lang = o.replyLanguage ?? "the language of the user's latest message";
  const lines: string[] = [CORE_RULES, ''];
  if (o.needsEquipmentIntake) {
    lines.push(
      'EQUIPMENT INTAKE REQUIRED: The user asked to build or change a workout plan, but we do not yet know what equipment they have access to.',
      'Do NOT output a ```json plan block in this reply. Instead, ask a concise, friendly 3-bullet intake question:',
      '1. What equipment do you have access to? (e.g., Full commercial gym, home dumbbells/barbell, pull-up bar & dip bars, doorway pull-up bar only, or pure bodyweight)',
      '2. How many days per week do you want to train? (if not already stated)',
      '3. What is your main goal right now? (muscle growth, strength, fat loss, or calisthenics skills)',
      'Tell them you will generate their ready-to-apply plan as soon as they answer.',
    );
  } else if (o.plan) {
    lines.push('When the user asks to create or change their weekly plan, describe it briefly, then add the full plan as a ```json code block at the very end.', o.schema ?? '');
  } else {
    lines.push('Never write plan JSON in this reply. If they want a new or changed weekly plan, outline it in a few lines and tell them to say "make it my plan" (in Arabic: "اعمل لي الخطة") to get one they can apply.');
  }
  lines.push('', MEMORY_RULE, '', o.summary);
  let out = lines.join('\n') + `\nLANGUAGE: reply in ${lang}. Decide from the latest message only, never from earlier messages, the user's name or the data above.`;
  if (lang === 'Arabic') out += ' Write formal Modern Standard Arabic (الفصحى), not dialect: natural Arabic phrasing, not translated English; use the Arabic comma (،) and question mark (؟). Keep exercise names in English in parentheses the first time, and write numbers as Western digits (0-9).';
  return out;
}

/** The unsent error message when a repair is needed: the coach sees exactly what was wrong. */
export function repairRequest(problems: readonly string[]): string {
  return ['REPAIR REQUEST (automatic, from the app): your last plan JSON has these problems:', ...problems.slice(0, 12).map((p) => `- ${p}`),
    'Reply with ONLY the corrected ```json block (same shape as before), no other text.'].join('\n');
}

// ---------------------------------------------------------------- Context ("what the coach knows")

const trimNum = (x: number): string => x.toFixed(1).replace(/\.0$/, '');
const day1 = (d: number) => new Date(d * 86_400_000).toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric', timeZone: 'UTC' });
const dayLong = (dow: number) => new Intl.DateTimeFormat('en', { weekday: 'long', timeZone: 'UTC' }).format(new Date(Date.UTC(1970, 0, 4 + dow)));
const dayShort = (dow: number) => new Intl.DateTimeFormat('en', { weekday: 'short', timeZone: 'UTC' }).format(new Date(Date.UTC(1970, 0, 4 + dow)));

type SetLike = { reps: number | null; seconds: number | null; weightKg: number | null };
export function setLabel(kind: ExerciseKind | undefined, set: SetLike): string {
  const load = set.weightKg != null && set.weightKg > 0 ? set.weightKg : null;
  if (kind === 'HOLD') return `${set.seconds}s${load != null ? `+${trimNum(load)}kg` : ''}`;
  if (kind === 'WEIGHTED') return `${set.reps}×${trimNum(set.weightKg ?? 0)}kg`;
  return `${set.reps}${load != null ? `+${trimNum(load)}kg` : ''}`;
}

/**
 * What the coach knows about the user: goals, equipment, this week, plan, recovery, fatigue/deload status, recent
 * sessions, 4-week trends, records, nutrition, badges and remembered facts. (Fitbit lines are left out: no Fitbit on the web.)
 */
export function progressSummary(snap: ProgressSnapshot, today: number, readiness: readonly ReadinessDayRow[], equipment: ReadonlySet<Equipment>): string {
  const s = snap.settings;
  const ex = snap.exercises;
  const dateText = new Date(today * 86_400_000).toLocaleDateString('en-US', { weekday: 'long', month: 'short', day: 'numeric', year: 'numeric', timeZone: 'UTC' });
  let out = `User data (today is ${dateText}):\n`;
  if (s.name.trim()) out += `- Name: ${s.name}\n`;

  if (snap.latestWeight != null) {
    const w = snap.weights;
    const monthAgo = [...w].reverse().find((x) => x.epochDay <= today - 28) ?? w[0];
    let trend = '';
    if (monthAgo && monthAgo.epochDay < (w[w.length - 1]?.epochDay ?? today)) {
      const delta = snap.latestWeight - monthAgo.kg;
      const days = w[w.length - 1].epochDay - monthAgo.epochDay;
      trend = Math.abs(delta) < 0.05 ? ` (steady over the last ${days} days)` : ` (${delta > 0 ? '+' : '−'}${Math.abs(delta).toFixed(1)} kg over the last ${days} days)`;
    }
    out += `- Body weight: ${snap.latestWeight.toFixed(1)} kg${trend}\n`;
  }
  const body = [
    s.heightCm > 0 ? `Height ${Math.trunc(s.heightCm)} cm` : null,
    s.birthYear > 0 ? `age ${new Date(today * 86_400_000).getUTCFullYear() - s.birthYear}` : null,
    s.bodyFatPct >= 4 && s.bodyFatPct <= 60 ? `body fat ${s.bodyFatPct.toFixed(1)}%` : null,
  ].filter((x): x is string => x != null);
  if (body.length) out += `- ${body.join(', ')}\n`;
  if (/lb/i.test(s.unit)) out += '- Units: the user thinks in pounds. The data below is in kg; convert weights to lb when you mention them.\n';

  const eq = [...equipment];
  out += eq.length ? `- Available equipment: ${eq.map(equipmentLabel).join(', ')}\n` : '- Available equipment: not specified yet (ask before building a workout plan)\n';

  const trainingCount = snap.plan ? new Set(snap.plan.days.filter((d) => !d.day.isRest && d.items.length).map((d) => d.day.dayOfWeek)).size : 0;
  const target = s.weeklyTargetOverride > 0 ? s.weeklyTargetOverride : trainingCount;
  out += `- Goals: ${s.kcalGoal} kcal and ${s.proteinGoal} g protein per day; ${target} workouts per week\n`;
  const goalText = s.nutritionGoal === 'CUT' ? 'CUT (lose fat while keeping muscle and strength: keep protein high, aim to hold lifts steady, a moderate deficit)'
    : s.nutritionGoal === 'GAIN' ? 'GAIN (build muscle with a small surplus: push progressive overload)'
    : 'MAINTAIN (keep body weight steady while getting stronger)';
  out += `- Body goal: ${goalText}\n`;
  const wt = weightTrendNote(s.nutritionGoal, s.goalSetDay, snap.weights.map((w) => [w.epochDay, w.kg] as const), today);
  if (wt === 'CUT_TOO_FAST') out += '- Weight trend: losing more than 1% of body weight a week on the cut; suggest eating a little more\n';
  if (wt === 'GAIN_STALLED') out += '- Weight trend: no gain for 3+ weeks on the gain goal; suggest a little more food\n';

  const st = snap.streak;
  const missed = st.days.map((d, i) => (d === 'MISSED' ? dayLong(i + 1) : null)).filter((x): x is string => x != null);
  const upcoming = st.days.map((d, i) => (d === 'UPCOMING' ? dayLong(i + 1) : null)).filter((x): x is string => x != null);
  out += `- Level ${snap.level.level} (rank ${snap.level.rank.letter}, ${snap.level.rank.title}); weekly streak ${st.weeks} (best ${st.best})\n`;
  out += st.targetThisWeek === 0
    ? `- This week: ${st.doneThisWeek} workouts done; they started mid-week, so the weekly target starts next Monday`
    : `- This week: ${st.doneThisWeek} of ${st.targetThisWeek} workouts done`;
  if (missed.length) out += `; missed planned days: ${missed.join(', ')}`;
  if (upcoming.length) out += `; still to come: ${upcoming.join(', ')}`;
  out += '\n';
  const tags = [...snap.dayTags.entries()].filter(([d]) => d >= today - 13).sort((a, b) => a[0] - b[0]);
  if (tags.length) out += `- Excused days (last 2 weeks; never treat these as missed, be kind about them): ${tags.map(([d, tag]) => `${day1(d).replace(',', '')} ${tag.toLowerCase()}`).join(', ')}\n`;
  const dow = ((today + 3) % 7 + 7) % 7 + 1;
  const todayPlan = snap.plan?.days.find((d) => d.day.dayOfWeek === dow);
  out += '- Today: ' + (!todayPlan || todayPlan.day.isRest ? 'rest day' : snap.workoutDoneToday ? `${todayPlan.day.title} (done)` : `${todayPlan.day.title} (not done yet)`) + '\n';

  if (snap.plan) {
    out += `- Current plan: ${snap.plan.plan.name} (source: ${snap.plan.plan.source})\n`;
    for (const d of [...snap.plan.days].sort((a, b) => a.day.dayOfWeek - b.day.dayOfWeek)) {
      if (d.day.isRest) { out += `  ${dayShort(d.day.dayOfWeek)}: rest\n`; continue; }
      const names = [...d.items].sort((a, b) => a.position - b.position).map((i) => {
        const e = ex.get(i.exerciseId);
        const per = e?.kind === 'HOLD' ? `${i.seconds ?? 30}s`
          : i.repsMin != null && i.repsMax != null && i.repsMax !== i.repsMin ? `${i.repsMin}-${i.repsMax}`
          : i.repsMin != null ? `${i.repsMin}` : '';
        return `${e?.name ?? i.exerciseId}${per ? ` ${i.sets}×${per}` : ''}`;
      }).join(', ');
      out += `  ${dayShort(d.day.dayOfWeek)}: ${d.day.title}: ${names}\n`;
    }
  } else out += '- No plan set yet\n';

  const recovering = [...snap.recovery.entries()].filter(([, r]) => r.fatigue >= 0.2).sort((a, b) => b[1].fatigue - a[1].fatigue).slice(0, 6);
  if (recovering.length) out += `- Muscle recovery: ${recovering.map(([m, r]) => `${MUSCLE_INFO[m].label} (${Math.round((1 - r.fatigue) * 100)}% recovered, ready in ${r.hoursUntilReady} h)`).join(', ')}\n`;

  const rd = [...readiness].sort((a, b) => b.epochDay - a.epochDay).find((r) => r.epochDay >= today - 1);
  if (rd) out += `- Readiness (${rd.epochDay === today ? 'today' : 'yesterday'}): recovery ${rd.recovery}%, sleep ${rd.sleepMin} min${rd.hrv > 0 ? `, HRV ${Math.round(rd.hrv)}` : ''}${rd.rhr > 0 ? `, resting HR ${Math.round(rd.rhr)}` : ''}\n`;

  const sessions = snap.sessions as unknown as SessionEntity[];
  const done = snap.doneSets;
  if (sessions.length) {
    const load = analyzeFatigueAndPlateaus(sessions, done as never, ex, today, [...readiness].sort((a, b) => a.epochDay - b.epochDay).slice(-14));
    out += `- Training load & deload status: weekly set volume is ${load.trainingLoadPct}% of 4-week average`;
    if (load.stalledLifts.length) out += `; stalled lifts (2+ weeks without progression): ${load.stalledLifts.slice(0, 4).map((l) => `${l.exerciseName} (${l.sessionsCount} sessions)`).join(', ')}`;
    if (load.needsDeload) out += `; DELOAD RECOMMENDED (${load.deloadReasons.join('; ')} — suggest reducing working sets by 35-40% or intensity by 10% for 1 week)`;
    out += '\n';
  }

  const recent = snap.sessions.slice(0, 5);
  if (recent.length) {
    out += '- Recent workouts (newest first):\n';
    for (const se of recent) {
      const sets = done.filter((x) => x.sessionId === se.id);
      const minutes = se.finishedAt != null ? Math.trunc((se.finishedAt - se.startedAt) / 60_000) : null;
      const groups = new Map<string, typeof sets>();
      for (const x of sets) groups.set(x.exerciseId, [...(groups.get(x.exerciseId) ?? []), x]);
      const summary = [...groups.entries()].slice(0, 7).map(([id, list]) => `${ex.get(id)?.name ?? id} ${list.map((x) => setLabel(ex.get(id)?.kind, x)).join('/')}${list.some((x) => x.isPr) ? ' PR' : ''}`).join('; ');
      out += `  ${new Date(se.epochDay * 86_400_000).toISOString().slice(0, 10)} ${se.title}${minutes != null ? ` (${minutes} min)` : ''}: ${summary}\n`;
      const nm = (id: string) => ex.get(id)?.name ?? id;
      const notes = sets.filter((x) => x.note.trim()).slice(0, 4).map((x) => `${nm(x.exerciseId)}: ${x.note}`).join('; ');
      if (notes) out += `    notes: ${notes}\n`;
      const felt = sets.filter((x) => x.effort === 'HARD' || x.effort === 'PAIN').slice(0, 4).map((x) => `${nm(x.exerciseId)} set ${x.setIndex + 1}: ${x.effort.toLowerCase()}`).join('; ');
      if (felt) out += `    felt: ${felt}\n`;
    }
  }

  const sessionDay = new Map(snap.sessions.map((x) => [x.id!, x.epochDay]));
  const byEx = new Map<string, typeof done>();
  for (const x of done) byEx.set(x.exerciseId, [...(byEx.get(x.exerciseId) ?? []), x]);
  const score = (kind: ExerciseKind, x: SetLike & { exerciseId?: string }) => recordScore(kind, x.reps, x.seconds, x.weightKg, x.exerciseId);
  const best = <T extends SetLike>(kind: ExerciseKind, list: T[]): T => list.reduce((m, x) => (score(kind, x) > score(kind, m) ? x : m));
  const trends = [...byEx.entries()].sort((a, b) => b[1].length - a[1].length).slice(0, 8).flatMap(([id, sets]) => {
    const e = ex.get(id);
    if (!e) return [];
    const old = sets.filter((x) => { const d = sessionDay.get(x.sessionId) ?? 0; return d >= today - 56 && d <= today - 21; });
    const fresh = sets.filter((x) => (sessionDay.get(x.sessionId) ?? 0) > today - 21);
    if (!old.length || !fresh.length) return [];
    return [`${e.name} ${setLabel(e.kind, best(e.kind, old))} → ${setLabel(e.kind, best(e.kind, fresh))}`];
  });
  if (trends.length) out += `- Best set 3-8 weeks ago → best set in the last 3 weeks: ${trends.join('; ')}\n`;

  const bests = [...byEx.entries()].flatMap(([id, sets]) => {
    const e = ex.get(id);
    return e && sets.length ? [{ name: e.name, label: setLabel(e.kind, best(e.kind, sets)), count: sets.length }] : [];
  }).sort((a, b) => b.count - a.count).slice(0, 12);
  if (bests.length) out += `- Personal bests: ${bests.map((b) => `${b.name} ${b.label}`).join(', ')}\n`;

  const week = snap.nutritionByDay.filter((d) => d.epochDay > today - 7 && d.epochDay < today);
  if (week.length) {
    const hit = week.filter((d) => d.protein >= s.proteinGoal).length;
    out += `- Last 7 days nutrition: avg ${Math.trunc(week.reduce((a, d) => a + d.kcal, 0) / week.length)} kcal, ${Math.trunc(week.reduce((a, d) => a + d.protein, 0) / week.length)} g protein over ${week.length} logged days; protein goal hit on ${hit} of them\n`;
  } else out += '- No meals logged in the last 7 days\n';
  out += `- Today so far: ${Math.round(snap.today.kcal)} kcal, ${Math.round(snap.today.protein)} g protein`;
  if (s.trackCarbs) out += `, ${Math.round(snap.today.carbs)} g carbs (goal ${s.carbsGoal} g)`;
  out += '\n';

  const unlocked = [...snap.badges].sort((a, b) => b.unlockedAt - a.unlockedAt);
  out += `- Badges: ${unlocked.length} of ${BADGES.length} unlocked`;
  if (unlocked.length) out += `; latest: ${unlocked.slice(0, 3).map((b) => BADGES_BY_ID.get(b.id)?.title).filter(Boolean).join(', ')}`;
  out += '\n';

  const notes = s.coachNotes.split('\n').filter((l) => l.trim());
  if (notes.length) out += '- Things to remember about the user:\n' + notes.map((l) => `  • ${l}\n`).join('');
  return out;
}
