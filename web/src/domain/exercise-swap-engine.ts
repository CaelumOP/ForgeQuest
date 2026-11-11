// Equipment, biomechanical profiles and exercise substitution: same as Android ExerciseSwapEngine.kt.
import { STRETCH_IDS } from '../data/seed/exercise-catalog'
import type { ExerciseEntity, PlanDraftDay, PlanDraftItem, PlanItemEntity } from './entities'
import { isExerciseKind, isMuscle, type ExerciseKind } from './muscle'

const f = Math.fround

/** Equipment categories for filtering and biomechanical exercise substitution. */
export const EQUIPMENT = ['BODYWEIGHT', 'PULL_UP_BAR', 'DIP_BARS_OR_BENCH', 'DUMBBELLS', 'KETTLEBELL', 'BARBELL_RACK', 'CABLES_MACHINES', 'BAND'] as const
export type Equipment = (typeof EQUIPMENT)[number]

export const EQUIPMENT_LABEL: Record<Equipment, { en: string; ar: string }> = {
  BODYWEIGHT: { en: 'Bodyweight', ar: 'وزن الجسم' },
  PULL_UP_BAR: { en: 'Pull-up bar', ar: 'عقلة' },
  DIP_BARS_OR_BENCH: { en: 'Dip bars / rings / bench', ar: 'متوازي أو حلقات أو مقعد' },
  DUMBBELLS: { en: 'Dumbbells', ar: 'دمبل' },
  KETTLEBELL: { en: 'Kettlebell', ar: 'كيتل بيل' },
  BARBELL_RACK: { en: 'Barbell & rack', ar: 'بار وراك' },
  CABLES_MACHINES: { en: 'Cables & gym machines', ar: 'أجهزة وكوابل النادي' },
  BAND: { en: 'Resistance band', ar: 'حزام مطاطي' },
}

/** Lowest score a plan auto-swap accepts: a related movement (18) plus some shared muscles, or the same pattern. */
export const MIN_SWAP_SCORE = 30

const allEquipment = (): Set<Equipment> => new Set(EQUIPMENT)
const isEquipment = (raw: string): raw is Equipment => (EQUIPMENT as readonly string[]).includes(raw)

/** Quick presets for Onboarding and Profile settings. */
export const EQUIPMENT_PRESETS = ['FULL_GYM', 'HOME_GYM', 'DUMBBELLS_AND_BAR', 'DUMBBELLS_AND_KETTLEBELL', 'CALISTHENICS_PARK', 'PULL_UP_BAR_ONLY', 'BODYWEIGHT_ONLY'] as const
export type EquipmentPreset = (typeof EQUIPMENT_PRESETS)[number]

export const PRESET_INFO: Record<EquipmentPreset, { id: string; labelEn: string; labelAr: string; equipment: readonly Equipment[] }> = {
  FULL_GYM: { id: 'full_gym', labelEn: 'Full gym', labelAr: 'نادي متكامل', equipment: EQUIPMENT },
  HOME_GYM: {
    id: 'home_gym', labelEn: 'Home gym (barbell, dumbbells, bar)', labelAr: 'نادي منزلي (بار، دمبل، عقلة)',
    equipment: ['BODYWEIGHT', 'PULL_UP_BAR', 'DIP_BARS_OR_BENCH', 'DUMBBELLS', 'KETTLEBELL', 'BARBELL_RACK'],
  },
  DUMBBELLS_AND_BAR: {
    id: 'dumbbells_bar', labelEn: 'Dumbbells & pull-up bar', labelAr: 'دمبل وعقلة',
    equipment: ['BODYWEIGHT', 'PULL_UP_BAR', 'DIP_BARS_OR_BENCH', 'DUMBBELLS'],
  },
  DUMBBELLS_AND_KETTLEBELL: {
    id: 'dumbbells_kb', labelEn: 'Dumbbells & kettlebell', labelAr: 'دمبل وكيتلبل',
    equipment: ['BODYWEIGHT', 'DUMBBELLS', 'KETTLEBELL'],
  },
  CALISTHENICS_PARK: {
    id: 'calisthenics', labelEn: 'Calisthenics (pull-up bar & dip bars)', labelAr: 'كاليستنكس (عقلة ومتوازي)',
    equipment: ['BODYWEIGHT', 'PULL_UP_BAR', 'DIP_BARS_OR_BENCH'],
  },
  PULL_UP_BAR_ONLY: { id: 'pull_up_bar', labelEn: 'Doorway pull-up bar only', labelAr: 'عقلة منزلية فقط', equipment: ['BODYWEIGHT', 'PULL_UP_BAR'] },
  BODYWEIGHT_ONLY: { id: 'bodyweight', labelEn: 'Pure bodyweight (no equipment)', labelAr: 'وزن الجسم فقط (بدون معدات)', equipment: ['BODYWEIGHT'] },
}

export const presetEquipment = (preset: EquipmentPreset): Set<Equipment> => new Set(PRESET_INFO[preset].equipment)

export function encodePreset(preset: EquipmentPreset): string {
  return PRESET_INFO[preset].equipment.join(',')
}

const sameSet = <T>(a: ReadonlySet<T>, b: ReadonlySet<T>) => a.size === b.size && [...a].every((x) => b.has(x))

export function presetFromEncoded(raw: string): EquipmentPreset | null {
  if (raw.trim() === '') return null
  const key = raw.trim().toLowerCase()
  const byId = EQUIPMENT_PRESETS.find((p) => PRESET_INFO[p].id === key)
  if (byId) return byId
  const parsed = parseEquipmentSet(raw)
  return EQUIPMENT_PRESETS.find((p) => sameSet(presetEquipment(p), parsed)) ?? null
}

/** Parses a comma-separated equipment setting string. Empty string means "not configured". */
export function parseEquipmentSet(raw: string): Set<Equipment> {
  const trimmed = raw.trim()
  if (trimmed === '') return new Set()
  const lower = trimmed.toLowerCase()
  if (lower === 'all' || lower === 'full_gym') return allEquipment()
  const preset = EQUIPMENT_PRESETS.find((p) => PRESET_INFO[p].id === lower)
  if (preset) return presetEquipment(preset)
  const parsed = new Set<Equipment>()
  for (const token of trimmed.split(',')) {
    const name = token.trim().toUpperCase()
    if (isEquipment(name)) parsed.add(name)
  }
  if (parsed.size === 0) return parsed
  parsed.add('BODYWEIGHT')
  return parsed
}

/**
 * Whole words or phrases from [alternatives] ("a|b c"). Spelled out with Unicode classes instead of `\b`,
 * which in JavaScript only knows English letters, so Arabic words would never match.
 */
const words = (alternatives: string) => new RegExp(`(?<![\\p{L}\\p{N}_])(?:${alternatives})(?![\\p{L}\\p{N}_])`, 'u')

const NEGATION = words("no|not|don['’]?t|doesn['’]?t|without|never|بدون|لا|ما|ليس|ماعندي|مافي")
const CLAUSE_BREAK = /[.,،;!?؟\n]|(?<!\p{L})(?:but|لكن|ولكن|بل|بس)(?!\p{L})/u

/** True when some match is not negated in its own clause: "no dumbbells" and «ما عندي دمبل» don't count. */
function hasPositive(re: RegExp, text: string): boolean {
  for (const m of text.matchAll(new RegExp(re.source, 'gu'))) {
    const before = text.substring(Math.max(0, m.index - 30), m.index).split(CLAUSE_BREAK)
    if (!NEGATION.test(before[before.length - 1])) return true
  }
  return false
}

/**
 * Scans coach memory notes and user messages for explicit equipment mentions so the coach knows whether
 * equipment intake has already been answered. Null when nothing was said.
 */
