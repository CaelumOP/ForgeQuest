import type { ExerciseEntity, PlanDraft, PlanItemEntity } from './entities';
import { MUSCLES, type Muscle } from './muscle';
import { parseProgressionMode } from './progression';
import { isExtraBlock } from './pr-picker';
import { parseScheduleMode } from './rotation';

export const PLAN_SHARE_FORMAT = 'forgequest-plan-v1';

export type SharedItem = {
  exerciseId: string;
  name?: string | null;
  kind?: string | null;
  muscles?: string[];
  block?: string;
  circuit?: boolean;
  sets?: number;
  repsMin?: number | null;
  repsMax?: number | null;
  seconds?: number | null;
  weightKg?: number | null;
  restSeconds?: number;
  note?: string;
  videoUrl?: string | null;
  supersetGroup?: number | null;
  autoWarmup?: boolean;
  progressionMode?: string;
};

export type NormalizedSharedItem = {
  exerciseId: string;
  name: string | null;
  kind: string | null;
  muscles: string[];
  block: string;
  circuit: boolean;
  sets: number;
  repsMin: number | null;
  repsMax: number | null;
  seconds: number | null;
  weightKg: number | null;
  restSeconds: number;
  note: string;
  videoUrl: string | null;
  supersetGroup: number | null;
  autoWarmup: boolean;
  progressionMode: string;
};

export type SharedDay = {
  dayOfWeek: number;
  title: string;
  isRest: boolean;
  note: string;
  items: NormalizedSharedItem[];
};

export type SharedPlan = {
  format: string;
  name: string;
  description: string;
  scheduleMode: string;
  days: SharedDay[];
};

export type MuscleSets = { muscle: Muscle; sets: number };

export type PlanParseResult =
  | { ok: true; plan: SharedPlan; customExercises: string[]; volume: MuscleSets[] }
  | { ok: false; errors: string[] };

const FENCED_RE = /```(?:json)?\s*(\{[\s\S]*?\})\s*```/g;

export function exportSharedPlan(
  plan: {
    plan: { name: string; description: string; scheduleMode?: string };
    days: { day: { dayOfWeek: number; title: string; isRest: boolean; note: string }; items: readonly Omit<PlanItemEntity, 'id'>[] }[];
  },
  exercises: ReadonlyMap<string, ExerciseEntity>,
): string {
  const shared: SharedPlan = {
    format: PLAN_SHARE_FORMAT,
    name: plan.plan.name,
    description: plan.plan.description,
    scheduleMode: parseScheduleMode(plan.plan.scheduleMode),
    days: [...plan.days]
      .sort((a, b) => a.day.dayOfWeek - b.day.dayOfWeek)
      .map((d) => ({
        dayOfWeek: d.day.dayOfWeek,
        title: d.day.title,
        isRest: d.day.isRest,
        note: d.day.note,
        items: [...d.items]
          .sort((a, b) => a.position - b.position)
          .map((item) => {
            const ex = exercises.get(item.exerciseId);
            return {
              exerciseId: item.exerciseId,
              name: ex?.name ?? null,
              kind: ex?.kind ?? null,
              muscles: ex?.primary ? [...ex.primary] : [],
              block: item.block,
              circuit: item.circuit,
              sets: item.sets,
              repsMin: item.repsMin,
              repsMax: item.repsMax,
              seconds: item.seconds,
              weightKg: item.weightKg,
              restSeconds: item.restSeconds,
              note: item.note,
              videoUrl: ex?.videoUrl ?? null,
              supersetGroup: item.supersetGroup ?? null,
              autoWarmup: item.autoWarmup ?? false,
              progressionMode: parseProgressionMode(item.progressionMode),
            };
          }),
      })),
  };
  return JSON.stringify(shared, null, 2);
}

export function sharedPlanToDraft(shared: SharedPlan): PlanDraft {
  return {
    name: shared.name,
    description: shared.description,
    days: shared.days.map((d) => ({
      dayOfWeek: d.dayOfWeek,
      title: d.title,
      isRest: d.isRest,
      note: d.note,
      items: d.items.map((i) => ({
        exerciseId: i.exerciseId,
        name: i.name,
        kind: i.kind,
        muscles: i.muscles,
        block: i.block,
        circuit: i.circuit,
        sets: i.sets,
        repsMin: i.repsMin,
        repsMax: i.repsMax,
        seconds: i.seconds,
        weightKg: i.weightKg,
        restSeconds: i.restSeconds,
        note: i.note,
        videoUrl: i.videoUrl,
      })),
    })),
  };
}

