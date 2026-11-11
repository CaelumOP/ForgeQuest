// Pure helpers and string-key mappings for PlanBuilderScreen (mirrors PlanBuilderLabels.kt, PlanLocalize.kt, PlanBuilderScreen.kt).
import { getLang, t, type Lang } from '../../i18n'
import { configuredEquipment, type Settings } from '../../data/settings'
import type { ExerciseEntity, PlanDraft, PlanDraftItem } from '../../domain/entities'
import {
  EQUIPMENT_PRESETS, PRESET_INFO, encodePreset, presetEquipment,
  type Equipment, type EquipmentPreset,
} from '../../domain/exercise-swap-engine'
import { MUSCLES, type Muscle } from '../../domain/muscle'
import {
  INJURIES, PlanBuilder, defaultPlanAnswers, decodePlanAnswers, planAnswersToInput, spreadDays,
  type BuiltPlan, type Cardio, type DayFocus, type Experience, type Injury,
  type PlanAnswers, type RepPref, type RestPref, type TrainGoal, type WhyTag,
} from '../../domain/plan-builder'
import { dayShort } from '../workouts/helpers'

export const FOCUS_MUSCLES: readonly Muscle[] = [
  'CHEST', 'SHOULDERS', 'BICEPS', 'TRICEPS', 'ABS',
  'UPPER_BACK', 'GLUTES', 'QUADS', 'HAMSTRINGS', 'CALVES',
]

export const DAY_COUNT_OPTIONS = [2, 3, 4, 5, 6] as const
export const MINUTE_OPTIONS = [20, 30, 45, 60] as const

export function goalKey(g: TrainGoal): string {
  switch (g) {
    case 'STRENGTH': return 'pb_goal_strength'
    case 'MUSCLE': return 'pb_goal_muscle'
    case 'FAT_LOSS': return 'pb_goal_fat'
    case 'ENDURANCE': return 'pb_goal_endurance'
    case 'GENERAL': return 'pb_goal_general'
  }
}

export function experienceKey(e: Experience): string {
  switch (e) {
    case 'NEW': return 'pb_exp_new'
    case 'SOME': return 'pb_exp_some'
    case 'SEASONED': return 'pb_exp_seasoned'
  }
}

export function injuryKey(i: Injury): string {
  switch (i) {
    case 'SHOULDER': return 'pb_injury_shoulder'
    case 'KNEE': return 'pb_injury_knee'
    case 'LOWER_BACK': return 'pb_injury_lower_back'
    case 'WRIST': return 'pb_injury_wrist'
    case 'ELBOW': return 'pb_injury_elbow'
    case 'NECK': return 'pb_injury_neck'
  }
}

export function repPrefKey(r: RepPref): string {
  switch (r) {
    case 'AUTO': return 'pb_auto'
    case 'LOW': return 'pb_reps_low'
    case 'MID': return 'pb_reps_mid'
    case 'HIGH': return 'pb_reps_high'
  }
}

export function restPrefKey(r: RestPref): string {
  switch (r) {
    case 'AUTO': return 'pb_auto'
    case 'SHORT': return 'pb_rest_short'
    case 'LONG': return 'pb_rest_long'
  }
}

export function cardioKey(c: Cardio): string {
  switch (c) {
    case 'NONE': return 'pb_cardio_none'
    case 'FINISHER': return 'pb_cardio_finisher'
    case 'OWN_DAY': return 'pb_cardio_own'
  }
}

export function dayFocusKey(d: DayFocus): string {
  switch (d) {
    case 'FULL_BODY': return 'pb_day_full'
    case 'UPPER': return 'pb_day_upper'
    case 'LOWER': return 'pb_day_lower'
    case 'PUSH': return 'pb_day_push'
    case 'PULL': return 'pb_day_pull'
    case 'LEGS': return 'pb_day_legs'
    case 'CONDITIONING': return 'pb_day_cond'
  }
}

