// Offline plan generator: picks catalog exercises per movement slot, sets volume from experience, goal and focus, then
// trims each day to the time available. Pure and deterministic (ties break by score, then id); only catalog ids are
// produced so applying the draft never creates custom exercises. Android: PlanBuilder.kt.
import { STRETCH_IDS } from '../data/seed/exercise-catalog'
import type { ExerciseEntity, PlanDraft, PlanDraftDay, PlanDraftItem } from './entities'
import {
  EQUIPMENT_META, expandLegacyEquipment, profileFits, profileFor, SELECTABLE_EQUIPMENT,
  type BiomechanicalProfile, type Equipment, type MovementPattern,
} from './exercise-swap-engine'
import { tierOf as rawTierOf, TIERS, type Tier } from './exercise-tiers'
import { blocked, INJURIES, isInjury, type Injury } from './injury'
import { MUSCLES, type Muscle } from './muscle'
import {
  BELOW_MEV_MUSCLES, credits, DAY_MUSCLE_LIMIT, landmarkFor, OVERLAP_SETS, pushPull, pushPullBroken,
  SHORT_PLAN_MAX_MINUTES,
} from './routine-auditor'
import { isExtraBlock } from './pr-picker'
import { WARM_UP_BLOCK } from './workout-score'

export { blocked, INJURIES, isInjury, type Injury }

export const TRAIN_GOALS = ['STRENGTH', 'MUSCLE', 'FAT_LOSS', 'ENDURANCE', 'GENERAL'] as const
export type TrainGoal = (typeof TRAIN_GOALS)[number]

export const EXPERIENCES = ['NEW', 'SOME', 'SEASONED'] as const
export type Experience = (typeof EXPERIENCES)[number]

export const CARDIOS = ['NONE', 'FINISHER', 'OWN_DAY'] as const
export type Cardio = (typeof CARDIOS)[number]

export const REP_PREFS = ['AUTO', 'LOW', 'MID', 'HIGH'] as const
export type RepPref = (typeof REP_PREFS)[number]

export const REST_PREFS = ['AUTO', 'SHORT', 'LONG'] as const
export type RestPref = (typeof REST_PREFS)[number]

export const WORKOUT_STYLES = ['AUTO', 'STRAIGHT', 'SUPERSET', 'CIRCUIT'] as const
export type WorkoutStyle = (typeof WORKOUT_STYLES)[number]

export const DAY_FOCUSES = [
  'FULL_BODY', 'UPPER', 'LOWER', 'PUSH', 'PULL', 'LEGS', 'PUSH_LEGS', 'PULL_HINGE', 'CONDITIONING',
] as const
export type DayFocus = (typeof DAY_FOCUSES)[number]

/** Why the builder did something the person might wonder about. The UI turns these into text (EN and AR). */
export const WHY_TAGS = [
  'SKIPPED_FOR_INJURY', 'NO_EQUIPMENT', 'FOCUS_BOOST', 'TIME_TRIMMED',
  'TIER_FROM_MAX', 'HOLD_FALLBACK', 'CARDIO_ADDED', 'LIGHT_DAY',
] as const
export type WhyTag = (typeof WHY_TAGS)[number]

export type PlanInput = {
  goal: TrainGoal
  /** Training weekdays, 1 = Monday .. 7 = Sunday. */
  days: ReadonlySet<number> | readonly number[]
  minutes: number
  equipment: ReadonlySet<Equipment> | readonly Equipment[]
  experience?: Experience
  injuries?: ReadonlySet<Injury> | readonly Injury[]
  avoidIds?: ReadonlySet<string> | readonly string[]
  pushUpMax?: number | null
  pullUpMax?: number | null
  /** Muscles to give extra volume (max 3 are used). */
  focus?: ReadonlySet<Muscle> | readonly Muscle[]
  minutesByDay?: ReadonlyMap<number, number> | Record<number, number>
  cardio?: Cardio
  warmUp?: boolean
  coolDown?: boolean
  holdsOk?: boolean
  reps?: RepPref
  rest?: RestPref
  style?: WorkoutStyle
  /** 0 is the default build; each step picks the next among equally good exercises ('Show a different version'). */
  offset?: number
}

/** `days` and `why` are keyed by the training weekdays; the UI writes titles and notes from these. */
export type BuiltPlan = {
  draft: PlanDraft
  days: Map<number, DayFocus>
  why: Map<number, WhyTag[]>
}

export const COOL_DOWN_BLOCK = 'Cool-down'
export const FINISHER_BLOCK = 'Finisher'
export const CIRCUIT_BLOCKS = ['Circuit A', 'Circuit B', 'Circuit C', 'Circuit D'] as const
export const SUPERSET_POST_ROUND_SHORT = 40
export const CIRCUIT_POST_ROUND_SHORT = 30
export const CIRCUIT_POST_ROUND = 60
export const NO_PAIR_IDS: ReadonlySet<string> = new Set([
  'back_squat', 'front_squat', 'deadlift', 'sumo_deadlift',
  'romanian_deadlift', 'barbell_row', 'good_morning', 'overhead_press',
])
export const PROGRESSION_NOTE =
  'Progression: when you hit the top of the range on every set, add load or reps. Every 4th week, halve the sets.'

/** Pairwise safety check for supersets and circuits: distinct, non-spinal, no shared primary or primary/secondary overlap. */
export function safePair(a: ExerciseEntity, b: ExerciseEntity): boolean {
  return (
    a.id !== b.id &&
    !NO_PAIR_IDS.has(a.id) &&
    !NO_PAIR_IDS.has(b.id) &&
    !a.primary.some((m) => b.primary.includes(m) || b.secondary.includes(m)) &&
    !b.primary.some((m) => a.secondary.includes(m))
  )
}

export const COMPOUND_PATTERNS = new Set<MovementPattern>([
  'HORIZONTAL_PUSH', 'DIP_PUSH', 'VERTICAL_PUSH', 'VERTICAL_PULL', 'HORIZONTAL_PULL',
  'SQUAT_BILATERAL', 'SQUAT_UNILATERAL', 'HIP_HINGE',
])
export const isCompound = (pattern: MovementPattern): boolean => COMPOUND_PATTERNS.has(pattern)
export const isCore = (pattern: MovementPattern): boolean =>
  pattern === 'CORE_ANTI_EXTENSION' || pattern === 'CORE_FLEXION_ROTATION'

/** Machines that sit in a compound movement group but work one joint (leg extension). */
const ISOLATION_IDS = new Set(['leg_extension'])

/** A fly (chest fly, crossover, pec deck) is a one-joint chest move even though it shares the press movement group. */
export function isFly(ex: ExerciseEntity): boolean {
  const pattern = profileFor(ex).pattern
  if (pattern !== 'HORIZONTAL_PUSH' && pattern !== 'DIP_PUSH') return false
  const id = ex.id
  return id.includes('_fly') || id.startsWith('fly') || id.includes('flye') || id.includes('crossover') || id.includes('pec_deck')
}

/** Multi-joint lift: a compound movement group, not a fly and not a one-joint machine. Shared by the auditor and the baseline. */
export function isCompoundExercise(ex: ExerciseEntity): boolean {
  return isCompound(profileFor(ex).pattern) && !isFly(ex) && !ISOLATION_IDS.has(ex.id)
}

/** The muscles a movement group works; a catalog row whose main muscles miss them is filed in the wrong group. */
const PATTERN_MUSCLES: Partial<Record<MovementPattern, ReadonlySet<Muscle>>> = {
  HORIZONTAL_PUSH: new Set(['CHEST', 'TRICEPS', 'SHOULDERS']),
  DIP_PUSH: new Set(['CHEST', 'TRICEPS', 'SHOULDERS']),
  VERTICAL_PUSH: new Set(['SHOULDERS', 'TRICEPS']),
  VERTICAL_PULL: new Set(['UPPER_BACK', 'BICEPS']),
  HORIZONTAL_PULL: new Set(['UPPER_BACK', 'BICEPS', 'TRAPS', 'SHOULDERS']),
  UPPER_BACK_PULL: new Set(['UPPER_BACK', 'TRAPS', 'SHOULDERS']),
  SQUAT_BILATERAL: new Set(['QUADS', 'GLUTES']),
  SQUAT_UNILATERAL: new Set(['QUADS', 'GLUTES']),
  HIP_HINGE: new Set(['HAMSTRINGS', 'GLUTES', 'LOWER_BACK']),
  KNEE_FLEXION: new Set(['HAMSTRINGS']),
  LATERAL_RAISE: new Set(['SHOULDERS']),
  CORE_ANTI_EXTENSION: new Set(['ABS', 'OBLIQUES', 'LOWER_BACK']),
  CORE_FLEXION_ROTATION: new Set(['ABS', 'OBLIQUES']),
  ELBOW_FLEXION: new Set(['BICEPS', 'FOREARMS']),
  ELBOW_EXTENSION: new Set(['TRICEPS']),
  ANKLE_FLEXION: new Set(['CALVES', 'TIBIALIS']),
}

/** False for a catalog row whose main muscles do not belong to its movement group (a leg jump filed as a chest press). */
export function fitsItsMovement(ex: ExerciseEntity): boolean {
  const expected = PATTERN_MUSCLES[profileFor(ex).pattern]
  if (!expected) return true
  return ex.primary.some((m) => expected.has(m))
}