export function inferEquipmentFromText(...texts: string[]): Set<Equipment> | null {
  const combined = texts.join('\n').toLowerCase()
  if (combined.trim() === '') return null

  if (words('full gym|commercial gym|gym member|all equipment|نادي متكامل|مشترك في نادي|في النادي|جيم كامل').test(combined)) {
    return allEquipment()
  }
  if (words('no equipment|bodyweight only|pure bodyweight|nothing at home|بدون معدات|وزن الجسم فقط|وزن جسمي فقط|ما عندي معدات|لا أملك معدات').test(combined)) {
    return new Set(['BODYWEIGHT'])
  }

  const found = new Set<Equipment>()
  if (hasPositive(words('pull-?up bar|chin-?up bar|doorway bar|عقلة|بار عقلة'), combined)) found.add('PULL_UP_BAR')
  if (hasPositive(words('dip bar|parallel bar|rings|gymnastic rings|متوازي|حلقات'), combined)) found.add('DIP_BARS_OR_BENCH')
  if (hasPositive(words('dumbbell|dumbbells|دمبل|دامبل'), combined)) found.add('DUMBBELLS')
  if (hasPositive(words('kettlebell|kettlebells|كيتل بيل|كيتلبيل'), combined)) found.add('KETTLEBELL')
  // Not a lone «بار»: «بار عقلة» is a pull-up bar.
  if (hasPositive(words('barbell|squat rack|power rack|bench press|باربل|بار حديد|بار أولمبي|راك'), combined)) found.add('BARBELL_RACK')
  // Not a lone «أجهزة»: it means any device.
  if (hasPositive(words('cable|cables|lat pulldown|leg press|smith machine|كيبل|كوابل|أجهزة النادي|أجهزة الجيم'), combined)) found.add('CABLES_MACHINES')
  if (words('calisthenics|home workout|at home|في البيت|في المنزل|كاليستنكس').test(combined) && found.size === 0) {
    if (words('bodyweight|وزن الجسم').test(combined)) return new Set(['BODYWEIGHT'])
  }
  if (found.size === 0) return null
  found.add('BODYWEIGHT')
  return found
}

export type MovementPattern =
  | 'HORIZONTAL_PUSH' | 'DIP_PUSH' | 'VERTICAL_PUSH' | 'LATERAL_RAISE' | 'VERTICAL_PULL' | 'HORIZONTAL_PULL'
  | 'UPPER_BACK_PULL' | 'SQUAT_BILATERAL' | 'SQUAT_UNILATERAL' | 'HIP_HINGE' | 'KNEE_FLEXION' | 'ELBOW_FLEXION'
  | 'ELBOW_EXTENSION' | 'CORE_ANTI_EXTENSION' | 'CORE_FLEXION_ROTATION' | 'ANKLE_FLEXION' | 'CARRY_CONDITIONING'
  | 'HIP_ADDUCTION' | 'MOBILITY'

export const MOVEMENT_PATTERN_LABEL: Record<MovementPattern, { en: string; ar: string }> = {
  HORIZONTAL_PUSH: { en: 'Horizontal push', ar: 'دفع أفقي' },
  DIP_PUSH: { en: 'Dip / decline push', ar: 'دفع سفلي / متوازي' },
  VERTICAL_PUSH: { en: 'Vertical push', ar: 'دفع علوي' },
  LATERAL_RAISE: { en: 'Shoulder abduction', ar: 'رفرفة وكتف جانبي' },
  VERTICAL_PULL: { en: 'Vertical pull', ar: 'سحب عمودي' },
  HORIZONTAL_PULL: { en: 'Horizontal row', ar: 'سحب أفقي / تجديف' },
  UPPER_BACK_PULL: { en: 'Upper back / rear delt', ar: 'أعلى الظهر والكتف الخلفي' },
  SQUAT_BILATERAL: { en: 'Bilateral squat', ar: 'قرفصاء ثنائية' },
  SQUAT_UNILATERAL: { en: 'Unilateral leg / split squat', ar: 'أرجل فردية / طعن' },
  HIP_HINGE: { en: 'Hip hinge / posterior chain', ar: 'مفصل الورك / السلسلة الخلفية' },
  KNEE_FLEXION: { en: 'Knee flexion (hamstrings)', ar: 'ثني الركبة (خلفية الفخذ)' },
  ELBOW_FLEXION: { en: 'Elbow flexion (biceps)', ar: 'ثني المرفق (بايسبس)' },
  ELBOW_EXTENSION: { en: 'Elbow extension (triceps)', ar: 'مدّ المرفق (ترايسبس)' },
  CORE_ANTI_EXTENSION: { en: 'Core anti-extension hold', ar: 'ثبات الجذع ومقاومة التمدد' },
  CORE_FLEXION_ROTATION: { en: 'Core flexion / rotation', ar: 'ثني والتواء البطن' },
  ANKLE_FLEXION: { en: 'Calf / ankle flexion', ar: 'بطة وقصبة الساق' },
  CARRY_CONDITIONING: { en: 'Loaded carry / conditioning', ar: 'حمل ولياقة عامة' },
  HIP_ADDUCTION: { en: 'Hip adduction (inner thigh)', ar: 'ضمّ الورك (باطن الفخذ)' },
  MOBILITY: { en: 'Stretch / mobility', ar: 'تمدد ومرونة' },
}

export type JointAngle =
  | 'FLAT_HORIZONTAL' | 'INCLINE_UPWARD' | 'DECLINE_DOWNWARD' | 'OVERHEAD_VERTICAL' | 'FRONTAL_ABDUCTION'
  | 'OVERHEAD_PRONATED' | 'OVERHEAD_SUPINATED' | 'MID_ROW_PRONATED' | 'MID_ROW_SUPINATED' | 'HIGH_EXTERNAL_ROTATION'
  | 'KNEE_DOMINANT_BILATERAL' | 'KNEE_DOMINANT_SPLIT' | 'HIP_EXTENSION_SUPINE' | 'HIP_HINGE_STANDING' | 'HAMSTRING_CURL'
  | 'SAGITTAL_SUPINATED' | 'SAGITTAL_NEUTRAL' | 'SAGITTAL_TRICEP' | 'ISOMETRIC_PLANK' | 'SPINAL_FLEXION'
  | 'HANGING_HIP_FLEXION' | 'ROTATIONAL' | 'PLANTAR_FLEXION' | 'DORSI_FLEXION' | 'LOCOMOTION'
  | 'FRONTAL_ADDUCTION' | 'STRETCH'

export type BiomechanicalProfile = {
  equipment: Equipment
  pattern: MovementPattern
  angle: JointAngle
  preferredSwaps: string[]
}

export type SwapCandidate = {
  exercise: ExerciseEntity
  profile: BiomechanicalProfile
  score: number
  matchesUserEquipment: boolean
}

const prof = (equipment: Equipment, pattern: MovementPattern, angle: JointAngle, ...preferredSwaps: string[]): BiomechanicalProfile =>
  ({ equipment, pattern, angle, preferredSwaps })