const clampInt = (v: unknown, min: number, max: number, fallback: number): number => {
  const n = typeof v === 'number' && Number.isFinite(v) ? Math.round(v) : fallback;
  return Math.min(max, Math.max(min, n));
};

const clampOptInt = (v: unknown, min: number, max: number): number | null => {
  if (typeof v !== 'number' || !Number.isFinite(v)) return null;
  return Math.min(max, Math.max(min, Math.round(v)));
};

const clampOptFloat = (v: unknown, min: number, max: number): number | null => {
  if (typeof v !== 'number' || !Number.isFinite(v)) return null;
  return Math.min(max, Math.max(min, v));
};

const normId = (raw: string): string =>
  raw.trim().toLowerCase().replace(/[\s-]+/g, '_').replace(/[^a-z0-9_]/g, '');

function canonicalId(raw: string, name: string | null, known: ReadonlyMap<string, ExerciseEntity>): string | null {
  if (known.has(raw)) return raw;
  const n = normId(raw);
  if (known.has(n)) return n;
  for (const v of [n.replace(/es$/, ''), n.replace(/s$/, ''), `${n}s`, `${n}es`]) {
    if (known.has(v)) return v;
  }
  const byName = new Map<string, string>();
  for (const ex of known.values()) byName.set(normId(ex.name), ex.id);
  const byRaw = byName.get(n);
  if (byRaw) return byRaw;
  if (name) {
    const byGiven = byName.get(normId(name));
    if (byGiven) return byGiven;
  }
  return null;
}

