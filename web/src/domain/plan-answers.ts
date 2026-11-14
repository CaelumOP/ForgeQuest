// What the person told the plan builder, saved (as JSON under the `plan_builder_answers` setting) so a rebuild starts
// from the same answers. Mirrors PlanInput without the exercise library. Not part of backups. Android: PlanAnswers.kt.
import {
  EQUIPMENT_META,
  expandLegacyEquipment,
  isEquipment,
  parseEquipmentSet,
  presetEquipment,
  type Equipment,
} from './exercise-swap-engine'
import { isInjury, type Injury } from './injury'
import { isMuscle, type Muscle } from './muscle'
import {
  CARDIOS, EXPERIENCES, REP_PREFS, REST_PREFS, spreadDays, TRAIN_GOALS,
  type Cardio, type Experience, type PlanInput, type RepPref, type RestPref, type TrainGoal,
} from './plan-builder'

export type PlanAnswers = {
  goal: TrainGoal
  days: Set<number>
  minutes: number
  equipment: Set<Equipment>
  experience: Experience
  injuries: Set<Injury>
  avoidIds: Set<string>
  pushUpMax: number | null
  pullUpMax: number | null
  focus: Set<Muscle>
  minutesByDay: Map<number, number>
  cardio: Cardio
  warmUp: boolean
  coolDown: boolean
  holdsOk: boolean
  reps: RepPref
  rest: RestPref
}

export function defaultPlanAnswers(): PlanAnswers {
  return {
    goal: 'GENERAL',
    days: spreadDays(3),
    minutes: 45,
    equipment: presetEquipment('CALISTHENICS_PARK'),
    experience: 'SOME',
    injuries: new Set(),
    avoidIds: new Set(),
    pushUpMax: null,
    pullUpMax: null,
    focus: new Set(),
    minutesByDay: new Map(),
    cardio: 'NONE',
    warmUp: true,
    coolDown: false,
    holdsOk: true,
    reps: 'AUTO',
    rest: 'AUTO',
  }
}

export function planAnswersToInput(answers: Partial<PlanAnswers> = {}, offset = 0): Required<PlanInput> {
  const base = defaultPlanAnswers()
  const days = new Set<number>(answers.days ?? base.days)
  const equipment = expandLegacyEquipment(answers.equipment ?? base.equipment)
  equipment.add('BODYWEIGHT')
  const rawMinutesByDay = answers.minutesByDay ?? base.minutesByDay
  const entries: [number, number][] =
    rawMinutesByDay instanceof Map
      ? [...rawMinutesByDay.entries()]
      : Object.entries(rawMinutesByDay).map(([k, v]) => [Number(k), Number(v)])
  const minutesByDay = new Map<number, number>(entries.filter(([k]) => days.has(k)))
  return {
    goal: answers.goal ?? base.goal,
    days,
    minutes: answers.minutes ?? base.minutes,
    equipment,
    experience: answers.experience ?? base.experience,
    injuries: new Set<Injury>(answers.injuries ?? base.injuries),
    avoidIds: new Set<string>(answers.avoidIds ?? base.avoidIds),
    pushUpMax: answers.pushUpMax !== undefined ? answers.pushUpMax : base.pushUpMax,
    pullUpMax: answers.pullUpMax !== undefined ? answers.pullUpMax : base.pullUpMax,
    focus: new Set<Muscle>(answers.focus ?? base.focus),
    minutesByDay,
    cardio: answers.cardio ?? base.cardio,
    warmUp: answers.warmUp ?? base.warmUp,
    coolDown: answers.coolDown ?? base.coolDown,
    holdsOk: answers.holdsOk ?? base.holdsOk,
    reps: answers.reps ?? base.reps,
    rest: answers.rest ?? base.rest,
    offset,
  }
}

export { planAnswersToInput as toInput }

const isIn = <T extends string>(list: readonly T[], v: unknown): v is T =>
  typeof v === 'string' && (list as readonly string[]).includes(v)

const normalizeEquipment = (eq: Iterable<Equipment>): Equipment[] => {
  const expanded = expandLegacyEquipment(eq)
  expanded.add('BODYWEIGHT')
  return [...expanded].filter((e) => !EQUIPMENT_META[e].legacy)
}

export function encodePlanAnswers(answers: PlanAnswers): string {
  const minutesByDayObj: Record<string, number> = {}
  for (const [k, v] of answers.minutesByDay) {
    minutesByDayObj[String(k)] = v
  }
  return JSON.stringify({
    goal: answers.goal,
    days: [...answers.days],
    minutes: answers.minutes,
    equipment: normalizeEquipment(answers.equipment),
    experience: answers.experience,
    injuries: [...answers.injuries],
    avoidIds: [...answers.avoidIds],
    pushUpMax: answers.pushUpMax,
    pullUpMax: answers.pullUpMax,
    focus: [...answers.focus],
    minutesByDay: minutesByDayObj,
    cardio: answers.cardio,
    warmUp: answers.warmUp,
    coolDown: answers.coolDown,
    holdsOk: answers.holdsOk,
    reps: answers.reps,
    rest: answers.rest,
  })
}