/** Well-known exercises that win a tie on score, so the builder picks a bench press before an odd variation. */
export const STAPLE_IDS: ReadonlySet<string> = new Set([
  'bench_press', 'db_bench_press', 'incline_db_press', 'push_up', 'incline_push_up', 'knee_push_up', 'chest_press_machine',
  'chest_dip', 'bench_dip', 'overhead_press', 'db_shoulder_press', 'pike_push_up',
  'pull_up', 'chin_up', 'lat_pulldown', 'negative_pull_up', 'band_assisted_pull_up',
  'barbell_row', 'db_row', 'seated_cable_row', 'inverted_row', 'australian_pull_up',
  'face_pull', 'band_pull_apart', 'rear_delt_db_fly',
  'back_squat', 'front_squat', 'goblet_squat', 'db_squat', 'leg_press',
  'bulgarian_split_squat', 'lunge', 'reverse_lunge', 'step_up',
  'deadlift', 'romanian_deadlift', 'db_romanian_deadlift', 'hip_thrust', 'hip_bridge',
  'leg_curl', 'seated_leg_curl', 'nordic_curl',
  'plank', 'side_plank', 'dead_bug', 'hollow_hold', 'crunch', 'hanging_knee_raise', 'ab_roller', 'bird_dog',
  'barbell_curl', 'hammer_curl', 'incline_db_curl', 'cable_curl', 'band_curl',
  'skull_crusher', 'overhead_db_tricep_ext', 'overhead_cable_triceps_extension', 'band_overhead_triceps', 'close_grip_push_up',
  'lateral_raise', 'cable_lateral_raise',
  'calf_raise', 'standing_calf_raise', 'db_calf_raise', 'seated_calf_raise',
  'incline_bench_press', 'close_grip_bench_press', 'arnold_press', 't_bar_row', 'chest_supported_row',
  'sumo_deadlift', 'trap_bar_deadlift', 'hack_squat', 'glute_ham_raise', 'single_leg_rdl', 'diamond_push_up',
  'db_lunge', 'db_step_up', 'db_split_squat', 'squat',
])

const SET_SECONDS = 40
const TOLERANCE_MIN = 5
const CONDITIONING_REST = 30

/** Sensible training weekdays for `n` days a week (Monday = 1). */
export function spreadDays(n: number): Set<number> {
  if (n <= 0) return new Set()
  if (n === 1) return new Set([1])
  if (n === 2) return new Set([1, 4])
  if (n === 3) return new Set([1, 3, 5])
  if (n === 4) return new Set([1, 2, 4, 5])
  if (n === 5) return new Set([1, 2, 3, 5, 6])
  if (n === 6) return new Set([1, 2, 3, 4, 5, 6])
  return new Set([1, 2, 3, 4, 5, 6, 7])
}

export function buildPlan(input: PlanInput, library: readonly ExerciseEntity[]): BuiltPlan {
  return new Builder(input, library).run()
}

export { buildPlan as build }

/** Other allowed exercises for the slot `current` fills (same movement pattern), best first, for a swap button. */
export function alternatives(
  current: ExerciseEntity,
  input: PlanInput,
  library: readonly ExerciseEntity[],
  exclude: ReadonlySet<string> | readonly string[] = new Set(),
  limit = 6,
): ExerciseEntity[] {
  return new Builder(input, library).alternativesFor(current, new Set(exclude), limit)
}

export type GroupedWorkItem = {
  sets: number
  restSeconds: number
  block?: string
  circuit?: boolean
  supersetGroup?: number | null
}

/**
 * Total working seconds for a day's non-extra items (already filtered to sets > 0).
 * Straight item: sets * (40 + rest). Group (consecutive same non-null supersetGroup, or consecutive circuit=true in the
 * same block with supersetGroup null): rounds = min(sets), postRoundRest = last member's restSeconds, extra sets cost straight.
 */
export function groupedWorkSeconds(items: readonly GroupedWorkItem[]): number {
  let total = 0
  let i = 0
  while (i < items.length) {
    const cur = items[i]
    let end = i + 1
    if (cur.supersetGroup != null) {
      while (end < items.length && items[end].supersetGroup === cur.supersetGroup) end++
    } else if (cur.circuit) {
      while (
        end < items.length &&
        items[end].supersetGroup == null &&
        items[end].circuit &&
        items[end].block === cur.block
      ) {
        end++
      }
    }
    if (end - i >= 2) {
      let rounds = items[i].sets
      for (let j = i + 1; j < end; j++) {
        if (items[j].sets < rounds) rounds = items[j].sets
      }
      const postRoundRest = items[end - 1].restSeconds
      total += rounds * ((end - i) * SET_SECONDS + postRoundRest)
      for (let j = i; j < end; j++) {
        const extra = items[j].sets - rounds
        if (extra > 0) total += extra * (SET_SECONDS + items[j].restSeconds)
      }
    } else {
      total += cur.sets * (SET_SECONDS + cur.restSeconds)
    }
    i = end
  }
  return total
}

/** Minutes the day takes: grouped work with 40 s sets + rest, plus a flat 5 for a warm-up and 5 for a cool-down. */
export function estimateMinutes(day: PlanDraftDay): number {
  const work = day.items.filter((it) => !isExtraBlock(it.block) && it.sets > 0)
  const workSeconds = groupedWorkSeconds(work)
  const extras =
    (day.items.some((it) => it.block === WARM_UP_BLOCK) ? 5 : 0) +
    (day.items.some((it) => it.block === COOL_DOWN_BLOCK) ? 5 : 0)
  return Math.ceil(workSeconds / 60) + extras
}

type Role = 'MAIN' | 'FOCUS' | 'ACC' | 'FINISHER' | 'COND'

type Slot = {
  patterns: readonly MovementPattern[]
  role: Role
  focus: Muscle | null
  core: boolean
}

const makeSlot = (patterns: readonly MovementPattern[], role: Role, focus: Muscle | null = null): Slot => ({
  patterns,
  role,
  focus,
  core: patterns.some((p) => p === 'CORE_ANTI_EXTENSION' || p === 'CORE_FLEXION_ROTATION'),
})

type Pick = {
  ex: ExerciseEntity
  role: Role
  sets: number
  supersetGroup: number | null
  circuitBlock: string | null
}

type DayBuild = {
  focus: DayFocus
  light: boolean
  picks: Pick[]
  tags: Set<WhyTag>
}

const LOAD_EQUIPMENT = new Set<Equipment>([
  'DUMBBELLS', 'KETTLEBELL', 'BARBELL',
  ...SELECTABLE_EQUIPMENT.filter((e) => {
    const g = EQUIPMENT_META[e].group
    return g === 'UPPER_MACHINES' || g === 'LOWER_MACHINES'
  }),
])
const PUSH_PATTERNS = new Set<MovementPattern>(['HORIZONTAL_PUSH', 'DIP_PUSH', 'VERTICAL_PUSH'])
const PULL_PATTERNS = new Set<MovementPattern>(['VERTICAL_PULL', 'HORIZONTAL_PULL', 'UPPER_BACK_PULL'])

const MAJOR_MUSCLES = new Set<Muscle>([
  'CHEST', 'SHOULDERS', 'BICEPS', 'TRICEPS', 'UPPER_BACK',
  'QUADS', 'HAMSTRINGS', 'GLUTES', 'ABS',
])
const UPPER_MUSCLES = new Set<Muscle>(['CHEST', 'SHOULDERS', 'BICEPS', 'TRICEPS', 'FOREARMS', 'UPPER_BACK', 'TRAPS'])
const LOWER_MUSCLES = new Set<Muscle>(['QUADS', 'HAMSTRINGS', 'GLUTES', 'CALVES', 'ADDUCTORS', 'TIBIALIS'])
const CORE_MUSCLES = new Set<Muscle>(['ABS', 'OBLIQUES', 'LOWER_BACK'])

/** Preference order for the two warm-up moves per day focus: bodyweight/band easy rep moves only. */
const WARM_UP_UPPER_IDS = ['jumping_jacks', 'band_pull_apart', 'scapular_pull_up', 'scapular_australian_pull_up', 'knee_scapular_push_up'] as const
const WARM_UP_LOWER_IDS = ['jumping_jacks', 'half_squat', 'squat', 'hip_bridge'] as const
const WARM_UP_FULL_IDS = ['jumping_jacks', 'half_squat', 'squat', 'hip_bridge', 'knee_scapular_push_up'] as const
const WARM_UP_PULL_HINGE_IDS = ['jumping_jacks', 'hip_bridge', 'half_squat', 'squat', 'knee_scapular_push_up'] as const

function warmUpIdsFor(focus: DayFocus): readonly string[] {
  switch (focus) {
    case 'UPPER':
    case 'PUSH':
    case 'PULL':
      return WARM_UP_UPPER_IDS
    case 'LOWER':
    case 'LEGS':
      return WARM_UP_LOWER_IDS
    case 'PULL_HINGE':
      return WARM_UP_PULL_HINGE_IDS
    case 'FULL_BODY':
    case 'PUSH_LEGS':
    case 'CONDITIONING':
      return WARM_UP_FULL_IDS
  }
}

/** Preference order for the two cool-down stretches; the ones that work the muscles trained that day go first. */
const COOL_DOWN_IDS = [
  'childs_pose', 'hamstring_stretch', 'hip_flexor_stretch', 'chest_doorway_stretch', 'quad_stretch', 'lat_stretch',
  'cross_body_shoulder_stretch', 'figure_four_stretch', 'calf_stretch', 'triceps_overhead_stretch',
  'seated_spinal_twist', 'biceps_wall_stretch', 'butterfly_stretch', 'neck_side_stretch',
] as const