/** Explicit biomechanical profiles for all 186 built-in exercises in the exercise catalog. */
export const CATALOG_PROFILES: ReadonlyMap<string, BiomechanicalProfile> = new Map([
  // Warm-up / conditioning
  ['jumping_jacks', prof('BODYWEIGHT', 'CARRY_CONDITIONING', 'LOCOMOTION', 'burpee', 'calf_raise')],
  ['plank_shoulder_taps', prof('BODYWEIGHT', 'CORE_ANTI_EXTENSION', 'ISOMETRIC_PLANK', 'plank', 'incline_hollow_plank', 'hollow_hold')],
  ['burpee', prof('BODYWEIGHT', 'CARRY_CONDITIONING', 'LOCOMOTION', 'jumping_jacks', 'push_up', 'squat')],

  // Hemo foundation: core
  ['incline_hollow_plank', prof('BODYWEIGHT', 'CORE_ANTI_EXTENSION', 'ISOMETRIC_PLANK', 'plank', 'hollow_hold', 'plank_shoulder_taps')],
  ['incline_reverse_plank', prof('BODYWEIGHT', 'HIP_HINGE', 'HIP_EXTENSION_SUPINE', 'hip_bridge', 'superman_hold', 'hip_thrust')],
  ['hip_bridge', prof('BODYWEIGHT', 'HIP_HINGE', 'HIP_EXTENSION_SUPINE', 'hip_thrust', 'kb_swing', 'romanian_deadlift', 'incline_reverse_plank')],
  ['crunch', prof('BODYWEIGHT', 'CORE_FLEXION_ROTATION', 'SPINAL_FLEXION', 'cable_crunch', 'hanging_knee_raise', 'hollow_hold')],
  ['superman_hold', prof('BODYWEIGHT', 'HIP_HINGE', 'HIP_EXTENSION_SUPINE', 'hip_bridge', 'incline_reverse_plank', 'romanian_deadlift')],

  // Hemo foundation: upper body
  ['knee_scapular_push_up', prof('BODYWEIGHT', 'HORIZONTAL_PUSH', 'FLAT_HORIZONTAL', 'knee_push_up', 'plank_shoulder_taps', 'push_up')],
  ['scapular_australian_pull_up', prof('DIP_BARS_OR_BENCH', 'HORIZONTAL_PULL', 'MID_ROW_PRONATED', 'australian_pull_up', 'dead_hang', 'db_row')],
  ['bent_leg_bench_dip', prof('DIP_BARS_OR_BENCH', 'DIP_PUSH', 'DECLINE_DOWNWARD', 'reverse_grip_bench_dip', 'dip', 'diamond_push_up', 'incline_diamond_knee_push_up')],
  ['scapular_australian_chin_up', prof('DIP_BARS_OR_BENCH', 'HORIZONTAL_PULL', 'MID_ROW_SUPINATED', 'australian_chin_up', 'scapular_australian_pull_up', 'chin_up')],
  ['knee_push_up', prof('BODYWEIGHT', 'HORIZONTAL_PUSH', 'FLAT_HORIZONTAL', 'wide_knee_push_up', 'push_up', 'incline_diamond_knee_push_up', 'db_bench_press')],
  ['australian_pull_up', prof('DIP_BARS_OR_BENCH', 'HORIZONTAL_PULL', 'MID_ROW_PRONATED', 'db_row', 'kb_row', 'barbell_row', 'seated_cable_row', 'negative_pull_up', 'pull_up')],
  ['reverse_grip_bench_dip', prof('DIP_BARS_OR_BENCH', 'DIP_PUSH', 'DECLINE_DOWNWARD', 'bent_leg_bench_dip', 'dip', 'diamond_push_up')],
  ['australian_chin_up', prof('DIP_BARS_OR_BENCH', 'HORIZONTAL_PULL', 'MID_ROW_SUPINATED', 'australian_pull_up', 'chin_up', 'negative_pull_up', 'db_row')],
  ['wide_knee_push_up', prof('BODYWEIGHT', 'HORIZONTAL_PUSH', 'FLAT_HORIZONTAL', 'knee_push_up', 'push_up', 'db_bench_press')],
  ['dead_hang', prof('PULL_UP_BAR', 'VERTICAL_PULL', 'OVERHEAD_PRONATED', 'scapular_australian_pull_up', 'negative_pull_up', 'farmers_carry')],
  ['incline_diamond_knee_push_up', prof('BODYWEIGHT', 'ELBOW_EXTENSION', 'INCLINE_UPWARD', 'diamond_push_up', 'bent_leg_bench_dip', 'knee_push_up', 'tricep_pushdown')],

  // Hemo foundation: legs
  ['half_squat', prof('BODYWEIGHT', 'SQUAT_BILATERAL', 'KNEE_DOMINANT_BILATERAL', 'squat', 'half_wide_squat', 'half_narrow_squat', 'goblet_squat')],
  ['half_wide_squat', prof('BODYWEIGHT', 'SQUAT_BILATERAL', 'KNEE_DOMINANT_BILATERAL', 'half_squat', 'squat', 'goblet_squat')],
  ['half_narrow_squat', prof('BODYWEIGHT', 'SQUAT_BILATERAL', 'KNEE_DOMINANT_BILATERAL', 'half_squat', 'squat', 'lunge')],
  ['calf_raise_turned_out', prof('BODYWEIGHT', 'ANKLE_FLEXION', 'PLANTAR_FLEXION', 'calf_raise', 'standing_calf_raise')],
  ['calf_raise', prof('BODYWEIGHT', 'ANKLE_FLEXION', 'PLANTAR_FLEXION', 'standing_calf_raise', 'calf_raise_turned_out')],

  // Calisthenics basics
  ['push_up', prof('BODYWEIGHT', 'HORIZONTAL_PUSH', 'FLAT_HORIZONTAL', 'db_bench_press', 'bench_press', 'weighted_push_up', 'diamond_push_up', 'pseudo_planche_push_up', 'knee_push_up')],
  ['diamond_push_up', prof('BODYWEIGHT', 'ELBOW_EXTENSION', 'SAGITTAL_TRICEP', 'dip', 'skull_crusher', 'tricep_pushdown', 'push_up', 'bent_leg_bench_dip')],
  ['pike_push_up', prof('BODYWEIGHT', 'VERTICAL_PUSH', 'OVERHEAD_VERTICAL', 'db_shoulder_press', 'overhead_press', 'kb_press', 'handstand_push_up', 'incline_db_press')],
  ['pull_up', prof('PULL_UP_BAR', 'VERTICAL_PULL', 'OVERHEAD_PRONATED', 'chin_up', 'lat_pulldown', 'weighted_pull_up', 'negative_pull_up', 'australian_pull_up', 'db_row')],
  ['chin_up', prof('PULL_UP_BAR', 'VERTICAL_PULL', 'OVERHEAD_SUPINATED', 'pull_up', 'weighted_chin_up', 'lat_pulldown', 'australian_chin_up', 'db_row')],
  ['negative_pull_up', prof('PULL_UP_BAR', 'VERTICAL_PULL', 'OVERHEAD_PRONATED', 'australian_pull_up', 'lat_pulldown', 'pull_up', 'chin_up')],
  ['dip', prof('DIP_BARS_OR_BENCH', 'DIP_PUSH', 'DECLINE_DOWNWARD', 'weighted_dip', 'push_up', 'diamond_push_up', 'db_bench_press', 'bench_press', 'bent_leg_bench_dip')],
  ['squat', prof('BODYWEIGHT', 'SQUAT_BILATERAL', 'KNEE_DOMINANT_BILATERAL', 'goblet_squat', 'back_squat', 'lunge', 'bulgarian_split_squat', 'leg_press')],
  ['lunge', prof('BODYWEIGHT', 'SQUAT_UNILATERAL', 'KNEE_DOMINANT_SPLIT', 'db_lunge', 'bulgarian_split_squat', 'squat', 'goblet_squat')],
  ['bulgarian_split_squat', prof('DIP_BARS_OR_BENCH', 'SQUAT_UNILATERAL', 'KNEE_DOMINANT_SPLIT', 'db_lunge', 'lunge', 'pistol_squat', 'goblet_squat', 'back_squat')],
  ['pistol_squat', prof('BODYWEIGHT', 'SQUAT_UNILATERAL', 'KNEE_DOMINANT_SPLIT', 'bulgarian_split_squat', 'db_lunge', 'lunge', 'goblet_squat')],
  ['nordic_curl', prof('BODYWEIGHT', 'KNEE_FLEXION', 'HAMSTRING_CURL', 'leg_curl', 'romanian_deadlift', 'hip_bridge', 'kb_swing')],
  ['plank', prof('BODYWEIGHT', 'CORE_ANTI_EXTENSION', 'ISOMETRIC_PLANK', 'hollow_hold', 'incline_hollow_plank', 'plank_shoulder_taps', 'l_sit')],
  ['hollow_hold', prof('BODYWEIGHT', 'CORE_ANTI_EXTENSION', 'ISOMETRIC_PLANK', 'plank', 'l_sit', 'incline_hollow_plank', 'hanging_knee_raise')],
  ['hanging_knee_raise', prof('PULL_UP_BAR', 'CORE_FLEXION_ROTATION', 'HANGING_HIP_FLEXION', 'hanging_leg_raise', 'crunch', 'hollow_hold', 'l_sit')],
  ['hanging_leg_raise', prof('PULL_UP_BAR', 'CORE_FLEXION_ROTATION', 'HANGING_HIP_FLEXION', 'hanging_knee_raise', 'l_sit', 'hollow_hold', 'cable_crunch')],

  // Calisthenics skills
  ['muscle_up', prof('PULL_UP_BAR', 'VERTICAL_PULL', 'OVERHEAD_PRONATED', 'weighted_pull_up', 'pull_up', 'dip')],
  ['l_sit', prof('DIP_BARS_OR_BENCH', 'CORE_ANTI_EXTENSION', 'ISOMETRIC_PLANK', 'hollow_hold', 'hanging_leg_raise', 'plank')],
  ['handstand_hold', prof('BODYWEIGHT', 'VERTICAL_PUSH', 'OVERHEAD_VERTICAL', 'pike_push_up', 'handstand_push_up', 'overhead_press', 'db_shoulder_press')],
  ['handstand_push_up', prof('BODYWEIGHT', 'VERTICAL_PUSH', 'OVERHEAD_VERTICAL', 'pike_push_up', 'overhead_press', 'db_shoulder_press', 'kb_press')],
  ['tuck_planche', prof('BODYWEIGHT', 'HORIZONTAL_PUSH', 'FLAT_HORIZONTAL', 'pseudo_planche_push_up', 'push_up', 'dip')],
  ['pseudo_planche_push_up', prof('BODYWEIGHT', 'HORIZONTAL_PUSH', 'FLAT_HORIZONTAL', 'push_up', 'weighted_push_up', 'tuck_planche', 'dip', 'db_bench_press')],
  ['tuck_front_lever', prof('PULL_UP_BAR', 'HORIZONTAL_PULL', 'MID_ROW_PRONATED', 'front_lever_row', 'pull_up', 'australian_pull_up', 'barbell_row')],
  ['front_lever_row', prof('PULL_UP_BAR', 'HORIZONTAL_PULL', 'MID_ROW_PRONATED', 'tuck_front_lever', 'barbell_row', 'db_row', 'australian_pull_up', 'pull_up')],
  ['back_lever_tuck', prof('PULL_UP_BAR', 'UPPER_BACK_PULL', 'HIGH_EXTERNAL_ROTATION', 'tuck_front_lever', 'dip', 'pseudo_planche_push_up')],

  // Weighted calisthenics
  ['weighted_pull_up', prof('PULL_UP_BAR', 'VERTICAL_PULL', 'OVERHEAD_PRONATED', 'pull_up', 'weighted_chin_up', 'lat_pulldown', 'chin_up')],
  ['weighted_chin_up', prof('PULL_UP_BAR', 'VERTICAL_PULL', 'OVERHEAD_SUPINATED', 'chin_up', 'weighted_pull_up', 'lat_pulldown', 'pull_up')],
  ['weighted_dip', prof('DIP_BARS_OR_BENCH', 'DIP_PUSH', 'DECLINE_DOWNWARD', 'dip', 'bench_press', 'db_bench_press', 'weighted_push_up')],
  ['weighted_push_up', prof('BODYWEIGHT', 'HORIZONTAL_PUSH', 'FLAT_HORIZONTAL', 'push_up', 'bench_press', 'db_bench_press', 'pseudo_planche_push_up')],

  // Gym (Barbell, Dumbbells, Cables & Machines)
  ['bench_press', prof('BARBELL_RACK', 'HORIZONTAL_PUSH', 'FLAT_HORIZONTAL', 'db_bench_press', 'weighted_push_up', 'push_up', 'dip', 'pseudo_planche_push_up')],
  ['incline_db_press', prof('DUMBBELLS', 'HORIZONTAL_PUSH', 'INCLINE_UPWARD', 'db_bench_press', 'bench_press', 'pike_push_up', 'push_up', 'incline_diamond_knee_push_up')],
  ['overhead_press', prof('BARBELL_RACK', 'VERTICAL_PUSH', 'OVERHEAD_VERTICAL', 'db_shoulder_press', 'kb_press', 'pike_push_up', 'handstand_push_up')],
  ['lateral_raise', prof('DUMBBELLS', 'LATERAL_RAISE', 'FRONTAL_ABDUCTION', 'pike_push_up', 'db_shoulder_press', 'face_pull', 'plank_shoulder_taps')],
  ['face_pull', prof('CABLES_MACHINES', 'UPPER_BACK_PULL', 'HIGH_EXTERNAL_ROTATION', 'scapular_australian_pull_up', 'australian_pull_up', 'db_row', 'barbell_row')],
  ['barbell_row', prof('BARBELL_RACK', 'HORIZONTAL_PULL', 'MID_ROW_PRONATED', 'db_row', 'kb_row', 'seated_cable_row', 'australian_pull_up', 'pull_up')],
  ['lat_pulldown', prof('CABLES_MACHINES', 'VERTICAL_PULL', 'OVERHEAD_PRONATED', 'pull_up', 'chin_up', 'negative_pull_up', 'australian_pull_up', 'db_row')],
  ['seated_cable_row', prof('CABLES_MACHINES', 'HORIZONTAL_PULL', 'MID_ROW_PRONATED', 'db_row', 'barbell_row', 'kb_row', 'australian_pull_up')],
  ['deadlift', prof('BARBELL_RACK', 'HIP_HINGE', 'HIP_HINGE_STANDING', 'romanian_deadlift', 'kb_deadlift', 'kb_swing', 'hip_thrust', 'hip_bridge')],
  ['romanian_deadlift', prof('BARBELL_RACK', 'HIP_HINGE', 'HIP_HINGE_STANDING', 'kb_deadlift', 'deadlift', 'kb_swing', 'hip_thrust', 'nordic_curl', 'hip_bridge')],
  ['back_squat', prof('BARBELL_RACK', 'SQUAT_BILATERAL', 'KNEE_DOMINANT_BILATERAL', 'goblet_squat', 'leg_press', 'bulgarian_split_squat', 'db_lunge', 'squat')],
  ['leg_press', prof('CABLES_MACHINES', 'SQUAT_BILATERAL', 'KNEE_DOMINANT_BILATERAL', 'back_squat', 'goblet_squat', 'bulgarian_split_squat', 'db_lunge', 'squat')],
  ['leg_extension', prof('CABLES_MACHINES', 'SQUAT_BILATERAL', 'KNEE_DOMINANT_BILATERAL', 'goblet_squat', 'bulgarian_split_squat', 'squat', 'lunge', 'pistol_squat')],
  ['leg_curl', prof('CABLES_MACHINES', 'KNEE_FLEXION', 'HAMSTRING_CURL', 'nordic_curl', 'romanian_deadlift', 'kb_deadlift', 'hip_bridge', 'kb_swing')],
  ['hip_thrust', prof('BARBELL_RACK', 'HIP_HINGE', 'HIP_EXTENSION_SUPINE', 'hip_bridge', 'kb_swing', 'romanian_deadlift', 'kb_deadlift')],
  ['standing_calf_raise', prof('CABLES_MACHINES', 'ANKLE_FLEXION', 'PLANTAR_FLEXION', 'calf_raise', 'calf_raise_turned_out')],
  ['bicep_curl', prof('DUMBBELLS', 'ELBOW_FLEXION', 'SAGITTAL_SUPINATED', 'hammer_curl', 'chin_up', 'australian_chin_up')],
  ['hammer_curl', prof('DUMBBELLS', 'ELBOW_FLEXION', 'SAGITTAL_NEUTRAL', 'bicep_curl', 'chin_up', 'australian_chin_up', 'pull_up')],
  ['tricep_pushdown', prof('CABLES_MACHINES', 'ELBOW_EXTENSION', 'SAGITTAL_TRICEP', 'skull_crusher', 'diamond_push_up', 'dip', 'bent_leg_bench_dip')],
  ['skull_crusher', prof('BARBELL_RACK', 'ELBOW_EXTENSION', 'SAGITTAL_TRICEP', 'tricep_pushdown', 'diamond_push_up', 'dip', 'bent_leg_bench_dip')],
  ['shrug', prof('DUMBBELLS', 'UPPER_BACK_PULL', 'HIGH_EXTERNAL_ROTATION', 'farmers_carry', 'face_pull', 'scapular_australian_pull_up')],
  ['cable_crunch', prof('CABLES_MACHINES', 'CORE_FLEXION_ROTATION', 'SPINAL_FLEXION', 'crunch', 'hanging_knee_raise', 'hanging_leg_raise', 'hollow_hold')],
  ['tibialis_raise', prof('BODYWEIGHT', 'ANKLE_FLEXION', 'DORSI_FLEXION', 'band_dorsiflexion', 'heel_walk', 'calf_raise')],
  ['heel_walk', prof('BODYWEIGHT', 'ANKLE_FLEXION', 'DORSI_FLEXION', 'tibialis_raise', 'band_dorsiflexion')],
  ['band_dorsiflexion', prof('BAND', 'ANKLE_FLEXION', 'DORSI_FLEXION', 'tibialis_raise', 'heel_walk')],

  // Adductors, obliques and forearms
  ['copenhagen_plank_short', prof('DIP_BARS_OR_BENCH', 'HIP_ADDUCTION', 'ISOMETRIC_PLANK', 'copenhagen_plank_long', 'side_plank', 'side_lying_hip_adduction')],
  ['copenhagen_plank_long', prof('DIP_BARS_OR_BENCH', 'HIP_ADDUCTION', 'ISOMETRIC_PLANK', 'copenhagen_plank_short', 'side_plank', 'side_lying_hip_adduction')],
  ['side_lying_hip_adduction', prof('BODYWEIGHT', 'HIP_ADDUCTION', 'FRONTAL_ADDUCTION', 'band_hip_adduction', 'cossack_squat', 'copenhagen_plank_short')],
  ['cossack_squat', prof('BODYWEIGHT', 'SQUAT_UNILATERAL', 'KNEE_DOMINANT_SPLIT', 'lunge', 'side_lying_hip_adduction', 'bulgarian_split_squat')],
  ['band_hip_adduction', prof('BAND', 'HIP_ADDUCTION', 'FRONTAL_ADDUCTION', 'side_lying_hip_adduction', 'cossack_squat')],
  ['side_plank', prof('BODYWEIGHT', 'CORE_ANTI_EXTENSION', 'ISOMETRIC_PLANK', 'plank', 'hollow_hold', 'copenhagen_plank_short')],
  ['bicycle_crunch', prof('BODYWEIGHT', 'CORE_FLEXION_ROTATION', 'ROTATIONAL', 'russian_twist', 'crunch', 'plank_shoulder_taps')],
  ['wrist_curl', prof('DUMBBELLS', 'ELBOW_FLEXION', 'SAGITTAL_SUPINATED', 'hammer_curl', 'farmers_carry', 'dead_hang')],

  // Kettlebells and dumbbells at home
  ['kb_swing', prof('KETTLEBELL', 'HIP_HINGE', 'HIP_HINGE_STANDING', 'kb_deadlift', 'romanian_deadlift', 'hip_bridge', 'hip_thrust')],
  ['goblet_squat', prof('DUMBBELLS', 'SQUAT_BILATERAL', 'KNEE_DOMINANT_BILATERAL', 'back_squat', 'bulgarian_split_squat', 'db_lunge', 'squat')],
  ['kb_deadlift', prof('KETTLEBELL', 'HIP_HINGE', 'HIP_HINGE_STANDING', 'romanian_deadlift', 'deadlift', 'kb_swing', 'hip_bridge')],
  ['kb_clean', prof('KETTLEBELL', 'HIP_HINGE', 'HIP_HINGE_STANDING', 'kb_swing', 'kb_snatch', 'db_shoulder_press')],
  ['kb_press', prof('KETTLEBELL', 'VERTICAL_PUSH', 'OVERHEAD_VERTICAL', 'db_shoulder_press', 'overhead_press', 'pike_push_up')],
  ['kb_snatch', prof('KETTLEBELL', 'VERTICAL_PUSH', 'OVERHEAD_VERTICAL', 'kb_clean', 'kb_swing', 'db_shoulder_press', 'burpee')],
  ['kb_row', prof('KETTLEBELL', 'HORIZONTAL_PULL', 'MID_ROW_PRONATED', 'db_row', 'barbell_row', 'australian_pull_up', 'seated_cable_row')],
  ['turkish_get_up', prof('KETTLEBELL', 'CARRY_CONDITIONING', 'LOCOMOTION', 'plank_shoulder_taps', 'db_shoulder_press', 'plank')],
  ['farmers_carry', prof('DUMBBELLS', 'CARRY_CONDITIONING', 'LOCOMOTION', 'dead_hang', 'shrug', 'plank')],
  ['db_bench_press', prof('DUMBBELLS', 'HORIZONTAL_PUSH', 'FLAT_HORIZONTAL', 'bench_press', 'push_up', 'weighted_push_up', 'incline_db_press', 'dip')],
  ['db_shoulder_press', prof('DUMBBELLS', 'VERTICAL_PUSH', 'OVERHEAD_VERTICAL', 'overhead_press', 'kb_press', 'pike_push_up', 'handstand_push_up')],
  ['db_row', prof('DUMBBELLS', 'HORIZONTAL_PULL', 'MID_ROW_PRONATED', 'barbell_row', 'kb_row', 'australian_pull_up', 'seated_cable_row', 'pull_up')],
  ['db_lunge', prof('DUMBBELLS', 'SQUAT_UNILATERAL', 'KNEE_DOMINANT_SPLIT', 'bulgarian_split_squat', 'lunge', 'goblet_squat', 'back_squat')],
  ['russian_twist', prof('BODYWEIGHT', 'CORE_FLEXION_ROTATION', 'ROTATIONAL', 'crunch', 'plank_shoulder_taps', 'hanging_knee_raise')],

  // More strength moves
  ['seated_leg_curl', prof('CABLES_MACHINES', 'KNEE_FLEXION', 'HAMSTRING_CURL', 'leg_curl', 'nordic_curl', 'romanian_deadlift')],
  ['single_leg_rdl', prof('DUMBBELLS', 'HIP_HINGE', 'HIP_HINGE_STANDING', 'romanian_deadlift', 'db_romanian_deadlift', 'kb_deadlift')],
  ['db_romanian_deadlift', prof('DUMBBELLS', 'HIP_HINGE', 'HIP_HINGE_STANDING', 'romanian_deadlift', 'kb_deadlift', 'single_leg_rdl', 'hip_bridge')],
  ['good_morning', prof('BARBELL_RACK', 'HIP_HINGE', 'HIP_HINGE_STANDING', 'romanian_deadlift', 'db_romanian_deadlift', 'back_extension')],
  ['band_leg_curl', prof('BAND', 'KNEE_FLEXION', 'HAMSTRING_CURL', 'leg_curl', 'nordic_curl', 'hip_bridge')],
  ['barbell_curl', prof('BARBELL_RACK', 'ELBOW_FLEXION', 'SAGITTAL_SUPINATED', 'bicep_curl', 'cable_curl', 'preacher_curl', 'chin_up')],
  ['incline_db_curl', prof('DUMBBELLS', 'ELBOW_FLEXION', 'SAGITTAL_SUPINATED', 'bicep_curl', 'concentration_curl', 'hammer_curl')],
  ['concentration_curl', prof('DUMBBELLS', 'ELBOW_FLEXION', 'SAGITTAL_SUPINATED', 'bicep_curl', 'incline_db_curl', 'preacher_curl')],
  ['preacher_curl', prof('CABLES_MACHINES', 'ELBOW_FLEXION', 'SAGITTAL_SUPINATED', 'cable_curl', 'barbell_curl', 'bicep_curl')],
  ['cable_curl', prof('CABLES_MACHINES', 'ELBOW_FLEXION', 'SAGITTAL_SUPINATED', 'bicep_curl', 'barbell_curl', 'band_curl')],
  ['band_curl', prof('BAND', 'ELBOW_FLEXION', 'SAGITTAL_SUPINATED', 'bicep_curl', 'cable_curl', 'australian_chin_up')],
  ['seated_calf_raise', prof('CABLES_MACHINES', 'ANKLE_FLEXION', 'PLANTAR_FLEXION', 'standing_calf_raise', 'db_calf_raise', 'calf_raise')],
  ['single_leg_calf_raise', prof('BODYWEIGHT', 'ANKLE_FLEXION', 'PLANTAR_FLEXION', 'calf_raise', 'db_calf_raise', 'standing_calf_raise')],
  ['db_calf_raise', prof('DUMBBELLS', 'ANKLE_FLEXION', 'PLANTAR_FLEXION', 'standing_calf_raise', 'calf_raise', 'single_leg_calf_raise')],
  ['back_extension', prof('DIP_BARS_OR_BENCH', 'HIP_HINGE', 'HIP_EXTENSION_SUPINE', 'superman_hold', 'hip_bridge', 'good_morning')],
  ['bird_dog', prof('BODYWEIGHT', 'CORE_ANTI_EXTENSION', 'ISOMETRIC_PLANK', 'superman_hold', 'plank', 'back_extension')],
  ['cable_pull_through', prof('CABLES_MACHINES', 'HIP_HINGE', 'HIP_HINGE_STANDING', 'kb_swing', 'romanian_deadlift', 'hip_thrust')],
  ['pallof_press', prof('CABLES_MACHINES', 'CORE_FLEXION_ROTATION', 'ROTATIONAL', 'cable_woodchop', 'side_plank', 'russian_twist')],
  ['cable_woodchop', prof('CABLES_MACHINES', 'CORE_FLEXION_ROTATION', 'ROTATIONAL', 'pallof_press', 'russian_twist', 'bicycle_crunch')],
  ['db_side_bend', prof('DUMBBELLS', 'CORE_FLEXION_ROTATION', 'ROTATIONAL', 'cable_woodchop', 'russian_twist', 'side_plank')],
  ['mountain_climber', prof('BODYWEIGHT', 'CARRY_CONDITIONING', 'LOCOMOTION', 'burpee', 'plank_shoulder_taps', 'jumping_jacks')],
  ['cable_fly', prof('CABLES_MACHINES', 'HORIZONTAL_PUSH', 'FLAT_HORIZONTAL', 'pec_deck', 'db_bench_press', 'push_up')],
  ['pec_deck', prof('CABLES_MACHINES', 'HORIZONTAL_PUSH', 'FLAT_HORIZONTAL', 'cable_fly', 'chest_press_machine', 'push_up')],
  ['chest_press_machine', prof('CABLES_MACHINES', 'HORIZONTAL_PUSH', 'FLAT_HORIZONTAL', 'db_bench_press', 'bench_press', 'push_up')],
  ['shoulder_press_machine', prof('CABLES_MACHINES', 'VERTICAL_PUSH', 'OVERHEAD_VERTICAL', 'db_shoulder_press', 'overhead_press', 'pike_push_up')],
  ['cable_lateral_raise', prof('CABLES_MACHINES', 'LATERAL_RAISE', 'FRONTAL_ABDUCTION', 'lateral_raise', 'face_pull', 'db_shoulder_press')],
  ['reverse_pec_deck', prof('CABLES_MACHINES', 'UPPER_BACK_PULL', 'HIGH_EXTERNAL_ROTATION', 'face_pull', 'band_pull_apart', 'seated_cable_row')],
  ['straight_arm_pulldown', prof('CABLES_MACHINES', 'VERTICAL_PULL', 'OVERHEAD_PRONATED', 'lat_pulldown', 'pull_up', 'negative_pull_up')],
  ['overhead_cable_triceps_extension', prof('CABLES_MACHINES', 'ELBOW_EXTENSION', 'SAGITTAL_TRICEP', 'tricep_pushdown', 'skull_crusher', 'diamond_push_up')],
  ['band_pull_apart', prof('BAND', 'UPPER_BACK_PULL', 'HIGH_EXTERNAL_ROTATION', 'face_pull', 'reverse_pec_deck', 'scapular_australian_pull_up')],
  ['band_row', prof('BAND', 'HORIZONTAL_PULL', 'MID_ROW_PRONATED', 'seated_cable_row', 'db_row', 'australian_pull_up')],
  ['band_chest_press', prof('BAND', 'HORIZONTAL_PUSH', 'FLAT_HORIZONTAL', 'push_up', 'db_bench_press', 'chest_press_machine')],
  ['kb_sumo_deadlift', prof('KETTLEBELL', 'HIP_HINGE', 'HIP_HINGE_STANDING', 'kb_deadlift', 'kb_swing', 'romanian_deadlift')],
  ['kb_halo', prof('KETTLEBELL', 'VERTICAL_PUSH', 'OVERHEAD_VERTICAL', 'kb_press', 'db_shoulder_press', 'face_pull')],
  ['kb_windmill', prof('KETTLEBELL', 'CORE_FLEXION_ROTATION', 'ROTATIONAL', 'turkish_get_up', 'russian_twist', 'side_plank')],
  ['smith_squat', prof('CABLES_MACHINES', 'SQUAT_BILATERAL', 'KNEE_DOMINANT_BILATERAL', 'back_squat', 'leg_press', 'hack_squat', 'goblet_squat')],
  ['smith_bench_press', prof('CABLES_MACHINES', 'HORIZONTAL_PUSH', 'FLAT_HORIZONTAL', 'bench_press', 'chest_press_machine', 'db_bench_press')],
  ['smith_incline_press', prof('CABLES_MACHINES', 'HORIZONTAL_PUSH', 'INCLINE_UPWARD', 'incline_bench_press', 'incline_db_press', 'smith_bench_press')],
  ['smith_overhead_press', prof('CABLES_MACHINES', 'VERTICAL_PUSH', 'OVERHEAD_VERTICAL', 'overhead_press', 'shoulder_press_machine', 'db_shoulder_press')],
  ['smith_row', prof('CABLES_MACHINES', 'HORIZONTAL_PULL', 'MID_ROW_PRONATED', 'barbell_row', 'seated_cable_row', 'chest_supported_row')],
  ['smith_romanian_deadlift', prof('CABLES_MACHINES', 'HIP_HINGE', 'HIP_HINGE_STANDING', 'romanian_deadlift', 'db_romanian_deadlift', 'cable_pull_through')],
  ['scapular_pull_up', prof('PULL_UP_BAR', 'VERTICAL_PULL', 'OVERHEAD_PRONATED', 'dead_hang', 'negative_pull_up', 'band_assisted_pull_up')],
  ['flexed_arm_hang', prof('PULL_UP_BAR', 'VERTICAL_PULL', 'OVERHEAD_SUPINATED', 'negative_pull_up', 'dead_hang', 'band_assisted_pull_up')],
  ['band_assisted_pull_up', prof('PULL_UP_BAR', 'VERTICAL_PULL', 'OVERHEAD_PRONATED', 'negative_pull_up', 'pull_up', 'lat_pulldown')],
  ['neutral_grip_pull_up', prof('PULL_UP_BAR', 'VERTICAL_PULL', 'OVERHEAD_SUPINATED', 'chin_up', 'pull_up', 'lat_pulldown')],
  ['wide_grip_pull_up', prof('PULL_UP_BAR', 'VERTICAL_PULL', 'OVERHEAD_PRONATED', 'pull_up', 'lat_pulldown', 'neutral_grip_pull_up')],
  ['archer_pull_up', prof('PULL_UP_BAR', 'VERTICAL_PULL', 'OVERHEAD_PRONATED', 'weighted_pull_up', 'wide_grip_pull_up', 'pull_up')],
  ['l_sit_pull_up', prof('PULL_UP_BAR', 'VERTICAL_PULL', 'OVERHEAD_PRONATED', 'pull_up', 'hanging_leg_raise', 'weighted_pull_up')],
  ['incline_push_up', prof('BODYWEIGHT', 'HORIZONTAL_PUSH', 'DECLINE_DOWNWARD', 'knee_push_up', 'push_up', 'wide_knee_push_up')],
  ['decline_push_up', prof('BODYWEIGHT', 'HORIZONTAL_PUSH', 'INCLINE_UPWARD', 'push_up', 'pike_push_up', 'weighted_push_up')],
  ['archer_push_up', prof('BODYWEIGHT', 'HORIZONTAL_PUSH', 'FLAT_HORIZONTAL', 'weighted_push_up', 'pseudo_planche_push_up', 'decline_push_up')],
  ['single_arm_cable_row', prof('CABLES_MACHINES', 'HORIZONTAL_PULL', 'MID_ROW_SUPINATED', 'seated_cable_row', 'db_row', 'chest_supported_row')],
  ['cable_rear_delt_fly', prof('CABLES_MACHINES', 'UPPER_BACK_PULL', 'HIGH_EXTERNAL_ROTATION', 'reverse_pec_deck', 'face_pull', 'rear_delt_db_fly')],
  ['cable_crossover_high_to_low', prof('CABLES_MACHINES', 'HORIZONTAL_PUSH', 'DECLINE_DOWNWARD', 'cable_fly', 'pec_deck', 'db_fly')],
  ['cable_crossover_low_to_high', prof('CABLES_MACHINES', 'HORIZONTAL_PUSH', 'INCLINE_UPWARD', 'cable_fly', 'incline_db_press', 'db_fly')],
  ['cable_glute_kickback', prof('CABLES_MACHINES', 'HIP_HINGE', 'HIP_HINGE_STANDING', 'hip_thrust', 'cable_pull_through', 'single_leg_hip_thrust')],
  ['step_up', prof('BODYWEIGHT', 'SQUAT_UNILATERAL', 'KNEE_DOMINANT_SPLIT', 'lunge', 'reverse_lunge', 'bulgarian_split_squat')],
  ['reverse_lunge', prof('BODYWEIGHT', 'SQUAT_UNILATERAL', 'KNEE_DOMINANT_SPLIT', 'lunge', 'step_up', 'bulgarian_split_squat')],
  ['single_leg_hip_thrust', prof('BODYWEIGHT', 'HIP_HINGE', 'HIP_EXTENSION_SUPINE', 'hip_thrust', 'hip_bridge', 'cable_glute_kickback')],
  ['single_arm_db_press', prof('DUMBBELLS', 'VERTICAL_PUSH', 'OVERHEAD_VERTICAL', 'db_shoulder_press', 'kb_press', 'overhead_press')],
  ['single_arm_lat_pulldown', prof('CABLES_MACHINES', 'VERTICAL_PULL', 'OVERHEAD_SUPINATED', 'lat_pulldown', 'straight_arm_pulldown', 'neutral_grip_pull_up')],
  ['incline_bench_press', prof('BARBELL_RACK', 'HORIZONTAL_PUSH', 'INCLINE_UPWARD', 'incline_db_press', 'smith_incline_press', 'bench_press')],
  ['front_squat', prof('BARBELL_RACK', 'SQUAT_BILATERAL', 'KNEE_DOMINANT_BILATERAL', 'back_squat', 'goblet_squat', 'hack_squat')],
  ['hack_squat', prof('CABLES_MACHINES', 'SQUAT_BILATERAL', 'KNEE_DOMINANT_BILATERAL', 'leg_press', 'back_squat', 'smith_squat')],
  ['chest_supported_row', prof('DUMBBELLS', 'HORIZONTAL_PULL', 'MID_ROW_PRONATED', 'db_row', 'seated_cable_row', 'barbell_row')],
  ['db_fly', prof('DUMBBELLS', 'HORIZONTAL_PUSH', 'FLAT_HORIZONTAL', 'cable_fly', 'pec_deck', 'cable_crossover_high_to_low')],
  ['rear_delt_db_fly', prof('DUMBBELLS', 'UPPER_BACK_PULL', 'HIGH_EXTERNAL_ROTATION', 'reverse_pec_deck', 'cable_rear_delt_fly', 'band_pull_apart', 'face_pull')],
  ['quad_stretch', prof('BODYWEIGHT', 'MOBILITY', 'STRETCH', 'hip_flexor_stretch', 'shin_stretch')],
  ['hamstring_stretch', prof('BODYWEIGHT', 'MOBILITY', 'STRETCH', 'calf_stretch', 'figure_four_stretch')],
  ['hip_flexor_stretch', prof('BODYWEIGHT', 'MOBILITY', 'STRETCH', 'quad_stretch', 'pigeon_stretch')],
  ['pigeon_stretch', prof('BODYWEIGHT', 'MOBILITY', 'STRETCH', 'figure_four_stretch', 'hip_flexor_stretch')],
  ['figure_four_stretch', prof('BODYWEIGHT', 'MOBILITY', 'STRETCH', 'pigeon_stretch', 'hamstring_stretch')],
  ['calf_stretch', prof('BODYWEIGHT', 'MOBILITY', 'STRETCH', 'hamstring_stretch', 'shin_stretch')],
  ['childs_pose', prof('BODYWEIGHT', 'MOBILITY', 'STRETCH', 'thread_the_needle', 'lat_stretch')],
  ['chest_doorway_stretch', prof('BODYWEIGHT', 'MOBILITY', 'STRETCH', 'biceps_wall_stretch', 'cross_body_shoulder_stretch')],
  ['cross_body_shoulder_stretch', prof('BODYWEIGHT', 'MOBILITY', 'STRETCH', 'chest_doorway_stretch', 'triceps_overhead_stretch')],
  ['triceps_overhead_stretch', prof('BODYWEIGHT', 'MOBILITY', 'STRETCH', 'cross_body_shoulder_stretch', 'biceps_wall_stretch')],
  ['biceps_wall_stretch', prof('BODYWEIGHT', 'MOBILITY', 'STRETCH', 'chest_doorway_stretch', 'wrist_flexor_stretch')],
  ['neck_side_stretch', prof('BODYWEIGHT', 'MOBILITY', 'STRETCH', 'cross_body_shoulder_stretch', 'thread_the_needle')],
  ['thread_the_needle', prof('BODYWEIGHT', 'MOBILITY', 'STRETCH', 'childs_pose', 'seated_spinal_twist')],
  ['butterfly_stretch', prof('BODYWEIGHT', 'MOBILITY', 'STRETCH', 'frog_stretch', 'figure_four_stretch')],
  ['frog_stretch', prof('BODYWEIGHT', 'MOBILITY', 'STRETCH', 'butterfly_stretch', 'pigeon_stretch')],
  ['wrist_flexor_stretch', prof('BODYWEIGHT', 'MOBILITY', 'STRETCH', 'biceps_wall_stretch', 'triceps_overhead_stretch')],
  ['seated_spinal_twist', prof('BODYWEIGHT', 'MOBILITY', 'STRETCH', 'thread_the_needle', 'figure_four_stretch')],
  ['lat_stretch', prof('BODYWEIGHT', 'MOBILITY', 'STRETCH', 'childs_pose', 'triceps_overhead_stretch')],
  ['shin_stretch', prof('BODYWEIGHT', 'MOBILITY', 'STRETCH', 'calf_stretch', 'quad_stretch')],
])

