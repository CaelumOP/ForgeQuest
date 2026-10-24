// Daily calorie, protein and carbohydrate targets, same math as Android Nutrition.kt (calorie plan v2, 2026-10-24):
// - BMR via Katch-McArdle (when body fat % is known) or Mifflin-St Jeor.
// - TDEE = BMR × (day base + 0.025 per weekly training day): lifting no longer counts as an active day.
// - Cut and gain paces as % of body weight per week, capped, never below a body-based floor.
// - Protein per kg of body weight (lean mass when body fat is 30–60 %, adjusted weight above BMI 30 when unknown).
// - Fat is the larger of the chosen share of calories and 0.7 g/kg, before filling carbs.
// Kotlin uses 32-bit Float, so intermediate values go through Math.fround before each rounding.

const f = Math.fround
const clamp = (v: number, lo: number, hi: number) => Math.min(Math.max(v, lo), hi)
const round50 = (x: number) => Math.round(f(x / 50)) * 50

export const NUTRITION_GOALS = ['CUT', 'MAINTAIN', 'GAIN'] as const
export type NutritionGoal = (typeof NUTRITION_GOALS)[number]
export type WeightTrend = 'CUT_TOO_FAST' | 'GAIN_STALLED' | 'GAIN_TOO_FAST' | 'DIET_BREAK_DUE'

/** How active a normal day is outside workouts, and its activity factor before training days are added. */
export const DAY_ACTIVITIES = ['SITTING', 'MIXED', 'PHYSICAL'] as const
export type DayActivity = (typeof DAY_ACTIVITIES)[number]
export const DAY_BASE: Record<DayActivity, number> = { SITTING: 1.25, MIXED: 1.375, PHYSICAL: 1.5 }

export const CUT_PACES = ['GENTLE', 'STANDARD', 'AGGRESSIVE'] as const
export type CutPace = (typeof CUT_PACES)[number]
/** % of body weight per week, deficit cap (share of TDEE), protein g/kg, weeks before a break, weekly % that warns. */
export const CUT_PACE: Record<CutPace, { weeklyPct: number; maxTdeeShare: number; proteinPerKg: number; breakWeeks: number; fastWarnPct: number }> = {
  GENTLE: { weeklyPct: 0.5, maxTdeeShare: 0.2, proteinPerKg: 1.8, breakWeeks: 16, fastWarnPct: -0.75 },
  STANDARD: { weeklyPct: 0.75, maxTdeeShare: 0.25, proteinPerKg: 2.0, breakWeeks: 12, fastWarnPct: -1.0 },
  AGGRESSIVE: { weeklyPct: 1.0, maxTdeeShare: 0.3, proteinPerKg: 2.2, breakWeeks: 6, fastWarnPct: -1.25 },
}

export const GAIN_PACES = ['SLOW', 'STANDARD'] as const
export type GainPace = (typeof GAIN_PACES)[number]
export const GAIN_PACE: Record<GainPace, { weeklyPct: number; minSurplus: number; maxSurplus: number; proteinPerKg: number; fastWarnPct: number }> = {
  SLOW: { weeklyPct: 0.15, minSurplus: 125, maxSurplus: 225, proteinPerKg: 1.8, fastWarnPct: 0.3 },
  STANDARD: { weeklyPct: 0.25, minSurplus: 200, maxSurplus: 350, proteinPerKg: 1.8, fastWarnPct: 0.5 },
}

export const parseDayActivity = (raw: string | null | undefined): DayActivity => DAY_ACTIVITIES.find((x) => x === raw) ?? 'SITTING'
export const parseCutPace = (raw: string | null | undefined): CutPace => CUT_PACES.find((x) => x === raw) ?? 'STANDARD'
export const parseGainPace = (raw: string | null | undefined): GainPace => GAIN_PACES.find((x) => x === raw) ?? 'STANDARD'

export const MIN_SAFE_KCAL = 1200
export const MIN_SAFE_KCAL_MALE = 1500
export const MAX_SAFE_KCAL = 6000
export const MIN_PROTEIN_PER_KG = 1.6
export const DEFAULT_PROTEIN_PER_KG = 1.8
export const MIN_FAT_PER_KG = 0.7
export const MIN_FAT_SHARE_PCT = 20
export const DEFAULT_FAT_SHARE_PCT = 25
/** Each weekly training day adds this × BMR to every day (about 0.175 × BMR per session). */
export const PER_TRAINING_DAY = 0.025
/** kcal per day for 1 kg of body weight change per week (7,700 kcal/kg ÷ 7). */
export const KCAL_PER_KG_WEEK = 1100
export const MIN_TREND_DAYS = 14