const SQUAT: readonly MovementPattern[] = ['SQUAT_BILATERAL', 'SQUAT_UNILATERAL']
const UNILATERAL: readonly MovementPattern[] = ['SQUAT_UNILATERAL', 'SQUAT_BILATERAL']
const H_PUSH: readonly MovementPattern[] = ['HORIZONTAL_PUSH', 'DIP_PUSH']
const DIP: readonly MovementPattern[] = ['DIP_PUSH', 'HORIZONTAL_PUSH']
const H_PULL: readonly MovementPattern[] = ['HORIZONTAL_PULL', 'UPPER_BACK_PULL']
const BACK_PULL: readonly MovementPattern[] = ['UPPER_BACK_PULL', 'HORIZONTAL_PULL']
const HINGE: readonly MovementPattern[] = ['HIP_HINGE', 'KNEE_FLEXION']
const KNEE_FLEX: readonly MovementPattern[] = ['KNEE_FLEXION', 'HIP_HINGE']
const V_PUSH: readonly MovementPattern[] = ['VERTICAL_PUSH']
const V_PULL: readonly MovementPattern[] = ['VERTICAL_PULL', 'HORIZONTAL_PULL']
const CORE: readonly MovementPattern[] = ['CORE_ANTI_EXTENSION', 'CORE_FLEXION_ROTATION']
const LATERAL: readonly MovementPattern[] = ['LATERAL_RAISE', 'UPPER_BACK_PULL']
const CURL: readonly MovementPattern[] = ['ELBOW_FLEXION']
const EXTENSION: readonly MovementPattern[] = ['ELBOW_EXTENSION']
const CALF: readonly MovementPattern[] = ['ANKLE_FLEXION']
const CONDITIONING: readonly MovementPattern[] = ['CARRY_CONDITIONING']

const main = (p: readonly MovementPattern[]) => makeSlot(p, 'MAIN')
const acc = (p: readonly MovementPattern[]) => makeSlot(p, 'ACC')

function slotsFor(focus: DayFocus): Slot[] {
  switch (focus) {
    case 'FULL_BODY':
      return [main(SQUAT), main(H_PUSH), main(H_PULL), main(HINGE), main(V_PUSH), main(V_PULL), main(CORE), acc(CURL), acc(EXTENSION), acc(CALF)]
    case 'UPPER':
      return [main(H_PUSH), main(H_PULL), main(V_PUSH), main(V_PULL), main(CORE), acc(CURL), acc(EXTENSION), acc(LATERAL)]
    case 'LOWER':
      return [main(SQUAT), main(HINGE), main(UNILATERAL), main(CORE), acc(KNEE_FLEX), acc(CALF)]
    case 'PUSH':
      return [main(H_PUSH), main(V_PUSH), main(DIP), main(CORE), acc(LATERAL), acc(EXTENSION)]
    case 'PULL':
      return [main(V_PULL), main(H_PULL), main(BACK_PULL), main(CORE), acc(CURL), acc(BACK_PULL)]
    case 'LEGS':
      return [main(SQUAT), main(HINGE), main(UNILATERAL), main(CORE), acc(KNEE_FLEX), acc(CALF)]
    case 'PUSH_LEGS':
      return [main(SQUAT), main(H_PUSH), main(UNILATERAL), main(V_PUSH), acc(EXTENSION), acc(CALF)]
    case 'PULL_HINGE':
      return [main(HINGE), main(H_PULL), main(V_PULL), main(CORE), acc(KNEE_FLEX), acc(CURL)]
    case 'CONDITIONING':
      return [makeSlot(CONDITIONING, 'COND'), makeSlot(CONDITIONING, 'COND'), makeSlot(CONDITIONING, 'COND'), main(CORE)]
  }
}

function trains(day: DayFocus, m: Muscle): boolean {
  if (day === 'FULL_BODY') return true
  if (day === 'CONDITIONING') return false
  if (day === 'PUSH_LEGS') {
    return m === 'CHEST' || m === 'SHOULDERS' || m === 'TRICEPS' || m === 'QUADS' || m === 'CALVES'
  }
  if (day === 'PULL_HINGE') {
    return m === 'UPPER_BACK' || m === 'BICEPS' || m === 'TRAPS' || m === 'FOREARMS' ||
      m === 'HAMSTRINGS' || m === 'GLUTES' || CORE_MUSCLES.has(m)
  }
  if (CORE_MUSCLES.has(m)) return true
  switch (day) {
    case 'UPPER':
      return UPPER_MUSCLES.has(m)
    case 'LOWER':
    case 'LEGS':
      return LOWER_MUSCLES.has(m)
    case 'PUSH':
      return m === 'CHEST' || m === 'SHOULDERS' || m === 'TRICEPS'
    case 'PULL':
      return m === 'UPPER_BACK' || m === 'BICEPS' || m === 'TRAPS' || m === 'FOREARMS'
  }
}

function titleFor(f: DayFocus): string {
  switch (f) {
    case 'FULL_BODY': return 'Full body'
    case 'UPPER': return 'Upper'
    case 'LOWER': return 'Lower'
    case 'PUSH': return 'Push'
    case 'PULL': return 'Pull'
    case 'LEGS': return 'Legs'
    case 'PUSH_LEGS': return 'Push and legs'
    case 'PULL_HINGE': return 'Pull, hinge and core'
    case 'CONDITIONING': return 'Conditioning'
  }
}

const byIdAsc = (a: string, b: string) => (a < b ? -1 : a > b ? 1 : 0)
const tierOrd = (t: Tier) => TIERS.indexOf(t)
const muscleOrd = (m: Muscle) => MUSCLES.indexOf(m)
const clamp = (v: number, lo: number, hi: number) => Math.min(Math.max(v, lo), hi)

class Builder {
  private readonly goal: TrainGoal
  private readonly days: Set<number>
  private readonly minutes: number
  private readonly experience: Experience
  private readonly injuries: Set<Injury>
  private readonly avoidIds: Set<string>
  private readonly pushUpMax: number | null
  private readonly pullUpMax: number | null
  private readonly focus: Set<Muscle>
  private readonly minutesByDay: Map<number, number>
  private readonly cardio: Cardio
  private readonly warmUp: boolean
  private readonly coolDown: boolean
  private readonly holdsOk: boolean
  private readonly reps: RepPref
  private readonly rest: RestPref
  private readonly style: WorkoutStyle
  private readonly offset: number

  private readonly allowedEq: Set<Equipment>
  private readonly loadEquip: boolean
  private readonly lib: ExerciseEntity[]
  private readonly profiles = new Map<string, BiomechanicalProfile>()
  private readonly weekUsed = new Set<string>()
  private readonly equipOk: ExerciseEntity[]
  private readonly eligible: ExerciseEntity[]
  private readonly stretches: ExerciseEntity[]
  private readonly experienceCap: Tier

  constructor(input: PlanInput, library: readonly ExerciseEntity[]) {
    this.goal = input.goal
    this.days = new Set(input.days)
    this.minutes = input.minutes
    const eq = new Set<Equipment>(input.equipment)
    this.experience = input.experience ?? 'SOME'
    this.injuries = new Set<Injury>(input.injuries ?? [])
    this.avoidIds = new Set<string>(input.avoidIds ?? [])
    this.pushUpMax = input.pushUpMax ?? null
    this.pullUpMax = input.pullUpMax ?? null
    this.focus = new Set(
      [...new Set<Muscle>(input.focus ?? [])]
        .sort((a, b) => muscleOrd(a) - muscleOrd(b))
        .slice(0, 3),
    )
    this.minutesByDay = new Map<number, number>(
      input.minutesByDay instanceof Map
        ? input.minutesByDay
        : Object.entries(input.minutesByDay ?? {}).map(([k, v]) => [Number(k), v]),
    )
    this.cardio = input.cardio ?? 'NONE'
    this.warmUp = input.warmUp ?? true
    this.coolDown = input.coolDown ?? false
    this.holdsOk = input.holdsOk ?? true
    this.reps = input.reps ?? 'AUTO'
    this.rest = input.rest ?? 'AUTO'
    this.style = input.style ?? 'AUTO'
    this.offset = input.offset ?? 0

    this.allowedEq = new Set<Equipment>([...expandLegacyEquipment(eq), 'BODYWEIGHT'])
    this.loadEquip = [...this.allowedEq].some((e) => LOAD_EQUIPMENT.has(e))

    const seen = new Set<string>()
    const distinct: ExerciseEntity[] = []
    for (const ex of library) {
      if (!seen.has(ex.id)) {
        seen.add(ex.id)
        distinct.push(ex)
      }
    }
    this.lib = distinct.sort((a, b) => byIdAsc(a.id, b.id))
    for (const ex of this.lib) {
      this.profiles.set(ex.id, profileFor(ex))
    }

    this.equipOk = this.lib.filter((e) => profileFits(this.prof(e), this.allowedEq) && !this.avoidIds.has(e.id))
    this.eligible = this.equipOk.filter(
      (e) =>
        !STRETCH_IDS.has(e.id) &&
        this.prof(e).pattern !== 'MOBILITY' &&
        fitsItsMovement(e) &&
        !blocked(e, this.prof(e), this.injuries),
    )
    this.stretches = this.equipOk.filter((e) => STRETCH_IDS.has(e.id) && !blocked(e, this.prof(e), this.injuries))

    this.experienceCap =
      this.experience === 'NEW' ? 'FOUNDATION'
      : this.experience === 'SOME' ? 'INTERMEDIATE'
      : 'ADVANCED'
  }

