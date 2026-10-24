// Port of app/.../data/ProgressRepository.kt: the numbers behind Home, Progress and badges, built from the
// stored rows with the domain functions. Past weeks' streak targets are locked in kv 'weekTargets' the first
// time they are seen, so a later plan change never rewrites a finished week.
import { db, putDayNote, putWeight, type BadgeRow, type ExerciseRow, type SessionRow, type SetRow, type WeightRow } from './db';
import { loadSettings, saveWeekTargets, weekTargets, type Settings } from './settings';
import { getActivePlan, trainingDays, type ActivePlan } from './workouts';
import { useLive } from './live';
import { BADGES, type BadgeDef, type BadgeStats } from '../domain/badges';
import { todayEpochDay } from '../domain/dates';
import type { SetEntity } from '../domain/entities';
import type { Muscle } from '../domain/muscle';
import {
  computeRecovery, computeStreak, fullTarget, levelFor, parseDayTag, sessionHits, totalXp, weeksToLock,
  type DayTag, type LevelInfo, type MuscleRecovery, type WeeklyStreak,
} from '../domain/progress';

export type DayNutrition = { epochDay: number; kcal: number; protein: number; carbs: number };

export type ProgressSnapshot = {
  settings: Settings;
  plan: ActivePlan | null;
  streak: WeeklyStreak;
  level: LevelInfo;
  recovery: Map<Muscle, MuscleRecovery>;
  badges: BadgeRow[];
  today: DayNutrition;
  nutritionByDay: DayNutrition[];
  weights: WeightRow[];
  sessions: SessionRow[];
  doneSets: SetRow[];
  exercises: Map<string, ExerciseRow>;
  stats: BadgeStats;
  /** Excused days (sick, injured, travel, rest) by epoch day. */
  dayTags: Map<number, DayTag>;
  latestWeight: number | null;
  workoutDoneToday: boolean;
  excusedToday: DayTag | null;
};

const NUTRITION_DAYS = 90;
/** Today plus the last 3 days can be excused, so it can't be used to rewrite history. */
export const EXCUSE_BACK_DAYS = 3;

export async function getProgressSnapshot(now = Date.now()): Promise<ProgressSnapshot> {
  const today = todayEpochDay(new Date(now));
  const [settings, plan, weights, allSessions, badges, dayNotes, exerciseRows, mealCount, scannedCount, meals] = await Promise.all([
    loadSettings(), getActivePlan(), db.weights.orderBy('epochDay').toArray(),
    db.sessions.toArray(), db.badges.toArray(), db.dayNotes.toArray(), db.exercises.toArray(),
    db.meals.count(), db.meals.filter((m) => m.photoPath != null && m.foodId == null).count(),
    db.meals.where('epochDay').aboveOrEqual(today - NUTRITION_DAYS).toArray(),
  ]);
  const sessions = allSessions.filter((s) => s.finishedAt != null).sort((a, b) => b.finishedAt! - a.finishedAt!);
  const finishedIds = new Set(sessions.map((s) => s.id!));
  const sets = (await db.sets.filter((s) => s.done).toArray()).filter((s) => finishedIds.has(s.sessionId));
  const exercises = new Map(exerciseRows.map((e) => [e.id, e]));

  const workoutDays = new Set(sessions.map((s) => s.epochDay));
  const start = Math.min(workoutDays.size ? Math.min(...workoutDays) : today, today);
  const dayTags = new Map<number, DayTag>();
  for (const n of dayNotes) { const t = parseDayTag(n.tag); if (t) dayTags.set(n.epochDay, t); }
  const planned = plan ? trainingDays(plan) : new Set<number>();

  // Lock each finished week's target the first time it is seen.
  let locked = await weekTargets();
  if (plan) {
    const fresh = weeksToLock(locked, fullTarget(planned, settings.weeklyTargetOverride), start, today);
    if (fresh.size) { await saveWeekTargets(fresh); locked = new Map([...locked, ...fresh]); }
  }
  const streak = computeStreak(workoutDays, planned, settings.weeklyTargetOverride, start, today, new Set(dayTags.keys()), locked);

  const recentFinishedAt = new Map<number, number>();
  for (const s of sessions) if (now - s.finishedAt! >= 0 && now - s.finishedAt! <= 49 * 3_600_000) recentFinishedAt.set(s.id!, s.finishedAt!);
  const recentSets = recentFinishedAt.size ? sets.filter((s) => recentFinishedAt.has(s.sessionId)) : [];
  const recovery = computeRecovery(sessionHits(recentSets as SetEntity[], exercises, recentFinishedAt), now);

  const byDayMap = new Map<number, DayNutrition>();
  for (const m of meals) {
    const d = byDayMap.get(m.epochDay) ?? { epochDay: m.epochDay, kcal: 0, protein: 0, carbs: 0 };
    d.kcal += m.kcal; d.protein += m.protein; d.carbs += m.carbs;
    byDayMap.set(m.epochDay, d);
  }
  const nutritionByDay = [...byDayMap.values()].sort((a, b) => a.epochDay - b.epochDay);
  const todayNutrition = byDayMap.get(today) ?? { epochDay: today, kcal: 0, protein: 0, carbs: 0 };

  const stats = statsFor(sets, sessions.length, streak.best, nutritionByDay, settings.proteinGoal, mealCount, scannedCount);
  const level = levelFor(totalXp(sessions.length, sets.length, sets.filter((s) => s.isPr).length, badges.length));
  return {
    settings, plan, streak, level, recovery, badges, today: todayNutrition, nutritionByDay, weights, sessions,
    doneSets: sets, exercises, stats, dayTags,
    latestWeight: weights.length ? weights[weights.length - 1].kg : null,
    workoutDoneToday: sessions.some((s) => s.epochDay === today),
    excusedToday: dayTags.get(today) ?? null,
  };
}