export function profileFor(ex: ExerciseEntity): BiomechanicalProfile {
  const known = CATALOG_PROFILES.get(ex.id)
  if (known) return known
  const lower = (ex.id + ' ' + ex.name).toLowerCase()
  const has = (...parts: string[]) => parts.some((p) => lower.includes(p))
  const eq: Equipment =
    has('cable', 'machine', 'pulldown', 'leg_press', 'pushdown') ? 'CABLES_MACHINES'
    : has('barbell', 'bench_press', 'back_squat', 'deadlift') ? 'BARBELL_RACK'
    : has('kb_', 'kettlebell') ? 'KETTLEBELL'
    : has('db_', 'dumbbell', 'curl', 'lateral') ? 'DUMBBELLS'
    : has('pull_up', 'chin_up', 'hang', 'lever', 'muscle_up') ? 'PULL_UP_BAR'
    : has('band', 'resistance') ? 'BAND'
    : has('dip', 'australian', 'row') ? 'DIP_BARS_OR_BENCH'
    : ex.kind === 'WEIGHTED' ? 'DUMBBELLS'
    : 'BODYWEIGHT'
  const main = ex.primary[0]
  const pattern: MovementPattern =
    has('pike', 'handstand', 'overhead', 'shoulder_press') ? 'VERTICAL_PUSH'
    : has('incline') && has('push') ? 'HORIZONTAL_PUSH'
    : has('dip') ? 'DIP_PUSH'
    : has('push_up', 'bench', 'planche') || main === 'CHEST' ? 'HORIZONTAL_PUSH'
    : has('pull_up', 'chin_up', 'pulldown') ? 'VERTICAL_PULL'
    : has('row', 'australian', 'lever') || main === 'UPPER_BACK' ? 'HORIZONTAL_PULL'
    : has('split', 'lunge', 'pistol') ? 'SQUAT_UNILATERAL'
    : has('squat', 'leg_press') || main === 'QUADS' ? 'SQUAT_BILATERAL'
    : has('curl') && main === 'HAMSTRINGS' ? 'KNEE_FLEXION'
    : has('deadlift', 'bridge', 'thrust', 'swing') || main === 'GLUTES' || main === 'HAMSTRINGS' || main === 'LOWER_BACK' ? 'HIP_HINGE'
    : main === 'BICEPS' ? 'ELBOW_FLEXION'
    : main === 'TRICEPS' ? 'ELBOW_EXTENSION'
    : ex.kind === 'HOLD' && (main === 'ABS' || main === 'OBLIQUES') ? 'CORE_ANTI_EXTENSION'
    : main === 'ABS' || main === 'OBLIQUES' ? 'CORE_FLEXION_ROTATION'
    : main === 'ADDUCTORS' ? 'HIP_ADDUCTION'
    : main === 'CALVES' || main === 'TIBIALIS' ? 'ANKLE_FLEXION'
    : main === 'SHOULDERS' ? 'VERTICAL_PUSH'
    : 'CARRY_CONDITIONING'
  const angle: Record<MovementPattern, JointAngle> = {
    HORIZONTAL_PUSH: has('incline') ? 'INCLINE_UPWARD' : 'FLAT_HORIZONTAL',
    DIP_PUSH: 'DECLINE_DOWNWARD',
    VERTICAL_PUSH: 'OVERHEAD_VERTICAL',
    LATERAL_RAISE: 'FRONTAL_ABDUCTION',
    VERTICAL_PULL: has('chin') ? 'OVERHEAD_SUPINATED' : 'OVERHEAD_PRONATED',
    HORIZONTAL_PULL: has('chin') ? 'MID_ROW_SUPINATED' : 'MID_ROW_PRONATED',
    UPPER_BACK_PULL: 'HIGH_EXTERNAL_ROTATION',
    SQUAT_BILATERAL: 'KNEE_DOMINANT_BILATERAL',
    SQUAT_UNILATERAL: 'KNEE_DOMINANT_SPLIT',
    HIP_HINGE: has('bridge', 'thrust') ? 'HIP_EXTENSION_SUPINE' : 'HIP_HINGE_STANDING',
    KNEE_FLEXION: 'HAMSTRING_CURL',
    ELBOW_FLEXION: 'SAGITTAL_SUPINATED',
    ELBOW_EXTENSION: 'SAGITTAL_TRICEP',
    CORE_ANTI_EXTENSION: 'ISOMETRIC_PLANK',
    CORE_FLEXION_ROTATION: 'SPINAL_FLEXION',
    ANKLE_FLEXION: 'PLANTAR_FLEXION',
    CARRY_CONDITIONING: 'LOCOMOTION',
    HIP_ADDUCTION: 'FRONTAL_ADDUCTION',
    MOBILITY: 'STRETCH',
  }
  return { equipment: eq, pattern, angle: angle[pattern], preferredSwaps: [] }
}