  private prof(e: ExerciseEntity): BiomechanicalProfile {
    return this.profiles.get(e.id)!
  }

  private capFor(pattern: MovementPattern): Tier {
    if (PUSH_PATTERNS.has(pattern) && this.pushUpMax != null) {
      return this.pushUpMax < 5 ? 'FOUNDATION' : this.pushUpMax <= 20 ? 'INTERMEDIATE' : 'ADVANCED'
    }
    if (PULL_PATTERNS.has(pattern) && this.pullUpMax != null) {
      return this.pullUpMax <= 0 ? 'FOUNDATION' : this.pullUpMax <= 7 ? 'INTERMEDIATE' : 'ADVANCED'
    }
    return this.experienceCap
  }

  private tierOf(e: ExerciseEntity): Tier {
    return rawTierOf(e) ?? 'FOUNDATION'
  }

  private withinCap(e: ExerciseEntity): boolean {
    return tierOrd(this.tierOf(e)) <= tierOrd(this.capFor(this.prof(e).pattern))
  }

  private score(e: ExerciseEntity, role: Role): number {
    let s = 40 - 12 * (tierOrd(this.capFor(this.prof(e).pattern)) - tierOrd(this.tierOf(e)))
    s += 10 * e.primary.filter((m) => this.focus.has(m)).length + 3 * e.secondary.filter((m) => this.focus.has(m)).length
    const loaded = role === 'MAIN' || role === 'FOCUS' || role === 'ACC'
    if (loaded && e.kind === 'WEIGHTED' && this.loadEquip && (this.goal === 'STRENGTH' || this.goal === 'MUSCLE')) {
      s += 8
    }
    return s
  }

  /** Within the best score: unused staples, then staples used on another day, then unused others, then the rest. */
  private tieRank(e: ExerciseEntity): number {
    return (STAPLE_IDS.has(e.id) ? 0 : 2) + (this.weekUsed.has(e.id) ? 1 : 0)
  }

  private best(cands: ExerciseEntity[], role: Role): ExerciseEntity {
    const sorted = [...cands].sort(
      (a, b) => this.score(b, role) - this.score(a, role) || this.tieRank(a) - this.tieRank(b) || byIdAsc(a.id, b.id),
    )
    const topScore = this.score(sorted[0], role)
    const tied: ExerciseEntity[] = []
    for (const c of sorted) {
      if (this.score(c, role) === topScore) tied.push(c)
      else break
    }
    const idx = ((this.offset % tied.length) + tied.length) % tied.length
    return tied[idx]
  }

  private groupsOf(slot: Slot): ((e: ExerciseEntity) => boolean)[] {
    if (slot.focus != null) {
      const f = slot.focus
      return [(e) => e.primary.includes(f)]
    }
    return slot.patterns.map((p) => (e: ExerciseEntity) => this.prof(e).pattern === p)
  }

  private pick(slot: Slot, dayUsed: Set<string>, tags: Set<WhyTag>): ExerciseEntity | null {
    const passes = this.holdsOk ? [true] : slot.core ? [false, true] : [false]
    for (const holdsAllowed of passes) {
      for (const match of this.groupsOf(slot)) {
        const cands = this.eligible.filter(
          (e) => match(e) && !dayUsed.has(e.id) && (holdsAllowed || e.kind !== 'HOLD') && this.withinCap(e),
        )
        if (cands.length === 0) continue
        const chosen = this.best(cands, slot.role)
        if (!this.holdsOk && chosen.kind === 'HOLD') tags.add('HOLD_FALLBACK')
        return chosen
      }
    }
    return null
  }

  /** Why a main slot came back empty: an injury rule removed everything that fits the equipment, or nothing fits. */
  private reasonFor(slot: Slot): WhyTag {
    const hit = this.groupsOf(slot).some((match) =>
      this.equipOk.some((e) => match(e) && blocked(e, this.prof(e), this.injuries)),
    )
    return hit ? 'SKIPPED_FOR_INJURY' : 'NO_EQUIPMENT'
  }

  alternativesFor(current: ExerciseEntity, exclude: Set<string>, limit: number): ExerciseEntity[] {
    const pattern = profileFor(current).pattern
    return this.eligible
      .filter(
        (e) =>
          e.id !== current.id &&
          !exclude.has(e.id) &&
          this.prof(e).pattern === pattern &&
          this.withinCap(e) &&
          (this.holdsOk || e.kind !== 'HOLD'),
      )
      .sort(
        (a, b) =>
          this.score(b, 'ACC') - this.score(a, 'ACC') ||
          (STAPLE_IDS.has(a.id) ? 0 : 1) - (STAPLE_IDS.has(b.id) ? 0 : 1) ||
          byIdAsc(a.id, b.id),
      )
      .slice(0, limit)
  }

  // ---- week layout ----

  private dayMinutes(d: number): number {
    return this.minutesByDay.get(d) ?? this.minutes
  }

  private resolveStyle(d: number): Exclude<WorkoutStyle, 'AUTO'> {
    if (this.style !== 'AUTO') return this.style
    if (this.goal === 'FAT_LOSS') return 'CIRCUIT'
    if (this.dayMinutes(d) <= 30) return 'SUPERSET'
    return 'STRAIGHT'
  }

  private assign(sel: number[]): Map<number, DayFocus> {
    const own = this.cardio === 'OWN_DAY' && sel.length >= 3
    const extra = sel.length === 7 || own ? sel[sel.length - 1] : null
    const lifting = extra != null ? sel.slice(0, -1) : sel
    const fatLossAB = this.goal === 'FAT_LOSS' && lifting.length >= 5
    const split: DayFocus[] = fatLossAB
      ? Array.from({ length: lifting.length }, (_, i) => (i % 2 === 0 ? 'PUSH_LEGS' : 'PULL_HINGE'))
      : lifting.length <= 3 ? Array.from({ length: lifting.length }, () => 'FULL_BODY')
      : lifting.length === 4 ? ['UPPER', 'LOWER', 'UPPER', 'LOWER']
      : lifting.length === 5 ? ['UPPER', 'LOWER', 'PUSH', 'PULL', 'LEGS']
      : ['PUSH', 'PULL', 'LEGS', 'PUSH', 'PULL', 'LEGS']
    const out = new Map<number, DayFocus>()
    // Short days: full body for 1-3 lifting days; with 4+ lifting days alternate the clean A/B halves so no muscle
    // is trained hard on two days running.
    let shortIdx = 0
    lifting.forEach((d, i) => {
      if (fatLossAB || this.dayMinutes(d) >= 30) out.set(d, split[i])
      else if (lifting.length < 4) out.set(d, 'FULL_BODY')
      else out.set(d, shortIdx++ % 2 === 0 ? 'PUSH_LEGS' : 'PULL_HINGE')
    })
    if (extra != null) {
      out.set(extra, this.cardio !== 'NONE' ? 'CONDITIONING' : 'FULL_BODY')
    }
    return out
  }

  private buildDay(focusOfDay: DayFocus, light: boolean, finisher: boolean): DayBuild {
    const day: DayBuild = { focus: focusOfDay, light, picks: [], tags: new Set() }
    if (light && focusOfDay !== 'CONDITIONING') day.tags.add('LIGHT_DAY')
    const base = slotsFor(focusOfDay)
    const focusSlots = [...this.focus].filter((m) => trains(focusOfDay, m)).map((m) => makeSlot([], 'FOCUS', m))
    const slots: Slot[] = [
      ...base.filter((s) => s.role !== 'ACC'),
      ...focusSlots,
      ...base.filter((s) => s.role === 'ACC'),
      ...(finisher && focusOfDay !== 'CONDITIONING' ? [makeSlot(CONDITIONING, 'FINISHER')] : []),
    ]
    const used = new Set<string>()
    for (const slot of slots) {
      const ex = this.pick(slot, used, day.tags)
      if (ex == null) {
        if (slot.role === 'MAIN') day.tags.add(this.reasonFor(slot))
        continue
      }
      used.add(ex.id)
      this.weekUsed.add(ex.id)
      day.picks.push({ ex, role: slot.role, sets: 3, supersetGroup: null, circuitBlock: null })
      if (slot.role === 'FOCUS' || ex.primary.some((m) => this.focus.has(m))) day.tags.add('FOCUS_BOOST')
      if (slot.role === 'FINISHER' || slot.role === 'COND') day.tags.add('CARDIO_ADDED')
      const pattern = this.prof(ex).pattern
      if ((PUSH_PATTERNS.has(pattern) && this.pushUpMax != null) || (PULL_PATTERNS.has(pattern) && this.pullUpMax != null)) {
        day.tags.add('TIER_FROM_MAX')
      }
    }
    return day
  }

  // ---- supersets & circuits ----

  private isUpper(e: ExerciseEntity): boolean {
    return e.primary.some((m) => UPPER_MUSCLES.has(m)) &&
      !e.primary.some((m) => LOWER_MUSCLES.has(m) || CORE_MUSCLES.has(m))
  }

  private isLowerOrCore(e: ExerciseEntity): boolean {
    return isCore(this.prof(e).pattern) ||
      (e.primary.some((m) => LOWER_MUSCLES.has(m) || CORE_MUSCLES.has(m)) &&
        !e.primary.some((m) => UPPER_MUSCLES.has(m)))
  }