const bmi = (kg: number, heightCm: number) => f(kg / f(f(heightCm / 100) * f(heightCm / 100)))

/**
 * [perKg] grams per kg (clamped to 1.2..2.5), rounded to 5 g, at least 60 g (50 g below 1.6 g/kg).
 * When [bodyFatPct] is 30–60 %, scales against lean mass on a 20 % body-fat reference frame. When body fat is unknown
 * and BMI is over 30, uses an adjusted weight (BMI-25 weight + a quarter of the rest).
 */
export function suggestedProtein(kg: number, perKg = DEFAULT_PROTEIN_PER_KG, bodyFatPct = 0, heightCm = 0): number {
  kg = f(kg)
  if (kg <= 0) return 120
  const bf = f(bodyFatPct)
  let effectiveMassKg = kg
  if (bf >= 30 && bf <= 60) effectiveMassKg = f(f(kg * f(1 - f(bf / 100))) / f(0.8))
  else if (bf <= 0 && heightCm > 0 && bmi(kg, heightCm) > 30) {
    const refKg = f(25 * f(f(heightCm / 100) * f(heightCm / 100)))
    effectiveMassKg = f(refKg + f(f(0.25) * f(kg - refKg)))
  }
  const ratio = f(clamp(f(perKg), f(1.2), 2.5))
  const raw = Math.round(f(f(effectiveMassKg * ratio) / 5)) * 5
  return f(perKg) >= f(MIN_PROTEIN_PER_KG) ? Math.max(raw, 60) : Math.max(raw, 50)
}

/**
 * Carbohydrate grams left after protein (4 kcal/g) and fat (9 kcal/g), rounded to 5 g.
 * Fat is the larger of [fatPct] % of calories and 0.7 g/kg of [bodyWeightKg], rounded to 5 g first.
 */
export function suggestedCarbs(kcal: number, proteinG: number, fatPct = DEFAULT_FAT_SHARE_PCT, bodyWeightKg = 0): number {
  const safeKcal = Math.max(kcal, MIN_SAFE_KCAL)
  const fatFromPctG = f(f(safeKcal * f(clamp(fatPct, 15, 45) / 100)) / 9)
  const minEssentialFatG = bodyWeightKg > 0 ? f(f(bodyWeightKg) * f(MIN_FAT_PER_KG)) : 0
  // web: audit #15 (carbs come from the fat rounded to 5 g, so protein + fat + carbs add up to the goal)
  const fatG = Math.round(f(Math.max(fatFromPctG, minEssentialFatG) / 5)) * 5
  const remainingKcal = f(f(safeKcal - f(proteinG * 4)) - f(fatG * 9))
  if (remainingKcal <= 0) return 0
  return Math.round(f(f(remainingKcal / 4) / 5)) * 5 // no minimum: 20 g could pass the calorie goal
}

/**
 * Resting BMR in kcal/day: Katch-McArdle (370 + 21.6 × lean kg) when [bodyFatPct] is 4..60, else
 * Mifflin-St Jeor (10×kg + 6.25×cm − 5×age + 5/−161) when height and age are known, else 22 kcal/kg.
 */
export function bmrKcal(kg: number, heightCm: number, age: number, male: boolean, bodyFatPct = 0): number {
  const safeKg = f(clamp(f(kg), 30, 250))
  const bf = f(bodyFatPct)
  if (bf >= 4 && bf <= 60) {
    const lbmKg = f(safeKg * f(1 - f(bf / 100)))
    return f(370 + f(f(21.6) * lbmKg))
  }
  const h = f(heightCm)
  if (h <= 0 || age <= 0) return f(safeKg * 22)
  return f(f(f(f(10 * safeKg) + f(6.25 * h)) - 5 * clamp(age, 10, 100)) + (male ? 5 : -161))
}

/** Day base + 0.025 per weekly training day, kept within 1.25..1.80. */
export function activityFactor(trainingDays: number, base = DAY_BASE.SITTING): number {
  return clamp(f(f(base) + f(f(PER_TRAINING_DAY) * clamp(trainingDays, 0, 7))), f(1.25), f(1.8))
}

