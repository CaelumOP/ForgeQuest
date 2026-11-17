import { EXERCISE_CATALOG, STRETCH_IDS } from '../data/seed/exercise-catalog';
import { dayOfWeek, weekStart } from './dates';
import { effortOf } from './effort';
import type { ExerciseEntity, PlanItemEntity, SessionEntity, SetEntity } from './entities';
import type { Muscle } from './muscle';
import { recordScore, type MuscleRecovery, type WeeklyStreak } from './progress';
import { isExtraBlock, workingVolume } from './volume-change';

export function recoverySplit(
  map: ReadonlyMap<Muscle, MuscleRecovery>,
  n = 3,
): { fresh: Muscle[]; recovering: Muscle[] } {
  const entries = [...map.entries()];
  const fresh = entries
    .filter(([, r]) => r.hoursUntilReady <= 0)
    .sort((a, b) => a[1].fatigue - b[1].fatigue)
    .slice(0, n)
    .map(([m]) => m);
  const recovering = entries
    .filter(([, r]) => r.hoursUntilReady > 0)
    .sort((a, b) => a[1].hoursUntilReady - b[1].hoursUntilReady)
    .slice(0, n)
    .map(([m]) => m);
  return { fresh, recovering };
}

export type InsightSlot =
  | { type: 'count'; n: number }
  | { type: 'kg'; kg: number }
  | { type: 'seconds'; s: number }
  | { type: 'exercise'; id: string }
  | { type: 'cue'; id: string; line: number }
  | { type: 'group'; muscle: Muscle }
  | { type: 'weekday'; day: number };

export type InsightKindId =
  | 'FIRST_ROUND_REPS'
  | 'FIRST_ROUND_HOLD'
  | 'CROSS_WORKOUTS'
  | 'ANNIVERSARY'
  | 'NEAR_RECORD_WEIGHT'
  | 'NEAR_RECORD_REPS'
  | 'BIGGEST_SESSION'
  | 'CROSS_HOURS'
  | 'CROSS_KG'
  | 'CROSS_SETS'
  | 'CROSS_REPS'
  | 'EXERCISE_TIMES'
  | 'BACK_AFTER_GAP'
  | 'VS_FOUR_WEEKS'
  | 'EASY_STREAK'
  | 'HARD_TWICE'
  | 'SAME_WEEKDAY'
  | 'MONTH_UP'
  | 'STREAK_BEST'
  | 'WEEK_ONE_LEFT'
  | 'STREAK_NEXT'
  | 'PACE_FASTER'
  | 'MUSCLE_NEGLECTED'
  | 'MUSCLE_WEEK'
  | 'MUSCLE_TOP'
  | 'AVG_REST'
  | 'FORM_TIP';

type KindMeta = { id: InsightKindId; rank: number; cooldownDays: number; plural: boolean; variants: number };

