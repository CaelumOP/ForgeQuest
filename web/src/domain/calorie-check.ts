// Real-world checks on the calorie target from what the person logs, same rules as Android CalorieCheck.kt
// (calorie plan v2): the real daily burn from food logs and weigh-ins, the step-limited move toward it, and the
// four signals. Pure, so the rules are unit tested.
import { weeklyChangePct, type CutPace, CUT_PACE, type NutritionGoal } from './nutrition'

const f = Math.fround

/** Something the logs say about the calorie target, in the order Home shows them. */
export type CalorieSignal = 'UNDER_FUELLED' | 'WEIGHT_CHANGED' | 'SCALE_CREEP' | 'STEP_DROP'

export const WINDOW_DAYS = 21
export const MIN_LOGGED_DAYS = 17
export const MIN_WEIGH_INS = 6
export const SKIP_AFTER_GOAL_DAYS = 7
export const MAX_STEP_KCAL = 200
export const COOLDOWN_DAYS = 14
export const KCAL_PER_KG = 7700

/** A food day counts only with at least max(800, 0.70 × BMR) logged, so a half-logged day can't fake a tiny intake. */
export const fullDayKcal = (bmr: number): number => Math.max(800, f(f(0.7) * bmr))

/** Share of the last 21 days (before today) that were fully logged. */
export function loggedShare(intake: ReadonlyMap<number, number>, today: number, bmr: number): number {
  const full = fullDayKcal(bmr)
  let n = 0
  for (let d = today - WINDOW_DAYS; d < today; d++) if ((intake.get(d) ?? 0) >= full) n++
  return f(n / WINDOW_DAYS)
}

/** Median of the slopes between every pair of points (kg per day). */
export function theilSen(points: ReadonlyArray<readonly [number, number]>): number {
  const slopes: number[] = []
  for (let i = 0; i < points.length; i++) {
    for (let j = i + 1; j < points.length; j++) {
      const dx = points[j][0] - points[i][0]
      if (dx > 0) slopes.push(f(f(points[j][1] - points[i][1]) / dx))
    }
  }
  if (!slopes.length) return 0
  slopes.sort((a, b) => a - b)
  const m = slopes.length >> 1
  return slopes.length % 2 ? slopes[m] : f((slopes[m - 1] + slopes[m]) / 2)
}

export type Burn = { kcal: number; loggedDays: number; kgPerWeek: number }

/**
 * The real daily burn over the last 21 days: average logged intake minus the weight trend × 7,700 kcal/kg. The trend
 * is the median of pairwise slopes (Theil–Sen): one salty day can't swing it, and it doesn't lag like a moving
 * average. Null until 17 of 21 days are fully logged, 6+ weigh-ins span 14+ days with 2 in the first and 2 in the last
 * week, and the window starts 7+ days after the goal changed. Kept within 0.8–1.3 × [formulaBurn].
 */
export function realBurn(
  intake: ReadonlyMap<number, number>, weights: ReadonlyArray<readonly [number, number]>, today: number,
  goalSetDay: number, bmr: number, formulaBurn: number,
): Burn | null {
  const start = today - WINDOW_DAYS
  const end = today - 1
  if (start < goalSetDay + SKIP_AFTER_GOAL_DAYS) return null
  const full = fullDayKcal(bmr)
  const logged: number[] = []
  for (let d = start; d <= end; d++) { const k = intake.get(d); if (k != null && k >= full) logged.push(k) }
  if (logged.length < MIN_LOGGED_DAYS) return null
  const w = weights.filter(([d]) => d >= start && d <= end).sort((a, b) => a[0] - b[0])
  if (w.length < MIN_WEIGH_INS || w[w.length - 1][0] - w[0][0] < 14) return null
  if (w.filter(([d]) => d < start + 7).length < 2 || w.filter(([d]) => d > end - 7).length < 2) return null
  const slope = theilSen(w)
  const avg = f(logged.reduce((a, x) => a + x, 0) / logged.length)
  const burn = Math.min(Math.max(f(avg - f(slope * KCAL_PER_KG)), f(f(0.8) * formulaBurn)), f(f(1.3) * formulaBurn))
  return { kcal: Math.round(f(burn / 10)) * 10, loggedDays: logged.length, kgPerWeek: f(slope * 7) }
}

/**
 * The next target on the way to [suggested]: at most ±200 kcal from [current], at most every 14 days after
 * [targetSetDay], and only when it moves 100+ kcal. Null when nothing should change.
 */
export function nextTarget(current: number, suggested: number, targetSetDay: number, today: number): number | null {
  if (today - targetSetDay < COOLDOWN_DAYS) return null
  const step = Math.min(Math.max(suggested - current, -MAX_STEP_KCAL), MAX_STEP_KCAL)
  return Math.abs(step) < 100 ? null : Math.round(f((current + step) / 50)) * 50
}

/** Average steps in the last 14 days vs the first 14 days of the cut, each needing 10 worn days (500+ steps). */
export function stepDrop(steps: ReadonlyMap<number, number>, goalSetDay: number, today: number): boolean {
  if (today - 14 < goalSetDay + 14) return false
  const avg = (from: number) => {
    const xs: number[] = []
    for (let d = from; d < from + 14; d++) { const s = steps.get(d); if (s != null && s >= 500) xs.push(s) }
    return xs.length >= 10 ? xs.reduce((a, x) => a + x, 0) / xs.length : null
  }
  const first = avg(goalSetDay)
  const last = avg(today - 14)
  return first != null && last != null && (first - last >= 2000 || last <= 0.75 * first)
}

export type SignalInput = {
  goal: NutritionGoal
  cutPace: CutPace
  goalSetDay: number
  today: number
  weights: ReadonlyArray<readonly [number, number]>
  /** From [loggedShare]. */
  loggedShare: number
  /** epochDay → steps; empty without a watch (always on web). */
  steps: ReadonlyMap<number, number>
  /** The deload check found stalled lifts or several low-recovery days. */
  deloadNeeded: boolean
  /** The weight the target was set at (0 when unknown). */
  targetWeightKg: number
}

/** The signals that apply today, most important first. */
export function calorieSignals(i: SignalInput): CalorieSignal[] {
  const out: CalorieSignal[] = []
  const cut = i.goal === 'CUT'
  const trend = weeklyChangePct(i.goalSetDay, i.weights, i.today)?.[0] ?? null
  if (cut && i.cutPace !== 'GENTLE' && (i.deloadNeeded || (trend != null && trend < -(CUT_PACE[i.cutPace].weeklyPct + 0.25)))) out.push('UNDER_FUELLED')
  const recent = i.weights.filter(([d]) => d >= i.today - 6 && d <= i.today)
  if (i.targetWeightKg > 0 && recent.length >= 2 && Math.abs(recent.reduce((a, [, kg]) => a + kg, 0) / recent.length - i.targetWeightKg) >= 2.5) out.push('WEIGHT_CHANGED')
  if (cut && i.loggedShare < MIN_LOGGED_DAYS / WINDOW_DAYS) {
    const since = i.weights.filter(([d]) => d >= i.goalSetDay)
    const month = weeklyChangePct(i.goalSetDay, i.weights, i.today, 28)?.[0] ?? null
    if (since.length >= MIN_WEIGH_INS && month != null && month > -0.15) out.push('SCALE_CREEP')
  }
  if (cut && stepDrop(i.steps, i.goalSetDay, i.today)) out.push('STEP_DROP')
  return out
}