/**
 * The day base from a watch: needs steps on at least 5 of the last 7 days (under 500 = not worn); days with a logged
 * workout count 1,000 steps less. 4,000 steps = 1.25, about 10,250 = 1.375, capped at 1.50. Null without enough data.
 * [days] = [steps, had a workout] for each of the last 7 days. (The web has no steps; this keeps the two apps alike.)
 */
export function stepsBase(days: ReadonlyArray<readonly [number, boolean]>): number | null {
  const valid = days.filter(([s]) => s >= 500).map(([s, workout]) => (workout ? Math.max(0, s - 1000) : s))
  if (valid.length < 5) return null
  const avg = f(valid.reduce((a, x) => a + x, 0) / valid.length)
  return Math.min(f(1.5), f(f(1.25) + f(f(f(0.02) * Math.max(0, f(avg - 4000))) / 1000)))
}

/** Estimated daily burn: BMR × activity factor. */
export function dailyBurn(kg: number, heightCm: number, age: number, male: boolean, trainingDays: number, bodyFatPct = 0, base = DAY_BASE.SITTING): number {
  return f(bmrKcal(kg, heightCm, age, male, bodyFatPct) * activityFactor(trainingDays, base))
}

/** The lowest daily target the app suggests or accepts: 1,500 men / 1,200 women, and never under 85 % of BMR. */
export function calorieFloor(kg: number, heightCm: number, age: number, male: boolean, bodyFatPct = 0): number {
  return round50(Math.max(male ? MIN_SAFE_KCAL_MALE : MIN_SAFE_KCAL, f(f(0.85) * bmrKcal(kg, heightCm, age, male, bodyFatPct))))
}

/** The Fast pace isn't offered to under-18s or the already lean (BMI < 20, body fat < 12 % men / < 20 % women). */
export function allowedCutPace(requested: CutPace, kg: number, heightCm: number, age: number, male: boolean, bodyFatPct = 0): CutPace {
  if (requested !== 'AGGRESSIVE') return requested
  const b = heightCm > 0 ? bmi(kg, heightCm) : 25
  const leanByFat = bodyFatPct >= 4 && bodyFatPct <= 60 && bodyFatPct < (male ? 12 : 20)
  return (age >= 1 && age <= 17) || b < 20 || leanByFat ? 'STANDARD' : requested
}

export type KcalOptions = { base?: number; cutPace?: CutPace; gainPace?: GainPace; burn?: number | null }

/**
 * Daily target: [dailyBurn], minus the pace's deficit (kg × weekly % × 1,100, capped at a share of TDEE) or plus its
 * surplus, rounded to 50 and never below [calorieFloor]. A Fast pace the person can't have becomes Standard.
 * `burn` replaces the formula's TDEE once the app has learned the real one from logs.
 */
export function suggestedKcal(
  kg: number,
  heightCm: number,
  age: number,
  male: boolean,
  trainingDays: number,
  bodyFatPct = 0,
  goal: NutritionGoal = 'MAINTAIN',
  { base = DAY_BASE.SITTING, cutPace = 'STANDARD', gainPace = 'STANDARD', burn = null }: KcalOptions = {},
): number {
  const safeKg = f(clamp(f(kg), 30, 250))
  const tdee = burn != null ? f(burn) : dailyBurn(safeKg, heightCm, age, male, trainingDays, bodyFatPct, base)
  let adjusted = tdee
  if (goal === 'CUT') {
    const p = CUT_PACE[allowedCutPace(cutPace, safeKg, heightCm, age, male, bodyFatPct)]
    adjusted = f(tdee - Math.min(f(f(f(safeKg * f(p.weeklyPct)) / 100) * KCAL_PER_KG_WEEK), f(tdee * f(p.maxTdeeShare))))
  } else if (goal === 'GAIN') {
    const p = GAIN_PACE[gainPace]
    adjusted = f(tdee + clamp(f(f(f(safeKg * f(p.weeklyPct)) / 100) * KCAL_PER_KG_WEEK), p.minSurplus, p.maxSurplus))
  }
  return clamp(round50(adjusted), calorieFloor(safeKg, heightCm, age, male, bodyFatPct), MAX_SAFE_KCAL)
}

