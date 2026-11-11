// Offline plan generator: picks catalog exercises per movement slot, sets volume from experience, goal and focus, then
// trims each day to the time available. Pure and deterministic (ties break by score, then id); only catalog ids are
// produced so applying the draft never creates custom exercises. Android: PlanBuilder.kt.
import { STRETCH_IDS } from '../data/seed/exercise-catalog'
import type { ExerciseEntity, PlanDraft, PlanDraftDay, PlanDraftItem } from './entities'
import { profileFor, type BiomechanicalProfile, type Equipment, type MovementPattern } from './exercise-swap-engine'
import { tierOf as rawTierOf, TIERS, type Tier } from './exercise-tiers'
import { blocked, INJURIES, isInjury, type Injury } from './injury'
import { MUSCLES, type Muscle } from './muscle'
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

export const DAY_FOCUSES = ['FULL_BODY', 'UPPER', 'LOWER', 'PUSH', 'PULL', 'LEGS', 'CONDITIONING'] as const
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
export const PROGRESSION_NOTE =
  'Progression: when you hit the top of the range on every set, add load or reps. Every 4th week, halve the sets.'

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

/** Minutes the day takes: sets x (40 s + rest) for the work, plus a flat 5 for a warm-up and 5 for a cool-down. */
export function estimateMinutes(day: PlanDraftDay): number {
  const workSeconds = day.items
    .filter((it) => it.block !== WARM_UP_BLOCK && it.block !== COOL_DOWN_BLOCK)
    .reduce((sum, it) => sum + it.sets * (SET_SECONDS + it.restSeconds), 0)
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

type Pick = { ex: ExerciseEntity; role: Role; sets: number }

type DayBuild = {
  focus: DayFocus
  light: boolean
  picks: Pick[]
  tags: Set<WhyTag>
}

const LOAD_EQUIPMENT = new Set<Equipment>(['DUMBBELLS', 'KETTLEBELL', 'BARBELL_RACK', 'CABLES_MACHINES'])
const PUSH_PATTERNS = new Set<MovementPattern>(['HORIZONTAL_PUSH', 'DIP_PUSH', 'VERTICAL_PUSH'])
const PULL_PATTERNS = new Set<MovementPattern>(['VERTICAL_PULL', 'HORIZONTAL_PULL', 'UPPER_BACK_PULL'])

const MAJOR_MUSCLES = new Set<Muscle>([
  'CHEST', 'SHOULDERS', 'BICEPS', 'TRICEPS', 'UPPER_BACK',
  'QUADS', 'HAMSTRINGS', 'GLUTES', 'ABS',
])
const UPPER_MUSCLES = new Set<Muscle>(['CHEST', 'SHOULDERS', 'BICEPS', 'TRICEPS', 'FOREARMS', 'UPPER_BACK', 'TRAPS'])
const LOWER_MUSCLES = new Set<Muscle>(['QUADS', 'HAMSTRINGS', 'GLUTES', 'CALVES', 'ADDUCTORS', 'TIBIALIS'])
const CORE_MUSCLES = new Set<Muscle>(['ABS', 'OBLIQUES', 'LOWER_BACK'])

/** Preference order for the two warm-up moves: bodyweight, easy rep moves only. */
const WARM_UP_IDS = ['jumping_jacks', 'calf_raise', 'hip_bridge', 'half_squat', 'knee_scapular_push_up'] as const

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
      return [main(V_PULL), main(H_PULL), main(BACK_PULL), main(CORE), acc(CURL), acc(HINGE)]
    case 'LEGS':
      return [main(SQUAT), main(HINGE), main(UNILATERAL), main(CORE), acc(KNEE_FLEX), acc(CALF)]
    case 'CONDITIONING':
      return [makeSlot(CONDITIONING, 'COND'), makeSlot(CONDITIONING, 'COND'), makeSlot(CONDITIONING, 'COND'), main(CORE)]
  }
}