export const INSIGHT_KINDS: readonly KindMeta[] = [
  { id: 'FIRST_ROUND_REPS', rank: 95, cooldownDays: 3, plural: true, variants: 3 },
  { id: 'FIRST_ROUND_HOLD', rank: 94, cooldownDays: 3, plural: false, variants: 3 },
  { id: 'CROSS_WORKOUTS', rank: 92, cooldownDays: 3, plural: false, variants: 3 },
  { id: 'ANNIVERSARY', rank: 90, cooldownDays: 28, plural: true, variants: 3 },
  { id: 'NEAR_RECORD_WEIGHT', rank: 85, cooldownDays: 5, plural: false, variants: 3 },
  { id: 'NEAR_RECORD_REPS', rank: 84, cooldownDays: 5, plural: false, variants: 3 },
  { id: 'BIGGEST_SESSION', rank: 83, cooldownDays: 7, plural: false, variants: 3 },
  { id: 'CROSS_HOURS', rank: 82, cooldownDays: 3, plural: true, variants: 3 },
  { id: 'CROSS_KG', rank: 81, cooldownDays: 3, plural: false, variants: 3 },
  { id: 'CROSS_SETS', rank: 80, cooldownDays: 3, plural: true, variants: 3 },
  { id: 'CROSS_REPS', rank: 79, cooldownDays: 3, plural: true, variants: 3 },
  { id: 'EXERCISE_TIMES', rank: 78, cooldownDays: 3, plural: false, variants: 3 },
  { id: 'BACK_AFTER_GAP', rank: 75, cooldownDays: 14, plural: true, variants: 3 },
  { id: 'VS_FOUR_WEEKS', rank: 70, cooldownDays: 14, plural: false, variants: 3 },
  { id: 'EASY_STREAK', rank: 65, cooldownDays: 14, plural: false, variants: 3 },
  { id: 'HARD_TWICE', rank: 64, cooldownDays: 14, plural: false, variants: 3 },
  { id: 'SAME_WEEKDAY', rank: 60, cooldownDays: 21, plural: true, variants: 3 },
  { id: 'MONTH_UP', rank: 58, cooldownDays: 14, plural: false, variants: 3 },
  { id: 'STREAK_BEST', rank: 57, cooldownDays: 7, plural: false, variants: 3 },
  { id: 'WEEK_ONE_LEFT', rank: 56, cooldownDays: 5, plural: false, variants: 3 },
  { id: 'STREAK_NEXT', rank: 55, cooldownDays: 10, plural: true, variants: 3 },
  { id: 'PACE_FASTER', rank: 50, cooldownDays: 10, plural: true, variants: 3 },
  { id: 'MUSCLE_NEGLECTED', rank: 45, cooldownDays: 14, plural: true, variants: 3 },
  { id: 'MUSCLE_WEEK', rank: 40, cooldownDays: 7, plural: true, variants: 3 },
  { id: 'MUSCLE_TOP', rank: 35, cooldownDays: 14, plural: false, variants: 3 },
  { id: 'AVG_REST', rank: 30, cooldownDays: 10, plural: false, variants: 3 },
  { id: 'FORM_TIP', rank: 20, cooldownDays: 21, plural: false, variants: 3 },
];

export type Insight = {
  kind: InsightKindId;
  plural: boolean;
  rank: number;
  cooldownDays: number;
  variant: number;
  slots: InsightSlot[];
};

export type InsightInput = {
  sessions: readonly SessionEntity[];
  sets: readonly SetEntity[];
  exercises: ReadonlyMap<string, ExerciseEntity>;
  streak?: WeeklyStreak | null;
  muscleWeekTargets?: ReadonlyMap<Muscle, number>;
  focusSessionId?: number | null;
};

type LifetimeTotals = { workouts: number; sets: number; reps: number; minutes: number; hours: number; kgLifted: number };

function lifetimeTotals(sessions: readonly SessionEntity[], sets: readonly SetEntity[]): LifetimeTotals {
  const ids = new Set(sessions.map((s) => s.id));
  const working = sets.filter((s) => s.done && ids.has(s.sessionId) && !isExtraBlock(s.block));
  const withWork = new Set(working.map((s) => s.sessionId));
  const counted = sessions.filter((s) => withWork.has(s.id));
  const minutes = counted.reduce((sum, s) => {
    if (s.finishedAt == null) return sum;
    return sum + Math.min(300, Math.max(1, Math.trunc((s.finishedAt - s.startedAt) / 60_000)));
  }, 0);
  const reps = working.reduce((sum, s) => sum + (s.reps ?? 0), 0);
  return {
    workouts: counted.length,
    sets: working.length,
    reps,
    minutes,
    hours: Math.trunc(minutes / 60),
    kgLifted: workingVolume(working),
  };
}

export type InsightHistoryEntry = { kind: string; variant: number; day: number };

export function parseInsightHistory(raw: string): InsightHistoryEntry[] {
  const out: InsightHistoryEntry[] = [];
  for (const part of raw.split(';')) {
    const f = part.split(':');
    if (f.length !== 3 || !f[0].trim()) continue;
    const v = Number(f[1]);
    const d = Number(f[2]);
    if (Number.isInteger(v) && Number.isInteger(d)) out.push({ kind: f[0], variant: v, day: d });
  }
  return out;
}