  private isPushPullOpposite(a: ExerciseEntity, b: ExerciseEntity): boolean {
    const ppA = pushPull(a)
    const ppB = pushPull(b)
    return ppA !== 0 && ppB !== 0 && ppA === -ppB
  }

  private isUpperVsLowerOrCore(a: ExerciseEntity, b: ExerciseEntity): boolean {
    return (this.isUpper(a) && this.isLowerOrCore(b)) || (this.isLowerOrCore(a) && this.isUpper(b))
  }

  private circuitCategory(p: Pick): number {
    const pp = pushPull(p.ex)
    if (pp === 1) return 0
    if (pp === -1) return 1
    if (this.isLowerOrCore(p.ex)) return 2
    return 3
  }

  private wouldPutIsolationFirst(c: Pick, picks: readonly Pick[], skip: ReadonlySet<Pick>): boolean {
    if (isCompoundExercise(c.ex)) return false
    return picks.some(
      (other) =>
        !skip.has(other) &&
        other !== c &&
        isCompoundExercise(other.ex) &&
        c.ex.primary.some((m) => other.ex.primary.includes(m)),
    )
  }

  private pairSupersets(picks: Pick[]): void {
    const firstMain = this.goal === 'STRENGTH' ? picks.find((p) => p.role === 'MAIN') ?? null : null
    const isCandidate = (p: Pick) =>
      (p.role === 'MAIN' || p.role === 'FOCUS' || p.role === 'ACC') && p !== firstMain
    const paired = new Set<Pick>()
    let nextGroup = 1
    let i = 0
    while (i < picks.length) {
      const a = picks[i]
      if (!isCandidate(a) || paired.has(a)) {
        i++
        continue
      }
      const before = new Set<Pick>(paired)
      for (let k = 0; k <= i; k++) before.add(picks[k])
      let matchIdx = -1
      for (let j = i + 1; j < picks.length; j++) {
        const b = picks[j]
        if (!isCandidate(b) || paired.has(b)) continue
        if (safePair(a.ex, b.ex) && this.isPushPullOpposite(a.ex, b.ex) && !this.wouldPutIsolationFirst(b, picks, before)) {
          matchIdx = j
          break
        }
      }
      if (matchIdx < 0) {
        for (let j = i + 1; j < picks.length; j++) {
          const b = picks[j]
          if (!isCandidate(b) || paired.has(b)) continue
          if (safePair(a.ex, b.ex) && this.isUpperVsLowerOrCore(a.ex, b.ex) && !this.wouldPutIsolationFirst(b, picks, before)) {
            matchIdx = j
            break
          }
        }
      }
      if (matchIdx >= 0) {
        const [b] = picks.splice(matchIdx, 1)
        picks.splice(i + 1, 0, b)
        a.supersetGroup = nextGroup
        b.supersetGroup = nextGroup
        paired.add(a)
        paired.add(b)
        nextGroup++
        i += 2
      } else {
        i++
      }
    }
  }

  private groupCircuits(picks: Pick[]): void {
    const mainPicks = picks.filter((p) => p.role === 'MAIN' || p.role === 'FOCUS' || p.role === 'ACC')
    const otherPicks = picks.filter((p) => p.role === 'COND' || p.role === 'FINISHER')
    const assigned = new Set<Pick>()
    const groups: Pick[][] = []
    let circuitCount = 0
    for (const first of mainPicks) {
      if (assigned.has(first)) continue
      assigned.add(first)
      const group: Pick[] = [first]
      while (group.length < 3) {
        const haveCats = new Set(group.map((m) => this.circuitCategory(m)))
        let next: Pick | null = null
        for (const wantedCat of [0, 1, 2]) {
          if (haveCats.has(wantedCat)) continue
          for (const c of mainPicks) {
            if (assigned.has(c) || this.circuitCategory(c) !== wantedCat) continue
            if (group.every((m) => safePair(m.ex, c.ex)) && !this.wouldPutIsolationFirst(c, mainPicks, assigned)) {
              next = c
              break
            }
          }
          if (next != null) break
        }
        if (next == null) {
          for (const c of mainPicks) {
            if (assigned.has(c)) continue
            if (group.every((m) => safePair(m.ex, c.ex)) && !this.wouldPutIsolationFirst(c, mainPicks, assigned)) {
              next = c
              break
            }
          }
        }
        if (next == null) break
        assigned.add(next)
        group.push(next)
      }
      if (group.length === 1) {
        group[0].circuitBlock = null
        groups.push(group)
      } else {
        group.sort((a, b) => this.circuitCategory(a) - this.circuitCategory(b))
        const name = CIRCUIT_BLOCKS[circuitCount] ?? `Circuit ${String.fromCharCode(65 + circuitCount)}`
        for (const p of group) p.circuitBlock = name
        circuitCount++
        groups.push(group)
      }
    }
    picks.splice(0, picks.length, ...groups.flat(), ...otherPicks)
  }

  private removePick(picks: Pick[], idx: number): void {
    if (idx < 0 || idx >= picks.length) return
    const [removed] = picks.splice(idx, 1)
    if (removed.supersetGroup != null) {
      for (const p of picks) {
        if (p.supersetGroup === removed.supersetGroup) p.supersetGroup = null
      }
    }
    if (removed.circuitBlock != null) {
      const rem = picks.filter((p) => p.circuitBlock === removed.circuitBlock)
      if (rem.length === 1) rem[0].circuitBlock = null
    }
  }

  private renumberSupersets(picks: Pick[]): void {
    let next = 1
    let i = 0
    while (i < picks.length) {
      const g = picks[i].supersetGroup
      if (g != null && i + 1 < picks.length && picks[i + 1].supersetGroup === g) {
        picks[i].supersetGroup = next
        picks[i + 1].supersetGroup = next
        next++
        i += 2
      } else {
        picks[i].supersetGroup = null
        i++
      }
    }
  }

  private renumberCircuits(picks: Pick[]): void {
    let nextIdx = 0
    let i = 0
    while (i < picks.length) {
      const blk = picks[i].circuitBlock
      if (blk != null) {
        let end = i + 1
        while (end < picks.length && picks[end].circuitBlock === blk) end++
        if (end - i >= 2) {
          const name = CIRCUIT_BLOCKS[nextIdx] ?? `Circuit ${String.fromCharCode(65 + nextIdx)}`
          for (let j = i; j < end; j++) picks[j].circuitBlock = name
          nextIdx++
        } else {
          picks[i].circuitBlock = null
        }
        i = end
      } else {
        i++
      }
    }
  }

  // ---- sets, reps, rest ----

  private repRange(): [number, number] {
    switch (this.reps) {
      case 'LOW': return [5, 8]
      case 'MID': return [8, 12]
      case 'HIGH': return [12, 20]
      case 'AUTO':
        switch (this.goal) {
          case 'STRENGTH': return [4, 6]
          case 'MUSCLE': return [8, 12]
          case 'FAT_LOSS': return [12, 15]
          case 'ENDURANCE': return [15, 20]
          case 'GENERAL': return [8, 12]
        }
    }
  }

  private repRangeFor(p: Pick): [number, number] {
    if (p.role === 'COND' || p.role === 'FINISHER') return [12, 20]
    const base = this.repRange()
    if (isCompoundExercise(p.ex)) return base
    if (this.reps !== 'AUTO') return base[0] >= 8 ? base : [8, 15]
    if (this.goal === 'STRENGTH') return [8, 12]
    if (this.goal === 'MUSCLE') return [10, 15]
    return base[0] >= 8 ? base : [8, 15]
  }

  private baseRestFor(p: Pick): number {
    if (p.role === 'COND' || p.role === 'FINISHER') return CONDITIONING_REST
    const seconds = isCompoundExercise(p.ex)
      ? (this.goal === 'STRENGTH' ? 180 : this.goal === 'MUSCLE' ? 150 : 120)
      : (this.goal === 'STRENGTH' || this.goal === 'MUSCLE' ? 90 : this.goal === 'GENERAL' ? 75 : 60)
    const factor = this.rest === 'AUTO' ? 1.0 : this.rest === 'SHORT' ? 0.6 : 1.5
    return clamp(Math.round(seconds * factor), 30, 240)
  }

  private restFor(d: number, picks: readonly Pick[], p: Pick): number {
    if (p.role === 'COND' || p.role === 'FINISHER') return CONDITIONING_REST
    if (p.supersetGroup != null) {
      const partner = picks.find((q) => q !== p && q.supersetGroup === p.supersetGroup)
      if (partner) {
        return this.dayMinutes(d) <= 30
          ? SUPERSET_POST_ROUND_SHORT
          : Math.max(this.baseRestFor(p), this.baseRestFor(partner))
      }
    }
    if (p.circuitBlock != null) {
      const size = picks.filter((q) => q.circuitBlock === p.circuitBlock).length
      if (size >= 2) {
        if (this.dayMinutes(d) <= 30) {
          return size === 2 ? SUPERSET_POST_ROUND_SHORT : CIRCUIT_POST_ROUND_SHORT
        }
        return CIRCUIT_POST_ROUND
      }
    }
    return this.baseRestFor(p)
  }