/** Null for nothing saved or text that no longer parses (a changed enum, a damaged file): the screen then starts fresh. */
export function decodePlanAnswers(raw: string | null | undefined): PlanAnswers | null {
  if (!raw || raw.trim() === '') return null
  try {
    const parsed: unknown = JSON.parse(raw)
    if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) return null
    const obj = parsed as Record<string, unknown>
    const d = defaultPlanAnswers()

    if (obj.goal !== undefined && !isIn(TRAIN_GOALS, obj.goal)) return null
    if (obj.experience !== undefined && !isIn(EXPERIENCES, obj.experience)) return null
    if (obj.cardio !== undefined && !isIn(CARDIOS, obj.cardio)) return null
    if (obj.reps !== undefined && !isIn(REP_PREFS, obj.reps)) return null
    if (obj.rest !== undefined && !isIn(REST_PREFS, obj.rest)) return null

    if (obj.days !== undefined) {
      if (!Array.isArray(obj.days) || !obj.days.every((x) => typeof x === 'number' && Number.isInteger(x))) return null
      d.days = new Set(obj.days)
    }
    if (obj.minutes !== undefined) {
      if (typeof obj.minutes !== 'number' || !Number.isFinite(obj.minutes)) return null
      d.minutes = Math.trunc(obj.minutes)
    }
    if (obj.equipment !== undefined) {
      if (Array.isArray(obj.equipment)) {
        if (!obj.equipment.every(isEquipment)) return null
        d.equipment = new Set(normalizeEquipment(obj.equipment))
      } else if (typeof obj.equipment === 'string') {
        d.equipment = parseEquipmentSet(obj.equipment)
      } else {
        return null
      }
    } else if (typeof obj.equipmentPreset === 'string' || typeof obj.preset === 'string') {
      d.equipment = parseEquipmentSet(String(obj.equipmentPreset ?? obj.preset))
    }
    if (obj.injuries !== undefined) {
      if (!Array.isArray(obj.injuries) || !obj.injuries.every((x): x is Injury => typeof x === 'string' && isInjury(x))) return null
      d.injuries = new Set(obj.injuries)
    }
    if (obj.avoidIds !== undefined) {
      if (!Array.isArray(obj.avoidIds) || !obj.avoidIds.every((x): x is string => typeof x === 'string')) return null
      d.avoidIds = new Set(obj.avoidIds)
    }
    if (obj.pushUpMax !== undefined) {
      if (obj.pushUpMax !== null && (typeof obj.pushUpMax !== 'number' || !Number.isFinite(obj.pushUpMax))) return null
      d.pushUpMax = obj.pushUpMax === null ? null : Math.trunc(obj.pushUpMax)
    }
    if (obj.pullUpMax !== undefined) {
      if (obj.pullUpMax !== null && (typeof obj.pullUpMax !== 'number' || !Number.isFinite(obj.pullUpMax))) return null
      d.pullUpMax = obj.pullUpMax === null ? null : Math.trunc(obj.pullUpMax)
    }
    if (obj.focus !== undefined) {
      if (!Array.isArray(obj.focus) || !obj.focus.every((x): x is Muscle => typeof x === 'string' && isMuscle(x))) return null
      d.focus = new Set(obj.focus)
    }
    if (obj.minutesByDay !== undefined) {
      if (typeof obj.minutesByDay !== 'object' || obj.minutesByDay === null || Array.isArray(obj.minutesByDay)) return null
      const map = new Map<number, number>()
      for (const [k, v] of Object.entries(obj.minutesByDay as Record<string, unknown>)) {
        const day = Number(k)
        if (!Number.isInteger(day) || typeof v !== 'number' || !Number.isFinite(v)) return null
        map.set(day, Math.trunc(v))
      }
      d.minutesByDay = map
    }
    if (obj.goal !== undefined) d.goal = obj.goal
    if (obj.experience !== undefined) d.experience = obj.experience
    if (obj.cardio !== undefined) d.cardio = obj.cardio
    if (obj.reps !== undefined) d.reps = obj.reps
    if (obj.rest !== undefined) d.rest = obj.rest
    if (obj.warmUp !== undefined) {
      if (typeof obj.warmUp !== 'boolean') return null
      d.warmUp = obj.warmUp
    }
    if (obj.coolDown !== undefined) {
      if (typeof obj.coolDown !== 'boolean') return null
      d.coolDown = obj.coolDown
    }
    if (obj.holdsOk !== undefined) {
      if (typeof obj.holdsOk !== 'boolean') return null
      d.holdsOk = obj.holdsOk
    }
    return d
  } catch {
    return null
  }
}

export const PlanAnswers = {
  defaults: defaultPlanAnswers,
  toInput: planAnswersToInput,
  encode: encodePlanAnswers,
  decode: decodePlanAnswers,
} as const
