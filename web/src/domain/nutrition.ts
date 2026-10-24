// Daily calorie, protein and carbohydrate targets, same math as Android Nutrition.kt:
// - BMR via Katch-McArdle (when body fat % is known) or Mifflin-St Jeor.
// - Calories floored at 1,200 kcal/day.
// - Protein per kg of body weight (or lean mass when body fat is 30–60 %).
// - Fat is the larger of the chosen share of calories and 0.7 g/kg, before filling carbs.
// Kotlin uses 32-bit Float, so intermediate values go through Math.fround before each rounding.

const f = Math.fround
const clamp = (v: number, lo: number, hi: number) => Math.min(Math.max(v, lo), hi)

export const NUTRITION_GOALS = ['CUT', 'MAINTAIN', 'GAIN'] as const
export type NutritionGoal = (typeof NUTRITION_GOALS)[number]
export type WeightTrend = 'CUT_TOO_FAST' | 'GAIN_STALLED'

export const MIN_SAFE_KCAL = 1200
export const MAX_SAFE_KCAL = 6000
export const MIN_PROTEIN_PER_KG = 1.6
export const DEFAULT_PROTEIN_PER_KG = 1.8
export const MIN_FAT_PER_KG = 0.7
export const MIN_FAT_SHARE_PCT = 20
export const DEFAULT_FAT_SHARE_PCT = 25
export const MAX_DEFICIT_PCT = 0.22
export const MIN_TREND_DAYS = 14

/**
 * [perKg] grams per kg (clamped to 1.2..2.5), rounded to 5 g, at least 60 g (50 g below 1.6 g/kg).
 * When [bodyFatPct] is 30–60 %, scales against lean mass on a 20 % body-fat reference frame.
 */
export function suggestedProtein(kg: number, perKg = DEFAULT_PROTEIN_PER_KG, bodyFatPct = 0): number {
  kg = f(kg)
  if (kg <= 0) return 120
  const bf = f(bodyFatPct)
  const effectiveMassKg = bf >= 30 && bf <= 60 ? f(f(kg * f(1 - f(bf / 100))) / f(0.8)) : kg
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

/**
 * BMR times an activity factor from planned training days, adjusted for the goal, rounded to 50 kcal and kept
 * within 1,200..6,000. Without height or age (and without body fat) it uses 33 kcal per kg.
 */
export function suggestedKcal(
  kg: number,
  heightCm: number,
  age: number,
  male: boolean,
  trainingDays: number,
  bodyFatPct = 0,
  goal: NutritionGoal = 'MAINTAIN',
): number {
  const safeKg = f(clamp(f(kg), 30, 250))
  const bf = f(bodyFatPct)
  const tdee = !(bf >= 4 && bf <= 60) && (f(heightCm) <= 0 || age <= 0)
    ? f(safeKg * 33)
    : f(bmrKcal(safeKg, heightCm, age, male, bodyFatPct) * f(activityFactor(trainingDays)))
  const adjusted =
    goal === 'CUT' ? f(tdee - Math.min(500, f(tdee * f(MAX_DEFICIT_PCT))))
    : goal === 'GAIN' ? f(tdee + 250)
    : tdee
  return clamp(Math.round(f(adjusted / 50)) * 50, MIN_SAFE_KCAL, MAX_SAFE_KCAL)
}

/** Activity multiplier from planned training days; 6–7 lifting days stop at 1.65 (no 1.9 tier). */
export function activityFactor(trainingDays: number): number {
  if (trainingDays <= 0) return 1.2
  if (trainingDays <= 3) return 1.375
  if (trainingDays <= 5) return 1.55
  return 1.65
}

/** Protein per kg behind the suggestion: a cut keeps more protein to hold on to muscle. */
export function proteinPerKgFor(goal: NutritionGoal): number {
  return goal === 'CUT' ? 2.0 : DEFAULT_PROTEIN_PER_KG
}

export function ageFromBirthYear(birthYear: number, currentYear: number): number {
  return birthYear <= 0 ? 0 : clamp(currentYear - birthYear, 0, 120)
}

/**
 * A gentle weekly note on the weight trend under the current goal, or null when there's nothing to say.
 * Needs weigh-ins spanning at least [MIN_TREND_DAYS] days since the goal was set. [weights] are
 * [epochDay, kg] pairs in any order.
 */
export function weightTrendNote(
  goal: NutritionGoal,
  goalSetDay: number,
  weights: ReadonlyArray<readonly [number, number]>,
  today: number,
): WeightTrend | null {
  if (goal === 'MAINTAIN') return null
  const since = weights.filter(([d]) => d >= goalSetDay && d <= today).sort((a, b) => a[0] - b[0])
  if (since.length < 3) return null
  const spanDays = since[since.length - 1][0] - since[0][0]
  if (spanDays < MIN_TREND_DAYS) return null
  // Average the first and last few weigh-ins so one salty dinner doesn't decide it. The two groups never share
  // a weigh-in, and the change is spread over the days between their middles, so few weigh-ins don't shrink it.
  const n = Math.min(3, Math.floor(since.length / 2))
  const headGroup = since.slice(0, n)
  const tailGroup = since.slice(since.length - n)
  const avg = (xs: number[]) => xs.reduce((a, x) => a + x, 0) / xs.length
  const head = f(avg(headGroup.map(([, kg]) => f(kg))))
  const tail = f(avg(tailGroup.map(([, kg]) => f(kg))))
  const betweenDays = f(avg(tailGroup.map(([d]) => d)) - avg(headGroup.map(([d]) => d)))
  const weeklyChangePct = f(f(f(f(tail - head) / head) * 100) / f(betweenDays / 7))
  if (goal === 'CUT') return weeklyChangePct < -1 ? 'CUT_TOO_FAST' : null
  return spanDays >= 21 && weeklyChangePct <= f(0.05) ? 'GAIN_STALLED' : null
}