export function whyTagKey(w: WhyTag): string {
  switch (w) {
    case 'SKIPPED_FOR_INJURY': return 'pb_why_injury'
    case 'NO_EQUIPMENT': return 'pb_why_equipment'
    case 'FOCUS_BOOST': return 'pb_why_focus'
    case 'TIME_TRIMMED': return 'pb_why_time'
    case 'TIER_FROM_MAX': return 'pb_why_tier'
    case 'HOLD_FALLBACK': return 'pb_why_hold'
    case 'CARDIO_ADDED': return 'pb_why_cardio'
    case 'LIGHT_DAY': return 'pb_why_light'
  }
}

export function sameSet<T>(a: ReadonlySet<T>, b: ReadonlySet<T>): boolean {
  if (a.size !== b.size) return false
  for (const x of a) if (!b.has(x)) return false
  return true
}

/** Preset whose equipment set matches `equipment`, or null for a custom mix. */
export function activeEquipmentPreset(equipment: ReadonlySet<Equipment>): EquipmentPreset | null {
  for (const p of EQUIPMENT_PRESETS) {
    if (sameSet(presetEquipment(p), equipment)) return p
  }
  return null
}

/** Preset encoding if `equipment` matches one, else the comma-joined names (always with BODYWEIGHT). */
export function equipmentSetting(equipment: ReadonlySet<Equipment>): string {
  const all = new Set<Equipment>([...equipment, 'BODYWEIGHT'])
  const preset = activeEquipmentPreset(all)
  return preset ? encodePreset(preset) : [...all].join(',')
}

/** "Home gym" out of "Home gym (barbell, dumbbells, bar)" (Android EquipmentPreset.shortLabel). */
export function presetShortLabel(p: EquipmentPreset, lang: Lang = getLang()): string {
  const full = lang === 'ar' ? PRESET_INFO[p].labelAr : PRESET_INFO[p].labelEn
  return full.split(' (')[0]!
}

/** "3×8-12" for reps, "3×30s" for holds (Android PlanBuilderScreen.kt scheme). */
export function scheme(
  item: Pick<PlanDraftItem, 'sets' | 'repsMin' | 'repsMax' | 'seconds'>,
  unitS = t('unit_s'),
): string {
  const sets = item.sets
  const secs = item.seconds
  if (secs != null) return `${sets}×${secs}${unitS}`
  const lo = item.repsMin
  const hi = item.repsMax
  if (lo != null && hi != null && hi !== lo) return `${sets}×${lo}-${hi}`
  if (lo != null || hi != null) return `${sets}×${lo ?? hi}`
  return `${sets}×`
}

/** Fills in localized day titles, why-tag notes, plan name and description (Android PlanLocalize.kt). */
export function localizeBuiltPlan(
  plan: BuiltPlan,
  goal: TrainGoal,
  tr: (key: string, ...args: (string | number)[]) => string = t,
): PlanDraft {
  const days = plan.draft.days.map((d) => {
    const focus = plan.days.get(d.dayOfWeek)
    const why = (plan.why.get(d.dayOfWeek) ?? []).map((w) => tr(whyTagKey(w))).join(' ')
    return {
      ...d,
      title: focus != null ? tr(dayFocusKey(focus)) : d.title,
      note: why,
    }
  })
  return {
    ...plan.draft,
    name: tr('pb_plan_name', tr(goalKey(goal))),
    description: tr('pb_progression'),
    days,
  }
}

/** Saved answers if present, else defaults with the user's configured equipment (if any). */
export function initialAnswers(s: Settings): PlanAnswers {
  const saved = decodePlanAnswers(s.planBuilderAnswers)
  if (saved) return saved
  const eq = configuredEquipment(s)
  const defaults = defaultPlanAnswers()
  return eq.size > 0
    ? { ...defaults, equipment: new Set<Equipment>([...eq, 'BODYWEIGHT']) }
    : defaults
}