/** The target suggested before calorie plan v2, kept only to spot people still on it (one-time Home card). */
export function legacySuggestedKcal(kg: number, heightCm: number, age: number, male: boolean, trainingDays: number, bodyFatPct = 0, goal: NutritionGoal = 'MAINTAIN'): number {
  const safeKg = f(clamp(f(kg), 30, 250))
  const factor = trainingDays <= 0 ? 1.2 : trainingDays <= 3 ? 1.375 : trainingDays <= 5 ? 1.55 : 1.65
  const bf = f(bodyFatPct)
  const tdee = !(bf >= 4 && bf <= 60) && (f(heightCm) <= 0 || age <= 0)
    ? f(safeKg * 33)
    : f(bmrKcal(safeKg, heightCm, age, male, bodyFatPct) * f(factor))
  const adjusted = goal === 'CUT' ? f(tdee - Math.min(500, f(tdee * f(0.22)))) : goal === 'GAIN' ? f(tdee + 250) : tdee
  return clamp(round50(adjusted), MIN_SAFE_KCAL, MAX_SAFE_KCAL)
}

/** Protein per kg behind the suggestion: it rises with the cut pace to hold on to muscle. */
export function proteinPerKgFor(goal: NutritionGoal, cutPace: CutPace = 'STANDARD', gainPace: GainPace = 'STANDARD'): number {
  return goal === 'CUT' ? CUT_PACE[cutPace].proteinPerKg : goal === 'GAIN' ? GAIN_PACE[gainPace].proteinPerKg : DEFAULT_PROTEIN_PER_KG
}

export function ageFromBirthYear(birthYear: number, currentYear: number): number {
  return birthYear <= 0 ? 0 : clamp(currentYear - birthYear, 0, 120)
}

/**
 * Weekly % change of body weight since [goalSetDay] from the first and last few weigh-ins, with the span in days,
 * or null with fewer than 3 weigh-ins spanning [minDays]. [weights] are [epochDay, kg] pairs in any order.
 */
export function weeklyChangePct(goalSetDay: number, weights: ReadonlyArray<readonly [number, number]>, today: number, minDays = MIN_TREND_DAYS): [number, number] | null {
  const since = weights.filter(([d]) => d >= goalSetDay && d <= today).sort((a, b) => a[0] - b[0])
  if (since.length < 3) return null
  const spanDays = since[since.length - 1][0] - since[0][0]
  if (spanDays < minDays) return null
  // Average the first and last few weigh-ins so one salty dinner doesn't decide it. The two groups never share
  // a weigh-in, and the change is spread over the days between their middles, so few weigh-ins don't shrink it.
  const n = Math.min(3, Math.floor(since.length / 2))
  const headGroup = since.slice(0, n)
  const tailGroup = since.slice(since.length - n)
  const avg = (xs: number[]) => xs.reduce((a, x) => a + x, 0) / xs.length
  const head = f(avg(headGroup.map(([, kg]) => f(kg))))
  const tail = f(avg(tailGroup.map(([, kg]) => f(kg))))
  const betweenDays = f(avg(tailGroup.map(([d]) => d)) - avg(headGroup.map(([d]) => d)))
  if (betweenDays <= 0 || head <= 0) return null
  return [f(f(f(f(tail - head) / head) * 100) / f(betweenDays / 7)), spanDays]
}

/**
 * A gentle note on the weight trend under the current goal and pace, or null: losing faster than the pace allows, a
 * diet break due after the pace's weeks, gaining too fast, or a stalled gain (28 days for women, so a menstrual
 * cycle's water swing can't fake one).
 */
export function weightTrendNote(
  goal: NutritionGoal,
  goalSetDay: number,
  weights: ReadonlyArray<readonly [number, number]>,
  today: number,
  cutPace: CutPace = 'STANDARD',
  gainPace: GainPace = 'STANDARD',
  female = false,
): WeightTrend | null {
  if (goal === 'MAINTAIN') return null
  const trend = weeklyChangePct(goalSetDay, weights, today)
  if (goal === 'CUT') {
    if (trend && trend[0] < f(CUT_PACE[cutPace].fastWarnPct)) return 'CUT_TOO_FAST'
    return goalSetDay > 0 && today - goalSetDay >= CUT_PACE[cutPace].breakWeeks * 7 ? 'DIET_BREAK_DUE' : null
  }
  if (!trend) return null
  if (trend[0] > f(GAIN_PACE[gainPace].fastWarnPct)) return 'GAIN_TOO_FAST'
  return trend[1] >= (female ? 28 : 21) && trend[0] <= f(0.05) ? 'GAIN_STALLED' : null
}