export function parseAndValidateSharedPlan(raw: string, exercises: ReadonlyMap<string, ExerciseEntity>): PlanParseResult {
  const trimmed = raw.trim();
  if (!trimmed) return { ok: false, errors: ['Empty plan text.'] };

  let body: string | null = null;
  for (const m of trimmed.matchAll(FENCED_RE)) {
    if (m[1]?.includes('"days"')) body = m[1];
  }
  if (!body && trimmed.startsWith('{') && trimmed.endsWith('}')) body = trimmed;
  if (!body) return { ok: false, errors: ['No valid JSON plan found.'] };

  let parsed: Record<string, unknown>;
  try {
    const v = JSON.parse(body);
    if (!v || typeof v !== 'object' || Array.isArray(v)) return { ok: false, errors: ['Could not parse plan JSON.'] };
    parsed = v as Record<string, unknown>;
  } catch {
    return { ok: false, errors: ['Could not parse plan JSON.'] };
  }

  if (parsed.format != null && parsed.format !== PLAN_SHARE_FORMAT) {
    return { ok: false, errors: ['Unsupported plan format.'] };
  }
  const rawDays = Array.isArray(parsed.days) ? parsed.days : [];
  const seenDays = new Set<number>();
  const validDays: Record<string, unknown>[] = [];
  for (const d of rawDays) {
    if (!d || typeof d !== 'object') continue;
    const dow = (d as Record<string, unknown>).dayOfWeek;
    if (typeof dow !== 'number' || !Number.isInteger(dow) || dow < 1 || dow > 7 || seenDays.has(dow)) continue;
    seenDays.add(dow);
    validDays.push(d as Record<string, unknown>);
  }
  if (validDays.length === 0) return { ok: false, errors: ['Plan has no valid days (1..7).'] };

  const errors: string[] = [];
  const custom: string[] = [];
  const cleanDays: SharedDay[] = validDays
    .sort((a, b) => Number(a.dayOfWeek) - Number(b.dayOfWeek))
    .map((d) => {
      const dow = Number(d.dayOfWeek);
      const rawItems = Array.isArray(d.items) ? d.items : [];
      const mapped: NormalizedSharedItem[] = rawItems
        .filter((x): x is Record<string, unknown> => !!x && typeof x === 'object')
        .map((it) => {
          const rawId = typeof it.exerciseId === 'string' ? it.exerciseId : '';
          const rawName = typeof it.name === 'string' ? it.name : null;
          const resolved = canonicalId(rawId, rawName, exercises);
          if (!resolved) {
            const label = (rawName ?? '').trim();
            if (!label) errors.push(`Day ${dow}: unknown exercise "${rawId}".`);
            else custom.push(label);
          }
          const id = resolved ?? (normId(rawId) || 'custom_exercise');
          const sets = clampInt(it.sets, 1, 10, 3);
          const repsMin = clampOptInt(it.repsMin, 1, 100);
          const rawMax = clampOptInt(it.repsMax, 1, 100);
          const repsMax = rawMax != null && repsMin != null ? Math.max(repsMin, rawMax) : rawMax;
          const block = typeof it.block === 'string' && it.block.trim() ? it.block.trim().slice(0, 30) : 'Main';
          const supersetGroup = typeof it.supersetGroup === 'number' && Number.isInteger(it.supersetGroup) ? it.supersetGroup : null;
          return {
            exerciseId: id,
            name: rawName,
            kind: typeof it.kind === 'string' ? it.kind : null,
            muscles: Array.isArray(it.muscles) ? it.muscles.filter((m): m is string => typeof m === 'string') : [],
            block,
            circuit: Boolean(it.circuit),
            sets,
            repsMin,
            repsMax,
            seconds: clampOptInt(it.seconds, 5, 3600),
            weightKg: clampOptFloat(it.weightKg, 0, 500),
            restSeconds: clampInt(it.restSeconds, 0, 600, 90),
            note: typeof it.note === 'string' ? it.note.slice(0, 200) : '',
            videoUrl: typeof it.videoUrl === 'string' ? it.videoUrl : null,
            supersetGroup,
            autoWarmup: Boolean(it.autoWarmup),
            progressionMode: parseProgressionMode(typeof it.progressionMode === 'string' ? it.progressionMode : undefined),
          };
        });
      const validGroups = validPairIndices(mapped.map((m) => ({ supersetGroup: m.supersetGroup, sets: m.sets, extra: isExtraBlock(m.block) })));
      const cleanItems = mapped.map((m, idx) => ({ ...m, supersetGroup: validGroups[idx] }));
      const isRest = Boolean(d.isRest) || cleanItems.length === 0;
      const rawTitle = typeof d.title === 'string' ? d.title.trim() : '';
      return {
        dayOfWeek: dow,
        title: (rawTitle || (isRest ? 'Rest day' : 'Workout')).slice(0, 40),
        isRest,
        note: typeof d.note === 'string' ? d.note.slice(0, 300) : '',
        items: cleanItems,
      };
    });

  if (errors.length > 0) return { ok: false, errors };

  const rawName = typeof parsed.name === 'string' ? parsed.name.trim() : '';
  const rawDesc = typeof parsed.description === 'string' ? parsed.description.trim() : '';
  const plan: SharedPlan = {
    format: PLAN_SHARE_FORMAT,
    name: (rawName || 'Imported plan').slice(0, 80),
    description: rawDesc.slice(0, 400),
    scheduleMode: parseScheduleMode(typeof parsed.scheduleMode === 'string' ? parsed.scheduleMode : undefined),
    days: cleanDays,
  };
  return { ok: true, plan, customExercises: [...new Set(custom)], volume: weeklyMuscleVolume(plan, exercises) };
}

/** Generates a non-colliding plan name so importing never overwrites or confuses existing plans. */
export function safeImportName(requested: string, existingNames: readonly string[]): string {
  const base = (requested.trim() || 'Imported plan').slice(0, 80);
  const taken = new Set(existingNames.map((n) => n.trim().toLowerCase()));
  if (!taken.has(base.toLowerCase())) return base;
  let n = 2;
  while (taken.has(`${base} (${n})`.toLowerCase())) n++;
  return `${base} (${n})`.slice(0, 80);
}

/** Weekly primary working sets per muscle across a PlanDraft or SharedPlan, excluding Warm-up/Cool-down blocks. */
export function weeklyMuscleVolume(
  plan: Pick<PlanDraft, 'days'> | Pick<SharedPlan, 'days'>,
  exercises: ReadonlyMap<string, Pick<ExerciseEntity, 'primary'>>,
): MuscleSets[] {
  const totals = new Map<Muscle, number>();
  for (const day of plan.days) {
    if (day.isRest) continue;
    for (const item of day.items) {
      if (isExtraBlock(item.block)) continue;
      const ex = exercises.get(item.exerciseId);
      const muscles: readonly Muscle[] = ex
        ? ex.primary
        : (item.muscles ?? []).filter((m): m is Muscle => (MUSCLES as readonly string[]).includes(m));
      const sets = Math.max(0, item.sets);
      for (const m of muscles) totals.set(m, (totals.get(m) ?? 0) + sets);
    }
  }
  const order = new Map(MUSCLES.map((m, i) => [m, i]));
  return [...totals.entries()]
    .map(([muscle, sets]) => ({ muscle, sets }))
    .sort((a, b) => b.sets - a.sets || (order.get(a.muscle) ?? 0) - (order.get(b.muscle) ?? 0));
}