const SYNERGISTIC: ReadonlyArray<readonly [MovementPattern, MovementPattern]> = [
  ['HORIZONTAL_PUSH', 'DIP_PUSH'],
  ['DIP_PUSH', 'ELBOW_EXTENSION'],
  ['VERTICAL_PUSH', 'LATERAL_RAISE'],
  ['VERTICAL_PULL', 'HORIZONTAL_PULL'],
  ['HORIZONTAL_PULL', 'UPPER_BACK_PULL'],
  ['SQUAT_BILATERAL', 'SQUAT_UNILATERAL'],
  ['HIP_HINGE', 'KNEE_FLEXION'],
  ['CORE_ANTI_EXTENSION', 'CORE_FLEXION_ROTATION'],
]

const synergistic = (a: MovementPattern, b: MovementPattern) =>
  SYNERGISTIC.some(([x, y]) => (x === a && y === b) || (x === b && y === a))

function jaccard<T>(a: readonly T[], b: readonly T[]): number {
  if (a.length === 0 && b.length === 0) return 0
  const sa = new Set(a)
  const sb = new Set(b)
  const inter = [...sa].filter((x) => sb.has(x)).length
  const union = new Set([...sa, ...sb]).size
  return union === 0 ? 0 : f(inter / union)
}

/**
 * Library exercises ranked as substitutes for [source]: same movement pattern 45, related pattern 18, same angle 20,
 * primary-muscle overlap up to 20, all-muscle overlap up to 10, same kind 5, curated swap 15 down to 3.
 * Ones the person can do with a score of 45+ come first.
 */