export function leastRecentVariant(kind: string, variants: number, history: readonly InsightHistoryEntry[]): number {
  const last = new Array<number>(variants).fill(-1);
  history.forEach((e, i) => {
    if (e.kind === kind && e.variant >= 0 && e.variant < variants) last[e.variant] = i;
  });
  let best = 0;
  for (let i = 1; i < variants; i++) if (last[i] < last[best]) best = i;
  return best;
}

const REP_MARKS = [5, 10, 15, 20, 25, 30, 40, 50];
const HOLD_MARKS = [30, 60, 90, 120, 180];
const WORKOUT_MARKS = [10, 25, 50, 100, 150, 200, 250, 300, 400, 500, 750, 1000, 1500, 2000];
const SET_MARKS = [100, 250, 500, 1000, 2500, 5000, 10_000, 25_000, 50_000];
const REP_TOTAL_MARKS = [1000, 2500, 5000, 10_000, 25_000, 50_000, 100_000, 250_000, 500_000, 1_000_000];
const HOUR_MARKS = [10, 25, 50, 100, 250, 500, 1000];
const KG_MARKS = [5000, 10_000, 25_000, 50_000, 100_000, 250_000, 500_000, 1_000_000];
const ANNIVERSARY_MONTHS = [1, 6, 12, 24, 36, 48, 60];
const STREAK_BADGE_WEEKS = [4, 12, 52];
const TIMES_MARKS = [10, 25, 50, 100, 150, 200, 300];
const BUILT_IN_IDS = new Set(EXERCISE_CATALOG.map((e) => e.id));

function plusMonthsEpochDay(epochDay: number, months: number): number {
  const d = new Date(epochDay * 86_400_000);
  const targetMonth = d.getUTCMonth() + months;
  const y = d.getUTCFullYear() + Math.floor(targetMonth / 12);
  const m = ((targetMonth % 12) + 12) % 12;
  const maxDay = new Date(Date.UTC(y, m + 1, 0)).getUTCDate();
  const day = Math.min(d.getUTCDate(), maxDay);
  return Math.floor(Date.UTC(y, m, day) / 86_400_000);
}

export function muscleWeekTargets(
  items: readonly PlanItemEntity[],
  exercises: ReadonlyMap<string, ExerciseEntity>,
): Map<Muscle, number> {
  const out = new Map<Muscle, number>();
  for (const item of items) {
    if (isExtraBlock(item.block)) continue;
    for (const m of exercises.get(item.exerciseId)?.primary ?? []) {
      out.set(m, (out.get(m) ?? 0) + item.sets);
    }
  }
  return out;
}

