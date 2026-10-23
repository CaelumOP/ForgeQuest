export const BADGE_CATEGORIES = ['CONSISTENCY', 'SKILL', 'STRENGTH', 'NUTRITION'] as const
export type BadgeCategory = (typeof BADGE_CATEGORIES)[number]

export const BADGE_CATEGORY_LABEL: Record<BadgeCategory, string> = {
  CONSISTENCY: 'Consistency',
  SKILL: 'Skills',
  STRENGTH: 'Strength',
  NUTRITION: 'Nutrition',
}

/** Everything badge rules need, computed once from the database. */
export type BadgeStats = {
  workouts: number
  bestStreakWeeks: number
  bestReps: Record<string, number>
  bestSeconds: Record<string, number>
  bestWeight: Record<string, number>
  mealsLogged: number
  mealsScanned: number
  proteinGoalDaysInARow: number
}

export function badgeStats(values: Partial<BadgeStats> = {}): BadgeStats {
  return {
    workouts: 0, bestStreakWeeks: 0, bestReps: {}, bestSeconds: {}, bestWeight: {},
    mealsLogged: 0, mealsScanned: 0, proteinGoalDaysInARow: 0, ...values,
  }
}

export type BadgeDef = {
  id: string
  title: string
  description: string
  category: BadgeCategory
  unlocked: (s: BadgeStats) => boolean
}

const reps = (id: string, n: number) => (s: BadgeStats) => (s.bestReps[id] ?? 0) >= n
const hold = (id: string, sec: number) => (s: BadgeStats) => (s.bestSeconds[id] ?? 0) >= sec
const load = (id: string, kg: number) => (s: BadgeStats) => (s.bestWeight[id] ?? 0) >= kg

const b = (id: string, title: string, description: string, category: BadgeCategory, unlocked: (s: BadgeStats) => boolean): BadgeDef =>
  ({ id, title, description, category, unlocked })

export const BADGES: readonly BadgeDef[] = [
  b('first_workout', 'First step', 'Finish your first workout', 'CONSISTENCY', (s) => s.workouts >= 1),
  b('workouts_10', 'Ten in', 'Finish 10 workouts', 'CONSISTENCY', (s) => s.workouts >= 10),
  b('workouts_50', 'Half century', 'Finish 50 workouts', 'CONSISTENCY', (s) => s.workouts >= 50),
  b('workouts_100', 'Hundred club', 'Finish 100 workouts', 'CONSISTENCY', (s) => s.workouts >= 100),
  b('streak_4', 'One month strong', 'Hit your weekly target 4 weeks in a row', 'CONSISTENCY', (s) => s.bestStreakWeeks >= 4),
  b('streak_12', 'Quarter of steel', 'Hit your weekly target 12 weeks in a row', 'CONSISTENCY', (s) => s.bestStreakWeeks >= 12),
  b('streak_52', 'Full year', 'Hit your weekly target 52 weeks in a row', 'CONSISTENCY', (s) => s.bestStreakWeeks >= 52),

  b('skill_muscle_up', 'First muscle-up', 'Log a muscle-up', 'SKILL', reps('muscle_up', 1)),
  b('skill_pistol', 'Pistol', 'Log a pistol squat', 'SKILL', reps('pistol_squat', 1)),
  b('skill_l_sit', 'L-sit 10 s', 'Hold an L-sit for 10 seconds', 'SKILL', hold('l_sit', 10)),
  b('skill_handstand', 'Handstand 30 s', 'Hold a handstand for 30 seconds', 'SKILL', hold('handstand_hold', 30)),
  b('skill_tuck_planche', 'Tuck planche', 'Hold a tuck planche for 10 seconds', 'SKILL', hold('tuck_planche', 10)),
  b('skill_tuck_lever', 'Tuck front lever', 'Hold a tuck front lever for 10 seconds', 'SKILL', hold('tuck_front_lever', 10)),
  b('skill_dead_hang', 'Iron grip', 'Dead hang for 60 seconds', 'SKILL', hold('dead_hang', 60)),

  b('str_knee_push_20', 'Foundation push', '20 knee push-ups in one set', 'STRENGTH', reps('knee_push_up', 20)),
  b('str_aus_pull_15', 'Foundation pull', '15 Australian pull-ups in one set', 'STRENGTH', reps('australian_pull_up', 15)),
  b('str_push_30', 'Push-up 30', '30 push-ups in one set', 'STRENGTH', reps('push_up', 30)),
  b('str_pull_1', 'First pull-up', 'Your first full pull-up', 'STRENGTH', reps('pull_up', 1)),
  b('str_pull_10', 'Pull-up 10', '10 pull-ups in one set', 'STRENGTH', reps('pull_up', 10)),
  b('str_dip_15', 'Dip 15', '15 dips in one set', 'STRENGTH', reps('dip', 15)),
  b('str_weighted_pull_20', '+20 kg pull-up', 'Pull-up with 20 kg added', 'STRENGTH', load('weighted_pull_up', 20)),
  b('str_weighted_dip_30', '+30 kg dip', 'Dip with 30 kg added', 'STRENGTH', load('weighted_dip', 30)),

  b('food_first', 'First bite', 'Log your first meal', 'NUTRITION', (s) => s.mealsLogged >= 1),
  b('food_scan', 'Snap & log', 'Log a meal with the camera', 'NUTRITION', (s) => s.mealsScanned >= 1),
  b('protein_3', 'Protein habit', 'Hit your protein goal 3 days in a row', 'NUTRITION', (s) => s.proteinGoalDaysInARow >= 3),
  b('protein_7', 'Protein week', 'Hit your protein goal 7 days in a row', 'NUTRITION', (s) => s.proteinGoalDaysInARow >= 7),
  b('protein_30', 'Protein month', 'Hit your protein goal 30 days in a row', 'NUTRITION', (s) => s.proteinGoalDaysInARow >= 30),
]

export const BADGES_BY_ID: ReadonlyMap<string, BadgeDef> = new Map(BADGES.map((d) => [d.id, d]))