export function rankSubstitutes(
  source: ExerciseEntity,
  library: readonly ExerciseEntity[],
  userEquipment: ReadonlySet<Equipment> = allEquipment(),
): SwapCandidate[] {
  const src = profileFor(source)
  const effective = new Set(userEquipment.size === 0 ? EQUIPMENT : userEquipment)
  effective.add('BODYWEIGHT')
  const sourceIsStretch = STRETCH_IDS.has(source.id)
  const ranked = library
    // A stretch is only ever swapped for another stretch, and a lift never for a stretch.
    .filter((c) => c.id !== source.id && STRETCH_IDS.has(c.id) === sourceIsStretch)
    .map((candidate): SwapCandidate => {
      const cand = profileFor(candidate)
      const patternPts = cand.pattern === src.pattern ? 45 : synergistic(src.pattern, cand.pattern) ? 18 : 0
      const anglePts = cand.angle === src.angle ? 20 : 0
      const primaryPts = Math.trunc(f(20 * jaccard(source.primary, candidate.primary)))
      const secondaryPts = Math.trunc(f(10 * jaccard([...source.primary, ...source.secondary], [...candidate.primary, ...candidate.secondary])))
      const kindPts = source.kind === candidate.kind ? 5 : 0
      const curatedIdx = src.preferredSwaps.indexOf(candidate.id)
      const curatedBonus = curatedIdx >= 0 ? Math.max(3, 15 - curatedIdx * 2) : 0
      return {
        exercise: candidate,
        profile: cand,
        score: patternPts + anglePts + primaryPts + secondaryPts + kindPts + curatedBonus,
        matchesUserEquipment: effective.has(cand.equipment),
      }
    })
  const good = (c: SwapCandidate) => (c.matchesUserEquipment && c.score >= 45 ? 1 : 0)
  return ranked.sort((a, b) =>
    good(b) - good(a) || b.score - a.score || (a.exercise.name < b.exercise.name ? -1 : a.exercise.name > b.exercise.name ? 1 : 0))
}