  /** Weekly effective sets the builder aims for, kept between the muscle's minimum and maximum. */
  private target(m: Muscle): number {
    const weekly = this.experience === 'NEW' ? 10 : this.experience === 'SOME' ? 12 : 14
    const raw = weekly * (MAJOR_MUSCLES.has(m) ? 1.0 : 0.5) + (this.focus.has(m) ? 4 : 0)
    const lm = landmarkFor(m)
    const floor = BELOW_MEV_MUSCLES.has(m) ? lm.mev : 0
    return clamp(raw, floor, lm.mrv)
  }

  private get maxSets(): number {
    return this.experience === 'NEW' ? 4 : 5
  }

  private budgetSeconds(d: number, reserved: number): number {
    return (this.dayMinutes(d) + TOLERANCE_MIN) * 60 - reserved
  }

  private removalRank(p: Pick): number {
    if (p.role === 'FINISHER' || p.role === 'COND') return 0
    if (p.role === 'ACC') return 1
    if (p.role === 'FOCUS') return 4
    if (isCore(this.prof(p.ex).pattern)) return 2
    if (p.role === 'MAIN') return 3
    return 4
  }

  private groupOf(picks: readonly Pick[], p: Pick): Pick[] {
    if (p.supersetGroup != null) {
      return picks.filter((x) => x.supersetGroup === p.supersetGroup)
    }
    if (p.circuitBlock != null) {
      return picks.filter((x) => x.circuitBlock === p.circuitBlock)
    }
    return [p]
  }

  private groupAddSeconds(d: number, picks: readonly Pick[], group: readonly Pick[]): number {
    if (group.length >= 2) {
      const postRoundRest = this.restFor(d, picks, group[group.length - 1])
      return group.length * SET_SECONDS + postRoundRest
    }
    return SET_SECONDS + this.restFor(d, picks, group[0])
  }

  private equalizeGroups(days: [number, DayBuild][], reserved: ReadonlyMap<number, number>): void {
    for (let i = 0; i < days.length; i++) {
      const [dow, b] = days[i]
      const seen = new Set<Pick>()
      for (const p of b.picks) {
        if (seen.has(p)) continue
        const group = this.groupOf(b.picks, p)
        for (const m of group) seen.add(m)
        if (group.length < 2) continue
        const minSets = Math.min(...group.map((m) => m.sets))
        const maxSets = Math.max(...group.map((m) => m.sets))
        if (minSets === maxSets) continue
        for (const m of group) m.sets = maxSets
        const budget = this.budgetSeconds(dow, reserved.get(dow)!)
        const withinBudget = this.workSeconds(dow, b.picks) <= budget
        const vol = new Volume(days, (mu) => this.target(mu))
        if (!withinBudget || maxSets > this.maxSets || vol.broken() != null) {
          for (const m of group) m.sets = minSets
        }
      }
    }
  }

  /**
   * Sets for every pick. Each starts at 2 (conditioning 3, finisher 2) and the day is fitted to its minutes; picks that
   * break a Plan check rule even at their starting sets leave; then one set at a time goes to the pick that best closes
   * the gap to the weekly targets, as long as every Plan check rule, the per-pick cap and the day's minutes still hold.
   */
  private allocate(builds: Map<number, DayBuild>, reserved: ReadonlyMap<number, number>): void {
    for (const b of builds.values()) {
      for (const p of b.picks) p.sets = p.role === 'COND' ? 3 : 2
    }
    const days: [number, DayBuild][] = [...builds.entries()]
    for (const [d, b] of days) {
      const budget = this.budgetSeconds(d, reserved.get(d)!)
      while (this.workSeconds(d, b.picks) > budget && b.picks.length > 1) {
        let drop = -1
        for (const match of [
          (p: Pick) => p.role === 'FINISHER',
          (p: Pick) => p.role === 'ACC',
          (p: Pick) => p.role !== 'FOCUS' && isCore(this.prof(p.ex).pattern),
          (p: Pick) => p.role !== 'FOCUS',
          () => true,
        ]) {
          for (let i = b.picks.length - 1; i >= 0; i--) {
            if (match(b.picks[i])) { drop = i; break }
          }
          if (drop >= 0) break
        }
        if (drop < 0) break
        this.removePick(b.picks, drop)
        b.tags.add('TIME_TRIMMED')
      }
    }
    let vol = new Volume(days, (m) => this.target(m))
    for (let round = 0; round < 200; round++) {
      const br = vol.broken()
      if (!br) break
      const [m, dayIdx] = br
      const candidates: [number, Pick][] = []
      for (let i = 0; i < days.length; i++) {
        if (days[i][1].picks.length <= 1) continue
        if (dayIdx != null && i !== dayIdx) continue
        for (const p of days[i][1].picks) {
          const matches = m == null ? pushPull(p.ex) === 1 : (credits(p.ex).get(m) ?? 0) > 0
          if (matches) candidates.push([i, p])
        }
      }
      if (candidates.length === 0) break
      candidates.sort((a, b) => {
        const rankDiff = this.removalRank(a[1]) - this.removalRank(b[1])
        if (rankDiff !== 0) return rankDiff
        const ca = m == null ? 1.0 : (credits(a[1].ex).get(m) ?? 0)
        const cb = m == null ? 1.0 : (credits(b[1].ex).get(m) ?? 0)
        if (cb !== ca) return cb - ca
        if (b[0] !== a[0]) return b[0] - a[0]
        return days[b[0]][1].picks.indexOf(b[1]) - days[a[0]][1].picks.indexOf(a[1])
      })
      const victim = candidates[0]
      const idx = days[victim[0]][1].picks.indexOf(victim[1])
      if (idx >= 0) this.removePick(days[victim[0]][1].picks, idx)
      vol = new Volume(days, (mu) => this.target(mu))
    }
    vol = this.fill(days, reserved, vol)
    // Still short of a minimum: trade one whole lift that does not feed the short muscle for more sets of those that do.
    for (let round = 0; round < 20; round++) {
      const short = vol.shortfall()
      if (short <= 1e-9) break
      const shortMuscles = vol.shortMuscles()
      const options: [number, Pick][] = []
      for (let i = 0; i < days.length; i++) {
        if (days[i][1].picks.length <= 2) continue
        const [dow, dayBuild] = days[i]
        const keepFirstMain =
          this.goal === 'STRENGTH' && this.resolveStyle(dow) === 'SUPERSET'
            ? dayBuild.picks.find((p) => p.role === 'MAIN') ?? null
            : null
        for (const q of dayBuild.picks) {
          if (q.role === 'FOCUS' || q === keepFirstMain) continue
          let touchesShort = false
          for (const mu of credits(q.ex).keys()) {
            if (shortMuscles.has(mu)) { touchesShort = true; break }
          }
          if (!touchesShort) options.push([i, q])
        }
      }
      options.sort((a, b) => this.removalRank(a[1]) - this.removalRank(b[1]) || b[0] - a[0])
      let improved = false
      for (const [i, q] of options) {
        const snapshot = days.map(([, d]) => d.picks.map((p) => [p, p.sets, p.supersetGroup, p.circuitBlock] as const))
        const idx = days[i][1].picks.indexOf(q)
        if (idx >= 0) this.removePick(days[i][1].picks, idx)
        const trial = this.fill(days, reserved, new Volume(days, (mu) => this.target(mu)))
        if (trial.broken() == null && trial.shortfall() < short - 1e-9) {
          vol = trial
          improved = true
          break
        }
        for (let j = 0; j < days.length; j++) {
          days[j][1].picks.length = 0
          for (const [pick, sets, sg, cb] of snapshot[j]) {
            pick.sets = sets
            pick.supersetGroup = sg
            pick.circuitBlock = cb
            days[j][1].picks.push(pick)
          }
        }
      }
      if (!improved) break
    }
    this.topUpShortDays(days, reserved, vol)
    this.equalizeGroups(days, reserved)
    for (const [, b] of days) {
      this.renumberSupersets(b.picks)
      this.renumberCircuits(b.picks)
    }
  }