export function pickInsights(
  input: InsightInput,
  history: string,
  today: number,
  max = 2,
): Insight[] {
  const entries = parseInsightHistory(history);
  const lastShown = new Map<string, number>();
  for (const e of entries) {
    const prev = lastShown.get(e.kind);
    if (prev == null || e.day > prev) lastShown.set(e.kind, e.day);
  }

  const sessions = [...input.sessions].sort(
    (a, b) => a.epochDay - b.epochDay || a.startedAt - b.startedAt || a.id - b.id,
  );
  const known = new Set(sessions.map((s) => s.id));
  const done = input.sets.filter((s) => s.done && known.has(s.sessionId));
  const work = done.filter((s) => !isExtraBlock(s.block) && !STRETCH_IDS.has(s.exerciseId));
  const workBySession = new Map<number, SetEntity[]>();
  for (const s of work) {
    const list = workBySession.get(s.sessionId);
    if (list) list.push(s);
    else workBySession.set(s.sessionId, [s]);
  }
  const dayOf = new Map(sessions.map((s) => [s.id, s.epochDay]));
  const trained = sessions.filter((s) => (workBySession.get(s.id)?.length ?? 0) > 0);
  const focus = input.focusSessionId != null
    ? trained.find((s) => s.id === input.focusSessionId) ?? null
    : trained[trained.length - 1] ?? null;
  const before = focus ? trained.slice(0, trained.indexOf(focus)) : trained;

  const kindOf = (id: string) => input.exercises.get(id)?.kind ?? 'REPS';
  const score = (id: string, s: SetEntity) => recordScore(kindOf(id), s.reps, s.seconds, s.weightKg, id);
  const setsOf = (list: readonly SessionEntity[], id: string) =>
    list.flatMap((s) => workBySession.get(s.id) ?? []).filter((x) => x.exerciseId === id);
  const primary = (id: string) => input.exercises.get(id)?.primary ?? [];

  const focusMap = new Map<string, SetEntity[]>();
  if (focus) {
    for (const s of workBySession.get(focus.id) ?? []) {
      const l = focusMap.get(s.exerciseId);
      if (l) l.push(s);
      else focusMap.set(s.exerciseId, [s]);
    }
  }
  const focusExercises = [...focusMap.entries()].sort((a, b) => (a[0] < b[0] ? -1 : a[0] > b[0] ? 1 : 0));

  const crossing = (marks: readonly number[], metric: (t: LifetimeTotals) => number): InsightSlot[] | null => {
    if (!focus) return null;
    const was = metric(lifetimeTotals(before, done));
    const now = metric(lifetimeTotals([...before, focus], done));
    let mark: number | null = null;
    for (const m of marks) if (m > was && m <= now) mark = m;
    return mark != null ? [{ type: 'count', n: mark }] : null;
  };

  const bestValue = (id: string, sets: readonly SetEntity[]): number => {
    const k = kindOf(id);
    if (k === 'WEIGHTED') {
      const f = sets.filter((s) => (s.reps ?? 0) >= 1);
      return f.length ? Math.max(...f.map((s) => s.weightKg ?? 0)) : 0;
    }
    if (k === 'HOLD') return sets.length ? Math.max(...sets.map((s) => s.seconds ?? 0)) : 0;
    return sets.length ? Math.max(...sets.map((s) => s.reps ?? 0)) : 0;
  };

  const valueSlot = (id: string, v: number): InsightSlot => {
    const k = kindOf(id);
    if (k === 'WEIGHTED') return { type: 'kg', kg: v };
    if (k === 'HOLD') return { type: 'seconds', s: Math.round(v) };
    return { type: 'count', n: Math.round(v) };
  };

  const spanMinutes = (sessionId: number): number | null => {
    const times = done.filter((s) => s.sessionId === sessionId && s.doneAt != null).map((s) => s.doneAt!);
    if (times.length < 4) return null;
    return Math.trunc((Math.max(...times) - Math.min(...times)) / 60_000);
  };

  const lastWith = (id: string, n: number): SetEntity[][] =>
    [...before, ...(focus ? [focus] : [])].map((s) => setsOf([s], id)).filter((l) => l.length > 0).slice(-n);

  const slotsFor = (kind: InsightKindId): InsightSlot[] | null => {
    switch (kind) {
      case 'FIRST_ROUND_REPS': {
        let best: [string, number] | null = null;
        for (const [id, sets] of focusExercises) {
          if (kindOf(id) !== 'REPS') continue;
          const prior = setsOf(before, id);
          if (!prior.length) continue;
          const bw = sets.filter((s) => (s.weightKg ?? 0) <= 0);
          if (!bw.length) continue;
          const now = Math.max(...bw.map((s) => s.reps ?? 0));
          const was = Math.max(...prior.map((s) => s.reps ?? 0));
          let mark: number | null = null;
          for (const m of REP_MARKS) if (m > was && m <= now) mark = m;
          if (mark != null && (!best || mark > best[1] || (mark === best[1] && id > best[0]))) best = [id, mark];
        }
        return best ? [{ type: 'exercise', id: best[0] }, { type: 'count', n: best[1] }] : null;
      }
      case 'FIRST_ROUND_HOLD': {
        let best: [string, number] | null = null;
        for (const [id, sets] of focusExercises) {
          if (kindOf(id) !== 'HOLD') continue;
          const prior = setsOf(before, id);
          if (!prior.length) continue;
          const now = Math.max(...sets.map((s) => s.seconds ?? 0));
          const was = Math.max(...prior.map((s) => s.seconds ?? 0));
          let mark: number | null = null;
          for (const m of HOLD_MARKS) if (m > was && m <= now) mark = m;
          if (mark != null && (!best || mark > best[1] || (mark === best[1] && id > best[0]))) best = [id, mark];
        }
        return best ? [{ type: 'exercise', id: best[0] }, { type: 'seconds', s: best[1] }] : null;
      }
      case 'CROSS_WORKOUTS': return crossing(WORKOUT_MARKS, (t) => t.workouts);
      case 'CROSS_HOURS': return crossing(HOUR_MARKS, (t) => t.hours);
      case 'CROSS_KG': return crossing(KG_MARKS, (t) => t.kgLifted);
      case 'CROSS_SETS': return crossing(SET_MARKS, (t) => t.sets);
      case 'CROSS_REPS': return crossing(REP_TOTAL_MARKS, (t) => t.reps);
      case 'ANNIVERSARY': {
        const first = trained[0];
        if (!first) return null;
        let months: number | null = null;
        for (const m of ANNIVERSARY_MONTHS) {
          const diff = today - plusMonthsEpochDay(first.epochDay, m);
          if (diff >= 0 && diff <= 6) months = m;
        }
        return months != null ? [{ type: 'count', n: months }] : null;
      }
      case 'NEAR_RECORD_WEIGHT': {
        let best: [string, number] | null = null;
        for (const [id, sets] of focusExercises) {
          if (kindOf(id) !== 'WEIGHTED') continue;
          const prior = setsOf(before, id).filter((s) => (s.reps ?? 0) >= 1);
          if (!prior.length) continue;
          if (Math.max(...sets.map((s) => score(id, s))) >= Math.max(...prior.map((s) => score(id, s)))) continue;
          const gap = Math.max(...prior.map((s) => s.weightKg ?? 0)) - Math.max(...sets.map((s) => s.weightKg ?? 0));
          if (gap > 0.05 && gap <= 2.51 && (!best || gap < best[1] || (gap === best[1] && id < best[0]))) best = [id, gap];
        }
        return best ? [{ type: 'exercise', id: best[0] }, { type: 'kg', kg: best[1] }] : null;
      }
      case 'NEAR_RECORD_REPS': {
        for (const [id, sets] of focusExercises) {
          if (kindOf(id) !== 'REPS') continue;
          const prior = setsOf(before, id);
          if (!prior.length) continue;
          if (Math.max(...sets.map((s) => score(id, s))) >= Math.max(...prior.map((s) => score(id, s)))) continue;
          if (Math.max(...prior.map((s) => s.reps ?? 0)) - Math.max(...sets.map((s) => s.reps ?? 0)) === 1) {
            return [{ type: 'exercise', id }];
          }
        }
        return null;
      }
      case 'BIGGEST_SESSION': {
        if (!focus) return null;
        const earlier = before.map((s) => workingVolume(workBySession.get(s.id) ?? [])).filter((v) => v > 0);
        const now = workingVolume(workBySession.get(focus.id) ?? []);
        return earlier.length >= 5 && now > Math.max(...earlier) ? [{ type: 'kg', kg: now }] : null;
      }
      case 'EXERCISE_TIMES': {
        let best: [string, number] | null = null;
        for (const [id] of focusExercises) {
          const n = before.filter((s) => (workBySession.get(s.id) ?? []).some((x) => x.exerciseId === id)).length + 1;
          if (TIMES_MARKS.includes(n) && (!best || n > best[1] || (n === best[1] && id > best[0]))) best = [id, n];
        }
        return best ? [{ type: 'exercise', id: best[0] }, { type: 'count', n: best[1] }] : null;
      }
      case 'BACK_AFTER_GAP': {
        if (!focus || !before.length) return null;
        const days = focus.epochDay - before[before.length - 1].epochDay;
        return days >= 10 ? [{ type: 'count', n: days }] : null;
      }
      case 'VS_FOUR_WEEKS': {
        if (!focus) return null;
        let best: { id: string; was: number; now: number; gain: number } | null = null;
        for (const [id, sets] of focusExercises) {
          const cands = before.filter((s) => focus.epochDay - s.epochDay >= 21 && focus.epochDay - s.epochDay <= 35 && setsOf([s], id).length > 0);
          if (!cands.length) continue;
          cands.sort((a, b) => Math.abs((focus.epochDay - a.epochDay) - 28) - Math.abs((focus.epochDay - b.epochDay) - 28) || a.epochDay - b.epochDay);
          const was = bestValue(id, setsOf([cands[0]], id));
          const now = bestValue(id, sets);
          if (was <= 0 || now <= was + 0.05) continue;
          const gain = (now - was) / was;
          if (!best || gain > best.gain || (gain === best.gain && id > best.id)) best = { id, was, now, gain };
        }
        return best ? [{ type: 'exercise', id: best.id }, valueSlot(best.id, best.was), valueSlot(best.id, best.now)] : null;
      }
      case 'EASY_STREAK': {
        for (const [id] of focusExercises) {
          const runs = lastWith(id, 3);
          if (runs.length < 3) continue;
          const easy = runs.every((sets) => sets.filter((s) => effortOf(s.effort, s.note) === 'EASY').length * 2 >= sets.length);
          const flat = Math.max(...runs[runs.length - 1].map((s) => score(id, s))) <= Math.max(...runs[0].map((s) => score(id, s)));
          if (easy && flat) return [{ type: 'exercise', id }];
        }
        return null;
      }
      case 'HARD_TWICE': {
        for (const [id] of focusExercises) {
          const runs = lastWith(id, 2);
          if (runs.length === 2 && runs.every((sets) => sets.every((s) => effortOf(s.effort, s.note) === 'HARD'))) {
            return [{ type: 'exercise', id }];
          }
        }
        return null;
      }
      case 'SAME_WEEKDAY': {
        if (!focus) return null;
        const days = new Set(trained.map((s) => s.epochDay));
        let weeks = 0;
        while (days.has(focus.epochDay - 7 * weeks)) weeks++;
        return weeks >= 4 ? [{ type: 'weekday', day: dayOfWeek(focus.epochDay) }, { type: 'count', n: weeks }] : null;
      }
      case 'MONTH_UP': {
        const now = trained.filter((s) => today - s.epochDay >= 0 && today - s.epochDay <= 29).length;
        const was = trained.filter((s) => today - s.epochDay >= 30 && today - s.epochDay <= 59).length;
        return was >= 3 && now >= was + 2 ? [{ type: 'count', n: now }, { type: 'count', n: was }] : null;
      }
      case 'STREAK_BEST': {
        const s = input.streak;
        return s && s.weeks >= 3 && s.weeks >= s.best ? [{ type: 'count', n: s.weeks }] : null;
      }
      case 'WEEK_ONE_LEFT': {
        const s = input.streak;
        return s && s.targetThisWeek >= 2 && s.doneThisWeek === s.targetThisWeek - 1
          ? [{ type: 'count', n: s.doneThisWeek }, { type: 'count', n: s.targetThisWeek }] : null;
      }
      case 'STREAK_NEXT': {
        const weeks = input.streak?.weeks;
        if (weeks == null || weeks < 1) return null;
        const next = STREAK_BADGE_WEEKS.find((w) => w > weeks);
        if (next == null) return null;
        const toNext = next - weeks;
        return toNext <= 3 ? [{ type: 'count', n: toNext }, { type: 'count', n: weeks }] : null;
      }
      case 'PACE_FASTER': {
        if (!focus || focus.planDayId == null) return null;
        const last = [...before].reverse().find((s) => s.planDayId === focus.planDayId);
        if (!last) return null;
        const now = spanMinutes(focus.id);
        const was = spanMinutes(last.id);
        if (now == null || was == null) return null;
        if ((workBySession.get(focus.id)?.length ?? 0) < (workBySession.get(last.id)?.length ?? 0)) return null;
        const diff = was - now;
        return diff >= 3 && diff * 10 >= was ? [{ type: 'count', n: diff }] : null;
      }
      case 'MUSCLE_NEGLECTED': {
        if (!input.muscleWeekTargets) return null;
        const last = new Map<Muscle, number>();
        for (const s of work) {
          const day = dayOf.get(s.sessionId);
          if (day == null) continue;
          for (const m of primary(s.exerciseId)) {
            if (day > (last.get(m) ?? Number.NEGATIVE_INFINITY)) last.set(m, day);
          }
        }
        let best: [Muscle, number] | null = null;
        for (const [m, target] of input.muscleWeekTargets) {
          if (target <= 0) continue;
          const d = last.get(m);
          if (d == null) continue;
          const gap = today - d;
          if (gap >= 10 && gap <= 60 && (!best || gap > best[1])) best = [m, gap];
        }
        return best ? [{ type: 'group', muscle: best[0] }, { type: 'count', n: best[1] }] : null;
      }
      case 'MUSCLE_WEEK': {
        if (!input.muscleWeekTargets || dayOfWeek(today) < 5 || dayOfWeek(today) > 6) return null;
        const ws = weekStart(today);
        const counts = new Map<Muscle, number>();
        let total = 0;
        for (const s of work) {
          const d = dayOf.get(s.sessionId);
          if (d == null || d < ws || d > today) continue;
          total++;
          for (const m of primary(s.exerciseId)) counts.set(m, (counts.get(m) ?? 0) + 1);
        }
        if (total === 0) return null;
        let best: { m: Muscle; target: number; done: number; deficit: number } | null = null;
        for (const [m, target] of input.muscleWeekTargets) {
          if (target <= 0) continue;
          const c = counts.get(m) ?? 0;
          if (c * 2 >= target) continue;
          const deficit = target - c;
          if (!best || deficit > best.deficit) best = { m, target, done: c, deficit };
        }
        return best ? [{ type: 'group', muscle: best.m }, { type: 'count', n: best.target }, { type: 'count', n: best.done }] : null;
      }
      case 'MUSCLE_TOP': {
        const counts = new Map<Muscle, number>();
        let total = 0;
        for (const s of work) {
          const d = dayOf.get(s.sessionId);
          if (d == null || today - d < 0 || today - d > 27) continue;
          total++;
          for (const m of primary(s.exerciseId)) counts.set(m, (counts.get(m) ?? 0) + 1);
        }
        if (total < 30) return null;
        let best: [Muscle, number] | null = null;
        for (const [m, c] of counts) {
          if (!best || c > best[1]) best = [m, c];
        }
        return best ? [{ type: 'group', muscle: best[0] }, { type: 'count', n: best[1] }] : null;
      }
      case 'AVG_REST': {
        if (!focus) return null;
        const times = done.filter((s) => s.sessionId === focus.id && s.doneAt != null).map((s) => s.doneAt!).sort((a, b) => a - b);
        const gaps: number[] = [];
        for (let i = 1; i < times.length; i++) {
          const g = Math.trunc((times[i] - times[i - 1]) / 1000);
          if (g >= 5 && g <= 600) gaps.push(g);
        }
        if (gaps.length < 5) return null;
        const avg = Math.round(gaps.reduce((a, b) => a + b, 0) / gaps.length);
        return [{ type: 'seconds', s: avg }];
      }
      case 'FORM_TIP': {
        const ids = focusExercises.map(([id]) => id).filter((id) => BUILT_IN_IDS.has(id));
        if (!ids.length) return null;
        const id = ids[((today % ids.length) + ids.length) % ids.length];
        return [{ type: 'exercise', id }, { type: 'cue', id, line: ((today % 3) + 3) % 3 }];
      }
    }
  };

  const out: Insight[] = [];
  for (const k of INSIGHT_KINDS) {
    const slots = slotsFor(k.id);
    if (!slots) continue;
    const seen = lastShown.get(k.id);
    if (seen != null && today - seen < k.cooldownDays) continue;
    out.push({
      kind: k.id,
      plural: k.plural,
      rank: k.rank,
      cooldownDays: k.cooldownDays,
      variant: leastRecentVariant(k.id, k.variants, entries),
      slots,
    });
  }
  out.sort((a, b) => b.rank - a.rank);
  return out.slice(0, max);
}