/** A plan item moved onto [newExercise]; a load never carries across exercises. */
export function translatePlanItem(oldItem: PlanItemEntity, newExercise: ExerciseEntity): PlanItemEntity {
  switch (newExercise.kind) {
    case 'HOLD':
      return { ...oldItem, exerciseId: newExercise.id, seconds: oldItem.seconds ?? 30, repsMin: null, repsMax: null, weightKg: null }
    case 'REPS': {
      const baseMin = oldItem.repsMin ?? 10
      const baseMax = oldItem.repsMax ?? Math.max(baseMin, 12)
      const loaded = oldItem.weightKg != null && oldItem.weightKg > 0
      return {
        ...oldItem,
        exerciseId: newExercise.id,
        repsMin: loaded ? Math.max(8, baseMin + 2) : baseMin,
        repsMax: loaded ? Math.max(12, baseMax + 2) : baseMax,
        seconds: null,
        weightKg: null,
      }
    }
    case 'WEIGHTED':
      return {
        ...oldItem,
        exerciseId: newExercise.id,
        repsMin: oldItem.repsMin ?? 8,
        repsMax: oldItem.repsMax ?? 12,
        seconds: null,
        // Never carry a load across exercises (100 kg back squat is not a 100 kg goblet squat): the first
        // session starts from the new exercise's own history, like translateDraftItem.
        weightKg: 0,
        restSeconds: Math.max(oldItem.restSeconds, 90),
      }
  }
}