function statsFor(
  sets: SetRow[], workouts: number, bestStreakWeeks: number, byDay: DayNutrition[], proteinGoal: number,
  mealsLogged: number, mealsScanned: number,
): BadgeStats {
  const bestReps: Record<string, number> = {};
  const bestSeconds: Record<string, number> = {};
  const bestWeight: Record<string, number> = {};
  for (const s of sets) {
    if (s.reps != null) bestReps[s.exerciseId] = Math.max(bestReps[s.exerciseId] ?? 0, s.reps);
    if (s.seconds != null) bestSeconds[s.exerciseId] = Math.max(bestSeconds[s.exerciseId] ?? 0, s.seconds);
    if (s.weightKg != null && s.weightKg > 0) bestWeight[s.exerciseId] = Math.max(bestWeight[s.exerciseId] ?? 0, s.weightKg);
  }
  const hitDays = [...new Set(byDay.filter((d) => d.protein >= proteinGoal).map((d) => d.epochDay))].sort((a, b) => a - b);
  let bestRun = 0; let run = 0; let prev: number | null = null;
  for (const d of hitDays) {
    run = prev != null && d === prev + 1 ? run + 1 : 1;
    bestRun = Math.max(bestRun, run);
    prev = d;
  }
  return { workouts, bestStreakWeeks, bestReps, bestSeconds, bestWeight, mealsLogged, mealsScanned, proteinGoalDaysInARow: bestRun };
}

/** The snapshot as a Preact hook; null until the first build. Rebuilds when any table it read changes. */
export const useProgress = (): ProgressSnapshot | null => useLive(() => getProgressSnapshot(), [], null as ProgressSnapshot | null);

/** Unlocks any badges whose rule now passes. Returns the newly unlocked ones. */
export async function evaluateBadges(): Promise<BadgeDef[]> {
  const snap = await getProgressSnapshot();
  const owned = new Set(snap.badges.map((b) => b.id));
  const fresh = BADGES.filter((b) => !owned.has(b.id) && b.unlocked(snap.stats));
  if (fresh.length) {
    const now = Date.now();
    await db.badges.bulkAdd(fresh.map((b) => ({ id: b.id, unlockedAt: now, seen: false })));
  }
  return fresh;
}

export async function markBadgesSeen(): Promise<void> {
  await db.badges.toCollection().modify({ seen: true });
}

export async function logWeight(kg: number): Promise<void> {
  await putWeight({ epochDay: todayEpochDay(), kg: Math.trunc(kg * 10) / 10, loggedAt: Date.now() });
}

export const deleteWeight = (epochDay: number): Promise<void> => db.weights.delete(epochDay);

/** Excuses `epochDay` (today or up to 3 days back); null `tag` takes the excuse away. */
export async function setDayTag(epochDay: number, tag: DayTag | null): Promise<void> {
  const today = todayEpochDay();
  if (epochDay > today || epochDay < today - EXCUSE_BACK_DAYS) return;
  if (tag == null) await db.dayNotes.delete(epochDay);
  else await putDayNote({ epochDay, tag, note: '', updatedAt: Date.now() });
}