  /**
   * When grouped circuits/supersets leave a >25-min request averaging <= SHORT_PLAN_MAX_MINUTES, add extra rounds up
   * to maxSets while every Plan check rule and the day budget hold, so the plan fills the chosen time.
   * Only as a last resort, if the plan still averages <= SHORT_PLAN_MAX_MINUTES, disband circuits one at a time on
   * non-light >30-min days (shortest day first, within budget) so exercises keep normal straight rest instead of 60 s.
   */
  private topUpShortDays(days: [number, DayBuild][], reserved: ReadonlyMap<number, number>, vol: Volume): void {
    if (days.length <= 2) return
    const longRequested = days.every(([dow]) => this.dayMinutes(dow) > SHORT_PLAN_MAX_MINUTES)
    if (!longRequested) return
    const estDay = (dow: number, b: DayBuild) => {
      const resMin = Math.round((reserved.get(dow) ?? 0) / 60)
      return Math.ceil(this.workSeconds(dow, b.picks) / 60) + resMin
    }
    const estTotal = () => days.reduce((sum, [dow, b]) => sum + estDay(dow, b), 0)
    const cap = this.maxSets
    while (estTotal() <= SHORT_PLAN_MAX_MINUTES * days.length) {
      let bestIdx = -1
      let bestGroup: Pick[] | null = null
      let bestScore = -1
      for (let i = 0; i < days.length; i++) {
        const [dow, b] = days[i]
        if (b.light || this.resolveStyle(dow) === 'STRAIGHT') continue
        const spare = this.budgetSeconds(dow, reserved.get(dow)!) - this.workSeconds(dow, b.picks)
        const seen = new Set<Pick>()
        for (const p of b.picks) {
          if (seen.has(p)) continue
          const group = this.groupOf(b.picks, p)
          for (const m of group) seen.add(m)
          if (!group.every((m) => m.role !== 'COND' && m.role !== 'FINISHER' && m.sets < cap)) continue
          if (this.groupAddSeconds(dow, b.picks, group) > spare) continue
          if (!vol.fitsGroup(i, group)) continue
          const minSets = Math.min(...group.map((m) => m.sets))
          const score = (cap - minSets) * 100 + (cap * group.length) - i
          if (score > bestScore) {
            bestIdx = i
            bestGroup = group
            bestScore = score
          }
        }
      }
      if (!bestGroup) break
      for (const m of bestGroup) {
        m.sets++
        vol.add(bestIdx, m, 1)
      }
    }
    while (estTotal() <= SHORT_PLAN_MAX_MINUTES * days.length) {
      let bestDay: DayBuild | null = null
      let bestBlock: string | null = null
      let bestDayEst = Number.MAX_SAFE_INTEGER
      for (let i = 0; i < days.length; i++) {
        const [dow, b] = days[i]
        if (b.light || this.dayMinutes(dow) <= 30) continue
        const curEst = estDay(dow, b)
        const curSec = this.workSeconds(dow, b.picks)
        const budget = this.budgetSeconds(dow, reserved.get(dow)!)
        const seenBlocks = new Set<string>()
        for (const p of b.picks) {
          const blk = p.circuitBlock
          if (blk == null || seenBlocks.has(blk)) continue
          seenBlocks.add(blk)
          const members = b.picks.filter((m) => m.circuitBlock === blk)
          if (members.length < 2) continue
          for (const m of members) m.circuitBlock = null
          const nextSec = this.workSeconds(dow, b.picks)
          for (const m of members) m.circuitBlock = blk
          if (nextSec > curSec && nextSec <= budget && curEst < bestDayEst) {
            bestDayEst = curEst
            bestDay = b
            bestBlock = blk
            break
          }
        }
      }
      if (!bestDay || bestBlock == null) break
      for (const m of bestDay.picks) {
        if (m.circuitBlock === bestBlock) m.circuitBlock = null
      }
    }
  }

  /** Adds one set at a time to the pick that best closes the gap to the weekly targets while every rule still holds. */
  private fill(days: [number, DayBuild][], reserved: ReadonlyMap<number, number>, start: Volume): Volume {
    const vol = start
    const cap = this.maxSets
    while (true) {
      const pushWanted = vol.pushBlocked() && days.some(([dow, b], i) => {
        const spare = this.budgetSeconds(dow, reserved.get(dow)!) - this.workSeconds(dow, b.picks)
        return !b.light && b.picks.some((p) => {
          if (p.role === 'COND' || p.role === 'FINISHER' || pushPull(p.ex) !== 1) return false
          const group = this.groupOf(b.picks, p)
          return group.every((m) => m.role !== 'COND' && m.role !== 'FINISHER' && m.sets < cap) &&
            this.groupAddSeconds(dow, b.picks, group) <= spare &&
            vol.gain(p) >= 0.5 - 1e-9 &&
            vol.fitsMuscleRulesGroup(i, group)
        })
      })
      let bestIdx = -1
      let bestPick: Pick | null = null
      let bestGain = 0
      for (let i = 0; i < days.length; i++) {
        const [dow, b] = days[i]
        if (b.light) continue
        const spare = this.budgetSeconds(dow, reserved.get(dow)!) - this.workSeconds(dow, b.picks)
        for (const p of b.picks) {
          if (p.role === 'COND' || p.role === 'FINISHER' || p.sets >= cap) continue
          const group = this.groupOf(b.picks, p)
          if (!group.every((m) => m.role !== 'COND' && m.role !== 'FINISHER' && m.sets < cap)) continue
          if (this.groupAddSeconds(dow, b.picks, group) > spare) continue
          let g = vol.gain(p)
          const boosted = g < 0.5 - 1e-9 && pushWanted && pushPull(p.ex) === -1
          if (boosted) g = 0.5
          if (g < 0.5 - 1e-9 || !vol.fitsGroup(i, group)) continue
          const baseGain = group.length === 1
            ? g
            : group.reduce((s, m) => s + (m === p && boosted ? 0.5 : vol.gain(m)), 0) / group.length
          const mevPart = this.resolveStyle(dow) !== 'STRAIGHT'
            ? group.reduce((s, m) => s + vol.mevGain(m), 0) / group.length
            : 0
          const rankGain = baseGain + mevPart * 10
          if (bestPick == null || rankGain > bestGain + 1e-9) {
            bestIdx = i
            bestPick = p
            bestGain = rankGain
          }
        }
      }
      if (!bestPick) break
      const bestGroup = this.groupOf(days[bestIdx][1].picks, bestPick)
      for (const m of bestGroup) {
        m.sets++
        vol.add(bestIdx, m, 1)
      }
    }
    return vol
  }

  private blockFor(picks: readonly Pick[], p: Pick): string {
    if (p.role === 'FINISHER') return FINISHER_BLOCK
    if (p.circuitBlock != null) {
      const count = picks.filter((x) => x.circuitBlock === p.circuitBlock).length
      if (count >= 2) return p.circuitBlock
    }
    return 'Main'
  }

  private isCircuit(picks: readonly Pick[], p: Pick): boolean {
    if (p.role === 'COND' || p.role === 'FINISHER') return true
    if (p.circuitBlock != null) {
      return picks.filter((x) => x.circuitBlock === p.circuitBlock).length >= 2
    }
    return false
  }

  private item(d: number, picks: readonly Pick[], p: Pick): PlanDraftItem {
    const [lo, hi] = this.repRangeFor(p)
    const e = p.ex
    let repsMin: number | null = null
    let repsMax: number | null = null
    let seconds: number | null = null
    switch (e.kind) {
      case 'HOLD': {
        const t = this.tierOf(e)
        seconds = t === 'FOUNDATION' ? 20 : t === 'INTERMEDIATE' ? 30 : 45
        break
      }
      case 'REPS':
        repsMin = Math.max(lo, 6)
        repsMax = Math.max(hi, repsMin + 2)
        break
      case 'WEIGHTED':
        repsMin = lo
        repsMax = hi
        break
    }
    return {
      exerciseId: e.id,
      name: e.name,
      kind: e.kind,
      muscles: [...e.primary],
      block: this.blockFor(picks, p),
      circuit: this.isCircuit(picks, p),
      supersetGroup: p.supersetGroup,
      sets: p.sets,
      repsMin,
      repsMax,
      seconds,
      weightKg: null,
      restSeconds: this.restFor(d, picks, p),
      note: '',
      videoUrl: null,
    }
  }

  private warmUpItems(focus: DayFocus, used: Set<string>): PlanDraftItem[] {
    if (!this.warmUp) return []
    const byId = new Map(this.eligible.map((e) => [e.id, e]))
    return warmUpIdsFor(focus)
      .map((id) => byId.get(id))
      .filter((e): e is ExerciseEntity => e != null && !used.has(e.id) && e.kind === 'REPS')
      .slice(0, 2)
      .map((e) => ({
        exerciseId: e.id,
        name: e.name,
        kind: e.kind,
        muscles: [...e.primary],
        block: WARM_UP_BLOCK,
        circuit: false,
        supersetGroup: null,
        sets: 2,
        repsMin: 10,
        repsMax: 15,
        seconds: null,
        weightKg: null,
        restSeconds: CONDITIONING_REST,
        note: '',
        videoUrl: null,
      }))
  }

  private coolDownItems(used: Set<string>, trained: Set<Muscle>): PlanDraftItem[] {
    if (!this.coolDown) return []
    const byId = new Map(this.stretches.map((e) => [e.id, e]))
    const relevance = (s: ExerciseEntity) =>
      s.primary.filter((m) => trained.has(m)).length * 2 + s.secondary.filter((m) => trained.has(m)).length
    return COOL_DOWN_IDS
      .map((id) => byId.get(id))
      .filter((e): e is ExerciseEntity => e != null && !used.has(e.id))
      .sort((a, b) => relevance(b) - relevance(a))
      .slice(0, 2)
      .map((e) => ({
        exerciseId: e.id,
        name: e.name,
        kind: e.kind,
        muscles: [...e.primary],
        block: COOL_DOWN_BLOCK,
        circuit: false,
        supersetGroup: null,
        sets: 1,
        repsMin: null,
        repsMax: null,
        seconds: 30,
        weightKg: null,
        restSeconds: CONDITIONING_REST,
        note: '',
        videoUrl: null,
      }))
  }

  private workSeconds(d: number, picks: readonly Pick[]): number {
    return groupedWorkSeconds(
      picks.map((p) => ({
        sets: p.sets,
        restSeconds: this.restFor(d, picks, p),
        block: this.blockFor(picks, p),
        circuit: this.isCircuit(picks, p),
        supersetGroup: p.supersetGroup,
      })),
    )
  }

  private orderRank(p: Pick): number {
    if (p.role === 'FINISHER') return 3
    const pattern = this.prof(p.ex).pattern
    return isCore(pattern) ? 2 : isCompoundExercise(p.ex) ? 0 : 1
  }

  private warmFor(day: DayBuild): PlanDraftItem[] {
    return this.warmUpItems(day.focus, new Set(day.picks.map((p) => p.ex.id)))
  }

  private coolFor(day: DayBuild): PlanDraftItem[] {
    return this.coolDownItems(new Set(day.picks.map((p) => p.ex.id)), new Set(day.picks.flatMap((p) => p.ex.primary)))
  }