/** Summaries for the six "Fit it to me" deep rows (Android PlanBuilderScreen.kt:378-401). */
export function rowSummaries(
  a: PlanAnswers,
  tr: (key: string, ...args: (string | number)[]) => string = t,
  lang: Lang = getLang(),
) {
  const auto = tr('pb_auto')
  const sep = tr('list_separator')

  const you = a.experience === 'SOME' && a.pushUpMax == null && a.pullUpMax == null
    ? auto
    : tr(experienceKey(a.experience)) + (
      a.pushUpMax != null || a.pullUpMax != null
        ? ` · ${a.pushUpMax ?? '–'} / ${a.pullUpMax ?? '–'}`
        : ''
    )

  const bodyLabels = [
    ...INJURIES.filter((i) => a.injuries.has(i)).map((i) => tr(injuryKey(i))),
    ...MUSCLES.filter((m) => a.focus.has(m)).map((m) => tr('muscle_' + m.toLowerCase())),
  ]
  const body = bodyLabels.length === 0 ? auto : bodyLabels.join(sep)

  const scheduleCustom = !sameSet(a.days, spreadDays(a.days.size)) || a.minutesByDay.size > 0
  const schedule = scheduleCustom
    ? [...a.days].sort((x, y) => x - y).map((d) => dayShort(d, lang)).join(sep)
    : auto

  const gearCustom = activeEquipmentPreset(a.equipment) == null
  const gear = gearCustom ? tr('pb_custom') : auto

  const styleCustom =
    a.reps !== 'AUTO' || a.rest !== 'AUTO' || !a.holdsOk ||
    a.cardio !== 'NONE' || !a.warmUp || a.coolDown
  const style = styleCustom ? tr('pb_custom') : auto

  const avoid = a.avoidIds.size === 0 ? auto : tr('pb_avoid_count', a.avoidIds.size)

  return { you, body, schedule, gear, style, avoid }
}

/** Replaces the exercise at `index` on `dayOfWeek` with the next best one for the same movement (Android PlanBuilderViewModel.swap). */
export function swapBuiltPlanItem(
  current: BuiltPlan,
  answers: PlanAnswers,
  tried: ReadonlyMap<string, ReadonlySet<string>>,
  library: readonly ExerciseEntity[],
  dayOfWeek: number,
  index: number,
): { review: BuiltPlan; tried: Map<string, Set<string>> } {
  const nextTried = new Map<string, Set<string>>()
  for (const [k, v] of tried) nextTried.set(k, new Set(v))

  const day = current.draft.days.find((d) => d.dayOfWeek === dayOfWeek)
  const old = day?.items[index]
  if (!day || !old) return { review: current, tried: nextTried }

  const currentEx = library.find((e) => e.id === old.exerciseId)
  if (!currentEx) return { review: current, tried: nextTried }

  const input = planAnswersToInput(answers)
  const inDay = new Set<string>(day.items.map((it) => it.exerciseId))
  const key = `${dayOfWeek}:${index}`
  const seen = new Set<string>([...(tried.get(key) ?? []), old.exerciseId])

  let next = PlanBuilder.alternatives(currentEx, input, library, new Set([...inDay, ...seen]), 1)[0]
  let nowSeen = seen
  if (!next) {
    // Out of new ones: start the loop again rather than doing nothing.
    nowSeen = new Set<string>([old.exerciseId])
    next = PlanBuilder.alternatives(currentEx, input, library, inDay, 1)[0]
  }
  if (!next) return { review: current, tried: nextTried }

  nextTried.set(key, new Set<string>([...nowSeen, next.id]))
  const hold = next.kind === 'HOLD'
  const item: PlanDraftItem = {
    ...old,
    exerciseId: next.id,
    name: next.name,
    kind: next.kind,
    muscles: [...next.primary],
    repsMin: hold ? null : old.repsMin ?? 8,
    repsMax: hold ? null : old.repsMax ?? 12,
    seconds: hold ? old.seconds ?? 30 : null,
  }
  const nextDays = current.draft.days.map((d) =>
    d.dayOfWeek === dayOfWeek
      ? { ...d, items: d.items.map((it, i) => (i === index ? item : it)) }
      : d,
  )
  return {
    review: { ...current, draft: { ...current.draft, days: nextDays } },
    tried: nextTried,
  }
}