function trains(day: DayFocus, m: Muscle): boolean {
  if (day === 'FULL_BODY') return true
  if (day === 'CONDITIONING') return false
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
    this.offset = input.offset ?? 0

    this.allowedEq = new Set<Equipment>([...eq, 'BODYWEIGHT'])
    this.loadEquip = [...eq].some((e) => LOAD_EQUIPMENT.has(e))

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

    this.equipOk = this.lib.filter((e) => this.allowedEq.has(this.prof(e).equipment) && !this.avoidIds.has(e.id))
    this.eligible = this.equipOk.filter((e) => !STRETCH_IDS.has(e.id) && !blocked(e, this.prof(e), this.injuries))
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

  private best(cands: ExerciseEntity[], role: Role): ExerciseEntity {
    const sorted = [...cands].sort((a, b) => this.score(b, role) - this.score(a, role) || byIdAsc(a.id, b.id))
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
        const fresh = cands.filter((e) => !this.weekUsed.has(e.id))
        const pool = fresh.length > 0 ? fresh : cands
        const chosen = this.best(pool, slot.role)
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
      .sort((a, b) => this.score(b, 'ACC') - this.score(a, 'ACC') || byIdAsc(a.id, b.id))
      .slice(0, limit)
  }

  // ---- week layout ----

  private dayMinutes(d: number): number {
    return this.minutesByDay.get(d) ?? this.minutes
  }

  private assign(sel: number[]): Map<number, DayFocus> {
    const own = this.cardio === 'OWN_DAY' && sel.length >= 3
    const extra = sel.length === 7 || own ? sel[sel.length - 1] : null
    const lifting = extra != null ? sel.slice(0, -1) : sel
    const split: DayFocus[] =
      lifting.length <= 3 ? Array.from({ length: lifting.length }, () => 'FULL_BODY')
      : lifting.length === 4 ? ['UPPER', 'LOWER', 'UPPER', 'LOWER']
      : lifting.length === 5 ? ['UPPER', 'LOWER', 'PUSH', 'PULL', 'LEGS']
      : ['PUSH', 'PULL', 'LEGS', 'PUSH', 'PULL', 'LEGS']
    const out = new Map<number, DayFocus>()
    lifting.forEach((d, i) => {
      out.set(d, this.dayMinutes(d) < 30 ? 'FULL_BODY' : split[i])
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
      day.picks.push({ ex, role: slot.role, sets: 3 })
      if (slot.role === 'FOCUS' || ex.primary.some((m) => this.focus.has(m))) day.tags.add('FOCUS_BOOST')
      if (slot.role === 'FINISHER' || slot.role === 'COND') day.tags.add('CARDIO_ADDED')
      const pattern = this.prof(ex).pattern
      if ((PUSH_PATTERNS.has(pattern) && this.pushUpMax != null) || (PULL_PATTERNS.has(pattern) && this.pullUpMax != null)) {
        day.tags.add('TIER_FROM_MAX')
      }
    }
    return day
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

  private baseRest(): number {
    const seconds =
      this.goal === 'STRENGTH' ? 150
      : this.goal === 'MUSCLE' ? 90
      : this.goal === 'FAT_LOSS' ? 45
      : this.goal === 'ENDURANCE' ? 30
      : 75
    const factor = this.rest === 'AUTO' ? 1.0 : this.rest === 'SHORT' ? 0.6 : 1.5
    return clamp(Math.round(seconds * factor), 30, 240)
  }

  private restFor(role: Role): number {
    return role === 'COND' || role === 'FINISHER' ? CONDITIONING_REST : this.baseRest()
  }

  private target(m: Muscle): number {
    const weekly = this.experience === 'NEW' ? 8 : this.experience === 'SOME' ? 12 : 16
    return weekly * (MAJOR_MUSCLES.has(m) ? 1.0 : 0.5) + (this.focus.has(m) ? 4 : 0)
  }

  private assignSets(builds: Iterable<DayBuild>): void {
    const counts = new Map<Muscle, number>()
    for (const b of builds) {
      for (const p of b.picks) {
        if (p.role === 'MAIN' || p.role === 'FOCUS' || p.role === 'ACC') {
          const m = p.ex.primary[0] ?? 'ABS'
          counts.set(m, (counts.get(m) ?? 0) + 1)
        }
      }
    }
    for (const b of builds) {
      for (const p of b.picks) {
        if (p.role === 'COND') {
          p.sets = 3
        } else if (p.role === 'FINISHER') {
          p.sets = 2
        } else {
          const m = p.ex.primary[0] ?? 'ABS'
          p.sets = clamp(Math.round(this.target(m) / counts.get(m)!), 2, 5)
        }
        if (b.light) p.sets = Math.min(p.sets, 3)
      }
    }
  }

  private item(p: Pick): PlanDraftItem {
    const [lo, hi] = p.role === 'COND' || p.role === 'FINISHER' ? [12, 20] : this.repRange()
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
      block: p.role === 'FINISHER' ? FINISHER_BLOCK : 'Main',
      circuit: this.goal === 'FAT_LOSS' || p.role === 'COND' || p.role === 'FINISHER',
      sets: p.sets,
      repsMin,
      repsMax,
      seconds,
      weightKg: null,
      restSeconds: this.restFor(p.role),
      note: '',
      videoUrl: null,
    }
  }

  private warmUpItems(used: Set<string>): PlanDraftItem[] {
    if (!this.warmUp) return []
    const byId = new Map(this.eligible.map((e) => [e.id, e]))
    return WARM_UP_IDS
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

  private workSeconds(picks: Pick[]): number {
    return picks.reduce((sum, p) => sum + p.sets * (SET_SECONDS + this.restFor(p.role)), 0)
  }

  /** Turns a day's picks into a draft day that fits the time: extras and accessories go first, then sets down to 2. */
  private finalizeDay(d: number, day: DayBuild): PlanDraftDay | null {
    if (day.picks.length === 0) return null
    const pickIds = new Set(day.picks.map((p) => p.ex.id))
    const trained = new Set(day.picks.flatMap((p) => p.ex.primary))
    const warm = this.warmUpItems(pickIds)
    const cool = this.coolDownItems(pickIds, trained)
    const reservedSeconds = ((warm.length > 0 ? 5 : 0) + (cool.length > 0 ? 5 : 0)) * 60
    const budgetSeconds = (this.dayMinutes(d) + TOLERANCE_MIN) * 60
    const picks = day.picks
    let trimmed = false
    const dropOrder: Role[] = ['FINISHER', 'ACC', 'FOCUS']
    while (this.workSeconds(picks) + reservedSeconds > budgetSeconds) {
      let drop: number | null = null
      for (const r of dropOrder) {
        for (let i = picks.length - 1; i >= 0; i--) {
          if (picks[i].role === r) {
            drop = i
            break
          }
        }
        if (drop != null) break
      }
      if (drop != null) {
        picks.splice(drop, 1)
      } else {
        let cut: number | null = null
        for (let i = 0; i < picks.length; i++) {
          if (picks[i].sets > 2) {
            if (cut == null || picks[i].sets > picks[cut].sets || (picks[i].sets === picks[cut].sets && i > cut)) {
              cut = i
            }
          }
        }
        if (cut != null) {
          picks[cut].sets--
        } else if (picks.length > 1) {
          picks.pop()
        } else {
          break
        }
      }
      trimmed = true
    }
    if (trimmed) day.tags.add('TIME_TRIMMED')
    return {
      dayOfWeek: d,
      title: titleFor(day.focus),
      isRest: false,
      note: '',
      items: [...warm, ...picks.map((p) => this.item(p)), ...cool],
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
    this.assignSets(builds.values())
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

export const PlanBuilder = {
  COOL_DOWN_BLOCK,
  FINISHER_BLOCK,
  PROGRESSION_NOTE,
  spreadDays,
  build: buildPlan,
  alternatives,
  estimateMinutes,
} as const

export * from './plan-answers'