  /** Turns a day's picks (already sized to the time by allocate) into a draft day: big lifts first, core, then finisher (unless paired/circuited). */
  private finalizeDay(d: number, day: DayBuild): PlanDraftDay | null {
    if (day.picks.length === 0) return null
    const warm = this.warmFor(day)
    const cool = this.coolFor(day)
    if (this.resolveStyle(d) === 'STRAIGHT') {
      day.picks.sort((a, b) => this.orderRank(a) - this.orderRank(b))
    }
    return {
      dayOfWeek: d,
      title: titleFor(day.focus),
      isRest: false,
      note: '',
      items: [...warm, ...day.picks.map((p) => this.item(d, day.picks, p)), ...cool],
    }
  }

  run(): BuiltPlan {
    const sel = [...this.days].filter((d) => d >= 1 && d <= 7).sort((a, b) => a - b)
    const name = 'Custom plan'
    if (sel.length === 0) {
      return {
        draft: { name, description: PROGRESSION_NOTE, days: [] },
        days: new Map(),
        why: new Map(),
      }
    }
    const layout = this.assign(sel)
    const finisher = this.cardio === 'FINISHER' || (this.cardio === 'OWN_DAY' && sel.length < 3)
    const builds = new Map<number, DayBuild>()
    for (const [d, f] of layout) {
      const light = sel.length === 7 && d === sel[sel.length - 1]
      let day = this.buildDay(f, light, finisher)
      // A conditioning day nothing fits (e.g. knee injury, bodyweight only) becomes a light full-body day.
      if (day.picks.length === 0 && f === 'CONDITIONING') {
        day = this.buildDay('FULL_BODY', true, false)
      }
      builds.set(d, day)
    }
    for (const [d, b] of builds) {
      b.picks.sort((a, c) => this.orderRank(a) - this.orderRank(c))
      const style = this.resolveStyle(d)
      if (style === 'SUPERSET') {
        this.pairSupersets(b.picks)
      } else if (style === 'CIRCUIT') {
        this.groupCircuits(b.picks)
      }
    }
    const reserved = new Map<number, number>()
    for (const [d, b] of builds) {
      reserved.set(d, ((this.warmFor(b).length > 0 ? 5 : 0) + (this.coolFor(b).length > 0 ? 5 : 0)) * 60)
    }
    this.allocate(builds, reserved)
    const days: PlanDraftDay[] = []
    const focusByDay = new Map<number, DayFocus>()
    const why = new Map<number, WhyTag[]>()
    for (const [d, b] of builds) {
      const out = this.finalizeDay(d, b)
      if (!out) continue
      days.push(out)
      focusByDay.set(d, b.focus)
      why.set(d, [...b.tags])
    }
    return {
      draft: { name, description: PROGRESSION_NOTE, days },
      days: focusByDay,
      why,
    }
  }
}

/** Running weekly totals for the plan being built, so each candidate set is checked in a few steps. */
class Volume {
  private readonly week = new Map<Muscle, number>()
  private readonly dayPrimary: Map<Muscle, number>[]
  private push = 0
  private pull = 0
  private readonly neighbours: number[][]

  constructor(
    private readonly days: readonly [number, DayBuild][],
    private readonly targetOf: (m: Muscle) => number,
  ) {
    this.dayPrimary = days.map(() => new Map<Muscle, number>())
    const indexByDow = new Map<number, number>(days.map(([dow], i) => [dow, i]))
    this.neighbours = days.map(([dow], i) => {
      const prev = indexByDow.get(dow === 1 ? 7 : dow - 1)
      const next = indexByDow.get(dow === 7 ? 1 : dow + 1)
      return [...new Set([prev, next].filter((idx): idx is number => idx != null && idx !== i))]
    })
    for (let i = 0; i < days.length; i++) {
      for (const p of days[i][1].picks) this.add(i, p, p.sets)
    }
  }

  add(dayIdx: number, p: Pick, n: number): void {
    for (const [m, c] of credits(p.ex)) {
      this.week.set(m, (this.week.get(m) ?? 0) + c * n)
      if (c >= 1.0) this.dayPrimary[dayIdx].set(m, (this.dayPrimary[dayIdx].get(m) ?? 0) + n)
    }
    const pp = pushPull(p.ex)
    if (pp === 1) this.push += n
    else if (pp === -1) this.pull += n
  }

  private overlaps(dayIdx: number, m: Muscle, after: number): boolean {
    return after >= OVERLAP_SETS && this.neighbours[dayIdx].some((nb) => (this.dayPrimary[nb].get(m) ?? 0) >= OVERLAP_SETS)
  }

  /** True when one more set of p on dayIdx keeps MRV, day-muscle limit, and back-to-back overlap. */
  fitsMuscleRules(dayIdx: number, p: Pick): boolean {
    return this.fitsMuscleRulesGroup(dayIdx, [p])
  }

  /** True when +1 set for every pick in group on dayIdx keeps MRV, day-muscle limit, and back-to-back overlap. */
  fitsMuscleRulesGroup(dayIdx: number, group: readonly Pick[]): boolean {
    const weekDelta = new Map<Muscle, number>()
    const dayDelta = new Map<Muscle, number>()
    for (const p of group) {
      for (const [m, c] of credits(p.ex)) {
        weekDelta.set(m, (weekDelta.get(m) ?? 0) + c)
        if (c >= 1.0) dayDelta.set(m, (dayDelta.get(m) ?? 0) + 1)
      }
    }
    for (const [m, d] of weekDelta) {
      if ((this.week.get(m) ?? 0) + d > landmarkFor(m).mrv + 1e-9) return false
    }
    for (const [m, d] of dayDelta) {
      const after = (this.dayPrimary[dayIdx].get(m) ?? 0) + d
      if (after > DAY_MUSCLE_LIMIT || this.overlaps(dayIdx, m, after)) return false
    }
    return true
  }

  /** True when one more set of p on dayIdx keeps every Plan check rule. */
  fits(dayIdx: number, p: Pick): boolean {
    return this.fitsGroup(dayIdx, [p])
  }

  /** True when +1 set for every pick in group on dayIdx keeps every Plan check rule. */
  fitsGroup(dayIdx: number, group: readonly Pick[]): boolean {
    if (!this.fitsMuscleRulesGroup(dayIdx, group)) return false
    let nextPush = this.push
    let nextPull = this.pull
    for (const p of group) {
      const pp = pushPull(p.ex)
      if (pp === 1) nextPush++
      else if (pp === -1) nextPull++
    }
    if (nextPush === this.push) return true
    return !pushPullBroken(nextPush, nextPull)
  }

  /** How much one more set of p closes the gap to the weekly targets. */
  gain(p: Pick): number {
    let sum = 0
    for (const [m, c] of credits(p.ex)) {
      sum += Math.min(c, Math.max(0, this.targetOf(m) - (this.week.get(m) ?? 0)))
    }
    return sum
  }

  /** Portion of one more set of p that closes a remaining BELOW_MEV deficit. */
  mevGain(p: Pick): number {
    let sum = 0
    for (const [m, c] of credits(p.ex)) {
      if (!BELOW_MEV_MUSCLES.has(m)) continue
      sum += Math.min(c, Math.max(0, landmarkFor(m).mev - (this.week.get(m) ?? 0)))
    }
    return sum
  }

  /** Sets missing to reach each tracked muscle's minimum, summed over the week. */
  shortfall(): number {
    let sum = 0
    for (const m of BELOW_MEV_MUSCLES) {
      sum += Math.max(0, landmarkFor(m).mev - (this.week.get(m) ?? 0))
    }
    return sum
  }

  shortMuscles(): Set<Muscle> {
    const out = new Set<Muscle>()
    for (const m of BELOW_MEV_MUSCLES) {
      if ((this.week.get(m) ?? 0) < landmarkFor(m).mev - 1e-9) out.add(m)
    }
    return out
  }

  /** A push set would still help but only the push/pull balance stops it, so a pull set is worth adding. */
  pushBlocked(): boolean {
    return pushPullBroken(this.push + 1, this.pull)
  }

  /** The first broken rule a pick could fix by leaving: [muscle or null for push/pull, day index or null for the week]. */
  broken(): [Muscle | null, number | null] | null {
    for (const m of MUSCLES) {
      if ((this.week.get(m) ?? 0) > landmarkFor(m).mrv + 1e-9) return [m, null]
    }
    for (let i = 0; i < this.days.length; i++) {
      for (const m of MUSCLES) {
        const v = this.dayPrimary[i].get(m) ?? 0
        if (v > DAY_MUSCLE_LIMIT || this.overlaps(i, m, v)) return [m, i]
      }
    }
    if (pushPullBroken(this.push, this.pull)) return [null, null]
    return null
  }
}

export const PlanBuilder = {
  COOL_DOWN_BLOCK,
  FINISHER_BLOCK,
  CIRCUIT_BLOCKS,
  SUPERSET_POST_ROUND_SHORT,
  CIRCUIT_POST_ROUND_SHORT,
  CIRCUIT_POST_ROUND,
  NO_PAIR_IDS,
  PROGRESSION_NOTE,
  STAPLE_IDS,
  safePair,
  isFly,
  isCompoundExercise,
  fitsItsMovement,
  spreadDays,
  build: buildPlan,
  alternatives,
  estimateMinutes,
} as const

export * from './plan-answers'