// ---------------------------------------------------------------- Superset invariant helpers

type SupersetCandidate = { supersetGroup?: number | null; sets: number; extra: boolean };

function validPairIndices(items: readonly SupersetCandidate[]): (number | null)[] {
  const result: (number | null)[] = Array(items.length).fill(null);
  let nextGroup = 1;
  let i = 0;
  while (i + 1 < items.length) {
    const a = items[i];
    const b = items[i + 1];
    if (a.supersetGroup != null && a.supersetGroup === b.supersetGroup && !a.extra && !b.extra && a.sets === b.sets) {
      result[i] = nextGroup;
      result[i + 1] = nextGroup;
      nextGroup++;
      i += 2;
    } else {
      i++;
    }
  }
  return result;
}

export function normalizeDaySupersets<T extends { position: number; block: string; sets: number; supersetGroup?: number | null }>(
  items: readonly T[],
): T[] {
  const ordered = [...items].sort((a, b) => a.position - b.position);
  const valid = validPairIndices(ordered.map((it) => ({ supersetGroup: it.supersetGroup ?? null, sets: it.sets, extra: isExtraBlock(it.block) })));
  return ordered.map((it, idx) => ({ ...it, supersetGroup: valid[idx] }));
}

export function canPairWithNext<T extends { block: string; sets: number; supersetGroup?: number | null }>(
  items: readonly T[],
  index: number,
): boolean {
  if (index < 0 || index >= items.length - 1) return false;
  const cur = items[index];
  const next = items[index + 1];
  if (isExtraBlock(cur.block) || isExtraBlock(next.block)) return false;
  if (cur.sets !== next.sets) return false;
  const pairedWithPrev = index > 0 && cur.supersetGroup != null && items[index - 1].supersetGroup === cur.supersetGroup;
  return !pairedWithPrev;
}

export function isPairedWithNext<T extends { supersetGroup?: number | null }>(items: readonly T[], index: number): boolean {
  if (index < 0 || index >= items.length - 1) return false;
  const g = items[index].supersetGroup;
  return g != null && items[index + 1].supersetGroup === g;
}

export function isPairedWithPrev<T extends { supersetGroup?: number | null }>(items: readonly T[], index: number): boolean {
  if (index <= 0 || index >= items.length) return false;
  const g = items[index].supersetGroup;
  return g != null && items[index - 1].supersetGroup === g;
}

export function setPairWithNext<T extends { position: number; block: string; sets: number; supersetGroup?: number | null }>(
  items: readonly T[],
  index: number,
  pair: boolean,
): T[] {
  const ordered = [...items].sort((a, b) => a.position - b.position);
  if (index < 0 || index >= ordered.length) return ordered;
  if (!pair) {
    const g = ordered[index].supersetGroup;
    if (g != null && index + 1 < ordered.length && ordered[index + 1].supersetGroup === g) {
      ordered[index] = { ...ordered[index], supersetGroup: null };
      ordered[index + 1] = { ...ordered[index + 1], supersetGroup: null };
    }
    return normalizeDaySupersets(ordered);
  }
  if (!canPairWithNext(ordered, index)) return ordered;
  const nextOldGroup = ordered[index + 1].supersetGroup;
  if (nextOldGroup != null && index + 2 < ordered.length && ordered[index + 2].supersetGroup === nextOldGroup) {
    ordered[index + 2] = { ...ordered[index + 2], supersetGroup: null };
  }
  let maxGroup = 0;
  for (const it of ordered) if (it.supersetGroup != null && it.supersetGroup > maxGroup) maxGroup = it.supersetGroup;
  const newGroup = maxGroup + 1;
  ordered[index] = { ...ordered[index], supersetGroup: newGroup };
  ordered[index + 1] = { ...ordered[index + 1], supersetGroup: newGroup };
  return normalizeDaySupersets(ordered);
}