/**
 * Swaps every exercise in [draft] that needs equipment the person lacks for the best-ranked one they can do,
 * avoiding repeats within a day. Returns the adapted draft and the number of swaps.
 * [dropUnfit] leaves out moves that need missing equipment and have no fair swap, instead of keeping them.
 */
export function adaptPlanToEquipment<D extends { days: PlanDraftDay[] }>(
  draft: D,
  library: ReadonlyMap<string, ExerciseEntity>,
  userEquipment: ReadonlySet<Equipment>,
  dropUnfit = false,
): [D, number] {
  if (userEquipment.size === 0) return [draft, 0]
  const allowed = new Set(userEquipment)
  allowed.add('BODYWEIGHT')
  if (EQUIPMENT.every((e) => allowed.has(e))) return [draft, 0]
  const allExercises = [...library.values()]
  let swaps = 0
  const days = draft.days.map((day) => {
    if (day.isRest || day.items.length === 0) return day
    const usedInDay = new Set(day.items.map((it) => it.exerciseId))
    const items = day.items.flatMap((item) => {
      const ex = library.get(item.exerciseId) ?? fallbackExercise(item)
      if (allowed.has(profileFor(ex).equipment)) return [item]
      // Only a real match (at least a related pattern or shared muscles): with none, keep the original rather than
      // turning a curl into a push-up or a burpee (audit 2026-10-24).
      const doable = rankSubstitutes(ex, allExercises, allowed).filter((c) => c.matchesUserEquipment && c.score >= MIN_SWAP_SCORE)
      const replacement = doable.find((c) => !usedInDay.has(c.exercise.id)) ?? doable[0]
      if (!replacement) {
        if (!dropUnfit) return [item]
        swaps++
        return []
      }
      swaps++
      usedInDay.add(replacement.exercise.id)
      return [translateDraftItem(item, replacement.exercise)]
    })
    return { ...day, items }
  })
  return [{ ...draft, days }, swaps]
}

function fallbackExercise(item: PlanDraftItem): ExerciseEntity {
  const kind: ExerciseKind = item.kind != null && isExerciseKind(item.kind)
    ? item.kind
    : item.seconds != null ? 'HOLD' : item.weightKg != null ? 'WEIGHTED' : 'REPS'
  return { id: item.exerciseId, name: item.name ?? item.exerciseId, kind, primary: item.muscles.filter(isMuscle).slice(0, 2), secondary: [] }
}

/** A draft item moved onto [target], dropping its custom-exercise fields and any load. */
export function translateDraftItem(item: PlanDraftItem, target: ExerciseEntity): PlanDraftItem {
  const base = { ...item, exerciseId: target.id, name: null, kind: null, muscles: [] }
  switch (target.kind) {
    case 'HOLD':
      return { ...base, seconds: item.seconds ?? 30, repsMin: null, repsMax: null, weightKg: null }
    case 'REPS':
      return { ...base, repsMin: item.repsMin ?? 10, repsMax: item.repsMax ?? 12, seconds: null, weightKg: null }
    case 'WEIGHTED':
      return { ...base, repsMin: item.repsMin ?? 8, repsMax: item.repsMax ?? 12, seconds: null, weightKg: null, restSeconds: Math.max(item.restSeconds, 90) }
  }
}
